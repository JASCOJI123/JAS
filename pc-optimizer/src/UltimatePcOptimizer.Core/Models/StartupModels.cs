namespace UltimatePcOptimizer.Core.Models;

public enum StartupLocation { CurrentUserRun, LocalMachineRun, LocalMachineRun32, CurrentUserStartupFolder, CommonStartupFolder }

public enum StartupImpact { NotMeasured, Low, Medium, High }

public sealed record StartupEntry(
    string Id,
    string Name,
    string Command,
    string? ExecutablePath,
    StartupLocation Location,
    bool IsEnabled,
    StartupImpact Impact,
    ulong? MemoryBytes,
    bool IsProtected,
    string? ProtectionReason);
