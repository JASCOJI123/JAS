using System.Diagnostics;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Safety;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Processes;

/// <summary>
/// Process information through limited-rights handles (works without touching protected processes).
/// Every write first re-checks the safety policy and the process start time.
/// </summary>
public sealed class WindowsProcessService : IProcessService
{
    private readonly Dictionary<int, (long CpuTicks, long WallTicks)> _cpuSamples = [];
    private readonly Lock _lock = new();

    public IReadOnlyList<ProcessIdentity> GetProcessIdentities()
    {
        var processes = Process.GetProcesses();
        var list = new List<ProcessIdentity>(processes.Length);
        foreach (var p in processes)
        {
            using (p)
            {
                var times = ReadTimes(p.Id);
                list.Add(new ProcessIdentity(p.Id, p.ProcessName, times?.Created));
            }
        }
        return list;
    }

    public IReadOnlyList<ProcessInfo> GetProcesses()
    {
        var processes = Process.GetProcesses();
        var now = Stopwatch.GetTimestamp();
        var result = new List<ProcessInfo>(processes.Length);
        var alive = new HashSet<int>();

        lock (_lock)
        {
            foreach (var p in processes)
            {
                using (p)
                {
                    if (p.Id is 0 or 4) continue; // Idle and System
                    alive.Add(p.Id);
                    using var handle = ProcessHandle.Open(p.Id, NativeMethods.ProcessQueryLimitedInformation);
                    var times = handle.IsValid ? ReadTimes(handle) : null;
                    var cpu = 0.0;
                    if (times is { } t)
                    {
                        if (_cpuSamples.TryGetValue(p.Id, out var prev) && now > prev.WallTicks)
                        {
                            var wallTicks = (now - prev.WallTicks) * TimeSpan.TicksPerSecond / Stopwatch.Frequency;
                            cpu = (t.CpuTicks - prev.CpuTicks) * 100.0 / wallTicks / Environment.ProcessorCount;
                        }
                        _cpuSamples[p.Id] = (t.CpuTicks, now);
                    }

                    var name = p.ProcessName;
                    var isGame = GameCatalog.FindByExecutable(name) is not null;
                    var category = ProcessSafetyPolicy.Classify(name, SafeSessionId(p), isGame);
                    result.Add(new ProcessInfo(
                        p.Id,
                        name,
                        handle.IsValid ? ReadPath(handle) : null,
                        (ulong)Math.Max(0, SafeWorkingSet(p)),
                        Math.Clamp(cpu, 0, 100),
                        SafeSessionId(p),
                        times?.Created,
                        handle.IsValid ? ToLevel(NativeMethods.GetPriorityClass(handle.Value)) : null,
                        category,
                        ProcessSafetyPolicy.IsProtected(category)));
                }
            }

            foreach (var dead in _cpuSamples.Keys.Where(k => !alive.Contains(k)).ToList()) _cpuSamples.Remove(dead);
        }
        return result;
    }

    public ProcessPriorityLevel? GetPriority(int pid, DateTime? expectedStartTime)
    {
        using var handle = ProcessHandle.Open(pid, NativeMethods.ProcessQueryLimitedInformation);
        if (!handle.IsValid || !StartTimeMatches(handle, expectedStartTime)) return null;
        return ToLevel(NativeMethods.GetPriorityClass(handle.Value));
    }

    public bool SetPriority(int pid, DateTime? expectedStartTime, ProcessPriorityLevel priority)
    {
        if (IsProtected(pid)) return false;
        using var handle = ProcessHandle.Open(pid, NativeMethods.ProcessQueryLimitedInformation | NativeMethods.ProcessSetInformation);
        if (!handle.IsValid || !StartTimeMatches(handle, expectedStartTime)) return false;
        return NativeMethods.SetPriorityClass(handle.Value, ToClass(priority));
    }

    public bool TryTerminate(int pid, out string? error)
    {
        error = null;
        try
        {
            if (IsProtected(pid))
            {
                error = "Bu tizim, xavfsizlik yoki anti-cheat jarayoni — uni yopish mumkin emas.";
                return false;
            }
            using var process = Process.GetProcessById(pid);
            process.Kill();
            return true;
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException or System.ComponentModel.Win32Exception or NotSupportedException)
        {
            error = ex.Message;
            return false;
        }
    }

    private static bool IsProtected(int pid)
    {
        try
        {
            using var p = Process.GetProcessById(pid);
            var category = ProcessSafetyPolicy.Classify(p.ProcessName, SafeSessionId(p), false);
            return ProcessSafetyPolicy.IsProtected(category);
        }
        catch (ArgumentException)
        {
            return true; // already gone; nothing to do
        }
    }

    private static bool StartTimeMatches(ProcessHandle handle, DateTime? expected)
    {
        if (expected is null) return true;
        var times = ReadTimes(handle);
        return times is not null && Math.Abs((times.Value.Created - expected.Value.ToUniversalTime()).TotalSeconds) < 1;
    }

    private static (DateTime Created, long CpuTicks)? ReadTimes(int pid)
    {
        using var handle = ProcessHandle.Open(pid, NativeMethods.ProcessQueryLimitedInformation);
        return handle.IsValid ? ReadTimes(handle) : null;
    }

    private static (DateTime Created, long CpuTicks)? ReadTimes(ProcessHandle handle)
    {
        if (!NativeMethods.GetProcessTimes(handle.Value, out var created, out _, out var kernel, out var user)) return null;
        return (DateTime.FromFileTimeUtc(created.Ticks), kernel.Ticks + user.Ticks);
    }

    private static unsafe string? ReadPath(ProcessHandle handle)
    {
        var buffer = stackalloc char[1024];
        uint size = 1024;
        return NativeMethods.QueryFullProcessImageName(handle.Value, 0, buffer, ref size) ? new string(buffer, 0, (int)size) : null;
    }

    private static int SafeSessionId(Process p)
    {
        try { return p.SessionId; } catch (InvalidOperationException) { return 0; }
    }

    private static long SafeWorkingSet(Process p)
    {
        try { return p.WorkingSet64; } catch (InvalidOperationException) { return 0; }
    }

    private static ProcessPriorityLevel? ToLevel(uint priorityClass) => priorityClass switch
    {
        0x40 => ProcessPriorityLevel.Idle,
        0x4000 => ProcessPriorityLevel.BelowNormal,
        0x20 => ProcessPriorityLevel.Normal,
        0x8000 => ProcessPriorityLevel.AboveNormal,
        0x80 => ProcessPriorityLevel.High,
        _ => null, // 0 = failure; 0x100 = realtime, which this app never sets or reports as changeable
    };

    private static uint ToClass(ProcessPriorityLevel level) => level switch
    {
        ProcessPriorityLevel.Idle => 0x40,
        ProcessPriorityLevel.BelowNormal => 0x4000,
        ProcessPriorityLevel.AboveNormal => 0x8000,
        ProcessPriorityLevel.High => 0x80,
        _ => 0x20,
    };
}
