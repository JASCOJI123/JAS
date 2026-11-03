using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.Core.Optimization;

public sealed record OptimizationPlan(IReadOnlyList<StepAnalysis> Steps)
{
    public IEnumerable<PlannedChange> AllChanges => Steps.SelectMany(s => s.Changes);
}

public sealed record OptimizationReport(
    ChangeBatch? Batch,
    IReadOnlyList<ChangeOutcome> Outcomes,
    RestorePointResult? RestorePoint,
    bool Cancelled)
{
    public int Succeeded => Outcomes.Count(o => o.Success);
    public int Failed => Outcomes.Count(o => !o.Success);
}

/// <summary>
/// One Click Optimize: analyse everything, let the user approve individual changes, create a
/// restore point, then apply the approved changes through the journaled executor.
/// </summary>
public sealed class OneClickOptimizer(
    IEnumerable<IOptimizationStep> steps,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent,
    ILogger<OneClickOptimizer> logger)
{
    public async Task<OptimizationPlan> AnalyzeAsync(CancellationToken ct = default)
    {
        var results = new List<StepAnalysis>();
        foreach (var step in steps)
        {
            try
            {
                results.Add(await step.AnalyzeAsync(ct).ConfigureAwait(false));
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                logger.LogError(ex, "Analysis step {Step} failed", step.Id);
                results.Add(StepAnalysis.Failed(step.Id, step.Title, ex.Message));
            }
        }
        return new OptimizationPlan(results);
    }

    public Task<OptimizationReport> ApplyAsync(IReadOnlyList<PlannedChange> approved, bool createRestorePoint, CancellationToken ct = default) =>
        ApplyBatchAsync(ChangeBatch.Create(BatchKind.OneClick, "One Click Optimize"), approved, createRestorePoint, consent, executor, restorePoints, ct);

    /// <summary>Shared by every feature that applies a set of user-approved changes.</summary>
    public static async Task<OptimizationReport> ApplyBatchAsync(
        ChangeBatch batch,
        IReadOnlyList<PlannedChange> approved,
        bool createRestorePoint,
        IConsentService consent,
        ChangeExecutor executor,
        IRestorePointService restorePoints,
        CancellationToken ct)
    {
        if (approved.Count == 0) return new OptimizationReport(null, [], null, false);

        RestorePointResult? restore = null;
        var touchesSystem = approved.Any(c => c.Kind != ChangeKind.FileCleanup);
        if (createRestorePoint && touchesSystem)
        {
            var description = $"Ultimate PC Optimizer — {batch.Title}";
            restore = await restorePoints.CreateAsync(description, ct).ConfigureAwait(false);
            if (!restore.Success)
            {
                var proceed = await consent.ConfirmAsync(new ConsentRequest(
                    "Restore Point yaratilmadi",
                    $"{restore.Message}\n\nDavom etilsinmi? Barcha o'zgarishlar baribir jurnalga yoziladi va Undo orqali qaytariladi.",
                    [], RiskLevel.Low)).ConfigureAwait(false);
                if (!proceed) return new OptimizationReport(null, [], restore, true);
            }
            else
            {
                batch = batch with { RestorePointDescription = description };
            }
        }

        var outcomes = await executor.ApplyAsync(batch, approved, ct).ConfigureAwait(false);
        return new OptimizationReport(batch, outcomes, restore, false);
    }
}
