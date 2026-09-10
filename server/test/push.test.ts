import assert from "node:assert/strict";
import { test } from "node:test";
import { buildApp } from "../src/app.js";

process.env.FCM_DEVICE_TOKEN = "test-device-token-for-unit-tests";

test("GET /health reports mock mode when no credential is configured", async () => {
  delete process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  delete process.env.GOOGLE_APPLICATION_CREDENTIALS;
  const app = buildApp();
  const res = await app.inject({ method: "GET", url: "/health" });
  assert.equal(res.statusCode, 200);
  const body = res.json();
  assert.equal(body.ok, true);
  assert.equal(body.mode, "mock");
  assert.equal(body.firebaseConfigured, false);
  await app.close();
});

test("POST /push happy path returns server-generated ids", async () => {
  const app = buildApp();
  const res = await app.inject({
    method: "POST",
    url: "/push",
    payload: { title: "Build Complete", message: "Deployment succeeded", priority: "high" },
  });
  assert.equal(res.statusCode, 200);
  const body = res.json();
  assert.equal(body.ok, true);
  assert.equal(body.mode, "mock");
  assert.equal(body.priority, "high");
  assert.match(body.message_id, /^[\da-f-]{36}$/);
  assert.ok(!Number.isNaN(Date.parse(body.sent_at)));
  await app.close();
});

test("POST /push honors message_id override and https url", async () => {
  const app = buildApp();
  const res = await app.inject({
    method: "POST",
    url: "/push",
    payload: {
      title: "t",
      message: "m",
      url: "https://example.com/x",
      message_id: "fixed-id-1",
    },
  });
  assert.equal(res.statusCode, 200);
  assert.equal(res.json().message_id, "fixed-id-1");
  await app.close();
});

test("POST /push rejects missing title/message", async () => {
  const app = buildApp();
  const res = await app.inject({ method: "POST", url: "/push", payload: {} });
  assert.equal(res.statusCode, 400);
  const errors = res.json().errors;
  assert.ok(errors.some((e: string) => e.includes("title")));
  assert.ok(errors.some((e: string) => e.includes("message")));
  await app.close();
});

test("POST /push rejects non-https url", async () => {
  const app = buildApp();
  const res = await app.inject({
    method: "POST",
    url: "/push",
    payload: { title: "t", message: "m", url: "http://example.com" },
  });
  assert.equal(res.statusCode, 400);
  assert.ok(res.json().errors.some((e: string) => e.includes("https")));
  await app.close();
});

test("POST /push rejects unknown priority", async () => {
  const app = buildApp();
  const res = await app.inject({
    method: "POST",
    url: "/push",
    payload: { title: "t", message: "m", priority: "urgent" },
  });
  assert.equal(res.statusCode, 400);
  assert.ok(res.json().errors.some((e: string) => e.includes("priority")));
  await app.close();
});

test("POST /push rejects when no device token is known", async () => {
  delete process.env.FCM_DEVICE_TOKEN;
  const app = buildApp();
  const res = await app.inject({
    method: "POST",
    url: "/push",
    payload: { title: "t", message: "m" },
  });
  process.env.FCM_DEVICE_TOKEN = "test-device-token-for-unit-tests";
  assert.equal(res.statusCode, 400);
  assert.ok(res.json().errors.some((e: string) => e.includes("token")));
  await app.close();
});

test("PUSH_AUTH_TOKEN enforces bearer auth", async () => {
  const app = buildApp({ authToken: "s3cret" });
  const denied = await app.inject({
    method: "POST",
    url: "/push",
    payload: { title: "t", message: "m" },
  });
  assert.equal(denied.statusCode, 401);

  const allowed = await app.inject({
    method: "POST",
    url: "/push",
    headers: { authorization: "Bearer s3cret" },
    payload: { title: "t", message: "m" },
  });
  assert.equal(allowed.statusCode, 200);
  await app.close();
});
