# UX-FLOW — Setup Center (BETA-003)

```
Installer Finish  ──[☑ Set up PigeonHub now]──┐
Start Menu "PigeonHub Setup" ─────────────────┤
pigeonhub onboard (advanced) ─────────────────┘
                 │  127.0.0.1:<random>/setup?session=<token>
                 ▼
      ┌─ 1 Welcome ────────────────────────────────┐
      │ PigeonHub                                  │
      │ "오래 걸리는 작업이 끝나거나 확인이 필요할  │
      │  때 알려드려요."            [시작하기]      │
      └──────────────┬─────────────────────────────┘
                     ▼
      ┌─ 2 Connect phone ──────────────────────────┐
      │  [ LARGE QR ]  (rendered in-page)          │
      │  PigeonHub → 연결 → PC 연결 → QR 스캔       │
      │  Waiting… 09:42   (auto-polling)           │
      │  expired → [새 QR 코드 만들기]              │
      │  approved → 휴대폰 연결 완료 ✓              │
      └──────────────┬─────────────────────────────┘
                     ▼
      ┌─ 3 Connect tools ──────────────────────────┐
      │  OpenAI Codex   Detected    [연결]          │
      │  Claude Code    Detected    [연결]          │
      │  ZCode · GLM    Detected    [연결]          │
      │  Grok Build     Not detected (안내만)       │
      │  Connect = privacy dialog → confirm →       │
      │  existing setup engine                      │
      │  Codex: "마지막 한 단계" trust guidance      │
      └──────────────┬─────────────────────────────┘
                     ▼
      ┌─ 4 Test ───────────────────────────────────┐
      │  [테스트 알림 보내기] → 알림을 보냈어요 ✓     │
      │  도착했나요? [네, 도착했어요]                │
      └──────────────┬─────────────────────────────┘
                     ▼
      ┌─ Done ─────────────────────────────────────┐
      │  pigeonhub run -- <command>                 │
      │  (Recipes = optional one-liner)  [끝내기]    │
      └─────────────────────────────────────────────┘
```

Language toggle (한국어/English) lives in the header; it is a session
preference (`SET_LANG`), not an account subsystem. Default follows the system
locale at first render (browser `lang`, session default ko).

Words that never appear in the flow: FCM, D1, Worker, hook payload, JSONL,
connector token, channel ID, adapter, schema, hooks.json.

Errors are recovery-shaped: expired QR → "[새 QR 코드 만들기]"; pairing lost →
re-pair; agent "확인 필요" → [연결 확인] (re-detect). No raw HTTP/exception
text anywhere.
