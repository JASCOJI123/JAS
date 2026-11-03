using System.Diagnostics;
using System.Management;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Restore;

[SupportedOSPlatform("windows")]
public sealed class WindowsRestorePointService(ILogger<WindowsRestorePointService> logger) : IRestorePointService
{
    private const uint ModifySettings = 12;
    private const uint BeginSystemChange = 100;

    public Task<RestorePointResult> CreateAsync(string description, CancellationToken ct = default) => Task.Run(() => Create(description), ct);

    private RestorePointResult Create(string description)
    {
        try
        {
            var before = CountRestorePoints();
            using var restore = new ManagementClass(@"\\.\root\default", "SystemRestore", null);
            using var parameters = restore.GetMethodParameters("CreateRestorePoint");
            parameters["Description"] = description.Length > 64 ? description[..64] : description;
            parameters["RestorePointType"] = ModifySettings;
            parameters["EventType"] = BeginSystemChange;
            using var output = restore.InvokeMethod("CreateRestorePoint", parameters, null);
            var code = Convert.ToUInt32(output["ReturnValue"], System.Globalization.CultureInfo.InvariantCulture);

            if (code != 0)
                return new RestorePointResult(false, code == 1058
                    ? "System Restore xizmati o'chirilgan. Control Panel → System → System Protection'da yoqing."
                    : $"Restore Point yaratilmadi (kod {code}). System Protection tizim diskida yoqilganini tekshiring.");

            // Windows silently skips creation if another point was made in the last 24 hours.
            return CountRestorePoints() > before
                ? new RestorePointResult(true, "Restore Point yaratildi.")
                : new RestorePointResult(true, "Windows so'nggi 24 soatdagi Restore Point'ni saqlab qoldi; yangisi yaratilmadi.");
        }
        catch (Exception ex) when (ex is ManagementException or UnauthorizedAccessException or System.Runtime.InteropServices.COMException)
        {
            logger.LogWarning(ex, "Restore point creation failed");
            return new RestorePointResult(false, "System Protection o'chirilgan yoki administrator huquqi yo'q.");
        }
    }

    private static int CountRestorePoints() => Wmi.Query("SELECT SequenceNumber FROM SystemRestore", @"root\default").Count;

    public void OpenSystemRestore() =>
        Process.Start(new ProcessStartInfo(Path.Combine(Environment.SystemDirectory, "rstrui.exe")) { UseShellExecute = true });
}
