using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Safety;

namespace UltimatePcOptimizer.Core.Optimization;

public sealed class CleanupStep(ICleanupService cleanup) : IOptimizationStep
{
    public string Id => "cleanup";
    public string Title => "Vaqtinchalik fayllar";

    public async Task<IReadOnlyList<CleanupScanResult>> ScanAsync(CancellationToken ct)
    {
        var results = new List<CleanupScanResult>();
        foreach (var category in CleanupCategoryInfo.OneClickDefaults)
            results.Add(await cleanup.ScanAsync(category, ct).ConfigureAwait(false));
        return results;
    }

    public static IReadOnlyList<PlannedChange> ToChanges(IEnumerable<CleanupScanResult> scans) => scans
        .Where(s => s.SizeBytes > 0)
        .Select(s => new PlannedChange(ChangeKind.FileCleanup, s.Category.ToString(), null,
            $"{CleanupCategoryInfo.Title(s.Category)} — {ByteFormatter.Format(s.SizeBytes)} ({s.FileCount} fayl)",
            RiskLevel.Safe))
        .ToList();

    public async Task<StepAnalysis> AnalyzeAsync(CancellationToken ct)
    {
        var scans = await ScanAsync(ct).ConfigureAwait(false);
        var total = scans.Aggregate(0UL, (sum, s) => sum + s.SizeBytes);
        return new StepAnalysis(Id, Title, $"{ByteFormatter.Format(total)} tozalash mumkin", [], ToChanges(scans));
    }
}

public sealed class StartupStep(IStartupService startup) : IOptimizationStep
{
    public string Id => "startup";
    public string Title => "Startup dasturlar";

    public Task<StepAnalysis> AnalyzeAsync(CancellationToken ct)
    {
        var entries = startup.GetEntries();
        var enabled = entries.Where(e => e.IsEnabled).ToList();
        var heavy = enabled.Where(e => !e.IsProtected && e.Impact == StartupImpact.High).ToList();

        // Disabling a startup item is the user's decision; suggestions are never pre-selected.
        var changes = heavy.Select(e => new PlannedChange(ChangeKind.StartupEntry, e.Id, bool.FalseString,
            $"Startup'dan o'chirish: {e.Name} (ta'sir: yuqori)", RiskLevel.Low, RecommendedByDefault: false)).ToList();

        var findings = enabled.Where(e => !e.IsProtected && e.Impact == StartupImpact.Medium)
            .Select(e => $"{e.Name} — o'rtacha ta'sir").ToList();

        return Task.FromResult(new StepAnalysis(Id, Title,
            $"{enabled.Count} ta dastur Windows bilan ishga tushadi, {heavy.Count} tasi og'ir", findings, changes));
    }
}

public sealed class BackgroundProcessStep(IProcessService processes) : IOptimizationStep
{
    private const ulong HeavyMemoryBytes = 500UL * 1024 * 1024;
    private const double HeavyCpuPercent = 10;

    public string Id => "background";
    public string Title => "Fon jarayonlari";

    public Task<StepAnalysis> AnalyzeAsync(CancellationToken ct)
    {
        var heavy = processes.GetProcesses()
            .Where(p => !p.IsProtected && p.Category is not ProcessCategory.Game)
            .Where(p => p.WorkingSetBytes >= HeavyMemoryBytes || p.CpuPercent >= HeavyCpuPercent)
            .OrderByDescending(p => p.WorkingSetBytes)
            .Take(10)
            .ToList();

        // Running programs are never closed automatically; they are only reported.
        var findings = heavy.Select(p =>
            $"{p.Name}: {ByteFormatter.Format(p.WorkingSetBytes)} RAM, CPU {p.CpuPercent:0}%").ToList();
        var summary = heavy.Count == 0
            ? "Ortiqcha resurs sarflayotgan fon jarayoni topilmadi"
            : $"{heavy.Count} ta jarayon ko'p resurs sarflayapti — Background Process Manager'da boshqaring";
        return Task.FromResult(new StepAnalysis(Id, Title, summary, findings, []));
    }
}

public sealed class PowerPlanStep(IPowerPlanService power, HardwareProfileCache hardware, ISystemMonitor monitor) : IOptimizationStep
{
    public string Id => "power";
    public string Title => "Power Plan";

    public async Task<StepAnalysis> AnalyzeAsync(CancellationToken ct)
    {
        var profile = await hardware.GetAsync(ct).ConfigureAwait(false);
        var plans = power.GetPlans();
        var active = plans.FirstOrDefault(p => p.IsActive);
        var target = PowerPlanAdvisor.RecommendForPerformance(plans, profile.IsLaptop, monitor.Sample().IsOnBattery);

        if (target is null || target.Id == active?.Id)
            return new StepAnalysis(Id, Title, $"Joriy reja mos: {active?.Name ?? "noma'lum"}", [], []);

        return new StepAnalysis(Id, Title, $"Joriy reja: {active?.Name ?? "noma'lum"}", [],
        [
            new PlannedChange(ChangeKind.PowerPlan, PowerPlanChangeHandler.ActivePlanTarget, target.Id.ToString(),
                $"Power Plan'ni '{target.Name}' ga o'zgartirish", RiskLevel.Low),
        ]);
    }
}

public sealed class GamingSettingsStep(IRegistryAccess registry) : IOptimizationStep
{
    public string Id => "gaming";
    public string Title => "Game Mode va Game Bar";

    public Task<StepAnalysis> AnalyzeAsync(CancellationToken ct)
    {
        var changes = TweakPlanner.Plan(TweakCatalog.GameMode, registry)
            .Concat(TweakPlanner.Plan(TweakCatalog.GameDvrOff, registry)).ToList();
        var gameModeOn = TweakPlanner.IsApplied(TweakCatalog.GameMode, registry);
        var findings = new List<string> { gameModeOn ? "Game Mode yoqilgan" : "Game Mode o'chiq" };
        return Task.FromResult(new StepAnalysis(Id, Title,
            changes.Count == 0 ? "Gaming sozlamalari optimal" : $"{changes.Count} ta sozlama yaxshilanishi mumkin", findings, changes));
    }
}

public sealed class VisualEffectsStep(IRegistryAccess registry) : IOptimizationStep
{
    public string Id => "visual";
    public string Title => "Windows animatsiyalari";

    public Task<StepAnalysis> AnalyzeAsync(CancellationToken ct)
    {
        var changes = TweakPlanner.Plan(TweakCatalog.ReduceAnimations, registry);
        var findings = changes.Count == 0 ? new List<string>() : ["O'zgarishlar keyingi tizimga kirishda to'liq kuchga kiradi"];
        return Task.FromResult(new StepAnalysis(Id, Title,
            changes.Count == 0 ? "Animatsiyalar allaqachon optimallashtirilgan" : "Animatsiyalarni kamaytirish mumkin", findings, changes));
    }
}
