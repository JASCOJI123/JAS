using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Analysis;

public enum RecommendationSeverity { Info, Suggestion, Important }

/// <summary>Navigation key of the page that can act on the recommendation.</summary>
public sealed record Recommendation(string Title, string Detail, RecommendationSeverity Severity, string? Page);

public sealed record RecommendationContext(
    HardwareProfile Hardware,
    bool? HardwareSchedulingEnabled,
    bool GameModeEnabled,
    PowerPlan? ActivePlan,
    bool OnBattery,
    DateTime Today);

/// <summary>Turns the detected configuration into concrete, safe suggestions.</summary>
public static class RecommendationEngine
{
    public static IReadOnlyList<Recommendation> Build(RecommendationContext ctx)
    {
        var list = new List<Recommendation>();
        var hw = ctx.Hardware;
        const ulong gb = 1024UL * 1024 * 1024;

        if (hw.SystemDisk?.MediaType == DiskMediaType.Hdd)
            list.Add(new("Windows HDD'da o'rnatilgan",
                "Tizim va o'yinlarni SSD/NVMe'ga ko'chirish yuklanish vaqtini va stuttering'ni sezilarli kamaytiradi.",
                RecommendationSeverity.Important, null));

        if (hw.TotalRamBytes > 0 && hw.TotalRamBytes < 8 * gb)
            list.Add(new("RAM kam", "Zamonaviy o'yinlar uchun kamida 16 GB RAM tavsiya etiladi.", RecommendationSeverity.Important, "ram"));
        else if (hw.TotalRamBytes < 15 * gb)
            list.Add(new("RAM 8 GB", "O'yin paytida brauzer va boshqa og'ir dasturlarni yopib qo'ying.", RecommendationSeverity.Suggestion, "ram"));

        foreach (var display in hw.Displays.Where(d => d.MaxRefreshRateHz > d.RefreshRateHz && d.RefreshRateHz > 0))
            list.Add(new($"Monitor {display.RefreshRateHz} Hz'da ishlamoqda",
                $"Monitor {display.MaxRefreshRateHz} Hz'ni qo'llab-quvvatlaydi. Settings → Display → Advanced display'da yangilanish tezligini oshiring.",
                RecommendationSeverity.Important, "system"));

        var gpu = hw.PrimaryGpu;
        if (gpu?.DriverDate is { } driverDate && (ctx.Today - driverDate).TotalDays > 365)
            list.Add(new("GPU drayveri eski",
                $"{gpu.Name} drayveri {driverDate:yyyy-MM-dd} sanasidagi. Rasmiy {VendorSite(gpu.Vendor)} saytidan yangilang (dastur drayverlarni o'zi o'zgartirmaydi).",
                RecommendationSeverity.Suggestion, "gpu"));

        if (ctx.HardwareSchedulingEnabled == false && gpu is { IsIntegrated: false } && hw.Os.IsWindows11)
            list.Add(new("HAGS o'chiq",
                "Hardware-accelerated GPU scheduling yangi GPU'larda input lag'ni kamaytirishi mumkin. Settings → Graphics'da sinab ko'ring.",
                RecommendationSeverity.Suggestion, "fps"));

        if (!ctx.GameModeEnabled)
            list.Add(new("Game Mode o'chiq", "One Click Optimize orqali Game Mode'ni yoqing.", RecommendationSeverity.Suggestion, "optimize"));

        if (hw.IsLaptop && ctx.OnBattery)
            list.Add(new("Noutbuk batareyada", "O'yin uchun zaryadlovchini ulang — batareyada CPU/GPU quvvati cheklanadi.", RecommendationSeverity.Important, "modes"));
        else if (hw.IsLaptop)
            list.Add(new("Noutbuk", "Laptop Mode ishlash va harorat o'rtasida muvozanat saqlaydi.", RecommendationSeverity.Info, "modes"));

        if (!hw.IsLaptop && ctx.ActivePlan?.Id == KnownPowerPlans.PowerSaver)
            list.Add(new("Power Saver rejimi", "Desktop'da Power Saver CPU chastotasini cheklaydi. Desktop yoki Gaming Mode'ni tanlang.",
                RecommendationSeverity.Important, "modes"));

        if (hw.Gpus.Count > 1 && hw.Gpus.Any(g => g.IsIntegrated) && hw.Gpus.Any(g => !g.IsIntegrated))
            list.Add(new("Ikki GPU aniqlandi", "Game Library'da har bir o'yin uchun yuqori unumli GPU'ni tanlang.", RecommendationSeverity.Suggestion, "library"));

        return list;
    }

    private static string VendorSite(GpuVendor vendor) => vendor switch
    {
        GpuVendor.Nvidia => "NVIDIA",
        GpuVendor.Amd => "AMD",
        GpuVendor.Intel => "Intel",
        _ => "ishlab chiqaruvchi",
    };
}
