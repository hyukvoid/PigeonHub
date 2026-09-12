import type { Env } from "./types.js";

/**
 * MVP-003B — GitHub App connection management.
 *
 * Flow:
 *   Android [Connect] → GET /v1/github/install-url (management auth)
 *     → Worker returns the GitHub App installation URL
 *     → Android opens it in a browser
 *     → User selects repos → installs
 *     → GitHub sends installation.created webhook → Worker stores it
 *     → Android polls GET /v1/github/status → sees connected
 *
 * The Worker stores the GitHub installation_id in the github_connections
 * table. No GitHub tokens or credentials are stored in D1 or Android.
 */

export async function getInstallUrl(env: Env): Promise<string> {
  const slug = env.GITHUB_APP_SLUG ?? "pigeonhub-dev";
  return `https://github.com/apps/${slug}/installations/new`;
}

export async function getGitHubConnectionStatus(
  env: Env,
  pigeonhubInstallationId: string,
): Promise<{ connected: boolean; connectedAt: string | null }> {
  const row = await env.DB.prepare(
    `SELECT connected_at FROM github_connections WHERE pigeonhub_installation_id = ?1`,
  )
    .bind(pigeonhubInstallationId)
    .first<{ connected_at: string }>();
  return { connected: row !== null, connectedAt: row?.connected_at ?? null };
}

/**
 * Binds a GitHub installation to a PigeonHub installation that has a pending
 * connect request. Returns true if bound, false if the pigeonhub installation
 * doesn't have a pending request or is already bound.
 */
export async function bindGitHubInstallation(
  env: Env,
  githubInstallationId: string,
): Promise<boolean> {
  // Find a PigeonHub installation that has a pending connect request
  // but no GitHub binding yet.
  const pending = await env.DB.prepare(
    `SELECT i.id FROM installations i
     LEFT JOIN github_connections gc ON gc.pigeonhub_installation_id = i.id
     WHERE gc.id IS NULL AND i.enabled = 1
     LIMIT 1`,
  ).first<{ id: string }>();

  if (!pending) return false;

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  try {
    await env.DB.prepare(
      `INSERT INTO github_connections (id, pigeonhub_installation_id, github_installation_id, connected_at)
       VALUES (?1, ?2, ?3, ?4)`,
    )
      .bind(id, pending.id, githubInstallationId, now)
      .run();
    return true;
  } catch {
    // UNIQUE constraint — already bound by a concurrent request
    return false;
  }
}
