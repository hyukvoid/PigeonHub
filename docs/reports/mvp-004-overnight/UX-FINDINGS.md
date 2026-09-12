# MVP-004 — UX-FINDINGS

Findings from Artemis hands-on exploration (clean AVD `emulator-5554`, fresh install, invite onboarding).
Phase 1 records findings only; fixes land in later phases. Severity: P0 crash / P1 blocked-or-wrong / P2 confusing / P3 visual / P4 polish.

## Phase 1 — Baseline (2026-09-13, en-US → ko-KR walk)

### P0/P1 — functional correctness

1. **[P1] Inbox card shows a literal `null` URL row with an "Open" button** (10-inbox, 11-inbox-dark, 13-inbox-ko).
   - Root cause: sync JSON parsing uses `optString("url").ifEmpty { null }` — for a JSON `null` value org.json returns the *string* `"null"`, which passes the null-check and is stored in Room. The upsert then overwrites the FCM-inserted row's correct NULL with `"null"`. Same risky pattern on `connected_at`, `event_type`, `provider`, `run_id`, `attention_reason`, `facts_json` (InstallationRepository.kt ~L433-442, L353).
   - Fix planned: parse nullable JSON fields with `isNull()` guard (Android-side only, no backend change).
2. **[P1] Settings → About states "FCM integration: BLOCKED_PENDING_FIREBASE_SETUP (docs/FIREBASE_SETUP.md)"** — factually wrong (FCM delivery verified working in this session) and leaks internal file paths/dev jargon to users. Also `applicationId` shown.

### P2 — mixed language / trust

3. **[P2] Onboarding, Inbox (HomeScreen), Settings, Device screens are 100% hardcoded English** — string resources exist (values + values-ko) but the screens never call `stringResource`. KO locale shows English screens under Korean nav labels (13-inbox-ko).
4. **[P2] "Test 나에게 보내기"** — mixed-language button on the Connections My Push card (visible in *English* locale, 06/07). Test-send result texts are hardcoded Korean ("전송 완료 — 알림을 확인하세요" / "전송 실패").
5. **[P2] Bottom nav shows "Connections" twice** (debug build: Device tab reuses `nav_connections`). Confusing even in debug/dogfood.
6. **[P2] GitHub card shows "✓ Connected" status and a primary full-width "Connect" button at the same time** — contradictory state; a user cannot tell if action is needed (06).
7. **[P2] "Copy cURL" button on My Push card does not copy anything** — it only navigates (no-op when already on Connections). Misleading affordance (06).
8. **[P2] Test-send feedback text appears only as small gray text under the button; the OS notification is easy to miss while staring at the card** (minor, keep but improve wording/visibility).

### P3 — visual/layout

9. **[P3] Onboarding cards are top-aligned** with a large dead area below (01/02/03) — content should be vertically centered / branded.
10. **[P3] No icons anywhere on Connections cards** — GitHub / My Push / AI Agents / Custom cards are text-only and visually identical; scan-ability poor (06).
11. **[P3] Empty-state icon+text fine, but "Send your first test notification" TextButton is low-emphasis for the single most important next action** (05).
12. **[P3] Dev jargon on user screens**: "Endpoint" label + raw URL shown always on My Push card; "seq 1 - via FCM" (debug-only, acceptable); "HIGH"/"NORMAL" chips untranslated.
13. **[P3] MyPushScreen.kt is dead code** (not reachable from the app shell) but contains duplicate hardcoded UI — maintenance hazard.

### P4 — polish

14. **[P4] App/notification icon is a plain glyph; notification title for test send is generic "PigeonHub"** (fine, but test message could be friendlier + localized).
15. **[P4] No brand mark on onboarding header.**

### Dark mode baseline (11/12)

- Dynamic color (Material You) dark scheme renders correctly; no invisible-text or contrast failures observed on Inbox/Connections at 100%. System bars follow. ✅ No P0/P1 dark issues found at baseline.

## Phase 2+ findings (during fixes)

(filled as discovered)
