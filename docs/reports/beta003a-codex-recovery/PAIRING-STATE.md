# PAIRING STATE — ANDROID PC CARD — BETA-003A

## The observed bug (real Galaxy, BETA-003)

After a successful QR approval the card kept showing the primary
**[PC 연결]** CTA — the state machine had no memory of a completed pairing,
so the user was one tap away from re-pairing for no reason. FAIL per the
campaign bar.

## The fix

`InstallationRepository` now owns `PcPairingState` (DataStore-persisted,
restored on process start):

| State | Card | CTA |
| --- | --- | --- |
| `NOT_PAIRED` | 연결된 PC 없음 | **[PC 연결]** |
| `PAIRED` | **PC가 연결됐어요** pill | none — the primary Connect CTA disappears |
| `STALE` | PC 연결을 새로 고쳐야 해요 | **[PC 다시 연결]** |

- `markPcPaired()` is called the moment the approval RPC succeeds → the
  card flips **immediately** (scanner close → dialog approve → card shows
  PAIRED). No app restart, no re-entry.
- `markPcPairingStale()` is reserved for a future live health check and is
  **never guessed** from a QR or network error (an approval failure is not
  staleness evidence).
- Parsing is a pure, unit-tested mapper (`PcPairingStateTest`, 3 tests):
  unknown/corrupted stored values degrade to NOT_PAIRED — a stored pairing
  is never fabricated and never lost on a malformed value.
- `ConnectionsScreen` is the only place in the app with a PC CTA; no other
  surface needed the same fix.
- No [PC 추가] / multi-PC affordances were added — single-PC is the real
  product today.
