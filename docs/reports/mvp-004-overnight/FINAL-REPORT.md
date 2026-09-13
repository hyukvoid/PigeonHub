# MVP-004 Overnight Product Polish — FINAL REPORT

Date: 2026-09-13 (overnight campaign)
Scope honored: UX/i18n/visual polish only. No new integrations, no backend changes, no pipeline refactors, no schema changes, no dependency additions.

## Verdict

**All stop conditions PASS. Phases 1–7 all executed.**

BUILD=PASS · CLEAN_AVD=PASS · MY_PUSH=PASS · KOREAN_UI=PASS · ENGLISH_UI=PASS ·
LIGHT_MODE=PASS · DARK_MODE=PASS · FONT_200=PASS · ARTEMIS_REGRESSION=PASS · NO_CRASH=PASS
(logcat FATAL EXCEPTION count = 0 across the whole campaign)

## 1. Before

From the Phase-1 clean-AVD walk (en-US → ko-KR, light/dark), recorded in [UX-FINDINGS.md](UX-FINDINGS.md):

- **[P1] Inbox cards showed a literal `null` URL row with an "Open" button** — sync parser stored the string `"null"` (org.json `optString` pitfall).
- **[P1] Settings → About displayed "FCM integration: BLOCKED_PENDING_FIREBASE_SETUP (docs/FIREBASE_SETUP.md)"** — factually wrong (FCM was delivering in that same session) plus `applicationId` dev jargon.
- **[P2] KO/EN mixing everywhere**: Onboarding, Inbox, Settings were 100% hardcoded English (string resources existed but were never wired); the test button literally read **"Test 나에게 보내기"** in the English locale.
- **[P2] Bottom nav showed "Connections" twice** (debug Device tab reused the label).
- **[P2] GitHub card showed "✓ Connected" and a full-width primary "Connect" button simultaneously.**
- **[P2] "Copy cURL" didn't copy anything** — it navigated to the tab it was already on. AI Agents/Custom "Set up" buttons were the same dead end.
- **[P3] Onboarding cards were top-aligned with a screen of dead space; no brand mark.**
- **[P3] Connections cards were text-only and visually identical** — no icons, no scan-ability.
- **[P3] Empty-inbox CTA was a low-emphasis text button.**

## 2. Changes

| Commit | Change |
|---|---|
| `afc49d4` | Campaign docs: PLAN, PROGRESS, UX-FINDINGS + before screenshots |
| `74b4078` | **i18n completed**: Home/Onboarding/Settings/Connections wired to `stringResource`; KO strings finished (incl. notification channel names); `nav_device` label; About card de-jargoned (version only); **P1 null-URL fix** (`optStringOrNull` at parse + https render guard for the persisted bad row); dead `MyPushScreen.kt` removed |
| `768c0f9` | **Appearance setting** (System/Light/Dark, default System) in a dedicated DataStore; system bars restyled to follow the override |
| `8ff5f1f` | **Connections visual polish**: uniform icon system (official GitHub mark as tinted VectorDrawable, Send, SmartToy, Api — 24dp glyph in a 40dp primary-container circle), connected/not-connected status chips, "Manage on GitHub" outlined action when already connected, real Copy-cURL, in-place expandable "How to connect" |
| `2a90ecd` | **Onboarding** centered with brand mark; empty-inbox CTA promoted to a filled button |
| `c8a4e89` | **Cold-start empty-inbox flash suppressed** (draw nothing until Room's first emission) |

## 3. Artemis Findings (hands-on, after the baseline fixes)

- **Scenario F caught a "disappearing message"**: after a per-app locale switch (process restart) the inbox briefly showed "No messages yet" although Room still held the row. Diagnosis: UI flash of the flow's initial value, not data loss (DB pulled via `run-as` and verified). Fixed in `c8a4e89`, re-verified on cold start.
- Dark mode at 100/150/200% and both locales: no contrast or invisible-content issues found (dynamic-color dark scheme held up; GitHub mark stays visible via onPrimaryContainer tint).
- Font 150–200%: all screens scroll, buttons reflow (CTA wraps to two lines cleanly), no clipped text. English bottom-nav labels wrap ("Connectio ns") at ≥150% — standard Material 3 behavior, Korean labels unaffected; accepted, see findings #18.
- Fresh-install persona walk (Phase 5/Scenario A): purpose → invite → permission → inbox all answer "what is this / what next" without jargon.

## 4. Tests (Artemis regression, Scenario A–G)

| Scenario | What was exercised | Result |
|---|---|---|
| A | `pm`-clean install → onboarding (invite redeemed) → Inbox | PASS |
| B | Connections → Send test notification → OS notification (shade captured) → Inbox row | PASS — real FCM delivery (`[fcm] delivered push … priority=HIGH`), no "null" URL |
| C | GitHub status on Connections (inherited via owner-scoped fan-out on two fresh installs) | PASS — "Connected" + "Manage on GitHub" |
| D | In-app Dark → Inbox/Connections/Settings walk | PASS |
| E | English locale → main screens | PASS |
| F | Korean locale → main screens (found & fixed the cold-start flash mid-run) | PASS |
| G | Font 200%: onboarding (fresh install) → Inbox → Connections → Settings | PASS |

MY_PUSH (in-app test → Worker → D1 → FCM → notification → durable inbox) re-verified three times during the campaign (KO and EN locales).

## 5. Screenshots

Before: `docs/reports/mvp-004-overnight/screenshots/before/` (01–15: onboarding, invite, permission, inbox, connections, settings — EN/KO/light/dark, incl. the "null" URL row and duplicated "Connections" tab)
After: `docs/reports/mvp-004-overnight/screenshots/after/` (p2 KO inbox/connections, p3 theme card + dark, p4 icon cards/how-to/copy snackbar, p5 onboarding centered + persona walk, p6 stress 150/200 light/dark/KO, p7 scenarios A–G)

## 6. Commits (this campaign)

`afc49d4` · `74b4078` · `768c0f9` · `8ff5f1f` · `2a90ecd` · `c8a4e89`

(plus this report commit)

## 7. Remaining Issues

1. **English bottom-nav label wrapping at font ≥150%** — accepted: Material 3 default; shortening labels reduces clarity; Korean unaffected.
2. **Endpoint string shown in full on My Push card** — kept (SelectionContainer, monospace): this is the "copy and paste into your script" surface; hiding it would hide the product's core affordance. A future "reveal/hide" toggle is polish, not a defect.
3. **"seq N - via FCM" line and Diagnostics tab** — debug builds only (`BuildConfig.DEBUG`); release builds never render them.
4. **Single-use invite provisioning is manual** (owner runs a local script + `wrangler secret put`). Out of scope tonight; the flow itself worked 3× during the campaign.
5. **Webhook duplicate redelivery returns 500 after insert** (pre-existing, noted in MVP-003B) — untouched per no-backend-change rule.

## 8. Recommendation (next, max 3)

1. **Localization smoke test in CI**: a unit/instrumentation check that fails when a screen composes hardcoded strings (lint `UnusedResources` + a screen-tour screenshot test) so the KO/EN drift that caused tonight's P2s can't silently return.
2. **GitHub "last event" health on the Connections card**: `github_health_*` strings already exist unused; surfacing "last build notification: X ago / notifications working" would make the Connected state trustworthy at a glance (worker data already supports it).
3. **Inbox unread affordance**: "new" is a small label today; an unread dot + bold title (and a swipe-to-mark-read) would make triage faster as message volume grows.
