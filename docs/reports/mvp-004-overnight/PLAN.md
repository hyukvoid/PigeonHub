# MVP-004 Overnight Product Polish — PLAN

Date: 2026-09-13 (overnight campaign)
Scope: UX/i18n/visual polish only. No new integrations, APIs, backend services, or core pipeline refactors.

## Goal

A real first-time user should be able to install PigeonHub and immediately understand what it is,
what to do next, and trust its states — in Korean and English, in Light and Dark, at 100–200% font scale.

## Baseline (inherited from MVP-001..003B)

- Push pipeline (webhook → worker → D1 → FCM → Android) verified E2E.
- App: Compose + Material 3, bottom nav (Inbox / Connections / Settings, + Device in debug).
- strings.xml + values-ko/strings.xml exist; screens partially use them (hardcoded English remains).

## Phases

| Phase | Focus | Exit criteria |
|-------|-------|---------------|
| 1 | Baseline Capture | Full app walked on clean AVD via Artemis; findings recorded in UX-FINDINGS.md before any fix |
| 2 | Language Consistency | No mixed KO/EN on any screen; hardcoded strings moved to resources; natural Korean |
| 3 | Light / Dark Mode | System/Light/Dark all legible on every screen; no low-contrast issues |
| 4 | Connections Visual Polish | Cards scan-able by icon + status + next action; consistent icon system |
| 5 | UX Simplification | Non-technical persona can answer "what is this / what next" on every screen |
| 6 | Accessibility & Stress | 100/150/200% font × Light/Dark × KO/EN: no clipping/overflow |
| 7 | Artemis Regression | Scenarios A–G all PASS on emulator |

## Stop conditions (all required)

BUILD=PASS, CLEAN_AVD=PASS, MY_PUSH=PASS, KOREAN_UI=PASS, ENGLISH_UI=PASS,
LIGHT_MODE=PASS, DARK_MODE=PASS, FONT_200=PASS, ARTEMIS_REGRESSION=PASS, NO_CRASH=PASS
and Phases 1–7 all executed.

Blocked items are recorded and reported; the campaign does not end on a single block.

## Safety rules honored

No backend rewrite, no pipeline refactor, no fan-out structure change, no D1 data deletion,
no secrets printed/committed, no broad dependency upgrades, no new architecture,
no UI redesign that hides existing features. Small commits; never proceed on a broken build.
