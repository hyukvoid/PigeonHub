/**
 * MVP-019 P0 verification: stored-vs-delivered publish semantics.
 * Run: node scripts/stored_but_502_test.mjs <base-url>
 * Reads invite codes from .dev.vars itself; never prints secrets.
 */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

const BASE = process.argv[2] ?? "https://pigeonhub-push-test.pigeonhub.workers.dev";
const devVars = readFileSync(new URL("../.dev.vars", import.meta.url), "utf8");
const devVar = (name) => {
  const m = new RegExp(`^${name}=(.*)$`, "m").exec(devVars);
  if (!m) throw new Error(`missing ${name}`);
  return m[1].trim();
};
const sha256hex = (s) => createHash("sha256").update(s).digest("hex");

function record(name, pass, detail = "") {
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

const identity = {
  bootstrapId: `boot-mvp019-${randomUUID()}`,
  managementSecret: randomBytes(32).toString("base64url"),
  writeToken: randomBytes(32).toString("base64url"),
};

// bootstrap with a FAKE fcm token → every FCM send fails permanently (400).
// Invite codes are single-use; earlier campaigns burned the low numbers, so
// probe upward until one is accepted.
let boot, bootBody;
for (let n = 1; n <= 19; n++) {
  boot = await fetch(`${BASE}/v1/installations`, {
    method: "POST",
    headers: { Authorization: `Bearer ${identity.managementSecret}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      bootstrap_id: identity.bootstrapId,
      invite_code: devVar(`TEST_INVITE_CODE_${n}`),
      write_token_hash: sha256hex(identity.writeToken),
      fcm_token: `fake-dead-token-${randomUUID()}`,
      platform: "android",
    }),
  });
  bootBody = await boot.json();
  if (boot.ok && bootBody?.channel?.id) {
    console.log(`(used TEST_INVITE_CODE_${n})`);
    break;
  }
}
if (!boot?.ok || !bootBody?.channel?.id) {
  console.log("bootstrap failed:", boot?.status, bootBody?.error ?? "(no channel)");
  process.exit(1);
}
const channelId = bootBody.channel.id;
const endpoint = `${BASE}/v1/channels/${channelId}/messages`;

function publish(body, key, failAt) {
  const headers = {
    Authorization: `Bearer ${identity.writeToken}`,
    "Content-Type": "application/json",
  };
  if (key) headers["Idempotency-Key"] = key;
  if (failAt) headers["X-PigeonHub-Fail-At"] = failAt;
  return fetch(endpoint, { method: "POST", headers, body: JSON.stringify(body) }).then(async (r) => ({
    status: r.status,
    body: await r.json(),
  }));
}

const payload = () => ({
  title: "mvp019 stored-vs-502",
  message: "permanent fcm failure must not look like a transport error",
  priority: "normal",
});

// 1. Permanent FCM failure → 200 + stored:true + push_status:"failed" + non-retryable.
const key1 = `mvp019-k1-${randomUUID()}`;
const p1 = await publish(payload(), key1);
record(
  "permanent failure returns 200 stored:true",
  p1.status === 200 && p1.body.stored === true && p1.body.push_status === "failed",
  `status=${p1.status} push_status=${p1.body.push_status}`,
);
record(
  "permanent failure marked non-retryable",
  p1.body.delivery?.retryable === false,
  `delivery=${JSON.stringify(p1.body.delivery ?? null)}`,
);

// 2. Same key + same payload (client retry after lost response) → replay of the SAME row.
const p2 = await publish(payload(), key1);
record(
  "retry with same key converges (idempotent replay)",
  p2.status === 200 && p2.body.idempotent_replay === true && p2.body.message_id === p1.body.message_id,
  `status=${p2.status} replay=${p2.body.idempotent_replay} same_id=${p2.body.message_id === p1.body.message_id}`,
);
record(
  "replay carries the delivery outcome",
  p2.body.push_status === "failed" && typeof p2.body.error === "string" && p2.body.delivery?.retryable === false,
  `push_status=${p2.body.push_status}`,
);

// 3. Transient failure (failAt=fcm) → 502 pending + retryable; retry converges to one row.
const key2 = `mvp019-k2-${randomUUID()}`;
const t1 = await publish(payload(), key2, "fcm");
const t2 = await publish(payload(), key2, "fcm");
record(
  "transient failure stays 502 pending retryable",
  t1.status === 502 && t1.body.stored === true && t1.body.push_status === "pending" && t1.body.delivery?.retryable === true,
  `status=${t1.status} push_status=${t1.body.push_status}`,
);
record(
  "transient retry converges to the same row",
  t2.status === 200 && t2.body.idempotent_replay === true && t2.body.message_id === t1.body.message_id,
  `status=${t2.status} same_id=${t2.body.message_id === t1.body.message_id}`,
);

// 4. Row count for the channel: exactly 2 stored messages (k1 event + k2 event).
const msgs = await fetch(`${BASE}/v1/installations/me/messages?limit=200`, {
  headers: { Authorization: `Bearer ${identity.managementSecret}` },
}).then((r) => r.json());
const rows = (msgs.messages ?? []).filter((m) =>
  [p1.body.message_id, t1.body.message_id].includes(m.id),
);
record(
  "exactly one row per logical event",
  rows.length === 2 && new Set(rows.map((m) => m.id)).size === 2,
  `matched rows=${rows.length}`,
);

// 5. Legacy no-key publishes still each store one row (back-compat behavior unchanged).
const n1 = await publish(payload(), null);
const n2 = await publish(payload(), null);
record(
  "no-key publishes remain distinct events (back-compat)",
  n1.status === 200 && n2.status === 200 && n1.body.message_id !== n2.body.message_id,
  `distinct=${n1.body.message_id !== n2.body.message_id}`,
);
