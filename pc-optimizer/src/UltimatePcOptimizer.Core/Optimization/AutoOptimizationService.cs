using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.Core.Optimization;

public sealed record AutoOptimizationResult(ulong JunkBytesFound, int CategoriesCleaned);

/// <summary>
/// Automatic Optimization. Periodically scans for junk files. It only cleans automatically when the
/// user has explicitly enabled it, and only Safe-risk cleanup items; it never changes system settings.
/// </summary>
public sealed class AutoOptimizationService(
    CleanupStep cleanupStep,
    ChangeExecutor executor,
    ISettingsStore settingsStore,
    ILogger<AutoOptimizationService> logger)
{
    public async Task<AutoOptimizationResult?> RunIfDueAsync(CancellationToken ct = default)
    {
        var settings = await settingsStore.LoadAsync(ct).ConfigureAwait(false);
        if (!settings.AutoOptimizationEnabled) return null;
        var due = settings.LastAutoOptimization is null ||
                  DateTimeOffset.UtcNow - settings.LastAutoOptimization >= TimeSpan.FromHours(Math.Max(1, settings.AutoOptimizationIntervalHours));
        if (!due) return null;

        var scans = await cleanupStep.ScanAsync(ct).ConfigureAwait(false);
        var found = scans.Aggregate(0UL, (sum, x) => sum + x.SizeBytes);
        var safe = CleanupStep.ToChanges(scans).Where(c => c.Risk == RiskLevel.Safe).ToList();
        var cleanedCategories = 0;

        if (settings.AutoApplySafeCleanup && safe.Count > 0)
        {
            var outcomes = await executor.ApplyAsync(ChangeBatch.Create(BatchKind.Automatic, "Automatic Optimization"), safe, ct).ConfigureAwait(false);
            cleanedCategories = outcomes.Count(o => o.Success);
        }

        await settingsStore.SaveAsync(settings with { LastAutoOptimization = DateTimeOffset.UtcNow }, ct).ConfigureAwait(false);
        logger.LogInformation("Automatic optimization: {Bytes} junk found, {Cleaned} categories cleaned", found, cleanedCategories);
        return new AutoOptimizationResult(found, cleanedCategories);
    }
}
