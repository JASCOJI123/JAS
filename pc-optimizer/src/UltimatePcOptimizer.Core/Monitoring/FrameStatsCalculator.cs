using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Monitoring;

/// <summary>
/// Rolling frame statistics. "1% low" is the average FPS of the slowest 1% of frames (the
/// definition used by most benchmark tools); P99 frame time is the 99th percentile.
/// </summary>
public sealed class FrameStatsCalculator(TimeSpan window)
{
    private readonly Queue<FrameSample> _frames = new();

    public FrameStatsCalculator() : this(TimeSpan.FromSeconds(30)) { }

    public void Add(FrameSample sample)
    {
        if (sample.FrameTimeMs <= 0 || double.IsNaN(sample.FrameTimeMs)) return;
        _frames.Enqueue(sample);
        var cutoff = sample.TimestampSeconds - window.TotalSeconds;
        while (_frames.Count > 0 && _frames.Peek().TimestampSeconds < cutoff) _frames.Dequeue();
    }

    public void Reset() => _frames.Clear();

    public FrameStats GetStats()
    {
        if (_frames.Count == 0) return FrameStats.Empty;
        var frames = _frames.ToArray();
        var last = frames[^1];

        // Current FPS from the last second of frames, which reads steadier than 1000/last frametime.
        var oneSecondAgo = last.TimestampSeconds - 1;
        var recent = frames.Where(f => f.TimestampSeconds >= oneSecondAgo).Select(f => f.FrameTimeMs).ToArray();
        var current = recent.Length == 0 ? 0 : 1000.0 / recent.Average();

        return Compute(frames.Select(f => f.FrameTimeMs).ToArray()) with { CurrentFps = current, LastFrameTimeMs = last.FrameTimeMs };
    }

    public static FrameStats Compute(IReadOnlyList<double> frameTimesMs)
    {
        var valid = frameTimesMs.Where(t => t > 0 && !double.IsNaN(t)).ToArray();
        if (valid.Length == 0) return FrameStats.Empty;

        var totalMs = valid.Sum();
        var averageFrameTime = totalMs / valid.Length;
        var averageFps = valid.Length * 1000.0 / totalMs;

        var sorted = valid.OrderByDescending(t => t).ToArray(); // slowest first
        double LowFps(double fraction)
        {
            var count = Math.Max(1, (int)Math.Ceiling(sorted.Length * fraction));
            return 1000.0 / sorted.Take(count).Average();
        }

        var p99Index = Math.Clamp((int)Math.Ceiling(sorted.Length * 0.01) - 1, 0, sorted.Length - 1);

        return new FrameStats(
            valid.Length,
            1000.0 / valid[^1],
            averageFps,
            LowFps(0.01),
            LowFps(0.001),
            averageFrameTime,
            sorted[p99Index],
            valid[^1]);
    }
}
