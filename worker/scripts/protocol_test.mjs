/**
 * MVP-001C server protocol test suite. Run: node scripts/protocol_test.mjs <base-url>
 * Generates its own client credentials (never printed) and consumes real invite
 * codes from .dev.vars — rotate INVITE_HASHES before re-running the full suite.
 */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

const BASE = process.argv[2] ?? "https://pigeonhub-push.pigeonhub.workers.dev";
const devVars = [
  readFileSync(new URL("../.dev.vars", import.meta.url), "utf8"),
  readFileSync(new URL("../../server/.env", import.meta.url), "utf8"),
].join("\n");

function devVar(name) {
  const m = new RegExp(`^${name}=(.*)$`, "m").exec(devVars);
  if (!m) throw new Error(`missing ${name}`);
  return m[1].trim();
}
const sha256hex = (s) => createHash("sha256").update(s).digest("hex");

const results = [];
function record(name, pass, detail = "") {
  results.push({ name, pass, detail });
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

async function bootstrap(identity, inviteCode, fcmToken) {
  const res = await fetch(`${BASE}/v1/installations`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${identity.managementSecret}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      bootstrap_id: identity.bootstrapId,
      invite_code: inviteCode,
      write_token_hash: sha256hex(identity.writeToken),
      fcm_token: fcmToken,
      platform: "android",
    }),
  });
  return { status: res.status, body: await res.json() };
}

function newIdentity(tag) {
  return {
    bootstrapId: `boot-${tag}-${randomUUID()}`,
    managementSecret: randomBytes(32).toString("base64url"),
    writeToken: randomBytes(32).toString("base64url"),
  };
}
const invite = (n) => devVar(`INVITE_CODE_${n}`);
const REAL_DEVICE_TOKEN = devVar("FCM_DEVICE_TOKEN");

// ---------- Test A: response loss x20 (identical bootstrap re-sent) ----------
{
  const id = newIdentity("loss");
  const first = await bootstrap(id, invite(1), REAL_DEVICE_TOKEN);
  const statuses = [first.status];
  let converged = 0;
  for (let i = 0; i < 20; i++) {
    const r = await bootstrap(id, invite(1), REAL_DEVICE_TOKEN);
    statuses.push(r.status);
    if (r.body.installation_id === first.body.installation_id) converged++;
  }
  const allSuccess = statuses.every((st) => st === 200 || st === 201);
  record(
    "RESPONSE_LOSS_X20",
    first.status < 300 && converged === 20 && allSuccess,
    `first=${first.status} converged=${converged}/20 installation=${first.body.installation_id?.slice(0, 8)}…`,
  );
}

// ---------- Test C: bootstrap concurrency (same identity, 20 parallel) ----------
{
  const id = newIdentity("conc");
  const jobs = Array.from({ length: 20 }, () =>
    bootstrap(id, invite(2), REAL_DEVICE_TOKEN).then((r) => r.status),
  );
  const statuses = await Promise.all(jobs);
  const ok = statuses.filter((s) => s < 300).length;
  const me = await fetch(`${BASE}/v1/installations/me`, {
    headers: { Authorization: `Bearer ${id.managementSecret}` },
  });
  const meBody = await me.json();
  record(
    "BOOTSTRAP_CONCURRENCY",
    ok === 20 && me.status === 200,
    `ok=${ok}/20 me=${me.status} installation=${meBody.installation_id?.slice(0, 8)}…`,
  );
}

// ---------- Test D: invite concurrency (20 identities, one invite) ----------
{
  const jobs = Array.from({ length: 20 }, (_, i) => {
    const id = newIdentity(`inv${i}`);
    return bootstrap(id, invite(3), `fake-token-${i}-${randomUUID()}`).then((r) => r.status);
  });
  const statuses = await Promise.all(jobs);
  const accepted = statuses.filter((s) => s < 300).length;
  const rejected = statuses.filter((s) => s === 403).length;
  record(
    "INVITE_CONCURRENCY",
    accepted === 1 && rejected === 19,
    `accepted=${accepted} rejected_403=${rejected}`,
  );
}

// ---------- Test E: wrong management secret on existing bootstrap ----------
{
  const id = newIdentity("takeover");
  await bootstrap(id, invite(4), `fake-token-${randomUUID()}`);
  const impostor = {
    bootstrapId: id.bootstrapId,
    managementSecret: randomBytes(32).toString("base64url"),
    writeToken: randomBytes(32).toString("base64url"),
  };
  const attempt = await bootstrap(impostor, invite(4), `fake-token-${randomUUID()}`);
  const me = await fetch(`${BASE}/v1/installations/me`, {
    headers: { Authorization: `Bearer ${id.managementSecret}` },
  });
  record(
    "WRONG_MANAGEMENT_SECRET",
    attempt.status === 403 && me.status === 200,
    `impostor=${attempt.status} owner_me=${me.status}`,
  );
}

// ---------- Management / write authorization isolation ----------
{
  const id = newIdentity("isolation");
  const reg = await bootstrap(id, invite(5), `fake-token-${randomUUID()}`);
  const channelId = reg.body.channel?.id;
  const mgmtOnPublish = await fetch(`${BASE}/v1/channels/${channelId}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
    body: JSON.stringify({ title: "x", message: "y" }),
  });
  const writeOnMe = await fetch(`${BASE}/v1/installations/me`, {
    headers: { Authorization: `Bearer ${id.writeToken}` },
  });
  const writeOnRotation = await fetch(`${BASE}/v1/channels/${channelId}/write-token`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${id.writeToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ write_token_hash: sha256hex("x"), expected_version: 1 }),
  });
  record(
    "AUTH_ISOLATION",
    mgmtOnPublish.status === 401 && writeOnMe.status === 401 && writeOnRotation.status === 401,
    `mgmt_publish=${mgmtOnPublish.status} write_me=${writeOnMe.status} write_rotation=${writeOnRotation.status}`,
  );
}

// ---------- FCM token lifecycle: register → update → stale race ----------
{
  const id = newIdentity("fcmtok");
  const tokenA = `fcm-A-${randomUUID()}`;
  const reg = await bootstrap(id, invite(6), tokenA);
  const v1 = reg.body.fcm_token_version;
  const upd = await fetch(`${BASE}/v1/installations/me/push-token`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fcm_token: `fcm-B-${randomUUID()}`, expected_version: v1 }),
  });
  const updBody = await upd.json();
  const stale = await fetch(`${BASE}/v1/installations/me/push-token`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fcm_token: tokenA, expected_version: v1 }),
  });
  const staleVersion = await fetch(`${BASE}/v1/installations/me/push-token`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fcm_token: `fcm-C-${randomUUID()}`, expected_version: 99 }),
  });
  const malformedVersion = await fetch(`${BASE}/v1/installations/me/push-token`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fcm_token: `fcm-D-${randomUUID()}`, expected_version: "abc" }),
  });
  record(
    "FCM_TOKEN_RACE",
    upd.status === 200 && updBody.updated === true && updBody.fcm_token_version === v1 + 1 &&
      stale.status === 409 && staleVersion.status === 409 && malformedVersion.status === 400,
    `update=${upd.status}/v${updBody.fcm_token_version} stale=${stale.status} ` +
      `stale_v99=${staleVersion.status} malformed=${malformedVersion.status}`,
  );
}

// ---------- Write token rotation: rotate → old rejected → replay idempotent ----------
{
  const id = newIdentity("rotate");
  const reg = await bootstrap(id, invite(7), REAL_DEVICE_TOKEN);
  const channelId = reg.body.channel.id;
  const endpoint = `${BASE}/v1/channels/${channelId}/messages`;

  async function publish(token) {
    const res = await fetch(endpoint, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ title: "Rotation", message: "after rotate", priority: "normal" }),
    });
    return res.status;
  }

  const oldWorksBefore = await publish(id.writeToken);
  const newToken = randomBytes(32).toString("base64url");
  const rotate = await fetch(`${BASE}/v1/channels/${channelId}/write-token`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
    body: JSON.stringify({ write_token_hash: sha256hex(newToken), expected_version: 1 }),
  });
  const rotateBody = await rotate.json();

  const replays = await Promise.all(
    Array.from({ length: 20 }, () =>
      fetch(`${BASE}/v1/channels/${channelId}/write-token`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
        body: JSON.stringify({ write_token_hash: sha256hex(newToken), expected_version: 1 }),
      }).then((r) => r.status),
    ),
  );
  const oldAfter = await publish(id.writeToken);
  const newAfter = await publish(newToken);
  const replayOk = replays.every((s) => s === 200);
  record(
    "WRITE_ROTATION",
    oldWorksBefore === 200 && rotate.status === 200 && rotateBody.write_token_version === 2 &&
      replayOk && oldAfter === 401 && newAfter === 200,
    `old_before=${oldWorksBefore} rotate=${rotate.status}/v${rotateBody.write_token_version} ` +
      `replay20=${replayOk} old_after=${oldAfter} new_after=${newAfter}`,
  );
}

// ---------- Private publish end-to-end (real FCM → device, verified via adb) ----------
{
  const id = newIdentity("e2e");
  const reg = await bootstrap(id, invite(8), REAL_DEVICE_TOKEN);
  const endpoint = reg.body.channel.endpoint;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: { Authorization: `Bearer ${id.writeToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      title: "Private channel E2E",
      message: "bootstrap → publish → fcm",
      priority: "high",
      url: "https://example.com/private",
    }),
  });
  const body = await res.json();
  record(
    "PRIVATE_PUBLISH",
    res.status === 200 && body.stored === true && body.push_status === "fcm_accepted",
    `endpoint=/v1/channels/…/messages status=${res.status} stored=${body.stored} push=${body.push_status} seq=${body.seq}`,
  );
}

// ---------- Invalid invite ----------
{
  const id = newIdentity("badinvite");
  const res = await bootstrap(id, "phb_deadbeefdead", `fake-token-${randomUUID()}`);
  record("INVALID_INVITE", res.status === 403, `status=${res.status}`);
}

const failed = results.filter((r) => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
