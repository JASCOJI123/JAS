namespace UltimatePcOptimizer.Data;

public sealed record Migration(int Version, string Sql);

/// <summary>Append-only list of schema migrations. Never edit a released migration; add a new one.</summary>
public static class Migrations
{
    public static IReadOnlyList<Migration> All { get; } =
    [
        new(1, """
            CREATE TABLE change_batches (
                id              TEXT PRIMARY KEY,
                kind            TEXT NOT NULL,
                title           TEXT NOT NULL,
                created_at      TEXT NOT NULL,
                restore_point   TEXT NULL,
                reverted_at     TEXT NULL
            );
            CREATE INDEX ix_change_batches_kind_reverted ON change_batches (kind, reverted_at);
            CREATE INDEX ix_change_batches_created ON change_batches (created_at DESC);

            CREATE TABLE change_records (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_id        TEXT NOT NULL REFERENCES change_batches (id) ON DELETE CASCADE,
                kind            TEXT NOT NULL,
                target          TEXT NOT NULL,
                previous_exists INTEGER NOT NULL,
                previous_value  TEXT NULL,
                new_value       TEXT NULL,
                description     TEXT NOT NULL,
                is_reversible   INTEGER NOT NULL,
                status          TEXT NOT NULL,
                result_info     TEXT NULL,
                applied_at      TEXT NOT NULL,
                reverted_at     TEXT NULL
            );
            CREATE INDEX ix_change_records_batch ON change_records (batch_id);

            CREATE TABLE game_profiles (
                game_id                 TEXT PRIMARY KEY,
                game_name               TEXT NOT NULL,
                auto_boost              INTEGER NOT NULL,
                raise_game_priority     INTEGER NOT NULL,
                lower_background        INTEGER NOT NULL,
                suppress_notifications  INTEGER NOT NULL,
                high_performance_plan   INTEGER NOT NULL,
                prefer_high_perf_gpu    INTEGER NOT NULL,
                executable_path         TEXT NULL,
                updated_at              TEXT NOT NULL
            );

            CREATE TABLE games (
                id                TEXT PRIMARY KEY,
                name              TEXT NOT NULL,
                launcher          TEXT NOT NULL,
                install_directory TEXT NOT NULL,
                executables       TEXT NOT NULL,
                known_game_id     TEXT NULL
            );
            CREATE INDEX ix_games_launcher ON games (launcher);

            CREATE TABLE benchmarks (
                id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                game_name           TEXT NOT NULL,
                label               TEXT NOT NULL,
                recorded_at         TEXT NOT NULL,
                duration_seconds    REAL NOT NULL,
                frame_count         INTEGER NOT NULL,
                average_fps         REAL NOT NULL,
                one_percent_low     REAL NOT NULL,
                point_one_low       REAL NOT NULL,
                average_frametime   REAL NOT NULL,
                p99_frametime       REAL NOT NULL,
                average_cpu         REAL NOT NULL,
                average_gpu         REAL NULL,
                max_cpu_temp        REAL NULL,
                max_gpu_temp        REAL NULL,
                average_ram         REAL NOT NULL
            );
            CREATE INDEX ix_benchmarks_game ON benchmarks (game_name, recorded_at DESC);

            CREATE TABLE settings (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """),
    ];
}
