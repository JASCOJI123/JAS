namespace UltimatePcOptimizer.Core.Models;

/// <summary>Point-in-time view of system load, produced once per sampling tick.</summary>
public sealed record SystemSnapshot(
    DateTimeOffset Timestamp,
    double CpuLoadPercent,
    double? GpuLoadPercent,
    MemoryUsage Memory,
    double DiskActivityPercent,
    double? CpuTemperatureC,
    double? GpuTemperatureC,
    double? CpuClockMhz,
    double? GpuClockMhz,
    VramUsage? Vram,
    int ProcessCount,
    TimeSpan Uptime,
    bool IsOnBattery)
{
    public static SystemSnapshot Empty { get; } = new(
        DateTimeOffset.MinValue, 0, null, MemoryUsage.Empty, 0, null, null, null, null, null, 0, TimeSpan.Zero, false);
}

public sealed record MemoryUsage(ulong TotalBytes, ulong AvailableBytes, ulong CommitTotalBytes, ulong CommitLimitBytes)
{
    public static MemoryUsage Empty { get; } = new(0, 0, 0, 0);

    public ulong UsedBytes => TotalBytes > AvailableBytes ? TotalBytes - AvailableBytes : 0;

    public double UsedPercent => TotalBytes == 0 ? 0 : UsedBytes * 100.0 / TotalBytes;

    public double CommitPercent => CommitLimitBytes == 0 ? 0 : CommitTotalBytes * 100.0 / CommitLimitBytes;
}

public sealed record VramUsage(ulong UsedBytes, ulong TotalBytes)
{
    public double UsedPercent => TotalBytes == 0 ? 0 : UsedBytes * 100.0 / TotalBytes;
}
