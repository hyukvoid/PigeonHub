import { createHash, randomBytes, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
const BASE = "https://pigeonhub-push.pigeonhub.workers.dev";
const dv = readFileSync(new URL("../.dev.vars", import.meta.url), "utf8");
const inv = new RegExp("^INVITE_CODE_4=(.*)$", "m").exec(dv)[1].trim();
const sha = (s) => createHash("sha256").update(s).digest("hex");
const id = { bootstrapId: "boot-cap-" + randomUUID(), managementSecret: randomBytes(32).toString("base64url"), writeToken: randomBytes(32).toString("base64url") };
const res = await fetch(`${BASE}/v1/installations`, {
  method: "POST",
  headers: { Authorization: `Bearer ${id.managementSecret}`, "Content-Type": "application/json" },
  body: JSON.stringify({ bootstrap_id: id.bootstrapId, invite_code: inv, write_token_hash: sha(id.writeToken), fcm_token: "cap-" + randomUUID(), platform: "android" }),
});
console.log("beta capacity gate:", res.status, JSON.stringify(await res.json()));
