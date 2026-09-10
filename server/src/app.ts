import Fastify, { type FastifyInstance } from "fastify";
import { resolveTransport, sendMock, sendToFCM } from "./fcm.js";
import { validatePush, type PushRequestBody } from "./types.js";

export interface BuildOptions {
  /** When set, /push requires `Authorization: Bearer <authToken>`. */
  authToken?: string;
}

export function buildApp(options: BuildOptions = {}): FastifyInstance {
  const app = Fastify({
    logger: {
      redact: {
        // Never let auth material hit the logs.
        paths: ["req.headers.authorization", "req.headers.cookie"],
        censor: "[REDACTED]",
      },
    },
    bodyLimit: 64 * 1024,
  });

  app.get("/health", async () => {
    const transport = resolveTransport();
    return {
      ok: true,
      mode: transport.mode,
      firebaseConfigured: transport.mode === "fcm",
      hint: transport.detail,
    };
  });

  app.post("/push", async (request, reply) => {
    if (options.authToken) {
      const header = request.headers.authorization;
      if (header !== `Bearer ${options.authToken}`) {
        return reply.code(401).send({ ok: false, error: "unauthorized" });
      }
    }

    const fallbackToken = process.env.FCM_DEVICE_TOKEN;
    const result = validatePush((request.body ?? {}) as PushRequestBody, fallbackToken);
    if (!result.ok) {
      return reply.code(400).send({ ok: false, errors: result.errors });
    }

    const transport = resolveTransport();
    try {
      const sent =
        transport.mode === "fcm"
          ? await sendToFCM(result.push)
          : await sendMock(result.push);
      return {
        ok: true,
        mode: transport.mode,
        message_id: result.push.message_id,
        sent_at: result.push.sent_at,
        priority: result.push.priority,
        fcm_message_id: sent.fcmMessageId,
      };
    } catch (error) {
      // Log the failure reason but never the request body or token.
      request.log.error({ err: error }, "fcm send failed");
      return reply.code(502).send({
        ok: false,
        error: "fcm send failed",
        detail: error instanceof Error ? error.message : String(error),
      });
    }
  });

  return app;
}
