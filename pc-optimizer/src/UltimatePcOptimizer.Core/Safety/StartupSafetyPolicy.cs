namespace UltimatePcOptimizer.Core.Safety;

/// <summary>Startup items that are part of Windows security, input or hardware drivers and must stay enabled.</summary>
public static class StartupSafetyPolicy
{
    private static readonly (string Fragment, string Reason)[] Protected =
    [
        ("SecurityHealth", "Windows Security"),
        ("WindowsDefender", "Windows Defender"),
        ("MsMpEng", "Windows Defender"),
        ("ctfmon", "Klaviatura / kiritish tizimi"),
        ("RtkAudUService", "Audio drayver (Realtek)"),
        ("RtkAudioService", "Audio drayver (Realtek)"),
        ("RAVCpl", "Audio drayver (Realtek)"),
        ("RtHDVCpl", "Audio drayver (Realtek)"),
        ("WavesSvc", "Audio drayver (Waves)"),
        ("SynTPEnh", "Touchpad drayveri (Synaptics)"),
        ("ETDCtrl", "Touchpad drayveri (Elan)"),
        ("igfxtray", "Intel grafik drayveri"),
        ("BthAvrcpAppSvc", "Bluetooth"),
        ("vgtray", "Anti-cheat (Riot Vanguard)"),
        ("EasyAntiCheat", "Anti-cheat"),
        ("BEService", "Anti-cheat"),
    ];

    public static string? GetProtectionReason(string name, string command)
    {
        foreach (var (fragment, reason) in Protected)
        {
            if (name.Contains(fragment, StringComparison.OrdinalIgnoreCase) ||
                command.Contains(fragment, StringComparison.OrdinalIgnoreCase))
                return reason;
        }
        return null;
    }
}
