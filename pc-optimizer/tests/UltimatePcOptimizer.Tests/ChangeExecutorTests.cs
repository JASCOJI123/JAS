using Microsoft.Extensions.Logging.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Optimization;
using UltimatePcOptimizer.Data;

namespace UltimatePcOptimizer.Tests;

public class ChangeExecutorTests
{
    private static (ChangeExecutor Executor, ChangeJournalRepository Journal) Create(TempDatabase db, FakeRegistry registry, FakePower power)
    {
        var journal = new ChangeJournalRepository(db.Db);
        IChangeHandler[] handlers = [new RegistryChangeHandler(registry), new PowerPlanChangeHandler(power)];
        return (new ChangeExecutor(handlers, journal, NullLogger<ChangeExecutor>.Instance), journal);
    }

    [Fact]
    public async Task Apply_then_revert_restores_previous_values_and_removes_new_ones()
    {
        await using var db = await TempDatabase.CreateAsync();
        var registry = new FakeRegistry();
        var power = new FakePower();
        var (executor, journal) = Create(db, registry, power);

        var gameDvr = TweakCatalog.GameDvrOff.Assignments[0].Value;
        registry.Values[gameDvr.DisplayPath] = "1"; // existed before; AppCaptureEnabled did not

        var changes = TweakPlanner.Plan(TweakCatalog.GameDvrOff, registry).ToList();
        changes.Add(new PlannedChange(ChangeKind.PowerPlan, PowerPlanChangeHandler.ActivePlanTarget,
            Core.Models.KnownPowerPlans.HighPerformance.ToString(), "plan", RiskLevel.Low));

        var batch = ChangeBatch.Create(BatchKind.OneClick, "test");
        var outcomes = await executor.ApplyAsync(batch, changes);

        Assert.All(outcomes, o => Assert.True(o.Success, o.Message));
        Assert.Equal("0", registry.Values[gameDvr.DisplayPath]);
        Assert.Equal(2, registry.Values.Count);
        Assert.Equal(Core.Models.KnownPowerPlans.HighPerformance, power.GetActivePlan()!.Id);

        var report = await executor.RevertBatchAsync(batch.Id);

        Assert.Equal(3, report.Reverted);
        Assert.Equal("1", registry.Values[gameDvr.DisplayPath]);
        Assert.Single(registry.Values); // the value that did not exist was deleted again
        Assert.Equal(Core.Models.KnownPowerPlans.Balanced, power.GetActivePlan()!.Id);

        var stored = Assert.Single(await journal.GetBatchesAsync(10));
        Assert.NotNull(stored.RevertedAt);
        Assert.All(await journal.GetRecordsAsync(batch.Id), r => Assert.Equal(ChangeStatus.Reverted, r.Status));
    }

    [Fact]
    public async Task Registry_values_outside_the_allow_list_are_rejected_and_not_journaled()
    {
        await using var db = await TempDatabase.CreateAsync();
        var registry = new FakeRegistry();
        var (executor, journal) = Create(db, registry, new FakePower());

        var forbidden = new RegistryValueRef(RegistryHive.LocalMachine,
            @"SOFTWARE\Policies\Microsoft\Windows Defender", "DisableAntiSpyware", RegistryValueType.DWord);
        var batch = ChangeBatch.Create(BatchKind.Manual, "bad");
        var outcome = Assert.Single(await executor.ApplyAsync(batch,
            [new PlannedChange(ChangeKind.RegistryValue, forbidden.ToString(), "1", "disable defender", RiskLevel.Moderate)]));

        Assert.False(outcome.Success);
        Assert.Empty(registry.Values);
        Assert.Empty(await journal.GetRecordsAsync(batch.Id));
    }

    [Fact]
    public async Task Unavailable_power_plan_is_rejected()
    {
        await using var db = await TempDatabase.CreateAsync();
        var (executor, _) = Create(db, new FakeRegistry(), new FakePower());

        var outcome = Assert.Single(await executor.ApplyAsync(ChangeBatch.Create(BatchKind.Manual, "x"),
            [new PlannedChange(ChangeKind.PowerPlan, PowerPlanChangeHandler.ActivePlanTarget, Guid.NewGuid().ToString(), "x", RiskLevel.Low)]));

        Assert.False(outcome.Success);
    }

    [Fact]
    public async Task Restore_point_failure_asks_user_and_cancel_applies_nothing()
    {
        await using var db = await TempDatabase.CreateAsync();
        var registry = new FakeRegistry();
        var (executor, _) = Create(db, registry, new FakePower());
        var consent = new FakeConsent(false);
        var restore = new FakeRestorePoints(false);

        var report = await OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.OneClick, "x"),
            TweakPlanner.Plan(TweakCatalog.GameMode, registry), createRestorePoint: true, consent, executor, restore, default);

        Assert.True(report.Cancelled);
        Assert.Equal(1, consent.Asked);
        Assert.Empty(registry.Values);
    }
}
