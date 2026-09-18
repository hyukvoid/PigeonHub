# PROGRESS — MVP-019

Newest first.

## 2026-09-18 (MVP-019 session)

- [x] Branch `autonomous/mvp019-reliability-icon-hardening-20260918` created
      (from `e40a202`); `ui.xml` preserved untouched.
- [x] **P0 worker fix**: durability vs delivery split — permanent FCM failure
      now returns 200 `push_status:"failed"` + `delivery:{retryable:false}`;
      replay carries the delivery outcome; deployed (test `f2f121a2`, prod
      `a308d20c`). Verified on the real dead channel: 1 event → 1 row.
- [x] **P0 CLI**: Idempotency-Key on every publish (payload-hash, retry-stable),
      retry only when not stored, `saved, delivery failed` wording, stale-pairing
      guidance block. Verified end-to-end. 2 new unit tests (16/16).
- [x] **P1 stale RUNNING**: soft marker policy (2h, no new states, no push);
      `RelativeTime.staleNoUpdatesRes` + unit test; JobCard wiring; device
      evidence on the hard-killed CtrlC-test card.
- [x] **P1 icons**: official brand tiles for the four agent cards (Claude
      starburst / OpenAI blossom / Grok slash / ZCode mark), uniform rounded
      40dp container, generator script committed.
- [x] **P2 copy audit**: per-agent health lines (Grok no longer borrows CLI
      activity); Creative section keeps the shared CLI line deliberately.
- [x] **Bonus verification**: real ZCode desktop session (`sess_0598fadb`)
      produced a full real lifecycle overnight (RUNNING→PROGRESS×9→DONE,
      fcm_accepted) — closes MVP-017.5's simulated-payload caveat.
- [x] QA matrix: Light/Dark × KO/EN × Font 200% + detail screen + inbox —
      evidence PNGs committed; app locale/font/mode restored afterwards.
- [x] Regression gate: Python 16/16, worker typecheck, Android unit +
      assembleDebug + assembleRelease, `git diff --check`, secret scan.
