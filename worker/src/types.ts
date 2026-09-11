export interface Env {
  /** Not a secret: Firebase project id used in the FCM HTTP v1 URL. */
  FIREBASE_PROJECT_ID: string;
  /** Service-account client_email (Wrangler secret). */
  FIREBASE_CLIENT_EMAIL: string;
  /**
   * Service-account private key (Wrangler secret). PEM with real newlines OR
   * base64 of that PEM (base64 survives dotenv/CI transports better).
   */
  FIREBASE_PRIVATE_KEY: string;
  /** Device token used while there is no installation registry (D1 is a later milestone). */
  FCM_TEST_DEVICE_TOKEN: string;
  /** Development bearer secret guarding POST /push (distinct from future write tokens). */
  PUSH_BEARER_SECRET: string;
}

export type Priority = "normal" | "high";

export interface PushRequest {
  title?: unknown;
  message?: unknown;
  priority?: unknown;
  url?: unknown;
  token?: unknown;
  message_id?: unknown;
}

export interface ResolvedPush {
  message_id: string;
  title: string;
  message: string;
  priority: Priority;
  url?: string;
  sent_at: string;
  schema_version: "1";
  targetToken: string;
}
