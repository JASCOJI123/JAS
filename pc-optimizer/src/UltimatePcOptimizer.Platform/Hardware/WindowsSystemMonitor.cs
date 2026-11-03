using System.Diagnostics;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Hardware;

[SupportedOSPlatform("windows")]
public sealed class WindowsSystemMonitor : ISystemMonitor
{
    private readonly HardwareSensors _sensors;
    private readonly ILogger<WindowsSystemMonitor> _logger;
    private readonly PerformanceCounter? _cpu;
    private readonly PerformanceCounter? _diskIdle;
    private readonly GpuEngineCounters _gpuCounters = new();

    public WindowsSystemMonitor(HardwareSensors sensors, ILogger<WindowsSystemMonitor> logger)
    {
        _sensors = sensors;
        _logger = logger;
        // "% Processor Utility" matches Task Manager on modern CPUs with boost clocks.
        _cpu = TryCounter("Processor Information", "% Processor Utility", "_Total") ?? TryCounter("Processor", "% Processor Time", "_Total");
        _diskIdle = TryCounter("PhysicalDisk", "% Idle Time", "_Total");
        _cpu?.NextValue();
        _diskIdle?.NextValue();
    }

    public SystemSnapshot Sample()
    {
        var sensors = _sensors.Read();
        var memory = ReadMemory();
        var cpu = Math.Clamp(_cpu?.NextValue() ?? 0, 0, 100);
        var disk = _diskIdle is null ? 0 : Math.Clamp(100 - _diskIdle.NextValue(), 0, 100);
        var gpuLoad = sensors.GpuLoadPercent ?? _gpuCounters.Read();

        VramUsage? vram = sensors is { VramUsedMb: { } used, VramTotalMb: { } total } && total > 0
            ? new VramUsage((ulong)(used * 1024 * 1024), (ulong)(total * 1024 * 1024))
            : null;

        return new SystemSnapshot(
            DateTimeOffset.Now,
            cpu,
            gpuLoad,
            memory,
            disk,
            sensors.CpuTemperatureC,
            sensors.GpuTemperatureC,
            sensors.CpuClockMhz,
            sensors.GpuClockMhz,
            vram,
            CountProcesses(),
            TimeSpan.FromMilliseconds(Environment.TickCount64),
            IsOnBattery());
    }

    internal static MemoryUsage ReadMemory()
    {
        var status = new NativeMethods.MemoryStatusEx { Length = (uint)System.Runtime.InteropServices.Marshal.SizeOf<NativeMethods.MemoryStatusEx>() };
        if (!NativeMethods.GlobalMemoryStatusEx(ref status)) return MemoryUsage.Empty;

        ulong commitTotal = 0, commitLimit = 0;
        if (NativeMethods.GetPerformanceInfo(out var perf, (uint)System.Runtime.InteropServices.Marshal.SizeOf<NativeMethods.PerformanceInformation>()))
        {
            commitTotal = (ulong)perf.CommitTotal * (ulong)perf.PageSize;
            commitLimit = (ulong)perf.CommitLimit * (ulong)perf.PageSize;
        }
        return new MemoryUsage(status.TotalPhys, status.AvailPhys, commitTotal, commitLimit);
    }

    internal static bool IsOnBattery() =>
        NativeMethods.GetSystemPowerStatus(out var s) && s.AcLineStatus == 0 && s.BatteryFlag != 128;

    private static int CountProcesses()
    {
        var processes = Process.GetProcesses();
        foreach (var p in processes) p.Dispose();
        return processes.Length;
    }

    private PerformanceCounter? TryCounter(string category, string counter, string instance)
    {
        try
        {
            return PerformanceCounterCategory.Exists(category) ? new PerformanceCounter(category, counter, instance, readOnly: true) : null;
        }
        catch (Exception ex) when (ex is InvalidOperationException or UnauthorizedAccessException or System.ComponentModel.Win32Exception)
        {
            _logger.LogWarning(ex, "Performance counter {Category}\\{Counter} unavailable", category, counter);
            return null;
        }
    }

    public void Dispose()
    {
        _cpu?.Dispose();
        _diskIdle?.Dispose();
        _gpuCounters.Dispose();
    }

    /// <summary>
    /// GPU load fallback from the "GPU Engine" counters (what Task Manager uses) when no sensor is available.
    /// Instances appear and disappear with processes, so the list is refreshed periodically.
    /// </summary>
    private sealed class GpuEngineCounters : IDisposable
    {
        private List<PerformanceCounter> _counters = [];
        private long _refreshedAt;

        public double? Read()
        {
            try
            {
                if (Environment.TickCount64 - _refreshedAt > 10_000) Refresh();
                if (_counters.Count == 0) return null;
                double sum = 0;
                foreach (var c in _counters)
                {
                    try { sum += c.NextValue(); }
                    catch (InvalidOperationException) { } // instance ended
                }
                return Math.Clamp(sum, 0, 100);
            }
            catch (Exception ex) when (ex is InvalidOperationException or UnauthorizedAccessException)
            {
                return null;
            }
        }

        private void Refresh()
        {
            _refreshedAt = Environment.TickCount64;
            Dispose();
            if (!PerformanceCounterCategory.Exists("GPU Engine")) return;
            var category = new PerformanceCounterCategory("GPU Engine");
            _counters = category.GetInstanceNames()
                .Where(n => n.EndsWith("engtype_3D", StringComparison.OrdinalIgnoreCase))
                .Select(n => new PerformanceCounter("GPU Engine", "Utilization Percentage", n, readOnly: true))
                .ToList();
            foreach (var c in _counters)
            {
                try { c.NextValue(); } catch (InvalidOperationException) { }
            }
        }

        public void Dispose()
        {
            foreach (var c in _counters) c.Dispose();
            _counters = [];
        }
    }
}
