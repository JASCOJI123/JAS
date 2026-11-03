using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Data;

namespace UltimatePcOptimizer.Tests;

public class DataTests
{
    [Fact]
    public async Task Migrations_are_idempotent()
    {
        await using var db = await TempDatabase.CreateAsync();
        await db.Db.MigrateAsync();
        Assert.Equal(Migrations.All.Max(m => m.Version), await db.Db.GetVersionAsync());
    }

    [Fact]
    public async Task Settings_round_trip_with_defaults()
    {
        await using var db = await TempDatabase.CreateAsync();
        var repo = new SettingsRepository(db.Db);
        Assert.False((await repo.LoadAsync()).AutoApplySafeCleanup);

        await repo.SaveAsync(new AppSettings { AutoOptimizationEnabled = true, LatencyHosts = ["9.9.9.9"] });
        var loaded = await repo.LoadAsync();
        Assert.True(loaded.AutoOptimizationEnabled);
        Assert.Equal(["9.9.9.9"], loaded.LatencyHosts);
    }

    [Fact]
    public async Task Game_library_keeps_manual_games_when_detected_ones_are_replaced()
    {
        await using var db = await TempDatabase.CreateAsync();
        var repo = new GameLibraryRepository(db.Db);
        await repo.AddManualAsync(new InstalledGame("manual:1", "My Game", GameLauncher.Manual, @"C:\g", ["g.exe"], null));
        await repo.ReplaceDetectedAsync([new InstalledGame("steam:730", "CS2", GameLauncher.Steam, @"C:\s", ["cs2.exe"], "cs2")]);
        await repo.ReplaceDetectedAsync([new InstalledGame("epic:f", "Fortnite", GameLauncher.Epic, @"C:\e", ["f.exe"], "fortnite")]);

        var all = await repo.GetAllAsync();
        Assert.Equal(["Fortnite", "My Game"], all.Select(g => g.Name));
    }

    [Fact]
    public async Task Profiles_and_benchmarks_persist()
    {
        await using var db = await TempDatabase.CreateAsync();
        var profiles = new GameProfileRepository(db.Db);
        await profiles.SaveAsync(GameProfile.CreateDefault("cs2", "CS2", false, true));
        await profiles.SaveAsync(GameProfile.CreateDefault("cs2", "CS2", false, false));
        Assert.False((await profiles.GetAsync("cs2"))!.AutoBoost);

        var bench = new BenchmarkRepository(db.Db);
        var id = await bench.SaveAsync(new BenchmarkResult(0, "CS2", "Before", DateTimeOffset.UtcNow, TimeSpan.FromSeconds(60),
            new FrameStats(1000, 0, 200, 120, 90, 5, 9, 0), 40, 90, 70, null, 55));
        var stored = Assert.Single(await bench.GetRecentAsync(5));
        Assert.Equal(id, stored.Id);
        Assert.Equal(120, stored.Frames.OnePercentLowFps);
        Assert.Null(stored.MaxGpuTemperatureC);
    }
}
