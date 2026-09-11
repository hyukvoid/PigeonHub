import {
  canonicalRequestHash,
  D1UniqueRace,
  enforceQuota,
  insertPendingMessage,
  lookupIdempotency,
  refundQuota,
  updatePushStatus,
} from "./d1.js";
import { sendToFcm } from "./fcm.js";
import { validatePush } from "./validate.js";
import type { Env, PushRequest, ResolvedPush } from "./types.js";
import {
  bearerOf,
  channelEndpoint,
  constantTimeEquals,
  decryptFcmToken,
  encryptFcmToken,
  getChannelById,
  getChannelByInstallation,
  getInstallationByBootstrap,
  getInstallationById,
  getInstallationByManagementHash,
  isValidChannelId,
  resolveInvite,
  sha256Hex,
  verifyInviteFormat,
  createInstallationWithChannel,
  type InstallationRow,
} from "./bootstrap.js";

/**
 * PigeonHub production transport.
 *
 * Legacy dev tool:  POST /push                                (dev channel + fixed test device)
 * Product API:      POST /v1/installations                    (client-generated credentials)
 *                   GET  /v1/installations/me
 *                   PUT  /v1/installations/me/push-token      (management secret)
 *                   PUT  /v1/channels/{id}/write-token        (management secret; versioned rotation)
 *                   POST /v1/channels/{id}/messages           (write token → D1 → FCM)
 *
 * Durable contract: `stored: true` ⇒ the message is in D1 before/regardless of FCM.
 * States: pending | fcm_accepted | failed.
 */

function json(response: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(response), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function bearerMatches(request: Request, expected: string): boolean {
  const header = request.headers.get("Authorization") ?? "";
  const expectedHeader = `Bearer ${expected}`;
  if (header.length !== expectedHeader.length) return false;
  let diff = 0;
  for (let i = 0; i < header.length; i++) diff |= header.charCodeAt(i) ^ expectedHeader.charCodeAt(i);
  return diff === 0;
}

interface PublishContext {
  channelId: string;
  quotaScope: string;
  targetToken: string;
  push: ResolvedPush;
  requestHash: string;
  idempotencyKey: string | null;
  failAt: string | null;
}

async function durablePublish(env: Env, ctx: PublishContext): Promise<Response> {
  // ---- (A) failure injection BEFORE anything durable/external ----
  if (ctx.failAt === "d1_pre") {
    return json({ ok: false, stored: false, injected: "d1_pre" }, 500);
  }

  // ---- idempotency check ----
  if (ctx.idempotencyKey !== null) {
    const early = await lookup(env, ctx, ctx.idempotencyKey);
    if (early !== null) return early;
  }

  // ---- quota ----
  try {
    await enforceQuota(env, ctx.quotaScope);
  } catch (error) {
    if (error instanceof Error && error.message.startsWith("quota:")) {
      return json(
        { ok: false, stored: false, error: "quota exceeded", scope: error.message.slice(6) },
        429,
      );
    }
    throw error;
  }

  // ---- D1 insert (pending) ----
  let seq: number;
  try {
    seq = await insertPendingMessage(env, ctx.channelId, ctx.push, ctx.requestHash, ctx.idempotencyKey);
  } catch (error) {
    if (error instanceof D1UniqueRace) {
      if (error.indexName === "channel_idem" && ctx.idempotencyKey !== null) {
        // Lost an insert race against the same idempotency key:
        // converge on the winner and hand back the consumed quota slot.
        await refundQuota(env, ctx.quotaScope);
        const late = await lookup(env, ctx, ctx.idempotencyKey);
        if (late !== null) return late;
      }
      // seq allocation raced (should be impossible: D1 serializes writes);
      // retry once with the unique index as the final backstop.
      seq = await insertPendingMessage(env, ctx.channelId, ctx.push, ctx.requestHash, ctx.idempotencyKey);
    } else {
      // Genuine D1 failure: nothing stored, so FCM must never run.
      return json(
        {
          ok: false,
          stored: false,
          error: "d1 insert failed",
          detail: error instanceof Error ? error.message : String(error),
        },
        500,
      );
    }
  }

  // ---- (B) failure injection AFTER the durable insert ----
  if (ctx.failAt === "d1_post") {
    return json(
      {
        ok: false,
        stored: true,
        injected: "d1_post",
        message_id: ctx.push.message_id,
        seq,
        push_status: "pending",
      },
      500,
    );
  }

  // ---- (C) failure injection at the FCM hop ----
  if (ctx.failAt === "fcm") {
    await updatePushStatus(env, ctx.push.message_id, "pending", null, "injected fcm failure");
    return json(
      {
        ok: false,
        stored: true,
        injected: "fcm",
        message_id: ctx.push.message_id,
        seq,
        push_status: "pending",
      },
      502,
    );
  }

  // ---- FCM send ----
  const sent = await sendToFcm(env, ctx.push, ctx.targetToken, ctx.channelId, seq);

  if (sent.ok) {
    // ---- (D) failure injection between FCM accept and state update ----
    if (ctx.failAt === "status_update") {
      return json(
        {
          ok: false,
          stored: true,
          injected: "status_update",
          message_id: ctx.push.message_id,
          seq,
          push_status: "pending",
          note: "fcm already accepted; a naive retry would duplicate the push",
        },
        500,
      );
    }
    await updatePushStatus(env, ctx.push.message_id, "fcm_accepted", sent.fcmMessageId ?? null, null);
    return json({
      message_id: ctx.push.message_id,
      stored: true,
      push_status: "fcm_accepted",
      seq,
      fcm_message_id: sent.fcmMessageId,
    });
  }

  // FCM failed: the message REMAINS in D1 (that is the contract).
  const status = sent.transient ? "pending" : "failed";
  await updatePushStatus(env, ctx.push.message_id, status, null, sent.detail ?? "fcm failed");
  return json(
    {
      message_id: ctx.push.message_id,
      stored: true,
      push_status: status,
      seq,
      error: sent.detail,
    },
    502,
  );
}

async function lookup(
  env: Env,
  ctx: PublishContext,
  key: string,
): Promise<Response | null> {
  const result = await lookupIdempotency(env, ctx.channelId, key, ctx.requestHash);
  if (result.conflict) {
    return json({ ok: false, error: "idempotency key reused with a different payload" }, 409);
  }
  if (result.replay) {
    return json({
      message_id: result.replay.id,
      stored: true,
      push_status: result.replay.push_status,
      seq: result.replay.seq,
      idempotent_replay: true,
    });
  }
  return null;
}

async function parseJsonBody(request: Request): Promise<Record<string, unknown> | null> {
  try {
    return (await request.json()) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function stringField(body: Record<string, unknown>, key: string): string {
  const value = body[key];
  return typeof value === "string" ? value.trim() : "";
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return json({
        ok: true,
        service: "pigeonhub-push-worker",
        project: env.FIREBASE_PROJECT_ID || "(unset)",
        durable: env.DB !== undefined,
        bootstrap: Boolean(env.INVITE_HASHES && env.FCM_TOKEN_ENCRYPTION_KEY),
        injection: env.FAILURE_INJECTION === "on",
      });
    }

    // =====================================================================
    // Legacy development sender (NOT the user-facing API).
    // =====================================================================
    if (url.pathname === "/push") {
      if (request.method !== "POST") {
        return json({ ok: false, error: "method not allowed" }, 405);
      }
      if (!env.PUSH_BEARER_SECRET || !bearerMatches(request, env.PUSH_BEARER_SECRET)) {
        return json({ ok: false, error: "unauthorized" }, 401);
      }
      const body = await parseJsonBody(request);
      if (body === null) return json({ ok: false, errors: ["body is not valid JSON"] }, 400);

      const validated = validatePush(body, env.FCM_TEST_DEVICE_TOKEN);
      if (!validated.ok) return json({ ok: false, errors: validated.errors }, 400);

      const rawKey = request.headers.get("Idempotency-Key");
      const idempotencyKey = rawKey === null ? null : rawKey.trim() || null;
      const failAt =
        env.FAILURE_INJECTION === "on" ? request.headers.get("X-PigeonHub-Fail-At") : null;

      return durablePublish(env, {
        channelId: env.CHANNEL_ID ?? "dev",
        quotaScope: env.CHANNEL_ID ?? "dev",
        targetToken: env.FCM_TEST_DEVICE_TOKEN,
        push: validated.push,
        requestHash: await canonicalRequestHash(
          validated.push.title,
          validated.push.message,
          validated.push.priority,
          validated.push.url ?? null,
        ),
        idempotencyKey,
        failAt,
      });
    }

    // =====================================================================
    // MVP-001C product API.
    // =====================================================================
    if (url.pathname === "/v1/installations" && request.method === "POST") {
      return handleBootstrap(request, env);
    }

    if (url.pathname === "/v1/installations/me" && request.method === "GET") {
      return requireInstallation(request, env, async (installation) => {
        const channel = await getChannelByInstallation(env, installation.id);
        if (!channel) return json({ ok: false, error: "channel missing" }, 500);
        return json({
          installation_id: installation.id,
          enabled: installation.enabled === 1,
          channel: {
            id: channel.id,
            endpoint: channelEndpoint(request, channel.id),
          },
          write_token_version: channel.write_token_version,
          fcm_token_version: installation.fcm_token_version,
        });
      });
    }

    if (url.pathname === "/v1/installations/me/push-token" && request.method === "PUT") {
      return requireInstallation(request, env, async (installation) => {
        const body = await parseJsonBody(request);
        if (body === null) return json({ ok: false, errors: ["body is not valid JSON"] }, 400);
        const fcmToken = stringField(body, "fcm_token");
        if (fcmToken.length < 32 || fcmToken.length > 4096) {
          return json({ ok: false, errors: ["fcm_token is required (32..4096 chars)"] }, 400);
        }
        const expected = Number(body.expected_version);
        if (!Number.isInteger(expected) || expected < 1) {
          return json({ ok: false, errors: ["expected_version must be an integer >= 1"] }, 400);
        }

        // Idempotent no-op: same token → no write, no version bump.
        const currentToken = await decryptFcmToken(
          env,
          installation.fcm_token_ciphertext,
          installation.fcm_token_nonce,
        );
        if (constantTimeEquals(currentToken, fcmToken)) {
          return json({ fcm_token_version: installation.fcm_token_version, updated: false });
        }

        // Versioned conditional update: a stale (out-of-order) update must not win.
        const encrypted = await encryptFcmToken(env, fcmToken);
        const updated = await env.DB.prepare(
          `UPDATE installations
           SET fcm_token_ciphertext = ?2, fcm_token_nonce = ?3,
               fcm_token_version = fcm_token_version + 1, updated_at = ?4
           WHERE id = ?1 AND fcm_token_version = ?5`,
        )
          .bind(
            installation.id,
            encrypted.ciphertext,
            encrypted.nonce,
            new Date().toISOString(),
            expected,
          )
          .run();
        if ((updated.meta.changes ?? 0) === 0) {
          const fresh = await getInstallationByManagementHash(
            env,
            installation.management_credential_hash,
          );
          return json(
            {
              ok: false,
              error: "stale fcm token update",
              fcm_token_version: fresh?.fcm_token_version ?? installation.fcm_token_version,
            },
            409,
          );
        }
        return json({ fcm_token_version: expected + 1, updated: true });
      });
    }

    if (url.pathname === "/v1/installations/me/messages" && request.method === "GET") {
      return requireInstallation(request, env, async (installation) => {
        const channel = await getChannelByInstallation(env, installation.id);
        if (!channel) return json({ ok: false, error: "channel missing" }, 500);

        const params = url.searchParams;
        const limitRaw = Number(params.get("limit") ?? "50");
        const limit = Math.min(Math.max(Number.isInteger(limitRaw) && limitRaw > 0 ? limitRaw : 50, 1), 200);
        const afterRaw = Number(params.get("after_seq") ?? "0");
        const afterSeq = Number.isFinite(afterRaw) && afterRaw >= 0 ? Math.floor(afterRaw) : 0;

        // Snapshot pagination: the FIRST page fixes the upper seq bound so
        // messages published mid-sync land in the NEXT sync (never duplicated
        // or missed within one sync session).
        const snapshotParam = params.get("snapshot_max_seq");
        let snapshotMaxSeq: number;
        if (snapshotParam !== null) {
          const v = Number(snapshotParam);
          if (!Number.isFinite(v) || v < 0) {
            return json({ ok: false, error: "invalid snapshot_max_seq" }, 400);
          }
          snapshotMaxSeq = Math.floor(v);
        } else {
          const row = await env.DB.prepare(
            `SELECT COALESCE(MAX(seq), 0) AS m FROM messages WHERE channel_id = ?1`,
          )
            .bind(channel.id)
            .first<{ m: number }>();
          snapshotMaxSeq = row?.m ?? 0;
        }

        // Read-only: GET never mutates message state (no read receipts here).
        // Expired messages are excluded; physical deletion is MVP-001E.
        const nowIso = new Date().toISOString();
        const rows = await env.DB.prepare(
          `SELECT id, seq, title, message, priority, url, created_at, expires_at
           FROM messages
           WHERE channel_id = ?1 AND seq > ?2 AND seq <= ?3 AND expires_at > ?4
           ORDER BY seq ASC LIMIT ?5`,
        )
          .bind(channel.id, afterSeq, snapshotMaxSeq, nowIso, limit + 1)
          .all<{
            id: string;
            seq: number;
            title: string;
            message: string;
            priority: string;
            url: string | null;
            created_at: string;
            expires_at: string;
          }>();

        const all = rows.results ?? [];
        const hasMore = all.length > limit;
        const page = hasMore ? all.slice(0, limit) : all;
        const nextAfterSeq = page.length > 0 ? page[page.length - 1].seq : afterSeq;
        const retentionFloor = channel.retention_floor_seq ?? 0;
        const historyTruncated = afterSeq < retentionFloor;

        return json({
          messages: page,
          next_after_seq: nextAfterSeq,
          snapshot_max_seq: snapshotMaxSeq,
          has_more: hasMore,
          retention_floor_seq: retentionFloor,
          history_truncated: historyTruncated,
        });
      });
    }

    const rotationMatch = /^\/v1\/channels\/([^/]+)\/write-token$/.exec(url.pathname);
    if (rotationMatch && request.method === "PUT") {
      const channelId = decodeURIComponent(rotationMatch[1]);
      if (!isValidChannelId(channelId)) return json({ ok: false, error: "not found" }, 404);
      return requireInstallation(request, env, async (installation) => {
        const channel = await getChannelById(env, channelId);
        if (!channel || channel.installation_id !== installation.id) {
          return json({ ok: false, error: "not found" }, 404);
        }
        const body = await parseJsonBody(request);
        if (body === null) return json({ ok: false, errors: ["body is not valid JSON"] }, 400);
        const newHash = stringField(body, "write_token_hash").toLowerCase();
        if (!/^[0-9a-f]{64}$/.test(newHash)) {
          return json({ ok: false, errors: ["write_token_hash must be 64 hex chars"] }, 400);
        }
        const expected = Number(body.expected_version);
        if (!Number.isInteger(expected) || expected < 1) {
          return json({ ok: false, errors: ["expected_version must be an integer >= 1"] }, 400);
        }

        // Idempotent replay: rotation already applied (response was lost).
        if (constantTimeEquals(channel.write_token_hash, newHash)) {
          return json({ write_token_version: channel.write_token_version, replay: true });
        }
        if (channel.write_token_version !== expected) {
          // Never let an old rotation roll a newer token back.
          return json(
            { ok: false, error: "stale rotation", write_token_version: channel.write_token_version },
            409,
          );
        }
        const updated = await env.DB.prepare(
          `UPDATE channels SET write_token_hash = ?2, write_token_version = write_token_version + 1,
             updated_at = ?3
           WHERE id = ?1 AND write_token_version = ?4`,
        )
          .bind(channelId, newHash, new Date().toISOString(), expected)
          .run();
        if ((updated.meta.changes ?? 0) === 0) {
          return json(
            { ok: false, error: "stale rotation", write_token_version: channel.write_token_version },
            409,
          );
        }
        return json({ write_token_version: expected + 1, replay: false });
      });
    }

    const publishMatch = /^\/v1\/channels\/([^/]+)\/messages$/.exec(url.pathname);
    if (publishMatch && request.method === "POST") {
      const channelId = decodeURIComponent(publishMatch[1]);
      if (!isValidChannelId(channelId)) return json({ ok: false, error: "not found" }, 404);
      const writeToken = bearerOf(request);
      if (writeToken === null) return json({ ok: false, error: "unauthorized" }, 401);
      const channel = await getChannelById(env, channelId);
      if (!channel) return json({ ok: false, error: "not found" }, 404);
      const channelHash = await sha256Hex(writeToken);
      if (!constantTimeEquals(channel.write_token_hash, channelHash)) {
        return json({ ok: false, error: "unauthorized" }, 401);
      }
      const installation = await getInstallationById(env, channel.installation_id);
      if (!installation || installation.enabled !== 1) {
        return json({ ok: false, error: "installation disabled" }, 403);
      }

      const body = await parseJsonBody(request);
      if (body === null) return json({ ok: false, errors: ["body is not valid JSON"] }, 400);
      const validated = validatePush(body, installation.fcm_token_ciphertext);
      if (!validated.ok) return json({ ok: false, errors: validated.errors }, 400);

      const rawKey = request.headers.get("Idempotency-Key");
      const idempotencyKey = rawKey === null ? null : rawKey.trim() || null;
      const failAt =
        env.FAILURE_INJECTION === "on" ? request.headers.get("X-PigeonHub-Fail-At") : null;

      let targetToken: string;
      try {
        targetToken = await decryptFcmToken(
          env,
          installation.fcm_token_ciphertext,
          installation.fcm_token_nonce,
        );
      } catch {
        return json({ ok: false, stored: false, error: "token decryption failed" }, 500);
      }

      return durablePublish(env, {
        channelId: channel.id,
        quotaScope: `inst:${installation.id}`,
        targetToken,
        push: validated.push,
        requestHash: await canonicalRequestHash(
          validated.push.title,
          validated.push.message,
          validated.push.priority,
          validated.push.url ?? null,
        ),
        idempotencyKey,
        failAt,
      });
    }

    return json({ ok: false, error: "not found" }, 404);
  },
};

/** Management-secret guard shared by /me, push-token, and rotation routes. */
async function requireInstallation(
  request: Request,
  env: Env,
  handler: (installation: InstallationRow) => Promise<Response>,
): Promise<Response> {
  const bearer = bearerOf(request);
  if (bearer === null) return json({ ok: false, error: "unauthorized" }, 401);
  const managementHash = await sha256Hex(bearer);
  const installation = await getInstallationByManagementHash(env, managementHash);
  if (!installation || installation.enabled !== 1) {
    return json({ ok: false, error: "unauthorized" }, 401);
  }
  return handler(installation);
}

async function handleBootstrap(request: Request, env: Env): Promise<Response> {
  const managementSecret = bearerOf(request);
  if (managementSecret === null || managementSecret.length < 32) {
    return json({ ok: false, error: "unauthorized" }, 401);
  }
  const body = await parseJsonBody(request);
  if (body === null) return json({ ok: false, errors: ["body is not valid JSON"] }, 400);

  const bootstrapId = stringField(body, "bootstrap_id");
  const inviteCode = stringField(body, "invite_code");
  const writeTokenHash = stringField(body, "write_token_hash").toLowerCase();
  const fcmToken = stringField(body, "fcm_token");
  if (!/^[a-zA-Z0-9_-]{8,128}$/.test(bootstrapId)) {
    return json({ ok: false, errors: ["bootstrap_id must be 8..128 chars [A-Za-z0-9_-]"] }, 400);
  }
  if (!verifyInviteFormat(inviteCode)) {
    return json({ ok: false, errors: ["invite_code format invalid"] }, 400);
  }
  if (!/^[0-9a-f]{64}$/.test(writeTokenHash)) {
    return json({ ok: false, errors: ["write_token_hash must be 64 hex chars"] }, 400);
  }
  if (fcmToken.length < 32 || fcmToken.length > 4096) {
    return json({ ok: false, errors: ["fcm_token is required (32..4096 chars)"] }, 400);
  }

  const managementHash = await sha256Hex(managementSecret);
  const now = new Date().toISOString();

  // ---- retry path: same bootstrap_id must converge on the same installation ----
  const existing = await getInstallationByBootstrap(env, bootstrapId);
  if (existing) {
    if (!constantTimeEquals(existing.management_credential_hash, managementHash)) {
      // Never let a different secret take over an existing installation.
      return json({ ok: false, error: "bootstrap identity mismatch" }, 403);
    }
    const channel = await getChannelByInstallation(env, existing.id);
    if (!channel) return json({ ok: false, error: "channel missing" }, 500);

    // Opportunistic, versioned FCM token refresh if the token rotated while away.
    const storedToken = await decryptFcmToken(
      env,
      existing.fcm_token_ciphertext,
      existing.fcm_token_nonce,
    );
    let fcmTokenVersion = existing.fcm_token_version;
    if (!constantTimeEquals(storedToken, fcmToken)) {
      const encrypted = await encryptFcmToken(env, fcmToken);
      const updated = await env.DB.prepare(
        `UPDATE installations
         SET fcm_token_ciphertext = ?2, fcm_token_nonce = ?3,
             fcm_token_version = fcm_token_version + 1, updated_at = ?4
         WHERE id = ?1 AND fcm_token_version = ?5`,
      )
        .bind(existing.id, encrypted.ciphertext, encrypted.nonce, now, existing.fcm_token_version)
        .run();
      if ((updated.meta.changes ?? 0) > 0) fcmTokenVersion = existing.fcm_token_version + 1;
    }
    await env.DB.prepare(`UPDATE installations SET last_seen_at = ?2 WHERE id = ?1`)
      .bind(existing.id, now)
      .run();

    return json({
      installation_id: existing.id,
      channel: { id: channel.id, endpoint: channelEndpoint(request, channel.id) },
      write_token_version: channel.write_token_version,
      fcm_token_version: fcmTokenVersion,
      idempotent_replay: true,
    });
  }

  // ---- fresh registration ----
  const inviteHash = await resolveInvite(env, inviteCode);
  if (inviteHash === null) {
    return json({ ok: false, error: "invalid invite code" }, 403);
  }

  const outcome = await createInstallationWithChannel(env, {
    bootstrapId,
    managementHash,
    inviteHash,
    fcmToken,
    writeTokenHash,
  });

  if (outcome === "invite_used") {
    return json({ ok: false, error: "invite code already used" }, 403);
  }
  if (outcome === "bootstrap_race") {
    // Another request with the same bootstrap_id won the race; converge.
    const winner = await getInstallationByBootstrap(env, bootstrapId);
    if (winner && !constantTimeEquals(winner.management_credential_hash, managementHash)) {
      return json({ ok: false, error: "bootstrap identity mismatch" }, 403);
    }
    if (winner) {
      const channel = await getChannelByInstallation(env, winner.id);
      if (channel) {
        return json({
          installation_id: winner.id,
          channel: { id: channel.id, endpoint: channelEndpoint(request, channel.id) },
          write_token_version: channel.write_token_version,
          fcm_token_version: winner.fcm_token_version,
          idempotent_replay: true,
        });
      }
    }
    return json({ ok: false, error: "bootstrap race, retry" }, 503);
  }

  return json({
    installation_id: outcome.installationId,
    channel: { id: outcome.channelId, endpoint: channelEndpoint(request, outcome.channelId) },
    write_token_version: 1,
    fcm_token_version: 1,
  }, 201);
}
