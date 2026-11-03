using System.Diagnostics;
using UltimatePcOptimizer.Core.Abstractions;

namespace UltimatePcOptimizer.Platform.Gpu;

/// <summary>Reads GPU-related Windows settings. HAGS is only reported; the user changes it in Windows Settings.</summary>
public sealed class WindowsGpuSettingsService : IGpuSettingsService
{
    public bool? GetHardwareAcceleratedScheduling()
    {
        using var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Control\GraphicsDrivers");
        return key?.GetValue("HwSchMode") switch
        {
            2 => true,
            1 => false,
            _ => null,
        };
    }

    public void OpenGraphicsSettings() =>
        Process.Start(new ProcessStartInfo("ms-settings:display-advancedgraphics") { UseShellExecute = true });
}
