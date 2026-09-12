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

  /** D1 binding: messages + quota_buckets (free plan). */
  DB: D1Database;
  /** Single development channel identifier until installations exist. */
  CHANNEL_ID?: string;
  QUOTA_DAILY_LIMIT?: string;
  QUOTA_MINUTE_LIMIT?: string;
  /** Global daily acceptance ceiling across all installations (free-tier protection). */
  GLOBAL_DAILY_LIMIT?: string;
  /** "on" only on the test worker — enables X-PigeonHub-Fail-At handling. */
  FAILURE_INJECTION?: string;

  /** MVP-001C: SHA-256 hex hashes of the single-use beta invite codes. */
  INVITE_HASHES?: string;
  /** MVP-002A: separate single-use invite pool for automated tests (never the beta pool). */
  INVITE_TEST_HASHES?: string;
  /** MVP-001C: base64 256-bit application key used to AES-GCM-encrypt FCM tokens at rest. */
  FCM_TOKEN_ENCRYPTION_KEY?: string;
  /** MVP-001E: beta gate — bootstrap rejects fresh registrations at this installation count. */
  BETA_MAX_INSTALLATIONS?: string;
  /** MVP-001E: set to "on" (config/deploy) to globally disable publishing (free-tier kill switch). */
  PUBLISH_KILL_SWITCH?: string;
  /** MVP-003B: GitHub App URL slug for install/upgrade URLs. */
  GITHUB_APP_SLUG?: string;
  /** MVP-003B: GitHub webhook signature verification secret. */
  GITHUB_WEBHOOK_SECRET?: string;
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

/** Stored message row (subset the API needs). */
export interface StoredMessage {
  id: string;
  seq: number;
  push_status: "pending" | "fcm_accepted" | "failed";
  request_hash: string;
  fcm_message_id: string | null;
  last_error: string | null;
}

export type FailurePoint = "d1_pre" | "d1_post" | "fcm" | "status_update";

export class PublishError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly body?: Record<string, unknown>,
  ) {
    super(message);
  }
}
