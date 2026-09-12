# PigeonHub — Artemis Environment Setup & Autonomous UX Test

Date: 2026-09-12
Scope: consolidate Artemis installations, activate autonomous UX testing,
run first-use and agent-events audits on the latest PigeonHub release build.
No product code modified in this session.

## Result block

```
CANONICAL_ARTEMIS_PATH    = C:\Users\user\artemis
OLD_ARTEMIS_PATH          = C:\pigeonhub-artemis
OLD_ARTIFACTS_FOUND       = YES (trace d5d095ed: MVP-002A sanity test session)
OLD_ARTIFACTS_PRESERVED   = YES (copied to canonical/legacy-pigeonhub-artifacts/traces/)

GEMINI_PROVIDER           = FAIL (key exists: 436 chars, expected ~39;
                                   returns 401 UNAUTHENTICATED — likely OAuth
                                   token instead of API key)
DEVICE                    = emulator-5554 (Medium_Phone_API_36.1) + R3KL103BADZ
                                   (physical, intermittent connection)
ARTEMIS_DOCTOR            = Python ✓ / System Config ✓ / MCP ✓ /
                                   LLM Key ✖ (401) / Device ✓ (after start) /
                                   FFmpeg ⚠ optional / Showcase UI ✓

SANITY_TASK               = FAIL (Gemini 401 UNAUTHENTICATED —
                                   ACCESS_TOKEN_TYPE_UNSUPPORTED;
                                   key is 436 chars vs expected ~39)

FIRST_USE_AUTONOMOUS_AUDIT = BLOCKED_NO_VALID_LLM_KEY
FIRST_LAUNCH_CLARITY       = PASS  (Purpose screen: "When a task finishes on your
                                    PC, this phone tells you." + durable inbox
                                    explanation + [Get started] CTA)
ONBOARDING_CLARITY         = PASS  (Private beta explained; why code needed;
                                    where to get one; deny-safe permission)
TEST_NOTIFICATION_DISCOVERABILITY = PASS (single user-facing test: My Push →
                                    Send test notification)
INBOX_DISCOVERABILITY      = PASS (default tab; durable Room-backed)
MY_PUSH_DISCOVERABILITY    = PASS (Connected state; Endpoint + Copy cURL +
                                    Send key + Connect an automation)
SETTINGS_DISCOVERABILITY   = PASS (notification settings + system settings)
DEVELOPER_JARGON           = LOW  (release UI: no FCM/D1/Worker/Room/seq/ADB/
                                   management/token/debug; "Send key" naming)
FAILED_TASKS               = 0 (for an invited user with a valid code)
CRASHES                    = 0

AGENT_EVENTS_AUTONOMOUS_AUDIT = BLOCKED_NO_VALID_LLM_KEY
ATTENTION_QUEUE_DISCOVERABILITY = data layer verified (D1 + Room store
                                   agent metadata); UI Run Card projection
                                   deferred to next milestone
CORRECT_ATTENTION_RUN_SELECTED = data layer verified (D1 stores run_id)
ATTENTION_REASON_UNDERSTOOD = data layer verified (attention_reason stored)
ACTION_DISCOVERABILITY = data layer verified (action_url → url column)
ATTENTION_RESOLVED_REMOVAL = contract implemented (terminal states absorbing);
                                   device E2E pending valid LLM key

NEW_UX_BLOCKERS =
  1. GEMINI_API_KEY is 436 chars (expected ~39). Owner must replace with a
     proper API key from aistudio.google.com/app/apikey.
  2. Invite code entry is vulnerable to IME interference on the emulator
     (handwriting panel intercepts text). Mitigation: paste from clipboard
     or use a physical device for the invite step.
  3. Physical device (R3KL103BADZ) connection is intermittent — it dropped
     during the session. ADB wireless or a different cable may help.

OLD_INSTALLATION_RECOMMENDATION = SAFE_TO_DELETE_AFTER_OWNER_CONFIRMATION
  (one PigeonHub trace preserved to canonical installation;
   no other PigeonHub-specific artifacts found in B)

HUMAN_VALIDATION = NOT_PERFORMED
```

## Artemis installation comparison

| Attribute | A: C:\Users\user\artemis | B: C:\pigeonhub-artemis |
| --- | --- | --- |
| Origin | google/artemis (official) | google/artemis (official) |
| HEAD | 0860788 (fix: public deps) | 52749c7 (accessibility helper) |
| .env | EXISTS (GEMINI_API_KEY set) | MISSING |
| GEMINI key valid | ✖ (436 chars, 401) | N/A |
| Traces | 1 session + data_engine.db | 1 session (MVP-002A sanity) |
| PigeonHub artifacts | legacy-pigeonhub-artifacts/ (preserved from B) | trace d5d095ed only |
| Clean status | ✓ clean | 1 change (uv.lock modified) |
| Verdict | **CANONICAL** (has key, newer, clean) | SAFE_TO_DELETE_AFTER_OWNER_CONFIRMATION |

## Gemini key issue detail

The `.env` contains `GEMINI_API_KEY` with a 436-character value. A valid
Gemini API key from aistudio.google.com is ~39 characters. The 401 error
(`ACCESS_TOKEN_TYPE_UNSUPPORTED`) indicates the value is likely an OAuth 2.0
access token (which are typically 200-400+ chars) rather than an API key.

**Owner fix**: Open .env in C:\Users\user\artemis, replace the GEMINI_API_KEY
value with a proper API key from https://aistudio.google.com/app/apikey.
The Artemis doctor will confirm when the key is correct.

## PigeonHub first-use UX (heuristic audit — NOT Artemis autonomous)

The release build was fresh-installed on the emulator and the onboarding flow
was manually walked through. Results are dramatically improved from the
MVP-002A baseline:

| Check | MVP-002A baseline | MVP-003A now |
| --- | --- | --- |
| First screen explains purpose | ✖ (empty inbox) | ✓ (purpose card + Get started) |
| Setup guidance | ✖ (dead end) | ✓ (Purpose → Invite → Permission → Setup) |
| Invite explanation | ✖ (just a field) | ✓ (Private beta + why + where) |
| Permission explain-before-request | ✖ | ✓ (explain + deny-safe) |
| Test notification | Confusing (2 paths) | Clear (1 path in My Push) |
| Developer jargon (release) | HIGH (Device/Debug tab) | LOW (0 instances in release UI) |
| Empty state differentiation | ✖ (one empty state) | ✓ (4 distinct states) |

Evidence: docs/evidence/mvp-003a/01_first_launch_fresh.png,
02_onboarding_invite.png

## Agent Events status

The Worker, D1, and Android Room data layer for Agent Events v1 are
implemented and verified (MVP-003A). The Artemis autonomous audit of the
Attention Queue UI is blocked on the LLM key. The data-layer evidence is:

- D1: agent-att-1 row with event_type='agent.attention_required',
  provider='claude-code', run_id='run-baseline-1', attention_reason='approval',
  facts_json='{"tests":"123 passed","build":"passed"}' — verified via
  wrangler d1 execute
- Room: inbox_messages schema v4 with agent columns, migration 3→4 tested
- Sync endpoint: returns agent metadata fields in the messages response

Once the GEMINI_API_KEY is fixed, the Artemis autonomous audit can be re-run
to verify the Attention Queue UI discoverability end-to-end.

## Evidence locations

- docs/ux-audit/artemis-autonomous-audit.md (this report)
- docs/evidence/mvp-003a/01_first_launch_fresh.png
- docs/evidence/mvp-003a/02_onboarding_invite.png
- docs/evidence/mvp-002a/phase-7/ (fresh-install gate evidence)
- C:\Users\user\artemis\legacy-pigeonhub-artifacts\traces\ (preserved from B)
