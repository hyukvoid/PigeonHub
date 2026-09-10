import { cert, initializeApp } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import type { ResolvedPush } from "./types.js";

/**
 * Transport layer for the disposable dev sender.
 *
 * - mode "fcm":  real Firebase Admin SDK send. Activates automatically when
 *                FIREBASE_SERVICE_ACCOUNT_PATH (or GOOGLE_APPLICATION_CREDENTIALS)
 *                points at a service account JSON. That file is a SECRET — it is
 *                gitignored and must never be committed or logged.
 * - mode "mock": no credential available (current night-001 state:
 *                BLOCKED_PENDING_FIREBASE_SETUP). Validates the full request
 *                path and logs the exact FCM payload shape, so swapping in the
 *                credential later is a zero-code-change event.
 */

export type TransportMode = "fcm" | "mock";

export interface TransportInfo {
  mode: TransportMode;
  detail: string;
}

export function credentialPath(): string | undefined {
  return (
    process.env.FIREBASE_SERVICE_ACCOUNT_PATH ||
    process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    undefined
  );
}

export function resolveTransport(): TransportInfo {
  const path = credentialPath();
  if (path) {
    return { mode: "fcm", detail: path };
  }
  return {
    mode: "mock",
    detail: "set FIREBASE_SERVICE_ACCOUNT_PATH to enable real FCM sends (docs/FIREBASE_SETUP.md)",
  };
}

let firebaseInitialized = false;

function ensureFirebase(): void {
  if (firebaseInitialized) return;
  const path = credentialPath();
  if (!path) throw new Error("no service account configured");
  initializeApp({ credential: cert(path) });
  firebaseInitialized = true;
}

export interface SendResult {
  fcmMessageId: string;
}

/** Real send. Throws on FCM errors (unregistered token, quota, ...). */
export async function sendToFCM(push: ResolvedPush): Promise<SendResult> {
  ensureFirebase();
  const fcmMessageId = await getMessaging().send({
    token: push.token,
    // Data-only by design: the Android side owns rendering, validation and
    // channel selection. A notification payload would bypass our pipeline.
    data: {
      message_id: push.message_id,
      title: push.title,
      message: push.message,
      priority: push.priority,
      ...(push.url ? { url: push.url } : {}),
      sent_at: push.sent_at,
      schema_version: push.schema_version,
    },
    android: {
      // Server priority only steers FCM delivery urgency + the client's
      // channel choice; the heads-up behaviour itself comes from the
      // PigeonHub High channel importance on the device.
      // (firebase-admin's AndroidConfig.priority is lowercase.)
      priority: push.priority,
      ttl: 60 * 60 * 1000,
      collapseKey: "pigeonhub",
    },
  });
  return { fcmMessageId };
}

/** Mock transport: same payload shape, no network, token masked in logs. */
export async function sendMock(push: ResolvedPush): Promise<SendResult> {
  const { token, ...rest } = push;
  console.log("[mock-fcm] would send", JSON.stringify({ ...rest, token: maskToken(token) }));
  return { fcmMessageId: `mock/${push.message_id}` };
}

export function maskToken(token: string): string {
  return token.length <= 10 ? "***" : `${token.slice(0, 6)}…(${token.length} chars)`;
}
