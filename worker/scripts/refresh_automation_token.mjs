// Refreshes the automation installation's FCM token from server/.env (MVP-002A:
// CI installations are long-lived, so their token must track device rotation).
import { readFileSync } from "node:fs";
const creds = JSON.parse(readFileSync(new URL("../.automation-credentials.json", import.meta.url), "utf8"));
const envText = readFileSync(new URL("../../server/.env", import.meta.url), "utf8");
const token = new RegExp("^FCM_DEVICE_TOKEN=(.*)$", "m").exec(envText)[1].trim();
const base = "https://pigeonhub-push.pigeonhub.workers.dev";
const me = await fetch(`${base}/v1/installations/me`, {
  headers: { Authorization: `Bearer ${creds.management_secret}` },
});
const meBody = await me.json();
const version = meBody.fcm_token_version;
const put = await fetch(`${base}/v1/installations/me/push-token`, {
  method: "PUT",
  headers: { Authorization: `Bearer ${creds.managementSecret ?? creds.management_secret}`, "Content-Type": "application/json" },
  body: JSON.stringify({ fcm_token: token, expected_version: version }),
});
const putBody = await put.json();
console.log(`push-token update: HTTP ${put.status} version=${putBody.fcm_token_version ?? "?"} updated=${putBody.updated ?? "n/a"}`);
