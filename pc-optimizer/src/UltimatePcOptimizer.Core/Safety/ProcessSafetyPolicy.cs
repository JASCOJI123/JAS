using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Safety;

/// <summary>
/// Classifies processes and decides which ones the application may touch. System, security and
/// anti-cheat processes are never modified, terminated or re-prioritised.
/// </summary>
public static class ProcessSafetyPolicy
{
    private static readonly HashSet<string> SystemProcesses = new(StringComparer.OrdinalIgnoreCase)
    {
        "System", "Idle", "Registry", "smss", "csrss", "wininit", "winlogon", "services", "lsass", "lsaiso",
        "svchost", "fontdrvhost", "dwm", "explorer", "sihost", "ctfmon", "taskhostw", "RuntimeBroker",
        "StartMenuExperienceHost", "ShellExperienceHost", "SearchHost", "SearchIndexer", "SearchApp",
        "TextInputHost", "spoolsv", "audiodg", "conhost", "dllhost", "WmiPrvSE", "Memory Compression",
        "LogonUI", "ApplicationFrameHost", "SystemSettings", "SecurityHealthService", "WUDFHost",
        "dasHost", "wlanext", "MoUsoCoreWorker", "TiWorker", "TrustedInstaller", "msiexec", "smartscreen",
        "UltimatePcOptimizer", "PresentMon",
    };

    private static readonly HashSet<string> SecurityProcesses = new(StringComparer.OrdinalIgnoreCase)
    {
        "MsMpEng", "NisSrv", "MpDefenderCoreService", "SecurityHealthSystray", "SgrmBroker", "MpCmdRun",
        "avp", "avgsvc", "AvastSvc", "ekrn", "egui", "bdagent", "vsserv", "mcshield", "ccSvcHst", "NortonSecurity",
    };

    /// <summary>Anti-cheat components. The application never interacts with these in any way.</summary>
    private static readonly HashSet<string> AntiCheatProcesses = new(StringComparer.OrdinalIgnoreCase)
    {
        "vgc", "vgtray", "vgk", "EasyAntiCheat", "EasyAntiCheat_EOS", "EasyAntiCheat_Setup", "BEService",
        "BEService_x64", "BattlEye", "FACEIT", "faceitclient", "FaceitService", "ESEADriver2", "PnkBstrA",
        "PnkBstrB", "nProtect", "GameMon", "xigncode", "Ricochet", "atvi-randgrid_sr", "EAAntiCheat.GameService",
        "mhyprot2", "ACE-Tray", "SGuard64", "SGuardSvc64",
    };

    private static readonly HashSet<string> Launchers = new(StringComparer.OrdinalIgnoreCase)
    {
        "steam", "steamwebhelper", "EpicGamesLauncher", "EpicWebHelper", "RiotClientServices", "RiotClientUx",
        "RiotClientUxRender", "Battle.net", "EADesktop", "EABackgroundService", "UbisoftConnect", "upc",
        "GalaxyClient", "XboxPcApp", "GamingServices",
    };

    private static readonly HashSet<string> Browsers = new(StringComparer.OrdinalIgnoreCase)
    {
        "chrome", "msedge", "firefox", "opera", "opera_gx", "brave", "vivaldi", "yandex", "browser",
    };

    private static readonly HashSet<string> Communication = new(StringComparer.OrdinalIgnoreCase)
    {
        "Discord", "Telegram", "Skype", "Teams", "ms-teams", "Zoom", "slack", "WhatsApp", "Viber",
    };

    public static ProcessCategory Classify(string processName, int sessionId, bool isKnownGame)
    {
        var name = Normalize(processName);
        if (AntiCheatProcesses.Contains(name)) return ProcessCategory.AntiCheat;
        if (SecurityProcesses.Contains(name)) return ProcessCategory.Security;
        if (SystemProcesses.Contains(name) || sessionId == 0) return ProcessCategory.System;
        if (isKnownGame) return ProcessCategory.Game;
        if (Launchers.Contains(name)) return ProcessCategory.Launcher;
        if (Browsers.Contains(name)) return ProcessCategory.Browser;
        if (Communication.Contains(name)) return ProcessCategory.Communication;
        return ProcessCategory.User;
    }

    /// <summary>True when the process must never be modified by the application.</summary>
    public static bool IsProtected(ProcessCategory category) =>
        category is ProcessCategory.System or ProcessCategory.Security or ProcessCategory.AntiCheat;

    public static bool IsAntiCheat(string processName) => AntiCheatProcesses.Contains(Normalize(processName));

    /// <summary>
    /// Whether the Gaming Booster may temporarily lower this process's priority while a game runs.
    /// Communication apps are excluded because voice chat must keep working during a match.
    /// </summary>
    public static bool CanDeprioritizeDuringGame(ProcessCategory category) =>
        category is ProcessCategory.Browser or ProcessCategory.Launcher or ProcessCategory.User or ProcessCategory.Background;

    private static string Normalize(string name) =>
        name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? name[..^4] : name;
}
