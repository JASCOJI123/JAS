namespace UltimatePcOptimizer.Core.Models;

/// <summary>User preferences persisted in the local database.</summary>
public sealed record AppSettings
{
    public bool AutoOptimizationEnabled { get; init; }
    /// <summary>Only Safe-risk cleanup items are ever applied automatically, and only if the user turned this on.</summary>
    public bool AutoApplySafeCleanup { get; init; }
    public int AutoOptimizationIntervalHours { get; init; } = 24;
    public bool GameAutoDetection { get; init; } = true;
    public bool CreateRestorePointBeforeOptimization { get; init; } = true;
    public bool CheckForUpdates { get; init; } = true;
    public int MonitoringIntervalMs { get; init; } = 1000;
    public IReadOnlyList<string> LatencyHosts { get; init; } = ["1.1.1.1", "8.8.8.8"];
    public string ActiveMode { get; init; } = "Balanced";
    public DateTimeOffset? LastAutoOptimization { get; init; }
}
