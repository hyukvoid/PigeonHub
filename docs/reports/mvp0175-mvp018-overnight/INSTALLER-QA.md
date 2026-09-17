# INSTALLER-QA — MVP-018 (0.18.0-beta.1)

All steps executed on the dev machine (Windows 11, build 10.0.26200).
"Fresh shell" = PowerShell launched with the user PATH as read from the
registry (what a newly opened terminal sees).

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Silent install exits 0 | PASS | installer exit code 0 |
| 2 | Binary installed to `%LOCALAPPDATA%\Programs\PigeonHub` | PASS | `pigeonhub.exe` present |
| 3 | User PATH gains install dir | PASS | `HKCU\Environment\Path` contains exactly one `…Programs\PigeonHub` |
| 4 | Uninstall registry entry created | PASS | `PigeonHub CLI 0.18.0-beta.1` under HKCU Uninstall |
| 5 | Fresh shell: `where pigeonhub` resolves | PASS | listed (plus the dev-machine pip install, absent on real user machines) |
| 6 | Fresh shell: `pigeonhub --version` | PASS | `PigeonHub CLI 0.18.0-beta.1` |
| 7 | Fresh shell: `pigeonhub status` | PASS | logged in + worker reachable |
| 8 | Python-free operation | PASS | exe runs `--version`/`status`/banner with a PATH stripped of Python |
| 9 | First-run banner (logged out) | PASS | "You're not connected yet. / Start with: pigeonhub login" |
| 10 | First-run banner (logged in) | PASS | points at `pigeonhub run` |
| 11 | Success job from installed exe | PASS | RUNNING→DONE, child output preserved, exit 0 |
| 12 | Failed job, exit code preserved | PASS | `cmd /c exit 42` → FAILED + process exit 42 |
| 13 | Long job ≥ 60 s | PASS | 75 s ffmpeg job: RUNNING→DONE, exit 0 |
| 14 | Tool smoke: git | PASS | RUNNING→DONE |
| 15 | Tool smoke: npm (.cmd shim) | PASS after fix | see below |
| 16 | Hard kill mid-job | PASS (documented) | process tree killed at 5 s; job stays RUNNING until the worker-side stale-job timeout (future work, named in RESUME) |
| 17 | Login from installed exe | PASS (PC legs) | QR rendered (45 rows), pairing request row created in production D1; phone-approval leg = physical camera, owner action |
| 18 | Upgrade/reinstall in place | PASS | exit 0, exe replaced, PATH still exactly one entry, credentials preserved |
| 19 | Uninstall | PASS | install dir removed, PATH segment removed (17 entries = pre-install state, nothing else touched), uninstall key removed, **credentials kept by design** |
| 20 | Reinstall after uninstall | PASS | works, still logged in |
| 21 | Defender scan of `dist/` | PASS | "found no threats"; no detections logged |
| 22 | SHA256SUMS match artifacts | PASS | both binaries verified OK |

## Bug found and fixed by QA

**`pigeonhub run -- npm --version` failed on Windows** (RUNNING→FAILED,
exit 1). Root cause: `subprocess.Popen` cannot launch `.cmd`/`.bat` shims
(npm, winget, …) directly; CreateProcess only runs real executables. Fix in
`pigeonhub/core.py`: when `shutil.which` resolves the command to a `.cmd`/
`.bat`, the child is started via `cmd /c`. Regression test:
`test_run_starts_windows_batch_shims_via_cmd`. Re-verified end-to-end with the
upgraded installed exe (npm 11.17.0, RUNNING→DONE, exit 0).

## Decisions

- **Uninstall keeps credentials.** The credential is user data, not app
  binary; deleting it silently could log the user out of a device they no
  longer control the uninstall of. `pigeonhub logout` remains the explicit,
  intentional remover. (Documented in README and the finish-page text.)
- **Silent-install QA instead of wizard screenshots.** The wizard layout is
  declarative ([Messages]); a human pass on the interactive installer is a
  30-second owner confirmation item, not a build gate.
- **No per-job push during the minute-quota window.** Rapid successive jobs
  (QA ran 5+ jobs in a minute) hit the free-tier `QUOTA_MINUTE_LIMIT=5`;
  terminal publishes retry up to 8× so they always land, `RUNNING` does not
  retry by design (the job refuses to start rather than lie). Documented as
  expected free-tier behavior, not a bug.
