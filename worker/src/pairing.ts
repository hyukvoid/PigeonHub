/**
 * MVP-007: connector pairing.
 *
 * Flow: the phone (management secret) issues a short-lived one-time code; a
 * local connector redeems it for the channel endpoint + a freshly minted
 * connector token. Codes live 10 minutes, are single-use and revocable; only
 * their SHA-256 hash is stored. Connector tokens are stored hashed and are
 * accepted by the publish route alongside the device write token, so pairing
 * a new connector never rotates the tokens already installed on phones.
 */

import type { Env } from "./types.js";

export interface PairingIssue {
  code: string;
  expires_at: string;
}

export interface PairingRedeemResult {
  channel_id: string;
  endpoint: string;
  write_token: string;
  token_version: number;
}

export interface PairingRow {
  code_hash: string;
  installation_id: string;
  channel_id: string;
  created_at: string;
  expires_at: string;
  redeemed_at: string | null;
  revoked: number;
}

export const PAIRING_TTL_MS = 10 * 60 * 1000;

/** Human-typeable code: PHC-XXXXX-XXXXX-XXXXX (Crockford-ish alphabet). */
export function generatePairingCode(): string {
  const alphabet = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
  const bytes = crypto.getRandomValues(new Uint8Array(15));
  let raw = "";
  for (const b of bytes) raw += alphabet[b % alphabet.length];
  return `PHC-${raw.slice(0, 5)}-${raw.slice(5, 10)}-${raw.slice(10, 15)}`;
}

export function generateConnectorToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return "pct_" + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

export async function issuePairingCode(
  env: Env,
  installationId: string,
  channelId: string,
): Promise<PairingIssue> {
  const code = generatePairingCode();
  const now = new Date();
  const codeHash = await sha256Hex(code);
  await env.DB.prepare(
    `INSERT INTO pairing_codes (code_hash, installation_id, channel_id, created_at, expires_at)
     VALUES (?1, ?2, ?3, ?4, ?5)`,
  )
    .bind(codeHash, installationId, channelId, now.toISOString(), new Date(now.getTime() + PAIRING_TTL_MS).toISOString())
    .run();
  return { code, expires_at: new Date(now.getTime() + PAIRING_TTL_MS).toISOString() };
}

export async function revokePairingCodes(env: Env, installationId: string): Promise<number> {
  const result = await env.DB.prepare(
    `UPDATE pairing_codes SET revoked = 1
     WHERE installation_id = ?1 AND revoked = 0 AND redeemed_at IS NULL`,
  )
    .bind(installationId)
    .run();
  return result.meta.changes ?? 0;
}

export async function redeemPairingCode(
  env: Env,
  origin: string,
  code: string,
): Promise<PairingRedeemResult | { error: string }> {
  const codeHash = await sha256Hex(code.trim().toUpperCase());
  const row = await env.DB.prepare(
    `SELECT code_hash, installation_id, channel_id, created_at, expires_at, redeemed_at, revoked
     FROM pairing_codes WHERE code_hash = ?1`,
  )
    .bind(codeHash)
    .first<PairingRow>();
  if (!row) return { error: "invalid pairing code" };
  if (row.revoked === 1) return { error: "pairing code revoked" };
  if (row.redeemed_at !== null) return { error: "pairing code already used" };
  if (new Date(row.expires_at).getTime() < Date.now()) return { error: "pairing code expired" };

  const token = generateConnectorToken();
  const tokenHash = await sha256Hex(token);
  const now = new Date().toISOString();

  // Mark redeemed FIRST (one-time guarantee even if the token insert races),
  // then mint the connector token.
  await env.DB.prepare(
    `UPDATE pairing_codes SET redeemed_at = ?2 WHERE code_hash = ?1`,
  ).bind(codeHash, now).run();

  const versionRow = await env.DB.prepare(
    `SELECT COALESCE(MAX(version), 0) + 1 AS v FROM connector_tokens WHERE channel_id = ?1`,
  )
    .bind(row.channel_id)
    .first<{ v: number }>();
  const version = versionRow?.v ?? 1;

  await env.DB.prepare(
    `INSERT INTO connector_tokens (channel_id, token_hash, version, created_at)
     VALUES (?1, ?2, ?3, ?4)`,
  ).bind(row.channel_id, tokenHash, version, now).run();

  return {
    channel_id: row.channel_id,
    endpoint: `${origin}/v1/channels/${row.channel_id}/messages`,
    write_token: token,
    token_version: version,
  };
}

/** True when [token] is a valid connector token for the channel. */
export async function isValidConnectorToken(
  env: Env,
  channelId: string,
  tokenHash: string,
): Promise<boolean> {
  const row = await env.DB.prepare(
    `SELECT 1 AS ok FROM connector_tokens WHERE channel_id = ?1 AND token_hash = ?2`,
  )
    .bind(channelId, tokenHash)
    .first<{ ok: number }>();
  return row?.ok === 1;
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}
