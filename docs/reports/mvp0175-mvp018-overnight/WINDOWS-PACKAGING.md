# WINDOWS-PACKAGING — MVP-018

## Choice: PyInstaller onefile + Inno Setup (per-user)

The CLI (`pigeonhub/`) is stdlib-only with one runtime dependency (`qrcode`,
needed for the login QR). A rewrite is banned by campaign rules and would be
worse maintenance anyway, so the packaging question is "how do we ship this
Python package as one Windows exe".

| Option | Verdict | Why |
|---|---|---|
| **PyInstaller onefile** | **chosen** | Single artifact, no Python needed by users, subprocess semantics preserved (it spawns real child processes), credential storage untouched (reads/writes `%USERPROFILE%\.pigeonhub` like the source CLI), minimal maintenance (one command, `packaging/windows/build.ps1`) |
| Nuitka | rejected | Longer build, C toolchain requirement, marginal benefit for a stdlib CLI |
| pip + bundled interpreter | rejected | Two artifacts, worse first-run UX, slower startup |
| onedir | rejected for beta | Faster startup but many files; revisiting only if onefile startup (~1.3 s) is reported as a problem |

## Installer: Inno Setup

- **Per-user, no admin**: `PrivilegesRequired=lowest`; installs to
  `%LOCALAPPDATA%\Programs\PigeonHub` (`{autopf}` resolves per-user).
- **PATH**: appends the install dir to the **user** `Path`
  (`HKCU\Environment`) only when it is not already an exact segment; on
  uninstall the exact segment is removed and everything else is preserved.
  A `WM_SETTINGCHANGE` broadcast lets newly opened shells see it.
- **Minimal wizard**: welcome/dir/program-group/ready pages disabled; the
  finish page tells the user the two next commands (`pigeonhub login`,
  `pigeonhub run …`).
- **Credentials are never touched by the installer or uninstaller.** Login
  state lives in `%USERPROFILE%\.pigeonhub\credentials.json`; removal is the
  explicit `pigeonhub logout` (see INSTALLER-QA).
- No Start Menu icons — it is a CLI; only the uninstall entry exists.

## Version

Single source: `pigeonhub/__init__.py` (`__version__`). `pyproject.toml` reads
it via `dynamic = ["version"]`; the installer version is passed to ISCC from
`build.ps1`; the exe prints `PigeonHub CLI <version>` for `--version` and
`version`. Current release: **0.18.0-beta.1**.

## Build pipeline

`packaging/windows/build.ps1`:
1. read the version from `pigeonhub/__init__.py`
2. build `dist/pigeonhub.exe` (PyInstaller onefile, entry `pigeonhub_entry.py`)
3. compile `installer/PigeonHub-Setup-<version>.exe` (ISCC `/DAPP_VERSION`)
4. lay out `dist/` (`pigeonhub.exe`, setup exe, `SHA256SUMS.txt`)

Binary artifacts are gitignored; scripts are committed.

## Known limitations (beta.1)

- **No code signing** → SmartScreen/AV may warn on first run
  (`DEFERRED_OWNER_ACTION`); no paid certificate is required for this MVP.
- Onefile startup is ~1.2–1.6 s (self-extraction) on the dev machine.
- The `qrcode` dependency is bundled; the legacy `--qr-image` decode path
  (zxing/PIL) is not bundled — unused for the PC-first flow.
