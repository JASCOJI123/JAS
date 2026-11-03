using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.Core.Optimization;

public sealed record RegistryAssignment(RegistryValueRef Value, string Data);

public sealed record TweakDefinition(
    string Id,
    string Title,
    string Description,
    RiskLevel Risk,
    IReadOnlyList<RegistryAssignment> Assignments,
    bool TakesEffectAfterSignIn = false);

/// <summary>Documented, reversible Windows settings the optimizer may change. All are on the registry allow-list.</summary>
public static class TweakCatalog
{
    private static RegistryValueRef Cu(string key, string value, RegistryValueType type = RegistryValueType.DWord) =>
        new(RegistryHive.CurrentUser, key, value, type);

    public static TweakDefinition GameMode { get; } = new(
        "game-mode",
        "Windows Game Mode yoqish",
        "Windows o'yin vaqtida fon yangilanishlari va drayver o'rnatishlarini kechiktiradi, resurslarni o'yinga beradi.",
        RiskLevel.Safe,
        [
            new(Cu(@"Software\Microsoft\GameBar", "AutoGameModeEnabled"), "1"),
            new(Cu(@"Software\Microsoft\GameBar", "AllowAutoGameMode"), "1"),
        ]);

    public static TweakDefinition GameDvrOff { get; } = new(
        "game-dvr-off",
        "Game Bar fon yozuvini (Game DVR) o'chirish",
        "Fonda doimiy video yozib borish GPU va diskka yuk beradi. Game Bar o'zi ishlashda davom etadi.",
        RiskLevel.Low,
        [
            new(Cu(@"System\GameConfigStore", "GameDVR_Enabled"), "0"),
            new(Cu(@"Software\Microsoft\Windows\CurrentVersion\GameDVR", "AppCaptureEnabled"), "0"),
        ]);

    public static TweakDefinition ReduceAnimations { get; } = new(
        "reduce-animations",
        "Windows animatsiyalarini kamaytirish",
        "Oyna minimallashtirish, taskbar va Aero Peek animatsiyalarini o'chiradi. Shriftlar va ko'rinish sifati saqlanadi.",
        RiskLevel.Low,
        [
            new(Cu(@"Control Panel\Desktop\WindowMetrics", "MinAnimate", RegistryValueType.String), "0"),
            new(Cu(@"Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced", "TaskbarAnimations"), "0"),
            new(Cu(@"Software\Microsoft\Windows\DWM", "EnableAeroPeek"), "0"),
            new(Cu(@"Control Panel\Desktop", "MenuShowDelay", RegistryValueType.String), "100"),
        ],
        TakesEffectAfterSignIn: true);

    public static TweakDefinition NotificationsOff { get; } = new(
        "notifications-off",
        "Bildirishnomalarni vaqtincha o'chirish",
        "Toast bildirishnomalari o'yin vaqtida chiqmaydi. O'yin tugagach avtomatik tiklanadi.",
        RiskLevel.Safe,
        [new(Cu(@"Software\Microsoft\Windows\CurrentVersion\PushNotifications", "ToastEnabled"), "0")]);

    public static TweakDefinition BackgroundAppsOff { get; } = new(
        "background-apps-off",
        "Store ilovalarining fon ishini cheklash",
        "Microsoft Store ilovalari fonda ishlamaydi. Ba'zi ilovalar (Mail, Calendar) bildirishnoma yubormay qolishi mumkin.",
        RiskLevel.Moderate,
        [new(Cu(@"Software\Microsoft\Windows\CurrentVersion\BackgroundAccessApplications", "GlobalUserDisabled"), "1")]);

    public static IReadOnlyList<TweakDefinition> All { get; } =
        [GameMode, GameDvrOff, ReduceAnimations, NotificationsOff, BackgroundAppsOff];

    public static TweakDefinition? Find(string id) => All.FirstOrDefault(t => t.Id == id);

    /// <summary>Per-application GPU preference (Settings → Display → Graphics). 2 = high performance GPU.</summary>
    public static RegistryAssignment HighPerformanceGpuFor(string executablePath) =>
        new(Cu(@"Software\Microsoft\DirectX\UserGpuPreferences", executablePath, RegistryValueType.String), "GpuPreference=2;");
}

public static class TweakPlanner
{
    /// <summary>Plans only the assignments whose current value differs from the desired one.</summary>
    public static IReadOnlyList<PlannedChange> Plan(TweakDefinition tweak, IRegistryAccess registry, bool recommended = true) =>
        tweak.Assignments
            .Where(a => !string.Equals(registry.Read(a.Value), a.Data, StringComparison.OrdinalIgnoreCase))
            .Select(a => new PlannedChange(ChangeKind.RegistryValue, a.Value.ToString(), a.Data,
                $"{tweak.Title} ({a.Value.ValueName} = {a.Data})", tweak.Risk, recommended))
            .ToList();

    public static bool IsApplied(TweakDefinition tweak, IRegistryAccess registry) =>
        tweak.Assignments.All(a => string.Equals(registry.Read(a.Value), a.Data, StringComparison.OrdinalIgnoreCase));
}
