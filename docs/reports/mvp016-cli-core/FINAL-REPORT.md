# FINAL-REPORT — MVP-016 CLI Core

## Verdict

The PigeonHub CLI core is implemented on `autonomous/mvp016-cli-core-codex-20260917`.
It is a dependency-light Python package with a console entry point and keeps the
existing connector scripts working through a compatibility façade.

## Delivered

- `pigeonhub login`: PC-generated short-lived login request, terminal QR, Android
  approval polling, and atomic local credential storage.
- `pigeonhub logout` and `pigeonhub status`.
- `pigeonhub run`: unique job ID per invocation; `RUNNING` is accepted before the
  child starts; tracking failure prevents process start; child gets
  `PIGEONHUB_JOB_ID`; stdout/stderr and exit code are preserved.
- `pigeonhub notify`, `pigeonhub progress`, and `pigeonhub needs-action`.
- `schema_013.sql` plus Worker request/approve/poll routes for PC-first pairing.
- Android Camera2 + ZXing QR scanner and explicit PC approval dialog.
- Existing `connectors/pigeonhub_connector.py` imports and legacy `pair`/`attention`
  commands remain compatible.
- Connections examples now show `pigeonhub run` instead of the developer-only
  hook command path.

## Verification

- `python -m unittest discover -s tests -v`: 5/5 pass, including real child
  process lifecycle, same-job progress, exit-code preservation, tracking-failure
  refusal, and PC-first login polling.
- `python -m py_compile ...`: pass.
- `npm run typecheck` in `worker/`: pass.
- `android/gradlew.bat :app:compileDebugKotlin`: pass.
- Existing RC-001A `:app:testDebugUnitTest`: pass before the MVP-016 additions.

## Remaining operational step

Deploy/apply `worker/schema_013.sql` before enabling PC-first login against the
production Worker. MVP-018 still owns a signed Windows single executable,
installer, and PATH setup; this MVP intentionally keeps Python packaging as the
development distribution.
