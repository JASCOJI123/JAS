using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Analysis;

public enum MemoryPressure { Low, Moderate, High, Critical }

public sealed record PageFileStatus(bool AutomaticallyManaged, ulong AllocatedBytes, ulong CurrentUsageBytes, ulong PeakUsageBytes, bool Exists);

public sealed record MemoryAssessment(MemoryPressure Pressure, string Message, IReadOnlyList<string> Advice);

public static class MemoryAnalyzer
{
    public static MemoryAssessment Assess(MemoryUsage memory, PageFileStatus? pageFile)
    {
        var used = memory.UsedPercent;
        var commit = memory.CommitPercent;
        var pressure = used >= 92 || commit >= 95 ? MemoryPressure.Critical
            : used >= 85 || commit >= 85 ? MemoryPressure.High
            : used >= 70 ? MemoryPressure.Moderate
            : MemoryPressure.Low;

        var advice = new List<string>();
        if (pressure >= MemoryPressure.High)
            advice.Add("Ko'p RAM ishlatayotgan fon dasturlarini o'zingiz yoping (ro'yxat pastda).");
        if (pageFile is { Exists: false } && memory.TotalBytes < 32UL * 1024 * 1024 * 1024)
            advice.Add("Pagefile o'chirilgan. 32 GB dan kam RAM'da bu o'yinlarda crash keltirib chiqarishi mumkin — avtomatik boshqaruvni yoqing.");
        if (pageFile is { Exists: true, AutomaticallyManaged: false })
            advice.Add("Pagefile qo'lda sozlangan. Agar maxsus sabab bo'lmasa, 'Automatically manage' tavsiya etiladi.");
        if (memory.TotalBytes > 0 && memory.TotalBytes < 8UL * 1024 * 1024 * 1024)
            advice.Add("8 GB dan kam RAM zamonaviy o'yinlar uchun yetarli emas — 16 GB tavsiya etiladi.");

        var message = pressure switch
        {
            MemoryPressure.Low => "RAM bosimi past",
            MemoryPressure.Moderate => "RAM bosimi o'rtacha",
            MemoryPressure.High => "RAM bosimi yuqori — o'yinlarda stuttering bo'lishi mumkin",
            _ => "RAM bosimi kritik — tizim pagefile'dan faol foydalanmoqda",
        };
        return new MemoryAssessment(pressure, message, advice);
    }
}
