using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Analysis;

/// <summary>
/// Estimates startup impact from the memory the program uses while running and a list of
/// launchers/updaters known to be heavy at boot.
/// </summary>
public static class StartupImpactEstimator
{
    private static readonly string[] KnownHeavy =
    [
        "steam", "epicgameslauncher", "riotclient", "battle.net", "eadesktop", "upc", "ubisoft", "discord",
        "teams", "onedrive", "dropbox", "googledrivefs", "spotify", "adobe", "creative cloud", "skype", "zoom",
    ];

    public static StartupImpact Estimate(string name, string command, ulong? runningMemoryBytes)
    {
        const ulong mb = 1024UL * 1024;
        if (runningMemoryBytes >= 250 * mb) return StartupImpact.High;
        var text = $"{name} {command}".ToLowerInvariant();
        if (KnownHeavy.Any(text.Contains)) return StartupImpact.High;
        if (runningMemoryBytes >= 80 * mb) return StartupImpact.Medium;
        if (runningMemoryBytes is not null) return StartupImpact.Low;
        return StartupImpact.NotMeasured;
    }
}
