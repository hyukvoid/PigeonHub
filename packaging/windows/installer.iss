; PigeonHub CLI — per-user Windows installer (MVP-018; BETA-003 onboarding UX).
; Build: ISCC installer.iss /DAPP_VERSION=x.y.z
; Design decisions (see docs/reports/mvp0175-mvp018-overnight/WINDOWS-PACKAGING.md):
;   - per-user, no admin required (installs under %LOCALAPPDATA%\Programs)
;   - adds the install dir to the USER Path only, preserving all other entries
;   - uninstall removes the binary + Path entry but never touches
;     %USERPROFILE%\.pigeonhub (credentials live only through `pigeonhub logout`)
;   - BETA-003: "Set up PigeonHub now" on the Finish page launches the Setup
;     Center (fresh installs only) + a Start Menu shortcut; never auto-starts
;     on upgrades.

#define AppName "PigeonHub"
#define AppExeName "pigeonhub.exe"
#ifndef APP_VERSION
#define APP_VERSION "0.0.0-dev"
#endif
#define UninstallKey "{7C1F6A4E-52B0-4B1E-9F3A-6B1E5A2C9D44}_is1"

[Setup]
AppId={{7C1F6A4E-52B0-4B1E-9F3A-6B1E5A2C9D44}
AppName={#AppName}
AppVersion={#APP_VERSION}
AppVerName={#AppName} {#APP_VERSION}
DefaultDirName={autopf}\{#AppName}
PrivilegesRequired=lowest
DisableWelcomePage=yes
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
CreateUninstallRegKey=yes
Uninstallable=yes
UninstallDisplayName={#AppName}
OutputDir=installer
OutputBaseFilename=PigeonHub-Setup-{#APP_VERSION}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=no

[Files]
Source: "dist\pigeonhub.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\PigeonHub Setup"; Filename: "{app}\{#AppExeName}"; Parameters: "onboard"; Comment: "Connect your phone and tools to PigeonHub"
Name: "{autodesktop}\PigeonHub Setup"; Filename: "{app}\{#AppExeName}"; Parameters: "onboard"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut for PigeonHub Setup"; Flags: unchecked

[Run]
Filename: "{app}\{#AppExeName}"; Parameters: "onboard"; Flags: postinstall nowait skipifsilent runasoriginaluser; Description: "Set up PigeonHub now"; Check: FreshInstall

[Messages]
SelectDirDesc=Where should PigeonHub be installed?
FinishedLabelNoIcons=PigeonHub is installed.%n%nClick Finish to open the PigeonHub Setup Center and connect your phone — no terminal needed.%n%nPrefer the terminal? Open a NEW PowerShell window and run:%n%n    pigeonhub login
FinishedLabel=PigeonHub is installed.%n%nClick Finish to open the PigeonHub Setup Center and connect your phone — no terminal needed.%n%nPrefer the terminal? Open a NEW PowerShell window and run:%n%n    pigeonhub login

[Code]
const
  WM_SETTINGCHANGE = $001A;
  SMTO_ABORTIFHUNG = $0002;

function SendMessageTimeoutW(hWnd: HWND; Msg: UINT; wParam: Cardinal; lParam: string;
  fuFlags: UINT; uTimeout: UINT; var lpdwResult: DWORD): LongInt;
  external 'SendMessageTimeoutW@user32.dll stdcall setuponly';

function SendMessageTimeoutWU(hWnd: HWND; Msg: UINT; wParam: Cardinal; lParam: string;
  fuFlags: UINT; uTimeout: UINT; var lpdwResult: DWORD): LongInt;
  external 'SendMessageTimeoutW@user32.dll stdcall uninstallonly';

procedure BroadcastSettingChange(ForUninstall: Boolean);
var
  Msg: DWORD;
begin
  if ForUninstall then
    SendMessageTimeoutWU(HWND_BROADCAST, WM_SETTINGCHANGE, 0, 'Environment', SMTO_ABORTIFHUNG, 1000, Msg)
  else
    SendMessageTimeoutW(HWND_BROADCAST, WM_SETTINGCHANGE, 0, 'Environment', SMTO_ABORTIFHUNG, 1000, Msg);
end;

{ True on a first-time install; upgrades never auto-launch the Setup Center. }
{ Detected via the existing uninstall key (WizardIsUpgrade is not in all IS6 builds). }
function FreshInstall: Boolean;
begin
  Result := not RegKeyExists(HKEY_CURRENT_USER,
    'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#UninstallKey}');
end;

{ True when the user Path already contains AppDir as an exact segment. }
function PathContainsDir(const Path, Dir: string): Boolean;
begin
  Result := Pos(';' + Uppercase(Dir) + ';', ';' + Uppercase(Path) + ';') > 0;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Path, AppDir: string;
begin
  if CurStep <> ssPostInstall then Exit;
  AppDir := ExpandConstant('{app}');
  if RegQueryStringValue(HKEY_CURRENT_USER, 'Environment', 'Path', Path) then
  begin
    if not PathContainsDir(Path, AppDir) then
    begin
      if (Path <> '') and (Copy(Path, Length(Path), 1) <> ';') then
        Path := Path + ';';
      Path := Path + AppDir;
      RegWriteStringValue(HKEY_CURRENT_USER, 'Environment', 'Path', Path);
    end;
  end
  else
    RegWriteStringValue(HKEY_CURRENT_USER, 'Environment', 'Path', AppDir);
  BroadcastSettingChange(False);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Path, UpperPath, AppDir: string;
  P, Extra: Integer;
begin
  if CurUninstallStep <> usUninstall then Exit;
  AppDir := ExpandConstant('{app}');
  if not RegQueryStringValue(HKEY_CURRENT_USER, 'Environment', 'Path', Path) then Exit;
  UpperPath := ';' + Uppercase(Path) + ';';
  P := Pos(';' + Uppercase(AppDir) + ';', UpperPath);
  if P = 0 then Exit;
  { UpperPath has one extra leading ';', so the segment starts at Path[P]. }
  Extra := Length(AppDir);
  if (P > 1) and (Copy(Path, P - 1, 1) = ';') then
  begin
    P := P - 1;
    Extra := Extra + 1;
  end
  else if (P + Extra <= Length(Path)) and (Copy(Path, P + Extra, 1) = ';') then
    Extra := Extra + 1;
  Delete(Path, P, Extra);
  if Path = '' then
    RegDeleteValue(HKEY_CURRENT_USER, 'Environment', 'Path')
  else
    RegWriteStringValue(HKEY_CURRENT_USER, 'Environment', 'Path', Path);
  BroadcastSettingChange(True);
end;
