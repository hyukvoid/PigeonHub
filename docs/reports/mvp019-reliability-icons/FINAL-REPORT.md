# FINAL-REPORT — MVP-019 Beta Reliability Hardening + AI Agent Icon Accuracy

## Scoreboard

| Gate | Status |
|---|---|
| P0_STORED_BUT_502 | VERIFIED — worker 200-vs-502 semantics + CLI idempotency key; real dead channel: 1 event → 1 row (was 8); replay converges |
| P1_STALE_CREDENTIAL_UX | VERIFIED — "pairing can no longer reach your phone … Run: pigeonhub login" printed on real dead channel |
| P1_STALE_RUNNING_POLICY | VERIFIED — soft "no updates for X" marker (2h threshold), no fabricated states/pushes; device + unit tested |
| ICON_CLAUDE | VERIFIED — official orange starburst on ivory tile |
| ICON_CODEX | VERIFIED — official OpenAI blossom on black tile |
| ICON_GROK | VERIFIED — official Grok slash tile |
| ICON_ZCODE | VERIFIED — real ZCode app mark (kept per rules, trimmed + downscaled) |
| AGENT_CARD_VISUAL_CONSISTENCY | VERIFIED — one rounded 40dp container for all sections; brand tiles full-bleed; glyphs unchanged elsewhere |
| LIGHT_DARK | VERIFIED — evidence PNGs both modes |
| KO_EN | VERIFIED — per-app locale switch, Korean strings incl. the new stale marker |
| FONT_200 | VERIFIED — no truncation/overlap at 200% |
| ARTEMIS | VERIFIED — structured walk documented (ARTEMIS.md) |
| EXISTING_REGRESSION | VERIFIED — Python 16/16, worker typecheck, Android unit + assembleDebug/Release, inbox/cards render, health/relative-time intact |
| SECRET_SCAN | VERIFIED — clean |

## Final questions

- **Q1. duplicate row 문제는 재현 가능했고 수정 후 해결됐는가?** YES. 재현은
  어젯밤 프로덕션 데이터(이벤트 1개 → row 8개)로 존재했고, 수정 후 동일한
  죽은 채널에서 이벤트 3개 → row 3개, 동일 키 재시도는 replay로 수렴함을 D1으로
  확인했다.
- **Q2. dead channel 상태에서 사용자는 "다시 login 해야 함"을 이해할 수 있는가?**
  YES. CLI가 `pairing can no longer reach your phone — Run: pigeonhub login`
  안내를 정확히 출력한다(실제 채널에서 캡처).
- **Q3. AI Agent 카드만 보고 Claude / Codex / Grok / ZCode를 즉시 구분할 수
  있는가?** YES. 네 개의 공식 브랜드 마크(오렌지 스타버스트 / OpenAI 블로섬 /
  Grok 슬래시 / Z 마크) — 라이트·다크 모두 기기에서 확인.
- **Q4. 브랜드 인지성 ↑ 과 전체 카드 디자인 일관성 유지를 동시에 달성했는가?**
  YES. 모든 섹션이 동일한 40dp 라운드 컨테이너/카드 해부학을 공유하고,
  브랜드 타일만 콘텐츠가 다를 뿐이다(ARTEMIS.md 스크린샷).
- **Q5. 이번 작업이 beta 신뢰성 향상에 실제로 기여했는가?** YES. 저장된 이벤트의
  중복 발행 제거, 죽은 페어링의 명확한 자기 치유 안내, 좀비 RUNNING 카드의
  정직한 표시 — 세 항목 모두 베타 사용자가 겪는 실제 혼란을 직접 줄인다.

## Notes / follow-ups

- The overnight campaign's ZCode "simulated payload" caveat is now closed: a
  real desktop session produced a full lifecycle through the installed hooks.
- Creative cards deliberately keep the shared CLI health line (their pathway).
- Future (documented, not built): CLI keepalive for long silent jobs would
  enable true server-side staleness detection.
- Worker coalescing module untouched; its live-worker integration script
  remains the MVP-011 evidence.
