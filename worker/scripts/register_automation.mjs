/**
 * Registers an AUTOMATION installation for PigeonHub — the integration path a
 * script/CI uses to get its own private channel whose pushes land on the
 * enrolled device.
 *
 * Usage: node scripts/register_automation.mjs <name>
 * Reads:
 *   worker/.dev.vars  -> TEST_INVITE_CODE_1 (test pool, never the beta pool)
 *   server/.env       -> FCM_DEVICE_TOKEN   (target device for deliveries)
 * Writes:
 *   worker/.automation-credentials.json (gitignored pattern: .automation-*)
 *   { endpoint, write_token, installation_id, channel_id }
 * Prints only non-secret identifiers.
 */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";

const BASE = process.argv[3] ?? "https://pigeonhub-push.pigeonhub.workers.dev";
const NAME = process.argv[2] ?? "automation";
const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

function readVar(file, name) {
  const text = readFileSync(file, "utf8");
  const m = new RegExp(`^${name}=(.*)$`, "m").exec(text);
  if (!m) throw new Error(`missing ${name} in ${file}`);
  return m[1].trim();
}

const testInvite = readVar(new URL("../.dev.vars", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"), "TEST_INVITE_CODE_1");
const fcmToken = readVar(new URL("../../server/.env", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"), "FCM_DEVICE_TOKEN");

const managementSecret = randomBytes(32).toString("base64url");
const writeToken = randomBytes(32).toString("base64url");
const bootstrapId = `boot-auto-${NAME}-${randomUUID()}`;

const res = await fetch(`${BASE}/v1/installations`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${managementSecret}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    bootstrap_id: bootstrapId,
    invite_code: testInvite,
    write_token_hash: sha256hex(writeToken),
    fcm_token: fcmToken,
    platform: "automation",
  }),
});

const body = await res.json();
if (res.status >= 300) {
  console.error(`bootstrap failed: HTTP ${res.status} ${body.error ?? ""}`);
  process.exit(1);
}

writeFileSync(
  new URL("../.automation-credentials.json", import.meta.url),
  JSON.stringify(
    {
      name: NAME,
      installation_id: body.installation_id,
      channel_id: body.channel.id,
      endpoint: body.channel.endpoint,
      management_secret: managementSecret,
      write_token: writeToken,
      created_at: new Date().toISOString(),
    },
    null,
    2,
  ),
);

console.log(`automation installation registered: ${body.installation_id.slice(0, 8)}…`);
console.log(`channel: ${body.channel.id}`);
console.log(`endpoint: ${body.channel.endpoint.replace(BASE, "(worker origin)")}`);
console.log("credentials written to worker/.automation-credentials.json (gitignored)");

function sha256hex(value) {
  return createHash("sha256").update(value).digest("hex");
}
