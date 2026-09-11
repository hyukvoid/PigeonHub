/**
 * MVP-001D sync API protocol tests. Run: node scripts/sync_test.mjs <base-url>
 * Consumes two fresh invite codes (INVITE_CODE_1, INVITE_CODE_2).
 * Publishes real FCM messages to REAL_DEVICE_TOKEN? No — uses fake tokens where
 * delivery doesn't matter (push_status=failed still persists canonically), and
 * the real device token only in the final delivery probe.
 */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

const BASE = process.argv[2] ?? "https://pigeonhub-push.pigeonhub.workers.dev";
const devVars = [
  readFileSync(new URL("../.dev.vars", import.meta.url), "utf8"),
  readFileSync(new URL("../../server/.env", import.meta.url), "utf8"),
].join("\n");
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

function newIdentity(tag) {
  return {
    bootstrapId: `boot-${tag}-${randomUUID()}`,
    managementSecret: randomBytes(32).toString("base64url"),
    writeToken: randomBytes(32).toString("base64url"),
  };
}

async function bootstrap(identity, inviteCode) {
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
      fcm_token: `sync-test-${randomUUID()}`,
      platform: "android",
    }),
  });
  return res.json();
}

async function publish(identity, channelId, title, messageId) {
  const res = await fetch(`${BASE}/v1/channels/${channelId}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${identity.writeToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ title, message: `m:${messageId}`, priority: "normal", message_id: messageId }),
  });
  const body = await res.json();
  // Canonical acceptance = stored in D1 (FCM may legitimately fail on fake tokens).
  return body.stored === true ? 200 : res.status;
}

async function syncMessages(identity, afterSeq, limit, snapshot) {
  const params = new URLSearchParams({ after_seq: String(afterSeq), limit: String(limit) });
  if (snapshot !== undefined) params.set("snapshot_max_seq", String(snapshot));
  const res = await fetch(`${BASE}/v1/installations/me/messages?${params}`, {
    headers: { Authorization: `Bearer ${identity.managementSecret}` },
  });
  return { status: res.status, body: await res.json() };
}

const invite = (n) => devVar(`INVITE_CODE_${n}`);
const RUN = randomBytes(3).toString("hex"); // unique message ids per run (messages.id is a global PK)
const mid = (name) => `${RUN}-${name}`;

// ---------- setup: two isolated installations ----------
const A = newIdentity("syncA");
const a = await bootstrap(A, invite(1));
const B = newIdentity("syncB");
const b = await bootstrap(B, invite(2));

// ---------- MESSAGE_API_AUTH ----------
{
  const noAuth = await fetch(`${BASE}/v1/installations/me/messages`);
  const writeTokenAuth = await fetch(`${BASE}/v1/installations/me/messages`, {
    headers: { Authorization: `Bearer ${A.writeToken}` },
  });
  const ok = await syncMessages(A, 0, 50);
  record(
    "MESSAGE_API_AUTH",
    noAuth.status === 401 && writeTokenAuth.status === 401 && ok.status === 200,
    `no_auth=${noAuth.status} write_token=${writeTokenAuth.status} management=${ok.status}`,
  );
}

// ---------- publish 120 messages to A (fake token: FCM rejects, D1 keeps — canonical) ----------
{
  let failures = 0;
  let sample = "";
  for (let i = 1; i <= 120; i++) {
    const status = await publish(A, a.channel.id, `Sync msg ${i}`, mid(`sync-${i}`));
    if (status !== 200) {
      failures++;
      if (failures <= 2) {
        const probe = await fetch(`${BASE}/v1/channels/${a.channel.id}/messages`, {
          method: "POST",
          headers: { Authorization: `Bearer ${A.writeToken}`, "Content-Type": "application/json" },
          body: JSON.stringify({ title: "probe", message: "x", priority: "normal", message_id: `probe-${i}` }),
        });
        sample = `HTTP ${probe.status} ${(await probe.text()).slice(0, 160)}`;
      }
    }
    if (i % 20 === 0) await new Promise((r) => setTimeout(r, 1500));
  }
  record("SETUP_120_PUBLISHED", failures === 0, `failures=${failures} ${sample}`);
}

// ---------- PAGINATION: 50/page over 120 ----------
{
  const seen = [];
  let after = 0;
  let snapshot;
  let hasMore = true;
  let guards = 0;
  while (hasMore && guards++ < 10) {
    const page = await syncMessages(A, after, 50, snapshot);
    if (snapshot === undefined) snapshot = page.body.snapshot_max_seq;
    seen.push(...page.body.messages.map((m) => m.seq));
    after = page.body.next_after_seq;
    hasMore = page.body.has_more;
  }
  const unique = new Set(seen);
  const sortedOk = seen.every((s, idx) => idx === 0 || seen[idx - 1] < s);
  record(
    "PAGINATION",
    seen.length === 120 && unique.size === 120 && sortedOk,
    `pages=${guards} total=${seen.length} unique=${unique.size} ordered=${sortedOk}`,
  );
}

// ---------- SNAPSHOT_PAGINATION + CONCURRENT_INSERT ----------
{
  // Full sync first (cursor lands at 120), then start a NEW sync and publish
  // 20 messages mid-pagination: the snapshot must exclude them.
  let after = 0;
  let snapshot;
  let hasMore = true;
  let firstPageSeqs = [];
  let guards = 0;
  let publishedMidSync = false;
  const midIds = [];
  while (hasMore && guards++ < 10) {
    const page = await syncMessages(A, after, 50, snapshot);
    if (snapshot === undefined) snapshot = page.body.snapshot_max_seq;
    if (guards === 2 && !publishedMidSync) {
      for (let i = 1; i <= 20; i++) {
        const id = mid(`mid-${i}`);
        midIds.push(id);
        await publish(A, a.channel.id, `Mid-sync ${i}`, id);
      }
      publishedMidSync = true;
    }
    if (guards === 1) firstPageSeqs = page.body.messages.map((m) => m.seq);
    after = page.body.next_after_seq;
    hasMore = page.body.has_more;
  }
  const next = await syncMessages(A, after);
  const nextSeqs = next.body.messages.map((m) => m.seq);
  const nextOnlyNew = nextSeqs.every((s) => s > snapshot);
  const noDup = new Set(firstPageSeqs).size === firstPageSeqs.length;
  record(
    "SNAPSHOT_PAGINATION",
    publishedMidSync && snapshot === 120 && nextSeqs.length === 20 && nextOnlyNew && noDup,
    `snapshot=${snapshot} mid_published=20 next_page=${nextSeqs.length} next_gt_snapshot=${nextOnlyNew}`,
  );
  record(
    "CONCURRENT_INSERT",
    publishedMidSync && nextOnlyNew && next.body.messages[0]?.seq === 121,
    `first_mid_seq=${next.body.messages[0]?.seq}`,
  );
}

// ---------- INCREMENTAL_SYNC ----------
{
  await publish(A, a.channel.id, `Incremental`, mid(`incr-1`));
  const page = await syncMessages(A, 140);
  record(
    "INCREMENTAL_SYNC",
    page.body.messages.length === 1 && page.body.messages[0].id === mid('incr-1'),
    `returned=${page.body.messages.length} id=${page.body.messages[0]?.id}`,
  );
}

// ---------- CROSS_INSTALLATION_ISOLATION ----------
{
  const aPage = await syncMessages(A, 0, 200);
  const bPage = await syncMessages(B, 0, 200);
  const leak = aPage.body.messages.some((m) => bPage.body.messages.some((bm) => bm.id === m.id));
  record(
    "CROSS_INSTALLATION_ISOLATION",
    aPage.body.messages.length >= 120 && bPage.body.messages.length === 0 && !leak,
    `A=${aPage.body.messages.length} B=${bPage.body.messages.length} leak=${leak}`,
  );
}

// ---------- RETENTION contract fields ----------
{
  const page = await syncMessages(A, 0, 5);
  const badSnapshot = await fetch(
    `${BASE}/v1/installations/me/messages?after_seq=0&snapshot_max_seq=-5`,
    { headers: { Authorization: `Bearer ${A.managementSecret}` } },
  );
  record(
    "RETENTION_CONTRACT",
    typeof page.body.retention_floor_seq === "number" &&
      page.body.retention_floor_seq === 0 &&
      page.body.history_truncated === false &&
      badSnapshot.status === 400,
    `floor=${page.body.retention_floor_seq} truncated=${page.body.history_truncated} bad_snapshot=${badSnapshot.status}`,
  );
}

const failed = results.filter((r) => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
