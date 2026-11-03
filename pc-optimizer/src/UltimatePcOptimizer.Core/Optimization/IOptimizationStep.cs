using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.Core.Optimization;

/// <summary>
/// Result of analysing one area. Findings are information for the user; Changes are proposals the
/// user approves one by one. Nothing is modified during analysis.
/// </summary>
public sealed record StepAnalysis(
    string StepId,
    string Title,
    string Summary,
    IReadOnlyList<string> Findings,
    IReadOnlyList<PlannedChange> Changes)
{
    public static StepAnalysis Failed(string stepId, string title, string error) =>
        new(stepId, title, $"Tahlil qilib bo'lmadi: {error}", [], []);
}

public interface IOptimizationStep
{
    string Id { get; }
    string Title { get; }
    Task<StepAnalysis> AnalyzeAsync(CancellationToken ct);
}
