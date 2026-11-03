using System.Runtime.Versioning;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Hardware;

/// <summary>Auto PC Detection through WMI (CIMv2 and the Storage namespace).</summary>
[SupportedOSPlatform("windows")]
public sealed class WindowsHardwareDetector : IHardwareDetector, IPageFileProvider
{
    // Win32_SystemEnclosure.ChassisTypes values that identify portable machines.
    private static readonly HashSet<int> PortableChassis = [8, 9, 10, 11, 12, 14, 18, 21, 30, 31, 32];

    public Task<HardwareProfile> DetectAsync(CancellationToken ct = default) => Task.Run(Detect, ct);

    private static HardwareProfile Detect()
    {
        var cpuRow = Wmi.Query("SELECT Name, Manufacturer, NumberOfCores, NumberOfLogicalProcessors, MaxClockSpeed FROM Win32_Processor").FirstOrDefault();
        var cpu = cpuRow is null
            ? new CpuInfo("Unknown CPU", string.Empty, Environment.ProcessorCount, Environment.ProcessorCount, 0)
            : new CpuInfo(cpuRow.Str("Name"), cpuRow.Str("Manufacturer"), cpuRow.I32("NumberOfCores"), cpuRow.I32("NumberOfLogicalProcessors"), cpuRow.I32("MaxClockSpeed"));

        var gpus = DetectGpus();

        var ramModules = Wmi.Query("SELECT Capacity, Speed, ConfiguredClockSpeed FROM Win32_PhysicalMemory");
        var totalRam = ramModules.Aggregate(0UL, (sum, r) => sum + r.U64("Capacity"));
        if (totalRam == 0) totalRam = WindowsSystemMonitor.ReadMemory().TotalBytes;
        var ramSpeed = ramModules.Select(r => r.I32("ConfiguredClockSpeed") is > 0 and var c ? c : r.I32("Speed")).DefaultIfEmpty(0).Max();

        var board = Wmi.Query("SELECT Manufacturer, Product FROM Win32_BaseBoard").FirstOrDefault();
        var bios = Wmi.Query("SELECT SMBIOSBIOSVersion FROM Win32_BIOS").FirstOrDefault();
        var motherboard = new MotherboardInfo(board?.Str("Manufacturer") ?? string.Empty, board?.Str("Product") ?? string.Empty, bios?.Str("SMBIOSBIOSVersion") ?? string.Empty);

        var osRow = Wmi.Query("SELECT Caption, Version, BuildNumber, OSArchitecture FROM Win32_OperatingSystem").FirstOrDefault();
        _ = int.TryParse(osRow?.Str("BuildNumber"), out var build);
        var os = new OsInfo(osRow?.Str("Caption") ?? "Windows", osRow?.Str("Version") ?? Environment.OSVersion.Version.ToString(), build, osRow?.Str("OSArchitecture") ?? string.Empty);

        return new HardwareProfile(cpu, gpus, totalRam, ramSpeed, motherboard, os, DetectDisks(), DetectDisplays(), DetectLaptop());
    }

    private static List<GpuInfo> DetectGpus()
    {
        return Wmi.Query("SELECT Name, DriverVersion, DriverDate, AdapterRAM, PNPDeviceID FROM Win32_VideoController")
            .Where(r => !r.Str("Name").Contains("Basic Display", StringComparison.OrdinalIgnoreCase) &&
                        !r.Str("Name").Contains("Remote", StringComparison.OrdinalIgnoreCase))
            .Select(r =>
            {
                var name = r.Str("Name");
                var vendor = GpuVendorDetector.FromName(name);
                // AdapterRAM is a 32-bit field capped at 4 GB; the driver registry key has the real 64-bit size.
                var memory = Math.Max(r.U64("AdapterRAM"), ReadQwMemorySize(name));
                return new GpuInfo(name, vendor, r.Str("DriverVersion"), r.Date("DriverDate"), memory, GpuVendorDetector.LooksIntegrated(name, vendor));
            })
            .ToList();
    }

    private static ulong ReadQwMemorySize(string adapterName)
    {
        const string classKey = @"SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}";
        try
        {
            using var root = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(classKey);
            if (root is null) return 0;
            foreach (var sub in root.GetSubKeyNames().Where(n => n.Length == 4 && n.All(char.IsDigit)))
            {
                using var key = root.OpenSubKey(sub);
                if (key?.GetValue("DriverDesc") is string desc && desc == adapterName && key.GetValue("HardwareInformation.qwMemorySize") is long size)
                    return (ulong)size;
            }
        }
        catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException) { }
        return 0;
    }

    private static List<DiskInfo> DetectDisks()
    {
        var systemDrive = Path.GetPathRoot(Environment.SystemDirectory)?.TrimEnd('\\') ?? "C:";
        var systemDiskNumber = Wmi.Query($"SELECT DiskNumber FROM MSFT_Partition WHERE DriveLetter = '{systemDrive[0]}'", @"root\Microsoft\Windows\Storage")
            .Select(r => r.I32("DiskNumber")).DefaultIfEmpty(-1).First();

        return Wmi.Query("SELECT DeviceId, FriendlyName, MediaType, BusType, Size FROM MSFT_PhysicalDisk", @"root\Microsoft\Windows\Storage")
            .Select(r =>
            {
                // MediaType: 3 = HDD, 4 = SSD. BusType 17 = NVMe.
                var type = r.I32("BusType") == 17 ? DiskMediaType.Nvme
                    : r.I32("MediaType") switch { 3 => DiskMediaType.Hdd, 4 => DiskMediaType.Ssd, _ => DiskMediaType.Unknown };
                _ = int.TryParse(r.Str("DeviceId"), out var number);
                return new DiskInfo(r.Str("FriendlyName"), type, r.U64("Size"), number == systemDiskNumber);
            })
            .ToList();
    }

    private static List<DisplayInfo> DetectDisplays()
    {
        return Wmi.Query("SELECT Name, CurrentHorizontalResolution, CurrentVerticalResolution, CurrentRefreshRate, MaxRefreshRate FROM Win32_VideoController")
            .Where(r => r.I32("CurrentHorizontalResolution") > 0)
            .Select(r => new DisplayInfo(r.Str("Name"), r.I32("CurrentHorizontalResolution"), r.I32("CurrentVerticalResolution"),
                r.I32("CurrentRefreshRate"), Math.Max(r.I32("MaxRefreshRate"), r.I32("CurrentRefreshRate"))))
            .ToList();
    }

    private static bool DetectLaptop()
    {
        if (Wmi.Query("SELECT Name FROM Win32_Battery").Count > 0) return true;
        return Wmi.Query("SELECT ChassisTypes FROM Win32_SystemEnclosure")
            .Any(r => r.TryGetValue("ChassisTypes", out var v) && v is ushort[] types && types.Any(t => PortableChassis.Contains(t)));
    }

    public PageFileStatus? GetStatus()
    {
        var system = Wmi.Query("SELECT AutomaticManagedPagefile FROM Win32_ComputerSystem").FirstOrDefault();
        var files = Wmi.Query("SELECT AllocatedBaseSize, CurrentUsage, PeakUsage FROM Win32_PageFileUsage");
        if (system is null) return null;
        const ulong mb = 1024UL * 1024;
        return new PageFileStatus(
            system.Bool("AutomaticManagedPagefile"),
            files.Aggregate(0UL, (s, f) => s + f.U64("AllocatedBaseSize") * mb),
            files.Aggregate(0UL, (s, f) => s + f.U64("CurrentUsage") * mb),
            files.Aggregate(0UL, (s, f) => s + f.U64("PeakUsage") * mb),
            files.Count > 0);
    }
}
