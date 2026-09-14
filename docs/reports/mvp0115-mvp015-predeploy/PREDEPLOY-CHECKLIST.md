# PREDEPLOY-CHECKLIST

All checks re-run at end of campaign (2026-09-15, release APK + deployed worker).

| Gate | Result | Evidence / note |
|------|--------|-----------------|
| PREDEPLOY_FRESH_INSTALL | **PASS** | release APK: purpose → invite → permission → inbox → real push in shade. First attempt correctly blocked by `beta capacity reached` (gate works; cap → 40). evidence/predeploy_release_fresh_mypush.png |
| PREDEPLOY_UPGRADE | **PASS** | `install -r` over existing install all night — channel/inbox/prefs preserved; Room v5 (no schema change this campaign); D1 migrations 011/012 additive only |
| PREDEPLOY_MY_PUSH | **PASS** | real Worker → D1 → FCM → shade on release fresh install |
| PREDEPLOY_GITHUB | **PASS (carried)** | owner-scoped fan-out VERIFIED MVP-003B; webhook unchanged this campaign; health hook added + protocol-tested. Live re-fire not run (would touch the owner's real repos) |
| PREDEPLOY_PYTHON | **PASS** | connector run/progress/attention + ComfyUI submit/watch E2E all night; 429 backoff exercised |
| PREDEPLOY_JOB_MODEL | **PASS** | one Job Card per (source, job_id) across cli/comfyui/agent/github sources on device |
| PREDEPLOY_ATTENTION | **PASS** | RUNNING/PROGRESS inbox-only; DONE/FAILED/NEEDS_ACTION on HIGH channel (logcat `delivered push (priority=HIGH)`) |
| PREDEPLOY_DELETE | **PASS** | swipe + delete-all + tombstones; 9/9 protocol; no resurrection; KO/EN |
| PREDEPLOY_I18N | **PASS** | KO/EN walked on all new surfaces; found & fixed missing KO health strings mid-campaign; known EN-only Diagnostics screen (P3, by design) |
| PREDEPLOY_THEME | **PASS** | light + dark walked (Inbox/cards/Connections screenshots); system default untouched |
| PREDEPLOY_FONT200 | **PASS** | font 2.0 on Inbox/Connections/cards, no clipping; P4 known: bottom-nav EN label wrap ≥150% |
| PREDEPLOY_PROCESS_DEATH | **PASS** | force-stop → relaunch: cards, cursor, delete state, prefs intact (debug + release) |
| PREDEPLOY_SECRET_SCAN | **PASS** | no secrets in tracked files (.dev.vars/.env/credentials gitignored; gcp_auth.ts handles runtime keys only; gh.exe is the known bundled tool). Screenshots show burned one-time codes only |

## Owner decisions recommended before public release
- Real BETA_MAX_INSTALLATIONS value + stale-installation cleanup story.
- QUOTA_DAILY_LIMIT back to 50 (or a value they trust) — raised for E2E.
- Play signing key / store listing (out of scope by rule).
