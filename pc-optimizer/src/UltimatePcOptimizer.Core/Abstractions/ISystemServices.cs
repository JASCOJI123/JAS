using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Abstractions;

/// <summary>Live load and sensor readings.</summary>
public interface ISystemMonitor : IDisposable
{
    SystemSnapshot Sample();
}

public interface IHardwareDetector
{
    Task<HardwareProfile> DetectAsync(CancellationToken ct = default);
}

public interface IPageFileProvider
{
    Analysis.PageFileStatus? GetStatus();
}

public interface IBootTimeProvider
{
    /// <summary>Duration of the last boot as measured by Windows, if available.</summary>
    Task<TimeSpan?> GetLastBootDurationAsync(CancellationToken ct = default);
}

public interface IProcessService
{
    /// <summary>Lists processes with CPU usage measured since the previous call.</summary>
    IReadOnlyList<ProcessInfo> GetProcesses();
    /// <summary>Cheap listing (no CPU sampling, no module paths) used for game detection.</summary>
    IReadOnlyList<ProcessIdentity> GetProcessIdentities();
    ProcessPriorityLevel? GetPriority(int pid, DateTime? expectedStartTime);
    bool SetPriority(int pid, DateTime? expectedStartTime, ProcessPriorityLevel priority);
    /// <summary>Ends a process at the user's explicit request. Protected processes are refused.</summary>
    bool TryTerminate(int pid, out string? error);
}

public interface IStartupService
{
    IReadOnlyList<StartupEntry> GetEntries();
    bool? IsEnabled(string entryId);
    void SetEnabled(string entryId, bool enabled);
}

public interface ICleanupService
{
    Task<CleanupScanResult> ScanAsync(CleanupCategory category, CancellationToken ct = default);
    Task<CleanupResult> CleanAsync(CleanupCategory category, CancellationToken ct = default);
}

public interface IPowerPlanService
{
    IReadOnlyList<PowerPlan> GetPlans();
    PowerPlan? GetActivePlan();
    void SetActivePlan(Guid planId);
}

/// <summary>Raw registry access. Writes are restricted by <see cref="Safety.RegistryPolicy"/>.</summary>
public interface IRegistryAccess
{
    string? Read(RegistryValueRef value);
    void Write(RegistryValueRef value, string data);
    void Delete(RegistryValueRef value);
}

public sealed record RestorePointResult(bool Success, string Message);

public interface IRestorePointService
{
    Task<RestorePointResult> CreateAsync(string description, CancellationToken ct = default);
    /// <summary>Opens the Windows System Restore wizard.</summary>
    void OpenSystemRestore();
}

public interface IGpuSettingsService
{
    /// <summary>Hardware-accelerated GPU scheduling: true on, false off, null unsupported/unknown.</summary>
    bool? GetHardwareAcceleratedScheduling();
    void OpenGraphicsSettings();
}

public interface IDriverInfoService
{
    Task<IReadOnlyList<DriverInfo>> GetDriversAsync(CancellationToken ct = default);
}

public interface INetworkDiagnostics
{
    Task<LatencyStats> MeasureLatencyAsync(string host, int count, CancellationToken ct = default);
    Task<ConnectionDiagnostics> DiagnoseAsync(IReadOnlyList<string> internetHosts, CancellationToken ct = default);
}

public interface IGameLibraryScanner
{
    Task<IReadOnlyList<InstalledGame>> ScanAsync(CancellationToken ct = default);
}

public interface IFrameTimeSource
{
    bool IsAvailable { get; }
    string? UnavailableReason { get; }
    /// <summary>Streams presented frames of one process until cancelled or the process exits.</summary>
    IAsyncEnumerable<FrameSample> CaptureAsync(int processId, CancellationToken ct);
}

public sealed record UpdateInfo(string Version, string ReleaseNotes, string DownloadUrl);

public interface IUpdateService
{
    Task<UpdateInfo?> CheckAsync(CancellationToken ct = default);
}
