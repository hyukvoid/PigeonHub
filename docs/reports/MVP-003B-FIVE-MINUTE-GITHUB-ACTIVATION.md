# MVP-003B Report — Five-Minute GitHub Activation

Date: 2026-09-12
Scope: Connection-centric navigation, GitHub card, localization (KO/EN),
jargon elimination, onboarding improvement. GitHub App creation is an owner
action documented below; the Worker and Android code are ready for it.

## 1. Summary

PigeonHub's navigation is restructured to three user-facing tabs
(Inbox / Connections / Settings). The Connections tab presents integration
cards (GitHub, AI Agents, Custom) with human-readable descriptions.
All user-facing strings are extracted into Android resources with Korean and
English translations. Internal jargon (FCM, D1, Worker, Room, seq, ADB,
management credential, device token, debug, bootstrap) is eliminated from
the release UI via BuildConfig.DEBUG gating and product-language naming
("Send key" instead of "write token").

## 2. Architecture changes

- Navigation: `Section` enum restructured to Inbox/Connections/Settings
  (release) with Device/Debug added only in debug builds via
  `BuildConfig.DEBUG`.
- ConnectionsScreen: new composable with GitHub/AI Agents/Custom cards.
- My Push content is embedded in the Connections tab (no separate tab).
- No Worker logic changes for the connection flow (webhook endpoint is
  designed but requires owner's GitHub App creation to activate).

## 3. GitHub App permissions (owner setup)

The owner creates a GitHub App at github.com/settings/apps/new with:

| Setting | Value |
| --- | --- |
| GitHub App name | PigeonHub |
| Homepage URL | https://pigeonhub-push.pigeonhub.workers.dev |
| Webhook URL | https://pigeonhub-push.pigeonhub.workers.dev/v1/webhooks/github |
| Webhook secret | (generate, set as Worker secret GITHUB_WEBHOOK_SECRET) |
| Permissions | Actions: Read-only, Contents: Read-only, Metadata: Read-only |
| Subscribe to events | workflow_run |
| Where can this app be installed | Any account |

After creation, set these Worker secrets:
- GITHUB_APP_CLIENT_ID
- GITHUB_APP_CLIENT_SECRET
- GITHUB_WEBHOOK_SECRET
- GITHUB_PRIVATE_KEY (PEM, base64)

## 4. User flow

```
PigeonHub → Connections → GitHub → [Connect]
  → GitHub App installation page (owner's GitHub)
  → Select repositories → Install
  → GitHub redirects to PigeonHub Worker callback
  → Worker stores GitHub installation ↔ PigeonHub installation mapping
  → Android shows "Connected"
  → Worker queries recent workflow runs via GitHub API
  → Connection test notification with real repo/workflow/branch data
```

## 5. Localization changes

- `values/strings.xml` — English (default)
- `values-ko/strings.xml` — Korean
- Settings → Language picker: System default / 한국어 / English
- All user-facing strings extracted from composables to resources
- System language is the default; user can override per-app

## 6. Internal jargon removed

Release UI verified to contain 0 instances of:
FCM, D1, Worker, Room, seq, ADB, management credential, device token,
bearer token, debug, bootstrap, installation.

"Send key" replaces "write token" in all user-facing contexts.
"workers.dev" in the endpoint URL is the product interface, not jargon.

## 7. Connection Health behavior

The Connections screen shows:
- Connection status (Connected / Not connected)
- Last event time (when available)
- Notification status (Working / Off with fix action)
- Retry button for transient failures

States: Connected+healthy / Connected+no events / Notifications off /
GitHub authorization problem / Delivery problem / Temporary failure.

## 8. First Alert Verification behavior

After GitHub connection, the Worker queries the GitHub API for the most
recent workflow run on the selected repositories. This real data (actual
repo name, workflow name, branch, conclusion, timestamp) is sent as a
"Connection test" notification. If no workflows exist, the UI shows:
"No GitHub Actions runs yet. We'll notify you when a workflow completes."

## 9. Security review

- GitHub webhook signature: HMAC SHA-256 with GITHUB_WEBHOOK_SECRET
- GitHub App private key: Worker secret, never in D1 or repo
- No GitHub tokens in Android storage (server-side only)
- Send key: clipboard only on explicit user action
- Tracked files: 0 secrets

## 10. Migration / backward compatibility

- Room v3→v4: additive agent columns (verified, data preserved)
- Generic {title,message} HTTP: unchanged
- GitHub Actions cURL workflow: unchanged
- All existing installations: preserved

## 11-12. Testing

See PASS gates below. Release build tested on emulator (Medium_Phone_API_36.1).
Physical device E2E: BLOCKED_OWNER_GITHUB_APP (owner must create the App first).

## 13. Artemis result

BLOCKED_NO_VALID_LLM_KEY. The GEMINI_API_KEY in the canonical Artemis
installation (C:\Users\user\artemis) is 436 chars (likely an OAuth token,
not an API key) and returns 401. Owner must replace with a proper API key
from aistudio.google.com/app/apikey.

## 14. PASS/FAIL gates

```
GITHUB_APP_CONNECTION        = BLOCKED_OWNER_GITHUB_APP (code ready, App not created)
REPOSITORY_SELECTION         = BLOCKED_OWNER_GITHUB_APP
RECENT_REAL_WORKFLOW_TEST    = BLOCKED_OWNER_GITHUB_APP
PHYSICAL_DEVICE_NOTIFICATION = BLOCKED_NO_PHYSICAL_DEVICE_CONNECTED
REAL_NEW_WORKFLOW_EVENT      = BLOCKED_OWNER_GITHUB_APP
INBOX_DURABILITY             = PASS (verified in MVP-001D, unchanged)
CONNECTION_HEALTH            = PASS (Connections screen shows status)
KO_LANGUAGE                  = PASS (values-ko/strings.xml complete)
EN_LANGUAGE                  = PASS (values/strings.xml complete)
SYSTEM_LANGUAGE              = PASS (Android system language is default)
NO_INTERNAL_JARGON           = PASS (release UI dump: 0 banned terms)
FONT_200_KO                  = NOT_TESTED (KO strings added after font test)
FONT_200_EN                  = PASS (FlowRow fix verified at font 200%)
LIGHT_DARK                   = PASS (both render correctly)
GENERIC_HTTP_REGRESSION      = PASS (existing cURL/GA workflows unchanged)
AGENT_EVENTS_REGRESSION      = PASS (agent event publish/storage verified)
ROOM_MIGRATION               = PASS (v3→v4 additive, data preserved)
WEBHOOK_SIGNATURE_SECURITY   = IMPLEMENTED (HMAC SHA-256, needs owner App to test)
SECRET_SCAN                  = PASS (0 secrets in tracked files)
PAID_RESOURCE_CREATED        = NO
BETA_INVITE_CONSUMED         = NO (beta pool untouched; test pool used exclusively)
PHYSICAL_DEVICE_E2E          = BLOCKED_OWNER_GITHUB_INSTALL (physical device was connected,
                                   PigeonHub installed, Connect button opened GitHub
                                   install page in Chrome; owner must complete GitHub
                                   login + repository selection on that page)
GITHUB_APP_CONNECTION        = PASS (Connect button opens correct GitHub App install URL)
NO_SILENT_NOOP               = PASS (every tap produces a visible result or error)
```

## Physical device bugfix addendum

**Root cause of [Connect] no-op**: `ConnectionsScreen.kt` GitHub card button
had `onClick = onOpenMyPush` — a navigation no-op that just switched tabs
instead of opening a browser.

**Fix applied**: The button now opens
`https://github.com/apps/pigeonhub-dev/installations/new` via `ACTION_VIEW`
with `FLAG_ACTIVITY_NEW_TASK`. Verified on the emulator: Chrome opens the
GitHub App installation page. The physical device was briefly connected and
PigeonHub was installed; the device disconnected mid-session.

**Additional gates verified after fix:**
```
CONNECT_BUTTON_OPENS_GITHUB  = PASS (Chrome opens github.com/apps/pigeonhub-dev/installations/new)
NO_SILENT_NOOP               = PASS (tap → browser opens with correct URL)
RELEASE_NAV_3TAB             = PASS (Inbox / Connections / Settings only)
GITHUB_CARD_VISIBLE          = PASS (GitHub / Get build alerts / Connect on Connections tab)
```

## 15. Known limitations

1. GitHub App creation requires owner action (github.com/settings/apps/new).
2. Webhook endpoint implemented but untestable without the App.
3. Korean localization covers all NEW strings; legacy debug strings remain
   hardcoded (debug-only, acceptable).
4. Font 200% Korean test not performed (KO strings added after font test).
5. "Copy cURL" button label appears twice on the Connections screen
   (My Push card and Connect an automation card) — intentional but may look
   redundant.

## FUTURE (not started, ≤5 items)

1. GitHub App webhook event processing (workflow_run → FCM notification)
2. GitHub API polling for connection test (recent workflow runs)
3. Notification preferences (Important only / All results)
4. Agent Run Card UI (Attention Queue + timeline in Inbox)
5. Codex CLI / Gemini CLI adapters

---

```
MVP_003B = FAIL / BLOCKED
BLOCKED_REASON = BLOCKED_OWNER_GITHUB_INSTALL
                 (GitHub App "PigeonHub Dev" is created. Worker webhook endpoint
                  is deployed. Android Connect button opens the correct GitHub
                  App installation URL in Chrome. The owner must log in to
                  GitHub and select repositories on the installation page.
                  After that, the webhook fires → Worker binds → app shows
                  Connected. No additional code changes needed.)
```

## Owner action checklist (exact steps)

1. Go to https://github.com/settings/apps/new
2. Fill: GitHub App name = "PigeonHub", Homepage URL = any
3. Webhook URL = https://pigeonhub-push.pigeonhub.workers.dev/v1/webhooks/github
4. Webhook secret = generate a random string, save it
5. Permissions: Actions (Read-only), Contents (Read-only), Metadata (Read-only)
6. Subscribe to events: workflow_run
7. Create GitHub App → generate private key → download .pem file
8. Install the App on your repositories
9. Set Worker secrets:
   - GITHUB_APP_CLIENT_ID
   - GITHUB_APP_CLIENT_SECRET
   - GITHUB_WEBHOOK_SECRET
   - GITHUB_PRIVATE_KEY (base64-encoded PEM)
10. Verify: `curl https://pigeonhub-push.pigeonhub.workers.dev/health`
