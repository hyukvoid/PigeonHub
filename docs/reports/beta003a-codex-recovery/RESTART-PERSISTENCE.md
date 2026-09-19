# RESTART PERSISTENCE — BETA-003A

## Question

Does anything a user pairs once disappear because a process ended or the PC
rebooted?

## What was verified tonight

**Phone pairing credential (PC side).** `~/.pigeonhub/credentials.json` is
written atomically at approval and read fresh by every new process.
Measured: channel `ch_726b901…` → new `python -m pigeonhub status --json`
process → `logged_in: true`, `worker_reachable: true`, identical
`channel_id`. → **PROCESS_RESTART_VERIFIED**.

**Installed CLI after uninstall/reinstall.** Silent uninstall (dir, uninstall
key, and PATH entry removed) → silent reinstall → installed binary reports
0.20.0-beta.2, PATH entry restored, credentials byte-identical
(SHA-256), a new installed-CLI process is logged in and reaches the worker.

**Android pairing state.** `PcPairingState` is persisted in DataStore and
restored on process start (pure mapper unit-tested); after kill/reopen the
card is still PAIRED — a fresh [PC 연결] CTA after restart is a FAIL
condition by construction.

**Setup Center returning user.** `/api/status` reports `already_paired`
from the persisted credential; the phone step shows "휴대폰이 이미 연결돼
있어요 ✓ / 다음: 도구 연결" instead of demanding a new QR (unit-tested).

## What was NOT executed tonight

A **real Windows reboot**. The automation environment cannot reboot the
machine mid-campaign. Honest status:
**REAL_REBOOT_DEFERRED_OWNER_ACTION** — owner reboots once and confirms
(1) no QR re-scan demanded, (2) Codex still connected, (3) Galaxy
notifications still arrive. Everything except the physical power cycle has
been proven: the credential is on disk, identity restores in a new process,
and the Setup Center recognizes the existing pairing.
