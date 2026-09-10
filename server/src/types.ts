/** Shared types for the disposable dev sender. */

export interface PushRequestBody {
  title?: unknown;
  message?: unknown;
  priority?: unknown;
  url?: unknown;
  token?: unknown;
  /** Optional override so curl can replay the same id (duplicate-delivery tests). */
  message_id?: unknown;
}

export type Priority = "normal" | "high";

/** Validated, server-enriched push ready for a transport. */
export interface ResolvedPush {
  message_id: string;
  title: string;
  message: string;
  priority: Priority;
  url?: string;
  sent_at: string;
  schema_version: "1";
  token: string;
}

export type ValidateResult =
  | { ok: true; push: ResolvedPush }
  | { ok: false; errors: string[] };

/** Field values must match the Android PushPayload keys exactly. */
export function validatePush(
  body: PushRequestBody,
  fallbackToken: string | undefined,
): ValidateResult {
  const errors: string[] = [];

  const title = typeof body.title === "string" ? body.title.trim() : "";
  const message = typeof body.message === "string" ? body.message.trim() : "";

  if (!title) errors.push("title is required (string)");
  else if (title.length > 500) errors.push("title must be <= 500 characters");

  if (!message) errors.push("message is required (string)");
  else if (message.length > 4000) errors.push("message must be <= 4000 characters");

  let priority: Priority = "normal";
  if (body.priority !== undefined && body.priority !== null && body.priority !== "") {
    if (body.priority === "normal" || body.priority === "high") {
      priority = body.priority;
    } else {
      errors.push('priority must be "normal" or "high"');
    }
  }

  let url: string | undefined;
  if (body.url !== undefined && body.url !== null && body.url !== "") {
    if (typeof body.url !== "string") {
      errors.push("url must be a string");
    } else {
      try {
        const parsed = new URL(body.url);
        if (parsed.protocol !== "https:") {
          errors.push("url must be https (http is not allowed)");
        } else {
          url = body.url;
        }
      } catch {
        errors.push("url is not a valid absolute URL");
      }
    }
  }

  const token =
    typeof body.token === "string" && body.token.trim() ? body.token.trim() : fallbackToken;
  if (!token) {
    errors.push("no device token: pass { token } or set FCM_DEVICE_TOKEN");
  }

  let messageId: string;
  if (body.message_id === undefined || body.message_id === null || body.message_id === "") {
    messageId = crypto.randomUUID();
  } else if (typeof body.message_id === "string" && body.message_id.length <= 256) {
    messageId = body.message_id;
  } else {
    errors.push("message_id must be a string of at most 256 characters");
    messageId = "";
  }

  if (errors.length > 0) return { ok: false, errors };

  return {
    ok: true,
    push: {
      message_id: messageId,
      title,
      message,
      priority,
      ...(url ? { url } : {}),
      sent_at: new Date().toISOString(),
      schema_version: "1",
      token: token as string,
    },
  };
}
