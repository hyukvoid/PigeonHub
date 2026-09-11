import type { Env } from "./types.js";

/**
 * MVP-001C: client-generated credential bootstrap + private channels.
 *
 * Credentials are generated ON THE CLIENT before any network request; the
 * server only ever sees:
 *   - Authorization: Bearer <management_secret>   → stored as SHA-256 hex
 *   - body.write_token_hash                        → SHA-256 hex of the write token
 *   - body.fcm_token                               → stored AES-GCM encrypted
 *
 * The server never issues or returns secrets, so a lost response can never
 * mean a lost credential.
 */

export const CHANNEL_ENDPOINT_PATH = "/v1/channels";

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly body: Record<string, unknown>,
  ) {
    super(JSON.stringify(body));
  }
}

export function sha256Hex(value: string): Promise<string> {
  return crypto.subtle
    .digest("SHA-256", new TextEncoder().encode(value))
    .then((buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join(""));
}

export function bearerOf(request: Request): string | null {
  const header = request.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(.+)$/.exec(header);
  return match ? match[1].trim() : null;
}

export function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function bytesToB64(bytes: ArrayBuffer | Uint8Array): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)));
}

function b64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function aesKey(env: Env): Promise<CryptoKey> {
  if (!env.FCM_TOKEN_ENCRYPTION_KEY) {
    throw new Error("FCM_TOKEN_ENCRYPTION_KEY is not configured");
  }
  const raw = b64ToBytes(env.FCM_TOKEN_ENCRYPTION_KEY);
  return crypto.subtle.importKey("raw", raw, { name: "AES-GCM" }, false, [
    "encrypt",
    "decrypt",
  ]);
}

/** Fresh nonce per encryption; key version travels with the row. */
export async function encryptFcmToken(
  env: Env,
  token: string,
): Promise<{ ciphertext: string; nonce: string }> {
  const key = await aesKey(env);
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: nonce as unknown as BufferSource },
    key,
    new TextEncoder().encode(token),
  );
  return { ciphertext: bytesToB64(ciphertext), nonce: bytesToB64(nonce) };
}

export async function decryptFcmToken(
  env: Env,
  ciphertext: string,
  nonce: string,
): Promise<string> {
  const key = await aesKey(env);
  const plain = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: b64ToBytes(nonce) as unknown as BufferSource },
    key,
    b64ToBytes(ciphertext) as unknown as BufferSource,
  );
  return new TextDecoder().decode(plain);
}

export function verifyInviteFormat(code: string): boolean {
  return /^[a-zA-Z0-9_-]{6,200}$/.test(code);
}

/** Returns the invite hash (invite_id) for a valid code, or null for unknown codes. */
export async function resolveInvite(env: Env, code: string): Promise<string | null> {
  let hashes: string[] = [];
  try {
    hashes = JSON.parse(env.INVITE_HASHES ?? "[]") as string[];
  } catch {
    hashes = [];
  }
  try {
    hashes = hashes.concat(JSON.parse(env.INVITE_TEST_HASHES ?? "[]") as string[]);
  } catch {
    // test pool optional
  }
  if (hashes.length === 0) return null;
  const candidate = await sha256Hex(code);
  for (const hash of hashes) {
    if (constantTimeEquals(hash, candidate)) return candidate;
  }
  return null;
}

export interface InstallationRow {
  id: string;
  bootstrap_id: string;
  management_credential_hash: string;
  fcm_token_ciphertext: string;
  fcm_token_nonce: string;
  fcm_token_key_version: number;
  fcm_token_version: number;
  enabled: number;
}

export interface ChannelRow {
  id: string;
  installation_id: string;
  write_token_hash: string;
  write_token_version: number;
  retention_floor_seq: number;
}

export async function getInstallationByBootstrap(
  env: Env,
  bootstrapId: string,
): Promise<InstallationRow | null> {
  return env.DB.prepare(
    `SELECT id, bootstrap_id, management_credential_hash, fcm_token_ciphertext,
            fcm_token_nonce, fcm_token_key_version, fcm_token_version, enabled
     FROM installations WHERE bootstrap_id = ?1`,
  )
    .bind(bootstrapId)
    .first<InstallationRow>();
}

export async function getInstallationByManagementHash(
  env: Env,
  managementHash: string,
): Promise<InstallationRow | null> {
  return env.DB.prepare(
    `SELECT id, bootstrap_id, management_credential_hash, fcm_token_ciphertext,
            fcm_token_nonce, fcm_token_key_version, fcm_token_version, enabled
     FROM installations WHERE management_credential_hash = ?1`,
  )
    .bind(managementHash)
    .first<InstallationRow>();
}

export async function getInstallationById(env: Env, id: string): Promise<InstallationRow | null> {
  return env.DB.prepare(
    `SELECT id, bootstrap_id, management_credential_hash, fcm_token_ciphertext,
            fcm_token_nonce, fcm_token_key_version, fcm_token_version, enabled
     FROM installations WHERE id = ?1`,
  )
    .bind(id)
    .first<InstallationRow>();
}

export async function getChannelByInstallation(
  env: Env,
  installationId: string,
): Promise<ChannelRow | null> {
  return env.DB.prepare(
    `SELECT id, installation_id, write_token_hash, write_token_version, retention_floor_seq
     FROM channels WHERE installation_id = ?1`,
  )
    .bind(installationId)
    .first<ChannelRow>();
}

export async function getChannelById(env: Env, channelId: string): Promise<ChannelRow | null> {
  return env.DB.prepare(
    `SELECT id, installation_id, write_token_hash, write_token_version, retention_floor_seq
     FROM channels WHERE id = ?1`,
  )
    .bind(channelId)
    .first<ChannelRow>();
}

export interface CreateOutcome {
  ok: true;
  installationId: string;
  channelId: string;
}

/** Atomic installation + private channel creation (single D1 transaction). */
export async function createInstallationWithChannel(
  env: Env,
  params: {
    bootstrapId: string;
    managementHash: string;
    inviteHash: string;
    fcmToken: string;
    writeTokenHash: string;
  },
): Promise<CreateOutcome | "bootstrap_race" | "invite_used"> {
  const installationId = crypto.randomUUID();
  const channelId = `ch_${crypto.randomUUID().replace(/-/g, "").slice(0, 20)}`;
  const now = new Date().toISOString();
  const encrypted = await encryptFcmToken(env, params.fcmToken);
  try {
    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO installations
           (id, bootstrap_id, management_credential_hash, invite_id,
            fcm_token_ciphertext, fcm_token_nonce, fcm_token_key_version,
            fcm_token_version, enabled, created_at, updated_at, last_seen_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, 1, 1, 1, ?7, ?7, ?7)`,
      ).bind(
        installationId,
        params.bootstrapId,
        params.managementHash,
        params.inviteHash,
        encrypted.ciphertext,
        encrypted.nonce,
        now,
      ),
      env.DB.prepare(
        `INSERT INTO channels
           (id, installation_id, write_token_hash, write_token_version, created_at, updated_at)
         VALUES (?1, ?2, ?3, 1, ?4, ?4)`,
      ).bind(channelId, installationId, params.writeTokenHash, now),
    ]);
  } catch (error) {
    const text = error instanceof Error ? error.message : String(error);
    if (/installations\.invite_id|installations\.bootstrap_id/.test(text)) {
      // Distinguish by checking which UNIQUE constraint fired.
      if (/installations\.invite_id/.test(text)) return "invite_used";
      return "bootstrap_race";
    }
    throw error;
  }
  return { ok: true, installationId, channelId };
}

export function channelEndpoint(request: Request, channelId: string): string {
  return `${new URL(request.url).origin}${CHANNEL_ENDPOINT_PATH}/${channelId}/messages`;
}

/** Channel id format guard for the public publish/rotation routes. */
export function isValidChannelId(id: string): boolean {
  return /^ch_[0-9a-f]{10,40}$/.test(id);
}
