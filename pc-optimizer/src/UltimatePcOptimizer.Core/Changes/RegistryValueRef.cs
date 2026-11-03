namespace UltimatePcOptimizer.Core.Changes;

public enum RegistryHive { CurrentUser, LocalMachine }

public enum RegistryValueType { DWord, String, Binary }

/// <summary>Identifies one registry value. Serialised as "HIVE\SubKey|ValueName|Type" in the change journal.</summary>
public sealed record RegistryValueRef(RegistryHive Hive, string SubKey, string ValueName, RegistryValueType Type)
{
    public override string ToString() => $"{HiveName(Hive)}\\{SubKey}|{ValueName}|{Type}";

    public string DisplayPath => $"{HiveName(Hive)}\\{SubKey}\\{ValueName}";

    public static RegistryValueRef Parse(string text)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(text);
        var parts = text.Split('|');
        if (parts.Length != 3) throw new FormatException($"Invalid registry reference: '{text}'.");

        var path = parts[0];
        var slash = path.IndexOf('\\');
        if (slash <= 0) throw new FormatException($"Invalid registry path: '{path}'.");

        var hive = path[..slash].ToUpperInvariant() switch
        {
            "HKCU" => RegistryHive.CurrentUser,
            "HKLM" => RegistryHive.LocalMachine,
            _ => throw new FormatException($"Unsupported hive in '{path}'."),
        };
        if (!Enum.TryParse<RegistryValueType>(parts[2], out var type))
            throw new FormatException($"Unsupported value type '{parts[2]}'.");

        return new RegistryValueRef(hive, path[(slash + 1)..], parts[1], type);
    }

    private static string HiveName(RegistryHive hive) => hive == RegistryHive.CurrentUser ? "HKCU" : "HKLM";
}
