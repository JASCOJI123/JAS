using System.Globalization;
using Microsoft.Win32;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Safety;
using CoreHive = UltimatePcOptimizer.Core.Changes.RegistryHive;

namespace UltimatePcOptimizer.Platform.Registry;

/// <summary>Registry access with the allow-list enforced on every write and delete.</summary>
public sealed class WindowsRegistryAccess : IRegistryAccess
{
    public string? Read(RegistryValueRef value)
    {
        using var baseKey = Open(value.Hive);
        using var key = baseKey.OpenSubKey(value.SubKey);
        return key?.GetValue(value.ValueName, null, RegistryValueOptions.DoNotExpandEnvironmentNames) switch
        {
            null => null,
            int i => i.ToString(CultureInfo.InvariantCulture),
            long l => l.ToString(CultureInfo.InvariantCulture),
            byte[] b => Convert.ToBase64String(b),
            string[] multi => string.Join('\n', multi),
            var other => Convert.ToString(other, CultureInfo.InvariantCulture),
        };
    }

    public void Write(RegistryValueRef value, string data)
    {
        RegistryPolicy.EnsureAllowed(value);
        using var baseKey = Open(value.Hive);
        using var key = baseKey.CreateSubKey(value.SubKey, writable: true);
        switch (value.Type)
        {
            case RegistryValueType.DWord:
                key.SetValue(value.ValueName, int.Parse(data, CultureInfo.InvariantCulture), RegistryValueKind.DWord);
                break;
            case RegistryValueType.Binary:
                key.SetValue(value.ValueName, Convert.FromBase64String(data), RegistryValueKind.Binary);
                break;
            default:
                key.SetValue(value.ValueName, data, RegistryValueKind.String);
                break;
        }
    }

    public void Delete(RegistryValueRef value)
    {
        RegistryPolicy.EnsureAllowed(value);
        using var baseKey = Open(value.Hive);
        using var key = baseKey.OpenSubKey(value.SubKey, writable: true);
        key?.DeleteValue(value.ValueName, throwOnMissingValue: false);
    }

    private static RegistryKey Open(CoreHive hive) => RegistryKey.OpenBaseKey(
        hive == CoreHive.CurrentUser ? Microsoft.Win32.RegistryHive.CurrentUser : Microsoft.Win32.RegistryHive.LocalMachine,
        RegistryView.Registry64);
}
