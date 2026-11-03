using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Safety;
using UltimatePcOptimizer.Data;

namespace UltimatePcOptimizer.Tests;

internal sealed class FakeRegistry : IRegistryAccess
{
    public Dictionary<string, string> Values { get; } = new(StringComparer.OrdinalIgnoreCase);

    public string? Read(RegistryValueRef value) => Values.GetValueOrDefault(value.DisplayPath);

    public void Write(RegistryValueRef value, string data)
    {
        RegistryPolicy.EnsureAllowed(value);
        Values[value.DisplayPath] = data;
    }

    public void Delete(RegistryValueRef value) => Values.Remove(value.DisplayPath);
}

internal sealed class FakePower : IPowerPlanService
{
    public List<PowerPlan> Plans { get; } =
    [
        new(KnownPowerPlans.Balanced, "Balanced", true),
        new(KnownPowerPlans.HighPerformance, "High performance", false),
    ];

    public IReadOnlyList<PowerPlan> GetPlans() => Plans.ToList();
    public PowerPlan? GetActivePlan() => Plans.FirstOrDefault(p => p.IsActive);

    public void SetActivePlan(Guid planId)
    {
        for (var i = 0; i < Plans.Count; i++) Plans[i] = Plans[i] with { IsActive = Plans[i].Id == planId };
    }
}

internal sealed class FakeProcesses : IProcessService
{
    public List<ProcessInfo> Processes { get; } = [];

    public IReadOnlyList<ProcessInfo> GetProcesses() => Processes.ToList();
    public IReadOnlyList<ProcessIdentity> GetProcessIdentities() => Processes.Select(p => new ProcessIdentity(p.Pid, p.Name, p.StartTime)).ToList();
    public ProcessPriorityLevel? GetPriority(int pid, DateTime? expectedStartTime) => Processes.FirstOrDefault(p => p.Pid == pid)?.Priority;

    public bool SetPriority(int pid, DateTime? expectedStartTime, ProcessPriorityLevel priority)
    {
        var i = Processes.FindIndex(p => p.Pid == pid);
        if (i < 0) return false;
        Processes[i] = Processes[i] with { Priority = priority };
        return true;
    }

    public bool TryTerminate(int pid, out string? error) { error = null; return Processes.RemoveAll(p => p.Pid == pid) > 0; }

    public static ProcessInfo Make(int pid, string name, ProcessCategory category, double cpu = 5, ulong mem = 100_000_000) =>
        new(pid, name, null, mem, cpu, 1, null, ProcessPriorityLevel.Normal, category, ProcessSafetyPolicy.IsProtected(category));
}

internal sealed class FakeConsent(bool answer) : IConsentService
{
    public int Asked { get; private set; }
    public Task<bool> ConfirmAsync(ConsentRequest request) { Asked++; return Task.FromResult(answer); }
}

internal sealed class FakeRestorePoints(bool success) : IRestorePointService
{
    public int Created { get; private set; }
    public Task<RestorePointResult> CreateAsync(string description, CancellationToken ct = default)
    {
        Created++;
        return Task.FromResult(new RestorePointResult(success, success ? "ok" : "System Protection o'chiq"));
    }
    public void OpenSystemRestore() { }
}

internal sealed class FakeMonitor : ISystemMonitor
{
    public SystemSnapshot Sample() => SystemSnapshot.Empty;
    public void Dispose() { }
}

internal sealed class FakeHardware(bool isLaptop = false) : IHardwareDetector
{
    public Task<HardwareProfile> DetectAsync(CancellationToken ct = default) => Task.FromResult(TestData.Hardware(isLaptop));
}

internal static class TestData
{
    public static HardwareProfile Hardware(bool isLaptop = false, ulong vramGb = 8, string gpuName = "NVIDIA GeForce RTX 3070") => new(
        new CpuInfo("AMD Ryzen 7 5800X", "AMD", 8, 16, 4700),
        [new GpuInfo(gpuName, GpuVendorDetector.FromName(gpuName), "32.0.15.6094", new DateTime(2025, 1, 1), vramGb * 1024 * 1024 * 1024, false)],
        16UL * 1024 * 1024 * 1024,
        3200,
        new MotherboardInfo("ASUS", "TUF B550", "3000"),
        new OsInfo("Windows 11 Pro", "10.0.22631", 22631, "64-bit"),
        [new DiskInfo("Samsung 980", DiskMediaType.Nvme, 1_000_000_000_000, true)],
        [new DisplayInfo("Display", 1920, 1080, 144, 144)],
        isLaptop);
}

/// <summary>A real SQLite database in a temp file, deleted after the test.</summary>
internal sealed class TempDatabase : IAsyncDisposable
{
    private readonly string _path = Path.Combine(Path.GetTempPath(), $"upo-test-{Guid.NewGuid():N}.db");

    public SqliteDatabase Db { get; }

    private TempDatabase() => Db = new SqliteDatabase(_path);

    public static async Task<TempDatabase> CreateAsync()
    {
        var temp = new TempDatabase();
        await temp.Db.MigrateAsync();
        return temp;
    }

    public ValueTask DisposeAsync()
    {
        Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
        foreach (var file in new[] { _path, _path + "-wal", _path + "-shm" })
            if (File.Exists(file)) File.Delete(file);
        return ValueTask.CompletedTask;
    }
}
