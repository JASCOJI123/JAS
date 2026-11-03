using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Analysis;

public enum GpuTier { Entry, Mainstream, High, Enthusiast }

public sealed record GraphicsProfile(
    string GameName,
    GraphicsPreset Preset,
    int TargetFps,
    string Upscaler,
    string LatencyAdvice,
    string SyncAdvice,
    IReadOnlyList<string> Notes);

/// <summary>
/// Builds a recommended in-game graphics profile. It is advice the user applies inside the game:
/// the optimizer never edits game files or game configuration.
/// </summary>
public static class GraphicsProfileAdvisor
{
    public static GpuTier ClassifyGpu(GpuInfo? gpu)
    {
        if (gpu is null || gpu.IsIntegrated) return GpuTier.Entry;
        const ulong gb = 1024UL * 1024 * 1024;
        return gpu.DedicatedMemoryBytes switch
        {
            >= 16 * gb => GpuTier.Enthusiast,
            >= 10 * gb => GpuTier.High,
            >= 6 * gb => GpuTier.Mainstream,
            _ => GpuTier.Entry,
        };
    }

    public static GraphicsProfile Build(HardwareProfile hardware, KnownGame? game, string gameName)
    {
        var gpu = hardware.PrimaryGpu;
        var tier = ClassifyGpu(gpu);
        var display = hardware.PrimaryDisplay;
        var refresh = display?.RefreshRateHz is > 0 ? display.RefreshRateHz : 60;
        var pixels = (display?.Width ?? 1920) * (display?.Height ?? 1080);
        var competitive = game?.IsCompetitive ?? false;

        var preset = tier switch
        {
            GpuTier.Enthusiast => GraphicsPreset.Ultra,
            GpuTier.High => GraphicsPreset.High,
            GpuTier.Mainstream => GraphicsPreset.Medium,
            _ => GraphicsPreset.Low,
        };
        // Higher resolutions cost more GPU time; step one preset down above 1440p on non-enthusiast GPUs.
        if (pixels > 2560 * 1440 && tier != GpuTier.Enthusiast && preset > GraphicsPreset.Low) preset--;
        // Competitive shooters favour frame rate over image quality.
        if (competitive && preset > GraphicsPreset.Medium) preset = GraphicsPreset.Medium;

        var isRtx = gpu?.Vendor == GpuVendor.Nvidia && gpu.Name.Contains("RTX", StringComparison.OrdinalIgnoreCase);
        var upscaler = isRtx ? "DLSS (Quality)" : gpu?.Vendor == GpuVendor.Intel && !gpu.IsIntegrated ? "XeSS yoki FSR (Quality)" : "FSR (Quality)";
        if (competitive) upscaler = "O'chiq yoki Quality — aniq tasvir muhim";

        var latency = (gpu?.Vendor, game?.SupportsReflex) switch
        {
            (GpuVendor.Nvidia, true) => "NVIDIA Reflex: On + Boost",
            (GpuVendor.Nvidia, null) => "O'yin sozlamalarida NVIDIA Reflex bo'lsa — yoqing",
            (GpuVendor.Amd, _) => "AMD Radeon Anti-Lag'ni Adrenalin'da yoqing",
            _ => "Low Latency rejimini GPU panelida yoqing (mavjud bo'lsa)",
        };

        var targetFps = competitive ? Math.Max(refresh, 144) : refresh;
        var sync = refresh > 60
            ? $"V-Sync o'chiq, G-Sync/FreeSync yoqilgan bo'lsa FPS limit {refresh - 3}"
            : "V-Sync o'chiq, FPS limit 60–62 (tearing bezovta qilsa V-Sync yoqing)";

        var notes = new List<string>();
        if (tier == GpuTier.Entry) notes.Add("Soyalar, volumetrik effektlar va ray tracing'ni o'chiring — eng ko'p FPS beradi.");
        if (game?.HasKernelAntiCheat == true) notes.Add("Bu o'yinda kernel anti-cheat bor: optimizer o'yin jarayoniga tegmaydi.");
        notes.Add("Sozlamalarni o'yin ichidagi menyudan o'zgartiring — optimizer o'yin fayllarini o'zgartirmaydi.");

        return new GraphicsProfile(gameName, preset, targetFps, upscaler, latency, sync, notes);
    }
}
