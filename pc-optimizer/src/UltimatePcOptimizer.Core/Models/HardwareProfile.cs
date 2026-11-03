namespace UltimatePcOptimizer.Core.Models;

public enum GpuVendor { Unknown, Nvidia, Amd, Intel, Other }

public enum DiskMediaType { Unknown, Hdd, Ssd, Nvme }

public sealed record CpuInfo(string Name, string Manufacturer, int Cores, int Threads, int MaxClockMhz);

public sealed record GpuInfo(
    string Name,
    GpuVendor Vendor,
    string DriverVersion,
    DateTime? DriverDate,
    ulong DedicatedMemoryBytes,
    bool IsIntegrated);

public sealed record OsInfo(string Caption, string Version, int Build, string Architecture)
{
    /// <summary>Windows 11 kept the 10.0 version number; build 22000+ identifies it.</summary>
    public bool IsWindows11 => Build >= 22000;
}

public sealed record DiskInfo(string Model, DiskMediaType MediaType, ulong SizeBytes, bool IsSystemDisk);

public sealed record DisplayInfo(string Name, int Width, int Height, int RefreshRateHz, int MaxRefreshRateHz);

public sealed record MotherboardInfo(string Manufacturer, string Product, string BiosVersion);

public sealed record HardwareProfile(
    CpuInfo Cpu,
    IReadOnlyList<GpuInfo> Gpus,
    ulong TotalRamBytes,
    int RamSpeedMhz,
    MotherboardInfo Motherboard,
    OsInfo Os,
    IReadOnlyList<DiskInfo> Disks,
    IReadOnlyList<DisplayInfo> Displays,
    bool IsLaptop)
{
    /// <summary>The GPU games will most likely run on: the strongest discrete adapter, otherwise the first one.</summary>
    public GpuInfo? PrimaryGpu =>
        Gpus.Where(g => !g.IsIntegrated).OrderByDescending(g => g.DedicatedMemoryBytes).FirstOrDefault()
        ?? Gpus.FirstOrDefault();

    public DiskInfo? SystemDisk => Disks.FirstOrDefault(d => d.IsSystemDisk) ?? Disks.FirstOrDefault();

    public DisplayInfo? PrimaryDisplay => Displays.FirstOrDefault();
}

public static class GpuVendorDetector
{
    public static GpuVendor FromName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name)) return GpuVendor.Unknown;
        var n = name.ToUpperInvariant();
        if (n.Contains("NVIDIA") || n.Contains("GEFORCE") || n.Contains("QUADRO") || n.Contains("RTX")) return GpuVendor.Nvidia;
        if (n.Contains("AMD") || n.Contains("RADEON") || n.Contains("ATI ")) return GpuVendor.Amd;
        if (n.Contains("INTEL") || n.Contains("ARC ") || n.Contains("UHD") || n.Contains("IRIS")) return GpuVendor.Intel;
        return GpuVendor.Other;
    }

    public static bool LooksIntegrated(string? name, GpuVendor vendor)
    {
        if (string.IsNullOrWhiteSpace(name)) return false;
        var n = name.ToUpperInvariant();
        if (vendor == GpuVendor.Intel) return !n.Contains("ARC");
        if (vendor == GpuVendor.Amd) return n.Contains("RADEON(TM) GRAPHICS") || n.EndsWith("RADEON GRAPHICS") || n.Contains("VEGA 8") || n.Contains("VEGA 7");
        return n.Contains("MICROSOFT BASIC");
    }
}
