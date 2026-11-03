using Microsoft.Data.Sqlite;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using static UltimatePcOptimizer.Data.SqlMapping;

namespace UltimatePcOptimizer.Data;

public sealed class ChangeJournalRepository(SqliteDatabase db) : IChangeJournal
{
    public async Task CreateBatchAsync(ChangeBatch batch, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command(
            "INSERT INTO change_batches (id, kind, title, created_at, restore_point, reverted_at) VALUES ($id, $kind, $title, $created, $rp, NULL);",
            ("$id", batch.Id.ToString()), ("$kind", batch.Kind.ToString()), ("$title", batch.Title),
            ("$created", ToDb(batch.CreatedAt)), ("$rp", ToDb(batch.RestorePointDescription)));
        await cmd.ExecuteNonQueryAsync(ct).ConfigureAwait(false);
    }

    public async Task<long> AddPendingAsync(Guid batchId, PlannedChange change, PreviousState previous, bool isReversible, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("""
            INSERT INTO change_records (batch_id, kind, target, previous_exists, previous_value, new_value, description, is_reversible, status, applied_at)
            VALUES ($batch, $kind, $target, $pe, $pv, $nv, $desc, $rev, $status, $at);
            SELECT last_insert_rowid();
            """,
            ("$batch", batchId.ToString()), ("$kind", change.Kind.ToString()), ("$target", change.Target),
            ("$pe", previous.Existed ? 1 : 0), ("$pv", ToDb(previous.Value)), ("$nv", ToDb(change.NewValue)),
            ("$desc", change.Description), ("$rev", isReversible ? 1 : 0), ("$status", ChangeStatus.Pending.ToString()),
            ("$at", ToDb(DateTimeOffset.UtcNow)));
        return (long)(await cmd.ExecuteScalarAsync(ct).ConfigureAwait(false))!;
    }

    public async Task SetStatusAsync(long recordId, ChangeStatus status, string? resultInfo, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("""
            UPDATE change_records
               SET status = $status,
                   result_info = COALESCE($info, result_info),
                   reverted_at = CASE WHEN $status = 'Reverted' THEN $now ELSE reverted_at END
             WHERE id = $id;
            """,
            ("$status", status.ToString()), ("$info", ToDb(resultInfo)), ("$now", ToDb(DateTimeOffset.UtcNow)), ("$id", recordId));
        await cmd.ExecuteNonQueryAsync(ct).ConfigureAwait(false);
    }

    public async Task MarkBatchRevertedAsync(Guid batchId, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("UPDATE change_batches SET reverted_at = $now WHERE id = $id;",
            ("$now", ToDb(DateTimeOffset.UtcNow)), ("$id", batchId.ToString()));
        await cmd.ExecuteNonQueryAsync(ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<ChangeBatch>> GetBatchesAsync(int limit, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command(
            "SELECT id, kind, title, created_at, restore_point, reverted_at FROM change_batches ORDER BY created_at DESC LIMIT $limit;",
            ("$limit", limit));
        return await ReadBatchesAsync(cmd, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<ChangeBatch>> GetUnrevertedBatchesAsync(BatchKind kind, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command(
            "SELECT id, kind, title, created_at, restore_point, reverted_at FROM change_batches WHERE kind = $kind AND reverted_at IS NULL ORDER BY created_at;",
            ("$kind", kind.ToString()));
        return await ReadBatchesAsync(cmd, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<ChangeRecord>> GetRecordsAsync(Guid batchId, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("""
            SELECT id, batch_id, kind, target, previous_exists, previous_value, new_value, description,
                   is_reversible, status, result_info, applied_at, reverted_at
              FROM change_records WHERE batch_id = $batch ORDER BY id;
            """, ("$batch", batchId.ToString()));
        await using var r = await cmd.ExecuteReaderAsync(ct).ConfigureAwait(false);
        var list = new List<ChangeRecord>();
        while (await r.ReadAsync(ct).ConfigureAwait(false))
        {
            list.Add(new ChangeRecord(
                r.GetInt64(0),
                Guid.Parse(r.GetString(1)),
                Enum.Parse<ChangeKind>(r.GetString(2)),
                r.GetString(3),
                new PreviousState(r.GetInt64(4) == 1, ReadNullableString(r, 5)),
                ReadNullableString(r, 6),
                r.GetString(7),
                r.GetInt64(8) == 1,
                Enum.Parse<ChangeStatus>(r.GetString(9)),
                ReadNullableString(r, 10),
                ReadDate(r, 11),
                ReadNullableDate(r, 12)));
        }
        return list;
    }

    private static async Task<IReadOnlyList<ChangeBatch>> ReadBatchesAsync(SqliteCommand cmd, CancellationToken ct)
    {
        await using var r = await cmd.ExecuteReaderAsync(ct).ConfigureAwait(false);
        var list = new List<ChangeBatch>();
        while (await r.ReadAsync(ct).ConfigureAwait(false))
        {
            list.Add(new ChangeBatch(Guid.Parse(r.GetString(0)), Enum.Parse<BatchKind>(r.GetString(1)), r.GetString(2),
                ReadDate(r, 3), ReadNullableString(r, 4), ReadNullableDate(r, 5)));
        }
        return list;
    }
}
