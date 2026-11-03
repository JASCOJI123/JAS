using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.Core.Optimization;

public enum OptimizationMode { Gaming, Laptop, Desktop, Balanced, MaximumPerformance, Custom }

public sealed record ModeDefinition(
    OptimizationMode Mode,
    string Title,
    string Description,
    PowerPlanPreference PowerPlan,
    IReadOnlyList<string> TweakIds);

public static class ModeCatalog
{
    public static IReadOnlyList<ModeDefinition> Modes { get; } =
    [
        new(OptimizationMode.Gaming, "Gaming Mode", "O'yinlar uchun: yuqori unumli reja, Game Mode, Game DVR o'chiq, kamroq animatsiya.",
            PowerPlanPreference.HighPerformance, ["game-mode", "game-dvr-off", "reduce-animations"]),
        new(OptimizationMode.Laptop, "Laptop Mode", "Noutbuk: muvozanatli reja va harorat nazorati, fon yozuvi o'chiq.",
            PowerPlanPreference.Balanced, ["game-mode", "game-dvr-off"]),
        new(OptimizationMode.Desktop, "Desktop Mode", "Desktop: yuqori unumli reja va Game Mode.",
            PowerPlanPreference.HighPerformance, ["game-mode", "game-dvr-off"]),
        new(OptimizationMode.Balanced, "Balanced Mode", "Windows standart muvozanatli rejasi. Boshqa sozlamalarga tegilmaydi.",
            PowerPlanPreference.Balanced, []),
        new(OptimizationMode.MaximumPerformance, "Maximum Performance", "Ultimate/High Performance reja va barcha xavfsiz gaming sozlamalari.",
            PowerPlanPreference.Maximum, ["game-mode", "game-dvr-off", "reduce-animations"]),
        new(OptimizationMode.Custom, "Custom Optimization", "Reja va sozlamalarni o'zingiz tanlaysiz.",
            PowerPlanPreference.Balanced, []),
    ];

    public static ModeDefinition Get(OptimizationMode mode) => Modes.First(m => m.Mode == mode);
}

/// <summary>Plans and applies an optimization mode as one undoable batch.</summary>
public sealed class ModeService(IPowerPlanService power, IRegistryAccess registry)
{
    public IReadOnlyList<PlannedChange> Plan(ModeDefinition mode, PowerPlanPreference? customPlan = null, IReadOnlyList<string>? customTweaks = null)
    {
        var changes = new List<PlannedChange>();
        var plans = power.GetPlans();
        var target = PowerPlanAdvisor.FindWithFallback(plans, customPlan ?? mode.PowerPlan);
        if (target is not null && !target.IsActive)
            changes.Add(new(ChangeKind.PowerPlan, PowerPlanChangeHandler.ActivePlanTarget, target.Id.ToString(),
                $"Power Plan: {target.Name}", RiskLevel.Low));

        foreach (var id in customTweaks ?? mode.TweakIds)
        {
            var tweak = TweakCatalog.Find(id);
            if (tweak is not null) changes.AddRange(TweakPlanner.Plan(tweak, registry));
        }
        return changes;
    }
}
