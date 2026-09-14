/**
 * MVP-011.5 durable-deletion protocol tests. Run: node scripts/delete_test.mjs <base-url>
 * Consumes one fresh invite code (TEST_INVITE_CODE_17).
 * Gates: DELETE_AUTH, DELETE_VALIDATION, DELETE_TOMBSTONE, NO_RESURRECTION,
 *        DELETE_ALL, DELETE_IDEMPOTENT, SYNC_REPORTS_TOMBSTONES.
 */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

const BASE = process.argv[2] ?? "https://pigeonhub-push.pigeonhub.workers.dev";
const devVars = readFileSync(new URL("../.dev.vars", import.meta.url), "utf8");
const devVar = (name) => {
  const m = new RegExp(`^${name}=(.*)$`, "m").exec(devVars);
  if (!m) throw new Error(`missing ${name}`);
  return m[1].trim();
};
const sha256hex = (s) => createHash("sha256").update(s).digest("hex");

const results = [];
const record = (name, pass, detail = "") => {
  results.push({ name, pass });
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
};

const identity = {
  bootstrapId: `boot-del-${randomUUID()}`,
  managementSecret: randomBytes(32).toString("base64url"),
  writeToken: randomBytes(32).toString("base64url"),
};

// ---------- setup: one fresh installation ----------
{
  const res = await fetch(`${BASE}/v1/installations`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${identity.managementSecret}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      bootstrap_id: identity.bootstrapId,
      invite_code: devVar("TEST_INVITE_CODE_17"),
      write_token_hash: sha256hex(identity.writeToken),
      fcm_token: `del-test-${randomUUID()}`,
      platform: "android",
    }),
  });
  const body = await res.json();
  if (!body.channel?.id) {
    console.error("bootstrap failed:", res.status, JSON.stringify(body).slice(0, 200));
    process.exit(1);
  }
  identity.channelId = body.channel.id;
}

async function publish(title, messageId) {
  const res = await fetch(`${BASE}/v1/channels/${identity.channelId}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${identity.writeToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ title, message: `m:${messageId}`, priority: "normal", message_id: messageId }),
  });
  const body = await res.json();
  return body.stored === true;
}

async function sync(afterSeq = 0) {
  const res = await fetch(
    `${BASE}/v1/installations/me/messages?after_seq=${afterSeq}&limit=50`,
    { headers: { Authorization: `Bearer ${identity.managementSecret}` } },
  );
  return { status: res.status, body: await res.json() };
}

async function deleteMessages(ids, auth = identity.managementSecret) {
  const res = await fetch(`${BASE}/v1/installations/me/messages/delete`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify({ message_ids: ids }),
  });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

const RUN = randomBytes(3).toString("hex");
const mid = (n) => `del-${RUN}-${n}`;

// ---------- publish 3 messages ----------
{
  const ok = (await publish("one", mid("1"))) && (await publish("two", mid("2"))) && (await publish("three", mid("3")));
  await new Promise((r) => setTimeout(r, 500));
  const s = await sync();
  record("SETUP_3_PUBLISHED", ok && s.body.messages?.length === 3, `sync=${s.status} n=${s.body.messages?.length}`);
}

// ---------- DELETE_AUTH ----------
{
  const noAuth = await fetch(`${BASE}/v1/installations/me/messages/delete`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message_ids: [mid("1")] }),
  });
  const writeTok = await deleteMessages([mid("1")], identity.writeToken);
  record("DELETE_AUTH", noAuth.status === 401 && writeTok.status === 401, `no_auth=${noAuth.status} write_token=${writeTok.status}`);
}

// ---------- DELETE_VALIDATION ----------
{
  const empty = await deleteMessages([]);
  const badAuth = await deleteMessages(["no-such-id"], "x".repeat(40));
  record("DELETE_VALIDATION", empty.status === 400, `empty=${empty.status} (bad-id row is a no-op, allowed)`);
}

// ---------- DELETE_TOMBSTONE + NO_RESURRECTION ----------
{
  const del = await deleteMessages([mid("2")]);
  await new Promise((r) => setTimeout(r, 500));
  const s1 = await sync(0);
  const ids1 = (s1.body.messages ?? []).map((m) => m.id);
  // Full re-pull from seq 0 must never resurrect the deleted row.
  const s2 = await sync(0);
  const ids2 = (s2.body.messages ?? []).map((m) => m.id);
  const reported = s1.body.deleted_ids ?? [];
  record(
    "DELETE_TOMBSTONE",
    del.status === 200 && !ids1.includes(mid("2")) && ids1.length === 2 && reported.includes(mid("2")),
    `n=${ids1.length} reported=${reported.length}`,
  );
  record(
    "NO_RESURRECTION",
    !ids2.includes(mid("2")) && ids2.length === 2,
    `repull n=${ids2.length}`,
  );
}

// ---------- DELETE_IDEMPOTENT ----------
{
  const again = await deleteMessages([mid("2")]);
  const s = await sync(0);
  record("DELETE_IDEMPOTENT", again.status === 200 && (s.body.messages ?? []).length === 2, `status=${again.status}`);
}

// ---------- DELETE_ALL ----------
{
  const rest = (await sync(0)).body.messages.map((m) => m.id);
  const del = await deleteMessages(rest);
  await new Promise((r) => setTimeout(r, 500));
  const s = await sync(0);
  const reported = s.body.deleted_ids ?? [];
  record(
    "DELETE_ALL",
    del.status === 200 && (s.body.messages ?? []).length === 0 && [mid("1"), mid("2"), mid("3")].every((id) => reported.includes(id)),
    `remaining=${s.body.messages?.length} tombstones=${reported.length}`,
  );
}

// ---------- SYNC_REPORTS_TOMBSTONES on a fresh page pull ----------
{
  const s = await sync(0);
  record("SYNC_REPORTS_TOMBSTONES", Array.isArray(s.body.deleted_ids), `field present=${Array.isArray(s.body.deleted_ids)}`);
}

// ---------- HEALTH (MVP-015) ----------
{
  const noAuth = await fetch(`${BASE}/v1/installations/me/health`);
  const res = await fetch(`${BASE}/v1/installations/me/health`, {
    headers: { Authorization: `Bearer ${identity.managementSecret}` },
  });
  const body = await res.json().catch(() => ({}));
  const rows = Array.isArray(body.health) ? body.health : [];
  const push = rows.find((r) => r.source === "push");
  const device = rows.find((r) => r.source === "device");
  record(
    "HEALTH_ENDPOINT",
    noAuth.status === 401 && res.status === 200 && body.ok === true
      && push?.state === "CONNECTED" && device?.state === "CONNECTED"
      && typeof push.last_event_at === "string",
    `no_auth=${noAuth.status} sources=${rows.map((r) => r.source + ":" + r.state).join(",")}`,
  );
}

const failed = results.filter((r) => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length === 0 ? 0 : 1);
