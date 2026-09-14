# FINAL-REPORT — MVP-011.5 → MVP-015 Pre-Deployment Campaign
Night of 2026-09-14 → 2026-09-15. Branch `autonomous/mvp0115-mvp015-predeploy-overnight-20260914`.

## 1. Executive verdict

**PRE-DEPLOYMENT READY** (beta-scope).

Every MVP from 011.5 through 015 is VERIFIED with real end-to-end evidence, the
build/tests/tree are clean, and the only remaining blockers are owner-owned
external actions (Claude quota, store credentials, cap tuning). No P0/P1 issue
remains open; every P1 found this night was fixed and re-verified.

## 2. MVP scoreboard

| MVP | Status |
|-----|--------|
| MVP-011.5 Inbox Correctness & Message Management | **VERIFIED** |
| MVP-012 Real ComfyUI Integration | **VERIFIED** (real app, real workflows) |
| MVP-013 QR Pairing | **VERIFIED** |
| MVP-014 Real Agent Long-session E2E | **VERIFIED** (Codex) / Claude = DEFERRED_OWNER_ACTION |
| MVP-015 Connection Health & Reliability UX | **VERIFIED** |

## 3. What actually works (real E2E only)

- Live relative time that never freezes (60s tick + fg-resume jump), singular/
  plural in EN, elapsed durations that clamp clock skew.
- Durable inbox deletion: swipe one card or delete all; tombstones survive
  syncs; other clients converge; idempotent; KO/EN confirmations.
- Real ComfyUI: queue a workflow from PC (`submit`) or from the ComfyUI web UI
  (`watch`) → RUNNING/PROGRESS/DONE/FAILED on the phone through the standard
  pipeline; output counts, elapsed, node/step progress; failures name the node.
- QR pairing: phone shows QR, PC pairs by decoding a screenshot; one-time,
  10-minute, revocable, replay-rejected (protocol-proven).
- Real agent sessions (Codex CLI): start → step progress → DONE/FAILED as ONE
  Job Card, terminal states as HIGH-priority pushes.
- Connection health: worker-observed per-source states with relative "last
  activity" lines, DEGRADED surfacing the stale source, self-healing on new
  activity; recovery actions on every card.
- Fresh install (release APK) → first real push delivered; process death,
  offline recovery, and upgrade paths hold.

## 4. Owner actions (max 5)

1. **Claude Code quota**: add credits, then rerun the agent E2E pattern with
   Claude hooks (`connectors/agent_hook.py`) — Codex path already proven.
2. **Beta cap decision**: `BETA_MAX_INSTALLATIONS` raised 20→40 for testing;
   pick the real cap + a tombstone-safe cleanup for stale test installations.
3. **Quota re-tighten**: `QUOTA_DAILY_LIMIT` 50→300 was for nightly E2E;
   set the production value you trust.
4. **Physical-device pass**: one real Galaxy reconnect + camera QR scan
   (emulator cannot exercise the camera path; PC-side decode is already real).
5. **Play release credentials**: signing key + store listing (explicitly out of
   scope this campaign).

## 5. Known risks (severity order)

1. FCM-side deferral observed once (~10 min, burst of data messages); sync
   converges independently. If it recurs: WorkManager periodic sync.
2. Connector death mid-job leaves a RUNNING card (no server-side job timeout yet).
3. Test pool shares the beta install cap (see owner action 2).
4. Diagnostics screen EN-only; empty-state CTA label oversells (navigates).
5. Bottom-nav EN labels wrap at font ≥150% (P4, carried from MVP-004).

## 6. Commits → feature → verification

| Commit | Feature | Verification |
|--------|---------|--------------|
| `42dfba6` | tombstone deletion (worker+android WIP) + live relative time | delete_test 8/8, RelativeTimeTest |
| `5833274` | device-E2E fixes: connector `args.cmd` bug, elapsed clamp, plurals, snackbar | full device E2E, tests 8/8 |
| `a8d8254` | MVP-012 ComfyUI connector (submit/watch) + ComfyUI display fix | real-workflow E2E on device |
| `7650c91` | MVP-013 QR render + `pair --qr-image` + pairing_test 6/6 | QR-from-screenshot pairing E2E |
| `4acb11d` | MVP-014 Codex bridge + real agent E2E | real sessions DONE/FAILED on device |
| `0f9d6df` | MVP-015 health (worker + UI) + quota bump | delete_test 9/9, DEGRADED/self-heal E2E |
| (this commit) | docs + RESUME + checklist | — |

## 7. Evidence

`docs/reports/mvp0115-mvp015-predeploy/evidence/` — 12 screenshots covering the
running card at T0, delete confirmations (EN/KO), dark + font-200 renders,
ComfyUI cards, QR dialog, agent cards, health states, and the release fresh
install's delivered push. Protocol logs inline in E2E-EVIDENCE.md. Worker
versions: 957d83ca → 111909b5 → 06046c1d → 5e11002f.

## 8. Deployment recommendation

**CONDITIONAL GO** for a beta expansion (invite-scoped) — deployable as-is
tonight on the merits of the verified flows. Conditions before any public
listing: owner actions 1-5 above, of which only the beta-cap/quota tuning and
store credentials are true gates.

### What would prevent a normal user from successfully using PigeonHub tomorrow?

Honest answer: **nothing in the core loop, but three things could hurt a bad
first week** —

1. If their connector or ComfyUI dies mid-job, the phone shows a job stuck on
   "Running" forever (no timeout yet) — that reads as "notifications are broken"
   even though the health dot tells the truth. The new health line answers
   "why didn't I get notified?" without logs, which is the product promise, but
   a first-week user shouldn't have to interpret it.
2. The beta install cap (40) is close enough that an enthusiastic share-out
   would start rejecting new users with "beta capacity reached" — intended, but
   the owner must own that number before telling people.
3. Getting a real agent wired still requires following connector docs by hand
   (pair, hook config). Codex/CLI users manage; a ComfyUI-only user with no
   Python comfort does not — there is no one-click path yet.

Everything else we tested — install, invite, first push, job cards, deletes
that stay deleted, health that self-heals, Korean and English, big fonts,
dark rooms, killed processes, and airplane mode — worked tonight, on a release
build, without a developer present.
