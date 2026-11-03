using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Monitoring;

/// <summary>Collects frames and system samples for a fixed run, producing a comparable benchmark result.</summary>
public sealed class BenchmarkRecorder(string gameName, string label)
{
    private readonly List<double> _frameTimes = [];
    private readonly List<SystemSnapshot> _snapshots = [];
    private readonly DateTimeOffset _startedAt = DateTimeOffset.UtcNow;

    public int FrameCount => _frameTimes.Count;

    public void AddFrame(FrameSample sample)
    {
        if (sample.FrameTimeMs > 0) _frameTimes.Add(sample.FrameTimeMs);
    }

    public void AddSnapshot(SystemSnapshot snapshot) => _snapshots.Add(snapshot);

    public BenchmarkResult Complete()
    {
        var frames = FrameStatsCalculator.Compute(_frameTimes);
        var gpuLoads = _snapshots.Where(s => s.GpuLoadPercent.HasValue).Select(s => s.GpuLoadPercent!.Value).ToList();
        var cpuTemps = _snapshots.Where(s => s.CpuTemperatureC.HasValue).Select(s => s.CpuTemperatureC!.Value).ToList();
        var gpuTemps = _snapshots.Where(s => s.GpuTemperatureC.HasValue).Select(s => s.GpuTemperatureC!.Value).ToList();

        return new BenchmarkResult(
            0,
            gameName,
            label,
            _startedAt,
            DateTimeOffset.UtcNow - _startedAt,
            frames,
            _snapshots.Count == 0 ? 0 : _snapshots.Average(s => s.CpuLoadPercent),
            gpuLoads.Count == 0 ? null : gpuLoads.Average(),
            cpuTemps.Count == 0 ? null : cpuTemps.Max(),
            gpuTemps.Count == 0 ? null : gpuTemps.Max(),
            _snapshots.Count == 0 ? 0 : _snapshots.Average(s => s.Memory.UsedPercent));
    }
}
