using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;

namespace UltimatePcOptimizer.Core.Changes;

/// <summary>
/// The only path through which the application modifies the system. Every change is validated,
/// the previous state is captured and journaled before the change is applied, so it can be undone
/// even if the application crashes midway.
/// </summary>
public sealed class ChangeExecutor
{
    private readonly IReadOnlyDictionary<ChangeKind, IChangeHandler> _handlers;
    private readonly IChangeJournal _journal;
    private readonly ILogger<ChangeExecutor> _logger;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public ChangeExecutor(IEnumerable<IChangeHandler> handlers, IChangeJournal journal, ILogger<ChangeExecutor> logger)
    {
        _handlers = handlers.ToDictionary(h => h.Kind);
        _journal = journal;
        _logger = logger;
    }

    public async Task<IReadOnlyList<ChangeOutcome>> ApplyAsync(ChangeBatch batch, IReadOnlyList<PlannedChange> changes, CancellationToken ct = default)
    {
        await _gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            await _journal.CreateBatchAsync(batch, ct).ConfigureAwait(false);
            var outcomes = new List<ChangeOutcome>(changes.Count);
            foreach (var change in changes)
            {
                ct.ThrowIfCancellationRequested();
                outcomes.Add(await ApplyOneAsync(batch.Id, change, ct).ConfigureAwait(false));
            }
            return outcomes;
        }
        finally
        {
            _gate.Release();
        }
    }

    private async Task<ChangeOutcome> ApplyOneAsync(Guid batchId, PlannedChange change, CancellationToken ct)
    {
        if (!_handlers.TryGetValue(change.Kind, out var handler))
            return new ChangeOutcome(change, false, $"No handler for {change.Kind}.", null);

        try
        {
            handler.Validate(change);
        }
        catch (Exception ex) when (ex is ArgumentException or FormatException or InvalidOperationException or UnauthorizedAccessException)
        {
            _logger.LogWarning("Rejected change {Kind} {Target}: {Reason}", change.Kind, change.Target, ex.Message);
            return new ChangeOutcome(change, false, ex.Message, null);
        }

        long recordId = 0;
        try
        {
            var previous = await handler.CaptureAsync(change.Target, ct).ConfigureAwait(false);
            recordId = await _journal.AddPendingAsync(batchId, change, previous, handler.IsReversible, ct).ConfigureAwait(false);
            var info = await handler.ApplyAsync(change.Target, change.NewValue, ct).ConfigureAwait(false);
            await _journal.SetStatusAsync(recordId, ChangeStatus.Applied, info, ct).ConfigureAwait(false);
            _logger.LogInformation("Applied {Kind} {Target} -> {Value}", change.Kind, change.Target, change.NewValue);
            return new ChangeOutcome(change, true, info, recordId);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to apply {Kind} {Target}", change.Kind, change.Target);
            if (recordId != 0) await _journal.SetStatusAsync(recordId, ChangeStatus.Failed, ex.Message, CancellationToken.None).ConfigureAwait(false);
            return new ChangeOutcome(change, false, ex.Message, recordId == 0 ? null : recordId);
        }
    }

    /// <summary>Restores every reversible change of a batch, newest first.</summary>
    public async Task<RevertReport> RevertBatchAsync(Guid batchId, CancellationToken ct = default)
    {
        await _gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            var records = await _journal.GetRecordsAsync(batchId, ct).ConfigureAwait(false);
            int reverted = 0, failed = 0, skipped = 0;
            var messages = new List<string>();

            foreach (var record in records.OrderByDescending(r => r.Id))
            {
                // Pending records were journaled but may have been partially applied before a crash; restore them too.
                var needsRevert = record.Status is ChangeStatus.Applied or ChangeStatus.Pending or ChangeStatus.RevertFailed;
                if (!record.IsReversible || !needsRevert || !_handlers.TryGetValue(record.Kind, out var handler))
                {
                    skipped++;
                    continue;
                }

                try
                {
                    await handler.RevertAsync(record.Target, record.Previous, ct).ConfigureAwait(false);
                    await _journal.SetStatusAsync(record.Id, ChangeStatus.Reverted, null, ct).ConfigureAwait(false);
                    reverted++;
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    _logger.LogError(ex, "Failed to revert {Kind} {Target}", record.Kind, record.Target);
                    await _journal.SetStatusAsync(record.Id, ChangeStatus.RevertFailed, ex.Message, CancellationToken.None).ConfigureAwait(false);
                    messages.Add($"{record.Description}: {ex.Message}");
                    failed++;
                }
            }

            if (failed == 0) await _journal.MarkBatchRevertedAsync(batchId, ct).ConfigureAwait(false);
            return new RevertReport(batchId, reverted, failed, skipped, messages);
        }
        finally
        {
            _gate.Release();
        }
    }
}
