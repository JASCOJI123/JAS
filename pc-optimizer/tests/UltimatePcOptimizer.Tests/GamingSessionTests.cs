using Microsoft.Extensions.Logging.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Gaming;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Data;

namespace UltimatePcOptimizer.Tests;

public class GamingSessionTests
{
    private sealed record Rig(GamingSessionManager Manager, FakeProcesses Processes, FakeRegistry Registry, FakePower Power, ChangeExecutor Executor, ChangeJournalRepository Journal);

    private static Rig Create(TempDatabase db)
    {
        var registry = new FakeRegistry();
        var power = new FakePower();
        var processes = new FakeProcesses();
        var journal = new ChangeJournalRepository(db.Db);
        IChangeHandler[] handlers = [new RegistryChangeHandler(registry), new PowerPlanChangeHandler(power), new ProcessPriorityChangeHandler(processes)];
        var executor = new ChangeExecutor(handlers, journal, NullLogger<ChangeExecutor>.Instance);
        var manager = new GamingSessionManager(executor, journal, processes, power, registry, new GameProfileRepository(db.Db),
            new FakeConsent(true), new HardwareProfileCache(new FakeHardware()), new FakeMonitor(), NullLogger<GamingSessionManager>.Instance);
        return new Rig(manager, processes, registry, power, executor, journal);
    }

    [Fact]
    public async Task Session_boosts_game_and_restores_everything_when_it_ends()
    {
        await using var db = await TempDatabase.CreateAsync();
        var rig = Create(db);
        rig.Processes.Processes.AddRange(
        [
            FakeProcesses.Make(100, "cs2", ProcessCategory.Game),
            FakeProcesses.Make(200, "chrome", ProcessCategory.Browser, cpu: 12),
            FakeProcesses.Make(300, "Discord", ProcessCategory.Communication, cpu: 12),
            FakeProcesses.Make(400, "vgc", ProcessCategory.AntiCheat, cpu: 12),
        ]);

        var game = new RunningGame(100, "cs2", null, "cs2", "Counter-Strike 2", null);
        var profile = GameProfile.CreateDefault("cs2", "Counter-Strike 2", hasKernelAntiCheat: false, autoBoost: true);
        await rig.Manager.StartSessionAsync(game, profile, default);

        ProcessPriorityLevel? Priority(int pid) => rig.Processes.GetPriority(pid, null);
        Assert.Equal(ProcessPriorityLevel.High, Priority(100));
        Assert.Equal(ProcessPriorityLevel.BelowNormal, Priority(200));
        Assert.Equal(ProcessPriorityLevel.Normal, Priority(300)); // voice chat untouched
        Assert.Equal(ProcessPriorityLevel.Normal, Priority(400)); // anti-cheat untouched
        Assert.Equal(KnownPowerPlans.HighPerformance, rig.Power.GetActivePlan()!.Id);
        Assert.Contains(rig.Registry.Values, kv => kv.Key.EndsWith("ToastEnabled") && kv.Value == "0");

        await rig.Manager.EndSessionAsync(default);

        Assert.Equal(ProcessPriorityLevel.Normal, Priority(100));
        Assert.Equal(ProcessPriorityLevel.Normal, Priority(200));
        Assert.Equal(KnownPowerPlans.Balanced, rig.Power.GetActivePlan()!.Id);
        Assert.Empty(rig.Registry.Values);
        Assert.Null(rig.Manager.ActiveSession);
    }

    [Fact]
    public async Task Kernel_anti_cheat_game_process_is_never_touched()
    {
        await using var db = await TempDatabase.CreateAsync();
        var rig = Create(db);
        rig.Processes.Processes.Add(FakeProcesses.Make(100, "VALORANT-Win64-Shipping", ProcessCategory.Game));

        var game = new RunningGame(100, "VALORANT-Win64-Shipping", null, "valorant", "VALORANT", null);
        var profile = GameProfile.CreateDefault("valorant", "VALORANT", hasKernelAntiCheat: true, autoBoost: true) with { RaiseGamePriority = true };
        var changes = await rig.Manager.BuildChangesAsync(game, profile, default);

        Assert.DoesNotContain(changes, c => c.Kind == ChangeKind.ProcessPriority && c.Target.StartsWith("100|"));
    }

    [Fact]
    public async Task Unfinished_session_is_reverted_on_recovery()
    {
        await using var db = await TempDatabase.CreateAsync();
        var rig = Create(db);
        rig.Processes.Processes.Add(FakeProcesses.Make(100, "cs2", ProcessCategory.Game));
        await rig.Manager.StartSessionAsync(new RunningGame(100, "cs2", null, "cs2", "CS2", null),
            GameProfile.CreateDefault("cs2", "CS2", false, true), default);

        // Simulate a crash: a fresh manager instance with the same database and system state.
        var recovered = new GamingSessionManager(rig.Executor, rig.Journal, rig.Processes, rig.Power, rig.Registry,
            new GameProfileRepository(db.Db), new FakeConsent(true), new HardwareProfileCache(new FakeHardware()),
            new FakeMonitor(), NullLogger<GamingSessionManager>.Instance);
        await recovered.RecoverAsync();

        Assert.Equal(KnownPowerPlans.Balanced, rig.Power.GetActivePlan()!.Id);
        Assert.Equal(ProcessPriorityLevel.Normal, rig.Processes.GetPriority(100, null));
        Assert.Empty(await rig.Journal.GetUnrevertedBatchesAsync(BatchKind.GamingSession));
    }
}
