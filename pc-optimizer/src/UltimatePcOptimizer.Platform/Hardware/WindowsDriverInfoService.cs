using System.Runtime.Versioning;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Hardware;

/// <summary>Driver Information (read-only). Drivers are never installed or changed by the application.</summary>
[SupportedOSPlatform("windows")]
public sealed class WindowsDriverInfoService : IDriverInfoService
{
    private static readonly string[] Classes = ["DISPLAY", "NET", "MEDIA", "HDC", "SCSIADAPTER", "BLUETOOTH"];

    public Task<IReadOnlyList<DriverInfo>> GetDriversAsync(CancellationToken ct = default) => Task.Run<IReadOnlyList<DriverInfo>>(() =>
    {
        var filter = string.Join(" OR ", Classes.Select(c => $"DeviceClass = '{c}'"));
        return Wmi.Query($"SELECT DeviceName, DeviceClass, DriverProviderName, DriverVersion, DriverDate FROM Win32_PnPSignedDriver WHERE {filter}")
            .Where(r => r.Str("DeviceName").Length > 0)
            .Select(r => new DriverInfo(r.Str("DeviceName"), r.Str("DeviceClass"), r.Str("DriverProviderName"), r.Str("DriverVersion"), r.Date("DriverDate")))
            .DistinctBy(d => (d.DeviceName, d.Version))
            .OrderBy(d => d.DeviceClass).ThenBy(d => d.DeviceName)
            .ToList();
    }, ct);
}
