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

export interface LoginRequestIssue {
  request_id: string;
  challenge: string;
  poll_secret: string;
  expires_at: string;
}

interface LoginRequestRow {
  request_id: string;
  challenge_hash: string;
  poll_secret_hash: string;
  created_at: string;
  expires_at: string;
  approved_at: string | null;
  consumed_at: string | null;
  installation_id: string | null;
  channel_id: string | null;
}

export const PAIRING_TTL_MS = 10 * 60 * 1000;
export const LOGIN_REQUEST_TTL_MS = 10 * 60 * 1000;

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

function randomHex(bytesLength: number): string {
  const bytes = crypto.getRandomValues(new Uint8Array(bytesLength));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * MVP-016 PC-first login. The QR contains only request_id + challenge. The
 * poll secret stays in the PC process, so scanning a visible QR cannot fetch
 * the connector credential by itself.
 */
export async function createLoginRequest(env: Env): Promise<LoginRequestIssue> {
  const now = new Date();
  const requestId = `plr_${randomHex(16)}`;
  const challenge = `phc_${randomHex(16)}`;
  const pollSecret = `phs_${randomHex(32)}`;
  const expiresAt = new Date(now.getTime() + LOGIN_REQUEST_TTL_MS).toISOString();
  await env.DB.prepare(
    `INSERT INTO pairing_requests
      (request_id, challenge_hash, poll_secret_hash, created_at, expires_at)
     VALUES (?1, ?2, ?3, ?4, ?5)`,
  )
    .bind(requestId, await sha256Hex(challenge), await sha256Hex(pollSecret), now.toISOString(), expiresAt)
    .run();
  return { request_id: requestId, challenge, poll_secret: pollSecret, expires_at: expiresAt };
}

export async function approveLoginRequest(
  env: Env,
  requestId: string,
  challenge: string,
  installationId: string,
  channelId: string,
): Promise<{ error: string } | { status: "approved" }> {
  const row = await env.DB.prepare(
    `SELECT request_id, challenge_hash, poll_secret_hash, created_at, expires_at,
            approved_at, consumed_at, installation_id, channel_id
     FROM pairing_requests WHERE request_id = ?1`,
  )
    .bind(requestId)
    .first<LoginRequestRow>();
  if (!row) return { error: "invalid login request" };
  if (new Date(row.expires_at).getTime() < Date.now()) return { error: "login request expired" };
  if ((await sha256Hex(challenge)) !== row.challenge_hash) return { error: "login challenge mismatch" };
  if (row.consumed_at !== null) return { error: "login request already completed" };
  if (row.approved_at !== null) return { status: "approved" };
  const updated = await env.DB.prepare(
    `UPDATE pairing_requests
     SET approved_at = ?2, installation_id = ?3, channel_id = ?4
     WHERE request_id = ?1 AND approved_at IS NULL AND consumed_at IS NULL`,
  )
    .bind(requestId, new Date().toISOString(), installationId, channelId)
    .run();
  if ((updated.meta.changes ?? 0) === 0) return { error: "login request was changed" };
  return { status: "approved" };
}

export async function pollLoginRequest(
  env: Env,
  origin: string,
  requestId: string,
  pollSecret: string,
): Promise<
  | { status: "pending" }
  | (PairingRedeemResult & { status: "approved" })
  | { error: string }
> {
  const row = await env.DB.prepare(
    `SELECT request_id, challenge_hash, poll_secret_hash, created_at, expires_at,
            approved_at, consumed_at, installation_id, channel_id
     FROM pairing_requests WHERE request_id = ?1`,
  )
    .bind(requestId)
    .first<LoginRequestRow>();
  if (!row || (await sha256Hex(pollSecret)) !== row.poll_secret_hash) return { error: "invalid login request" };
  if (new Date(row.expires_at).getTime() < Date.now()) return { error: "login request expired" };
  if (row.consumed_at !== null) return { error: "login request already completed" };
  if (row.approved_at === null || row.installation_id === null || row.channel_id === null) return { status: "pending" };

  const consumedAt = new Date().toISOString();
  const consumed = await env.DB.prepare(
    `UPDATE pairing_requests SET consumed_at = ?2
     WHERE request_id = ?1 AND consumed_at IS NULL`,
  )
    .bind(requestId, consumedAt)
    .run();
  if ((consumed.meta.changes ?? 0) === 0) return { error: "login request already completed" };

  const token = generateConnectorToken();
  const tokenHash = await sha256Hex(token);
  const versionRow = await env.DB.prepare(
    `SELECT COALESCE(MAX(version), 0) + 1 AS v FROM connector_tokens WHERE channel_id = ?1`,
  )
    .bind(row.channel_id)
    .first<{ v: number }>();
  const version = versionRow?.v ?? 1;
  await env.DB.prepare(
    `INSERT INTO connector_tokens (channel_id, token_hash, version, created_at)
     VALUES (?1, ?2, ?3, ?4)`,
  )
    .bind(row.channel_id, tokenHash, version, consumedAt)
    .run();
  return {
    status: "approved",
    channel_id: row.channel_id,
    endpoint: `${origin}/v1/channels/${row.channel_id}/messages`,
    write_token: token,
    token_version: version,
  };
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
