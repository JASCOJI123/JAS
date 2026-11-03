namespace UltimatePcOptimizer.Core.Models;

public enum CleanupCategory
{
    SystemTemporaryFiles,
    WindowsTemp,
    UserTemp,
    DirectXShaderCache,
    ThumbnailCache,
    RecycleBin,
    WindowsUpdateCleanup,
    BrowserCache,
}

public sealed record CleanupScanResult(
    CleanupCategory Category,
    ulong SizeBytes,
    int FileCount,
    bool SizeIsEstimate,
    string? Note);

public sealed record CleanupResult(CleanupCategory Category, ulong FreedBytes, int DeletedFiles, int SkippedFiles, string? Note);

public static class CleanupCategoryInfo
{
    public static string Title(CleanupCategory c) => c switch
    {
        CleanupCategory.SystemTemporaryFiles => "Vaqtinchalik tizim fayllari (WER, crash dump)",
        CleanupCategory.WindowsTemp => "Windows Temp",
        CleanupCategory.UserTemp => "User Temp",
        CleanupCategory.DirectXShaderCache => "DirectX Shader Cache",
        CleanupCategory.ThumbnailCache => "Thumbnail Cache",
        CleanupCategory.RecycleBin => "Recycle Bin (Savat)",
        CleanupCategory.WindowsUpdateCleanup => "Windows Update Cleanup (DISM)",
        CleanupCategory.BrowserCache => "Browser Cache",
        _ => c.ToString(),
    };

    /// <summary>Categories the user must opt into explicitly; never pre-selected.</summary>
    public static bool RequiresExplicitConsent(CleanupCategory c) =>
        c is CleanupCategory.RecycleBin or CleanupCategory.BrowserCache or CleanupCategory.WindowsUpdateCleanup;

    /// <summary>
    /// Categories included in One Click Optimize. The shader cache is deliberately excluded: clearing
    /// it makes games recompile shaders on the next launch, which causes exactly the stutter we want to avoid.
    /// </summary>
    public static IReadOnlyList<CleanupCategory> OneClickDefaults { get; } =
    [
        CleanupCategory.SystemTemporaryFiles,
        CleanupCategory.WindowsTemp,
        CleanupCategory.UserTemp,
    ];

    public static string? Advice(CleanupCategory c) => c switch
    {
        CleanupCategory.DirectXShaderCache => "Faqat GPU drayveri yangilangandan keyin yoki grafik xatolarda tozalang — o'yinlar shaderlarni qayta kompilyatsiya qiladi.",
        CleanupCategory.BrowserCache => "Brauzerlar yopiq bo'lishi kerak. Login va parollar o'chirilmaydi — faqat kesh.",
        CleanupCategory.WindowsUpdateCleanup => "Windows'ning DISM StartComponentCleanup vositasi ishlatiladi. Bir necha daqiqa davom etishi mumkin.",
        CleanupCategory.RecycleBin => "Savatdagi fayllar butunlay o'chiriladi.",
        CleanupCategory.ThumbnailCache => "Explorer eskizlarni kerak bo'lganda qayta yaratadi.",
        _ => null,
    };
}
