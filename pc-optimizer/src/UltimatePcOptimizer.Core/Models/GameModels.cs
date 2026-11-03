namespace UltimatePcOptimizer.Core.Models;

public enum GameLauncher { Steam, Epic, Riot, Manual, Detected }

public sealed record InstalledGame(
    string Id,
    string Name,
    GameLauncher Launcher,
    string InstallDirectory,
    IReadOnlyList<string> ExecutableNames,
    string? KnownGameId);

public enum GraphicsPreset { Low, Medium, High, Ultra }

public sealed record GameProfile(
    string GameId,
    string GameName,
    bool AutoBoost,
    bool RaiseGamePriority,
    bool LowerBackgroundPriority,
    bool SuppressNotifications,
    bool UseHighPerformancePowerPlan,
    bool PreferHighPerformanceGpu,
    string? ExecutablePath,
    DateTimeOffset UpdatedAt)
{
    public static GameProfile CreateDefault(string gameId, string gameName, bool hasKernelAntiCheat, bool autoBoost) => new(
        gameId,
        gameName,
        AutoBoost: autoBoost,
        // Touching the process of a game protected by a kernel anti-cheat is avoided by default.
        RaiseGamePriority: !hasKernelAntiCheat,
        LowerBackgroundPriority: true,
        SuppressNotifications: true,
        UseHighPerformancePowerPlan: true,
        PreferHighPerformanceGpu: true,
        ExecutablePath: null,
        UpdatedAt: DateTimeOffset.UtcNow);
}

/// <summary>A running process recognised as a game.</summary>
public sealed record RunningGame(int Pid, string ProcessName, string? ExecutablePath, string GameId, string GameName, DateTime? StartTime);
