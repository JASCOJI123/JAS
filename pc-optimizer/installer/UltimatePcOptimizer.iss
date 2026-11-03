; Ultimate PC Optimizer — Windows x64 installer (Inno Setup 6).
; Build: ISCC.exe /DAppVersion=1.0.0 /DPublishDir=..\artifacts\publish UltimatePcOptimizer.iss
; scripts/publish.ps1 runs this automatically.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef PublishDir
  #define PublishDir "..\artifacts\publish"
#endif

#define AppName "Ultimate PC Optimizer"
#define AppExe "UltimatePcOptimizer.exe"

[Setup]
AppId={{6F1C2B8E-4C1D-4A8B-9E7F-2B5D3A9C0E11}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=Ultimate PC Optimizer
DefaultDirName={autopf}\UltimatePcOptimizer
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputBaseFilename=UltimatePcOptimizer-Setup-{#AppVersion}-x64
SetupIconFile=..\src\UltimatePcOptimizer.App\Assets\app.ico
UninstallDisplayIcon={app}\{#AppExe}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; Windows 10 1809+ / Windows 11
MinVersion=10.0.17763
PrivilegesRequired=admin
CloseApplications=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#PublishDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent shellexec

; User data (%LOCALAPPDATA%\UltimatePcOptimizer: change journal, profiles, logs) is intentionally kept on
; uninstall so that optimizations can still be reviewed; remove the folder manually if no longer needed.
