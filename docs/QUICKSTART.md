# PigeonHub Private Beta — Quickstart

Start it. Walk away. PigeonHub tells you when it matters.

What you need: a Windows PC and an Android phone. No Python, no command-line
experience beyond copy-paste.

## 1. Install PigeonHub on Windows

Double-click `PigeonHub-Setup-<version>.exe` → Install → Finish.

Windows may show a SmartScreen warning ("Windows protected your PC") because
the installer is not code-signed yet — click **More info → Run anyway**.
No administrator rights are needed.

## 2. Install PigeonHub on your phone

Install the PigeonHub Android APK we sent you (allow "install from this
source" if asked), open it, allow notifications, and sign in with your invite
code.

## 3. Connect your PC

On the PC, open PowerShell (Start → type "powershell") and run:

```
pigeonhub login
```

A QR code window opens. On the phone: **PigeonHub → 연결(Connections) →
PC 연결(Connect PC)** and point the camera at the QR so it sits inside the
frame. Approve on the phone when it asks. The PC says `Logged in` when done.

## 4. Run your first job

Still in PowerShell:

```
pigeonhub run --name "My first PigeonHub job" -- ping -n 6 127.0.0.1
```

About ten seconds later your phone shows the job **Running**, then **Done**.
That's the whole idea: start it, walk away, and your phone tells you.

## 5. Run something real

Any long-running command works — builds, crawls, renders, agents:

```
pigeonhub run --name "Blog crawler" -- python crawler.py
```

If the job fails or needs your input, the phone tells you immediately.

## 6. Optional: save commands you repeat

If you type the same command often, save it once:

```
pigeonhub recipe add
pigeonhub recipe run <its-name>
```

## Problems?

- `pigeonhub status` shows whether the PC is connected and the server is
  reachable.
- Your phone stopped showing jobs? Run `pigeonhub login` again.
- Anything else: write to us with the job name and what the card showed —
  never include prompts, code, or keys.
