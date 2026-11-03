using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using static UltimatePcOptimizer.Data.SqlMapping;

namespace UltimatePcOptimizer.Data;

public sealed class BenchmarkRepository(SqliteDatabase db) : IBenchmarkStore
{
    public async Task<long> SaveAsync(BenchmarkResult b, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("""
            INSERT INTO benchmarks (game_name, label, recorded_at, duration_seconds, frame_count, average_fps, one_percent_low,
                point_one_low, average_frametime, p99_frametime, average_cpu, average_gpu, max_cpu_temp, max_gpu_temp, average_ram)
            VALUES ($game, $label, $at, $dur, $frames, $avg, $low1, $low01, $ft, $p99, $cpu, $gpu, $ctemp, $gtemp, $ram);
            SELECT last_insert_rowid();
            """,
            ("$game", b.GameName), ("$label", b.Label), ("$at", ToDb(b.RecordedAt)), ("$dur", b.Duration.TotalSeconds),
            ("$frames", b.Frames.FrameCount), ("$avg", b.Frames.AverageFps), ("$low1", b.Frames.OnePercentLowFps),
            ("$low01", b.Frames.PointOnePercentLowFps), ("$ft", b.Frames.AverageFrameTimeMs), ("$p99", b.Frames.P99FrameTimeMs),
            ("$cpu", b.AverageCpuPercent), ("$gpu", ToDb(b.AverageGpuPercent)), ("$ctemp", ToDb(b.MaxCpuTemperatureC)),
            ("$gtemp", ToDb(b.MaxGpuTemperatureC)), ("$ram", b.AverageRamPercent));
        return (long)(await cmd.ExecuteScalarAsync(ct).ConfigureAwait(false))!;
    }

    public async Task<IReadOnlyList<BenchmarkResult>> GetRecentAsync(int limit, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("""
            SELECT id, game_name, label, recorded_at, duration_seconds, frame_count, average_fps, one_percent_low, point_one_low,
                   average_frametime, p99_frametime, average_cpu, average_gpu, max_cpu_temp, max_gpu_temp, average_ram
              FROM benchmarks ORDER BY recorded_at DESC LIMIT $limit;
            """, ("$limit", limit));
        await using var r = await cmd.ExecuteReaderAsync(ct).ConfigureAwait(false);
        var list = new List<BenchmarkResult>();
        while (await r.ReadAsync(ct).ConfigureAwait(false))
        {
            var frames = new FrameStats(r.GetInt32(5), 0, r.GetDouble(6), r.GetDouble(7), r.GetDouble(8), r.GetDouble(9), r.GetDouble(10), 0);
            list.Add(new BenchmarkResult(r.GetInt64(0), r.GetString(1), r.GetString(2), ReadDate(r, 3), TimeSpan.FromSeconds(r.GetDouble(4)),
                frames, r.GetDouble(11), ReadNullableDouble(r, 12), ReadNullableDouble(r, 13), ReadNullableDouble(r, 14), r.GetDouble(15)));
        }
        return list;
    }
}
