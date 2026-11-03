using CommunityToolkit.Mvvm.ComponentModel;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>A proposed change with its own approval checkbox.</summary>
public sealed partial class ChangeItem(PlannedChange change) : ObservableObject
{
    [ObservableProperty] private bool _isSelected = change.RecommendedByDefault;

    public PlannedChange Change { get; } = change;
    public string Description => Change.Description;
    public RiskLevel Risk => Change.Risk;
}

public sealed class StepItem(StepAnalysis analysis)
{
    public string Title => analysis.Title;
    public string Summary => analysis.Summary;
    public IReadOnlyList<string> Findings => analysis.Findings;
    public IReadOnlyList<ChangeItem> Changes { get; } = analysis.Changes.Select(c => new ChangeItem(c)).ToList();
}

public static class ReportText
{
    public static string Describe(OptimizationReport report)
    {
        if (report.Cancelled) return "Bekor qilindi — hech narsa o'zgartirilmadi.";
        if (report.Outcomes.Count == 0) return "Tanlangan o'zgarish yo'q.";
        var text = $"{report.Succeeded} ta o'zgarish qo'llandi";
        if (report.Failed > 0) text += $", {report.Failed} tasi bajarilmadi";
        if (report.RestorePoint is { } rp) text += $". {rp.Message}";
        return text + ". Istalgan vaqtda History bo'limidan Undo qilishingiz mumkin.";
    }
}
