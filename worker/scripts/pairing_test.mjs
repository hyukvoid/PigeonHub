/**
 * MVP-013 pairing protocol tests. Run: node scripts/pairing_test.mjs <base-url>
 * Consumes one fresh invite code (TEST_INVITE_CODE_16).
 * Gates: PAIR_SUCCESS, CONNECTOR_TOKEN_PUBLISH, ONE_TIME/REPLAY_REJECT,
 *        REVOKE, EXPIRY (row expired via D1 — own test row only), REPAIR.
 */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, rmSync } from "node:fs";

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
  bootstrapId: `boot-pair-${randomUUID()}`,
  managementSecret: randomBytes(32).toString("base64url"),
  writeToken: randomBytes(32).toString("base64url"),
};

{
  const res = await fetch(`${BASE}/v1/installations`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${identity.managementSecret}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      bootstrap_id: identity.bootstrapId,
      invite_code: devVar("TEST_INVITE_CODE_16"),
      write_token_hash: sha256hex(identity.writeToken),
      fcm_token: `pair-test-${randomUUID()}`,
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

async function issueCode() {
  const res = await fetch(`${BASE}/v1/installations/me/pairing-codes`, {
    method: "POST",
    headers: { Authorization: `Bearer ${identity.managementSecret}` },
  });
  return res.json();
}
async function redeem(code) {
  const res = await fetch(`${BASE}/v1/pairing/redeem`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  });
  return { status: res.status, body: await res.json() };
}
async function revokeAll() {
  const res = await fetch(`${BASE}/v1/installations/me/pairing-codes`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${identity.managementSecret}` },
  });
  return res.json();
}
async function publishWith(token, messageId) {
  const res = await fetch(`${BASE}/v1/channels/${identity.channelId}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ title: "pair test", message: "m", priority: "normal", message_id: messageId }),
  });
  const body = await res.json();
  return body.stored === true;
}

// ---- PAIR_SUCCESS ----
let codeA, tokenA;
{
  const issue = await issueCode();
  codeA = issue.code;
  const r = await redeem(codeA);
  tokenA = r.body?.write_token;
  record(
    "PAIR_SUCCESS",
    r.status === 200 && r.body?.ok && typeof tokenA === "string" && tokenA.startsWith("pct_")
      && r.body?.channel_id === identity.channelId && issue.ttl_seconds === 600,
    `status=${r.status} token=${tokenA ? "pct_…" : "missing"}`,
  );
}

// ---- CONNECTOR_TOKEN_PUBLISH: the minted token can publish to the channel ----
{
  const ok = await publishWith(tokenA, `pair-${randomBytes(3).toString("hex")}-1`);
  record("CONNECTOR_TOKEN_PUBLISH", ok, `stored=${ok}`);
}

// ---- ONE_TIME / REPLAY_REJECT ----
{
  const replay = await redeem(codeA);
  record("ONE_TIME_REPLAY_REJECT", replay.status === 403 && /already used/.test(replay.body?.error ?? ""), `status=${replay.status}`);
}

// ---- REVOKE ----
{
  const issue = await issueCode();
  await revokeAll();
  const r = await redeem(issue.code);
  record("REVOKE", r.status === 403 && /revoked/.test(r.body?.error ?? ""), `status=${r.status}`);
}

// ---- EXPIRY (expire the freshly issued code's own row via D1) ----
{
  const issue = await issueCode();
  const hash = sha256hex(issue.code.trim().toUpperCase());
  const sqlFile = new URL("./.expire_pairing_tmp.sql", import.meta.url);
  writeFileSync(sqlFile, `UPDATE pairing_codes SET expires_at = '2020-01-01T00:00:00.000Z' WHERE code_hash = '${hash}';\n`);
  try {
    execFileSync("npx", ["wrangler", "d1", "execute", "pigeonhub-messages", "--remote", "--yes",
      "--file", sqlFile.pathname.replace(/^\/([A-Z]:)/, "$1")],
      { cwd: new URL("..", import.meta.url).pathname.replace(/^\/([A-Z]:)/, "$1"), stdio: "pipe", shell: true });
  } finally {
    rmSync(sqlFile);
  }
  const r = await redeem(issue.code);
  record("EXPIRY", r.status === 403 && /expired/.test(r.body?.error ?? ""), `status=${r.status}`);
}

// ---- REPAIR: after all of the above, a brand-new code still pairs cleanly ----
{
  const issue = await issueCode();
  const r = await redeem(issue.code);
  const okPublish = r.body?.write_token ? await publishWith(r.body.write_token, `pair-${randomBytes(3).toString("hex")}-2`) : false;
  record("REPAIR", r.status === 200 && r.body?.ok && okPublish, `status=${r.status} republished=${okPublish}`);
}

const failed = results.filter((r) => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length === 0 ? 0 : 1);
