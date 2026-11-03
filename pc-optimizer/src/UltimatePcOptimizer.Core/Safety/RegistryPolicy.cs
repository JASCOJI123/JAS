using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.Core.Safety;

/// <summary>
/// Allow-list for registry writes. The application can only modify values listed here; everything
/// else is rejected before reaching the registry. This is what keeps registry edits controlled.
/// </summary>
public static class RegistryPolicy
{
    private static readonly HashSet<string> AllowedValues = new(StringComparer.OrdinalIgnoreCase)
    {
        @"HKCU\Software\Microsoft\GameBar|AutoGameModeEnabled",
        @"HKCU\Software\Microsoft\GameBar|AllowAutoGameMode",
        @"HKCU\Software\Microsoft\GameBar|UseNexusForGameBarEnabled",
        @"HKCU\System\GameConfigStore|GameDVR_Enabled",
        @"HKCU\Software\Microsoft\Windows\CurrentVersion\GameDVR|AppCaptureEnabled",
        @"HKCU\Software\Microsoft\Windows\CurrentVersion\PushNotifications|ToastEnabled",
        @"HKCU\Control Panel\Desktop\WindowMetrics|MinAnimate",
        @"HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced|TaskbarAnimations",
        @"HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced|ListviewAlphaSelect",
        @"HKCU\Software\Microsoft\Windows\DWM|EnableAeroPeek",
        @"HKCU\Control Panel\Desktop|MenuShowDelay",
        @"HKCU\Software\Microsoft\Windows\CurrentVersion\BackgroundAccessApplications|GlobalUserDisabled",
    };

    /// <summary>Keys whose values may be any name (e.g. one value per game executable).</summary>
    private static readonly HashSet<string> AllowedKeysAnyValue = new(StringComparer.OrdinalIgnoreCase)
    {
        @"HKCU\Software\Microsoft\DirectX\UserGpuPreferences",
    };

    public static bool IsAllowed(RegistryValueRef value)
    {
        var key = $"{Hive(value.Hive)}\\{value.SubKey}";
        return AllowedValues.Contains($"{key}|{value.ValueName}") || AllowedKeysAnyValue.Contains(key);
    }

    public static void EnsureAllowed(RegistryValueRef value)
    {
        if (!IsAllowed(value))
            throw new UnauthorizedAccessException($"Registry value '{value.DisplayPath}' is not on the allow-list.");
    }

    private static string Hive(RegistryHive hive) => hive == RegistryHive.CurrentUser ? "HKCU" : "HKLM";
}
