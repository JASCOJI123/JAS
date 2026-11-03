using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Abstractions;

/// <summary>Persistent journal of every change the application makes. It is the basis of Undo.</summary>
public interface IChangeJournal
{
    Task CreateBatchAsync(ChangeBatch batch, CancellationToken ct = default);
    Task<long> AddPendingAsync(Guid batchId, PlannedChange change, PreviousState previous, bool isReversible, CancellationToken ct = default);
    Task SetStatusAsync(long recordId, ChangeStatus status, string? resultInfo, CancellationToken ct = default);
    Task MarkBatchRevertedAsync(Guid batchId, CancellationToken ct = default);
    Task<IReadOnlyList<ChangeBatch>> GetBatchesAsync(int limit, CancellationToken ct = default);
    Task<IReadOnlyList<ChangeRecord>> GetRecordsAsync(Guid batchId, CancellationToken ct = default);
    /// <summary>Gaming sessions that were never reverted (e.g. after a crash or power loss).</summary>
    Task<IReadOnlyList<ChangeBatch>> GetUnrevertedBatchesAsync(BatchKind kind, CancellationToken ct = default);
}

public interface IGameProfileStore
{
    Task<GameProfile?> GetAsync(string gameId, CancellationToken ct = default);
    Task<IReadOnlyList<GameProfile>> GetAllAsync(CancellationToken ct = default);
    Task SaveAsync(GameProfile profile, CancellationToken ct = default);
}

public interface IGameLibraryStore
{
    Task<IReadOnlyList<InstalledGame>> GetAllAsync(CancellationToken ct = default);
    Task ReplaceDetectedAsync(IReadOnlyList<InstalledGame> games, CancellationToken ct = default);
    Task AddManualAsync(InstalledGame game, CancellationToken ct = default);
}

public interface IBenchmarkStore
{
    Task<long> SaveAsync(BenchmarkResult result, CancellationToken ct = default);
    Task<IReadOnlyList<BenchmarkResult>> GetRecentAsync(int limit, CancellationToken ct = default);
}

public interface ISettingsStore
{
    Task<AppSettings> LoadAsync(CancellationToken ct = default);
    Task SaveAsync(AppSettings settings, CancellationToken ct = default);
}
