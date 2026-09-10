import { buildApp } from "./app.js";
import { resolveTransport } from "./fcm.js";

const port = Number(process.env.PORT ?? 8787);
// HOST=0.0.0.0 if you must reach the sender from another machine on your LAN;
// with a USB device prefer `adb reverse tcp:8787 tcp:8787` and keep 127.0.0.1.
const host = process.env.HOST ?? "127.0.0.1";

const app = buildApp({ authToken: process.env.PUSH_AUTH_TOKEN || undefined });

app
  .listen({ port, host })
  .then((address) => {
    const transport = resolveTransport();
    app.log.info(`PigeonHub dev sender listening on ${address}`);
    if (transport.mode === "mock") {
      app.log.warn(
        "FCM transport: MOCK — nothing reaches a device yet. " +
          "Set FIREBASE_SERVICE_ACCOUNT_PATH (see server/.env.example / docs/FIREBASE_SETUP.md).",
      );
    } else {
      app.log.info("FCM transport: real Firebase Admin SDK");
    }
  })
  .catch((error) => {
    app.log.error(error, "failed to start");
    process.exit(1);
  });
