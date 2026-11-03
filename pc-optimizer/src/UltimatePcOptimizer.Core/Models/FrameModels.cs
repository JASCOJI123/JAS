namespace UltimatePcOptimizer.Core.Models;

/// <summary>A single presented frame: time since the previous present, in milliseconds.</summary>
public readonly record struct FrameSample(double TimestampSeconds, double FrameTimeMs);

public sealed record FrameStats(
    int FrameCount,
    double CurrentFps,
    double AverageFps,
    double OnePercentLowFps,
    double PointOnePercentLowFps,
    double AverageFrameTimeMs,
    double P99FrameTimeMs,
    double LastFrameTimeMs)
{
    public static FrameStats Empty { get; } = new(0, 0, 0, 0, 0, 0, 0, 0);
}

public sealed record BenchmarkResult(
    long Id,
    string GameName,
    string Label,
    DateTimeOffset RecordedAt,
    TimeSpan Duration,
    FrameStats Frames,
    double AverageCpuPercent,
    double? AverageGpuPercent,
    double? MaxCpuTemperatureC,
    double? MaxGpuTemperatureC,
    double AverageRamPercent);

public sealed record BenchmarkComparison(BenchmarkResult Before, BenchmarkResult After)
{
    public double AverageFpsChangePercent => Change(Before.Frames.AverageFps, After.Frames.AverageFps);
    public double OnePercentLowChangePercent => Change(Before.Frames.OnePercentLowFps, After.Frames.OnePercentLowFps);
    public double FrameTimeChangePercent => Change(Before.Frames.AverageFrameTimeMs, After.Frames.AverageFrameTimeMs);

    private static double Change(double before, double after) => before <= 0 ? 0 : (after - before) / before * 100.0;
}
