import type { Env } from "./types.js";

/**
 * MVP-003B — GitHub App connection management.
 *
 * The GitHub App installation is the stable OWNER identity; PigeonHub
 * installations (devices) are FAN-OUT MEMBERS of it:
 *
 *   GitHub installation → github_connections (membership set)
 *     → every active PigeonHub installation of that owner
 *     → common delivery pipeline → FCM per device
 *
 * Flow:
 *   Android [Connect] → GET /v1/github/install-url (management auth)
 *     → Worker returns the GitHub App installation URL
 *     → Android opens it in a browser
 *     → User selects repos → installs
 *     → GitHub sends installation.created webhook → Worker adds every
 *       active installation as a member (beta: single owner)
 *     → Android polls GET /v1/github/status → sees connected
 *
 * Reconnect rules (the reason this is not a 1:1 binding):
 *   - Connecting a new device never steals another device's membership.
 *   - A reinstalled / newly registered device joins on its next status
 *     poll while the beta has exactly one GitHub installation, so
 *     reinstall and additional devices never require re-connecting
 *     GitHub or disrupt other devices.
 *   - Stale/UNREGISTERED devices fail only their own delivery leg.
 *
 * The Worker stores only membership rows. No GitHub tokens or credentials
 * are stored in D1 or Android.
 */

export async function getInstallUrl(env: Env): Promise<string> {
  const slug = env.GITHUB_APP_SLUG ?? "pigeonhub-dev";
  return `https://github.com/apps/${slug}/installations/new`;
}

/**
 * Status with auto-join. While the whole deployment has exactly one GitHub
 * installation (private beta, single owner), an unbound-but-enabled device
 * joins it on its status poll — reinstall and new devices thus inherit the
 * GitHub connection without any re-connection ritual. Once a second GitHub
 * installation exists (multi-tenant), guessing is disabled and membership
 * comes only from the installation.created webhook flow.
 */
export async function getGitHubConnectionStatus(
  env: Env,
  pigeonhubInstallationId: string,
): Promise<{ connected: boolean; connectedAt: string | null }> {
  const row = await env.DB.prepare(
    `SELECT connected_at FROM github_connections WHERE pigeonhub_installation_id = ?1`,
  )
    .bind(pigeonhubInstallationId)
    .first<{ connected_at: string }>();
  if (row) return { connected: true, connectedAt: row.connected_at };

  const single = await env.DB.prepare(
    `SELECT github_installation_id
     FROM github_connections
     GROUP BY github_installation_id
     HAVING (SELECT COUNT(*) FROM (SELECT DISTINCT github_installation_id FROM github_connections)) = 1`,
  ).first<{ github_installation_id: string }>();
  if (!single) return { connected: false, connectedAt: null };

  const joined = await addMembership(env, single.github_installation_id, pigeonhubInstallationId);
  if (!joined) return { connected: false, connectedAt: null };
  return { connected: true, connectedAt: new Date().toISOString() };
}

/**
 * Adds (or moves) ONE device's membership. Keyed by the device: a device
 * reconnecting to a different GitHub installation updates only its own row
 * and never touches other members — connect is additive, never a steal.
 */
export async function addMembership(
  env: Env,
  githubInstallationId: string,
  pigeonhubInstallationId: string,
): Promise<boolean> {
  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  try {
    await env.DB.prepare(
      `INSERT INTO github_connections (id, pigeonhub_installation_id, github_installation_id, connected_at)
       VALUES (?1, ?2, ?3, ?4)
       ON CONFLICT(pigeonhub_installation_id) DO UPDATE SET
         github_installation_id = excluded.github_installation_id,
         connected_at = excluded.connected_at`,
    )
      .bind(id, pigeonhubInstallationId, githubInstallationId, now)
      .run();
    return true;
  } catch {
    return false;
  }
}

/**
 * installation.created webhook: the owner just installed/configured the
 * GitHub App, so every active installation not yet a member joins it.
 * (Beta semantics: one owner. Multi-tenant disambiguation arrives with
 * owner accounts.)
 */
export async function addAllUnboundAsMembers(
  env: Env,
  githubInstallationId: string,
): Promise<number> {
  const unbound = await env.DB.prepare(
    `SELECT i.id FROM installations i
     LEFT JOIN github_connections gc ON gc.pigeonhub_installation_id = i.id
     WHERE gc.id IS NULL AND i.enabled = 1`,
  ).all<{ id: string }>();

  let added = 0;
  for (const device of unbound.results ?? []) {
    if (await addMembership(env, githubInstallationId, device.id)) added++;
  }
  return added;
}

/**
 * Fan-out targets for delivery: every enabled member of the GitHub
 * installation that owns a channel. Stale/UNREGISTERED devices are not
 * filtered here — they fail their own FCM leg and are skipped per leg.
 */
export async function getFanoutTargets(
  env: Env,
  githubInstallationId: string,
): Promise<Array<{ pigeonhubInstallationId: string; channelId: string }>> {
  const res = await env.DB.prepare(
    `SELECT gc.pigeonhub_installation_id, c.id AS channel_id
     FROM github_connections gc
     JOIN channels c ON c.installation_id = gc.pigeonhub_installation_id
     JOIN installations i ON i.id = gc.pigeonhub_installation_id
     WHERE gc.github_installation_id = ?1 AND i.enabled = 1`,
  )
    .bind(githubInstallationId)
    .all<{ pigeonhub_installation_id: string; channel_id: string }>();
  return (res.results ?? []).map((r) => ({
    pigeonhubInstallationId: r.pigeonhub_installation_id,
    channelId: r.channel_id,
  }));
}
