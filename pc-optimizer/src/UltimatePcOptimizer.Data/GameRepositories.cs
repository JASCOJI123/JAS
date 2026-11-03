using System.Text.Json;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using static UltimatePcOptimizer.Data.SqlMapping;

namespace UltimatePcOptimizer.Data;

public sealed class GameProfileRepository(SqliteDatabase db) : IGameProfileStore
{
    private const string Columns = "game_id, game_name, auto_boost, raise_game_priority, lower_background, suppress_notifications, high_performance_plan, prefer_high_perf_gpu, executable_path, updated_at";

    public async Task<GameProfile?> GetAsync(string gameId, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command($"SELECT {Columns} FROM game_profiles WHERE game_id = $id;", ("$id", gameId));
        await using var r = await cmd.ExecuteReaderAsync(ct).ConfigureAwait(false);
        return await r.ReadAsync(ct).ConfigureAwait(false) ? Read(r) : null;
    }

    public async Task<IReadOnlyList<GameProfile>> GetAllAsync(CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command($"SELECT {Columns} FROM game_profiles ORDER BY game_name;");
        await using var r = await cmd.ExecuteReaderAsync(ct).ConfigureAwait(false);
        var list = new List<GameProfile>();
        while (await r.ReadAsync(ct).ConfigureAwait(false)) list.Add(Read(r));
        return list;
    }

    public async Task SaveAsync(GameProfile p, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command($"""
            INSERT INTO game_profiles ({Columns})
            VALUES ($id, $name, $auto, $raise, $lower, $notif, $plan, $gpu, $exe, $at)
            ON CONFLICT (game_id) DO UPDATE SET
                game_name = excluded.game_name, auto_boost = excluded.auto_boost,
                raise_game_priority = excluded.raise_game_priority, lower_background = excluded.lower_background,
                suppress_notifications = excluded.suppress_notifications, high_performance_plan = excluded.high_performance_plan,
                prefer_high_perf_gpu = excluded.prefer_high_perf_gpu, executable_path = excluded.executable_path,
                updated_at = excluded.updated_at;
            """,
            ("$id", p.GameId), ("$name", p.GameName), ("$auto", p.AutoBoost), ("$raise", p.RaiseGamePriority),
            ("$lower", p.LowerBackgroundPriority), ("$notif", p.SuppressNotifications), ("$plan", p.UseHighPerformancePowerPlan),
            ("$gpu", p.PreferHighPerformanceGpu), ("$exe", ToDb(p.ExecutablePath)), ("$at", ToDb(DateTimeOffset.UtcNow)));
        await cmd.ExecuteNonQueryAsync(ct).ConfigureAwait(false);
    }

    private static GameProfile Read(Microsoft.Data.Sqlite.SqliteDataReader r) => new(
        r.GetString(0), r.GetString(1), r.GetBoolean(2), r.GetBoolean(3), r.GetBoolean(4), r.GetBoolean(5),
        r.GetBoolean(6), r.GetBoolean(7), ReadNullableString(r, 8), ReadDate(r, 9));
}

public sealed class GameLibraryRepository(SqliteDatabase db) : IGameLibraryStore
{
    public async Task<IReadOnlyList<InstalledGame>> GetAllAsync(CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("SELECT id, name, launcher, install_directory, executables, known_game_id FROM games ORDER BY name;");
        await using var r = await cmd.ExecuteReaderAsync(ct).ConfigureAwait(false);
        var list = new List<InstalledGame>();
        while (await r.ReadAsync(ct).ConfigureAwait(false))
        {
            list.Add(new InstalledGame(r.GetString(0), r.GetString(1), Enum.Parse<GameLauncher>(r.GetString(2)), r.GetString(3),
                JsonSerializer.Deserialize<List<string>>(r.GetString(4)) ?? [], ReadNullableString(r, 5)));
        }
        return list;
    }

    /// <summary>Replaces launcher-detected games; manually added games are kept.</summary>
    public async Task ReplaceDetectedAsync(IReadOnlyList<InstalledGame> games, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var tx = (Microsoft.Data.Sqlite.SqliteTransaction)await c.BeginTransactionAsync(ct).ConfigureAwait(false);
        await using (var delete = c.Command("DELETE FROM games WHERE launcher <> $manual;", ("$manual", nameof(GameLauncher.Manual))))
        {
            delete.Transaction = tx;
            await delete.ExecuteNonQueryAsync(ct).ConfigureAwait(false);
        }
        foreach (var game in games.Where(g => g.Launcher != GameLauncher.Manual))
            await UpsertAsync(c, tx, game, ct).ConfigureAwait(false);
        await tx.CommitAsync(ct).ConfigureAwait(false);
    }

    public async Task AddManualAsync(InstalledGame game, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await UpsertAsync(c, null, game with { Launcher = GameLauncher.Manual }, ct).ConfigureAwait(false);
    }

    private static async Task UpsertAsync(Microsoft.Data.Sqlite.SqliteConnection c, Microsoft.Data.Sqlite.SqliteTransaction? tx, InstalledGame g, CancellationToken ct)
    {
        await using var cmd = c.Command("""
            INSERT INTO games (id, name, launcher, install_directory, executables, known_game_id)
            VALUES ($id, $name, $launcher, $dir, $exes, $known)
            ON CONFLICT (id) DO UPDATE SET name = excluded.name, launcher = excluded.launcher,
                install_directory = excluded.install_directory, executables = excluded.executables, known_game_id = excluded.known_game_id;
            """,
            ("$id", g.Id), ("$name", g.Name), ("$launcher", g.Launcher.ToString()), ("$dir", g.InstallDirectory),
            ("$exes", JsonSerializer.Serialize(g.ExecutableNames)), ("$known", ToDb(g.KnownGameId)));
        cmd.Transaction = tx;
        await cmd.ExecuteNonQueryAsync(ct).ConfigureAwait(false);
    }
}
