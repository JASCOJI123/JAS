using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Optimization;
using UltimatePcOptimizer.Core.Safety;

namespace UltimatePcOptimizer.Core.Gaming;

public sealed record GamingSession(RunningGame Game, Guid BatchId, DateTimeOffset StartedAt, IReadOnlyList<ChangeOutcome> Outcomes);

/// <summary>
/// Gaming Booster. When a game starts it temporarily applies the game's profile (power plan,
/// notifications, Game Mode, priorities) as one journaled batch, and reverts that batch when the
/// game exits. Unfinished sessions are reverted on the next start of the application.
/// </summary>
public sealed class GamingSessionManager(
    ChangeExecutor executor,
    IChangeJournal journal,
    IProcessService processes,
    IPowerPlanService power,
    IRegistryAccess registry,
    IGameProfileStore profiles,
    IConsentService consent,
    HardwareProfileCache hardware,
    ISystemMonitor monitor,
    ILogger<GamingSessionManager> logger)
{
    private const int MaxBackgroundProcesses = 15;
    private const ulong BackgroundMemoryThreshold = 300UL * 1024 * 1024;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public GamingSession? ActiveSession { get; private set; }

    public event EventHandler? SessionChanged;

    public void Attach(GameDetectionService detection)
    {
        detection.GameStarted += (_, game) => _ = OnGameStartedAsync(game);
        detection.GameStopped += (_, game) => _ = OnGameStoppedAsync(game);
    }

    /// <summary>Reverts sessions left open by a crash or a forced shutdown.</summary>
    public async Task RecoverAsync(CancellationToken ct = default)
    {
        foreach (var batch in await journal.GetUnrevertedBatchesAsync(BatchKind.GamingSession, ct).ConfigureAwait(false))
        {
            var report = await executor.RevertBatchAsync(batch.Id, ct).ConfigureAwait(false);
            logger.LogInformation("Recovered gaming session {Batch}: {Reverted} reverted, {Failed} failed", batch.Id, report.Reverted, report.Failed);
        }
    }

    private async Task OnGameStartedAsync(RunningGame game)
    {
        try
        {
            var profile = await GetOrAskProfileAsync(game).ConfigureAwait(false);
            if (profile.AutoBoost) await StartSessionAsync(game, profile, CancellationToken.None).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Failed to start gaming session for {Game}", game.GameName);
        }
    }

    private async Task OnGameStoppedAsync(RunningGame game)
    {
        try
        {
            if (ActiveSession?.Game.Pid == game.Pid) await EndSessionAsync(CancellationToken.None).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Failed to end gaming session for {Game}", game.GameName);
        }
    }

    /// <summary>The first time a game is seen, the user decides whether it should be boosted automatically.</summary>
    private async Task<GameProfile> GetOrAskProfileAsync(RunningGame game)
    {
        var existing = await profiles.GetAsync(game.GameId).ConfigureAwait(false);
        if (existing is not null) return existing;

        var known = GameCatalog.FindById(game.GameId);
        var defaults = GameProfile.CreateDefault(game.GameId, game.GameName, known?.HasKernelAntiCheat ?? false, autoBoost: false);
        var accepted = await consent.ConfirmAsync(new ConsentRequest(
            $"{game.GameName} aniqlandi",
            "Gaming Booster bu o'yin ishga tushganda avtomatik yoqilsinmi? O'yin yopilganda barcha sozlamalar tiklanadi.",
            DescribeProfile(defaults),
            RiskLevel.Low)).ConfigureAwait(false);

        var profile = defaults with { AutoBoost = accepted };
        await profiles.SaveAsync(profile).ConfigureAwait(false);
        return profile;
    }

    public static IReadOnlyList<string> DescribeProfile(GameProfile p)
    {
        var items = new List<string>();
        if (p.UseHighPerformancePowerPlan) items.Add("Yuqori unumli Power Plan");
        if (p.SuppressNotifications) items.Add("Bildirishnomalarni vaqtincha o'chirish");
        items.Add("Windows Game Mode'ni yoqish");
        if (p.RaiseGamePriority) items.Add("O'yin jarayoniga High ustuvorlik");
        if (p.LowerBackgroundPriority) items.Add("Fon dasturlari ustuvorligini vaqtincha pasaytirish (yopilmaydi)");
        return items;
    }

    public async Task<GamingSession?> StartSessionAsync(RunningGame game, GameProfile profile, CancellationToken ct)
    {
        await _gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (ActiveSession is not null) return ActiveSession;

            var changes = await BuildChangesAsync(game, profile, ct).ConfigureAwait(false);
            var batch = ChangeBatch.Create(BatchKind.GamingSession, $"Gaming Booster — {game.GameName}");
            var outcomes = await executor.ApplyAsync(batch, changes, ct).ConfigureAwait(false);
            ActiveSession = new GamingSession(game, batch.Id, DateTimeOffset.UtcNow, outcomes);
            logger.LogInformation("Gaming session started for {Game}: {Count} changes", game.GameName, outcomes.Count(o => o.Success));
        }
        finally
        {
            _gate.Release();
        }
        SessionChanged?.Invoke(this, EventArgs.Empty);
        return ActiveSession;
    }

    public async Task<RevertReport?> EndSessionAsync(CancellationToken ct)
    {
        RevertReport? report = null;
        await _gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (ActiveSession is null) return null;
            report = await executor.RevertBatchAsync(ActiveSession.BatchId, ct).ConfigureAwait(false);
            logger.LogInformation("Gaming session for {Game} ended: {Reverted} restored", ActiveSession.Game.GameName, report.Reverted);
            ActiveSession = null;
        }
        finally
        {
            _gate.Release();
        }
        SessionChanged?.Invoke(this, EventArgs.Empty);
        return report;
    }

    internal async Task<IReadOnlyList<PlannedChange>> BuildChangesAsync(RunningGame game, GameProfile profile, CancellationToken ct)
    {
        var changes = new List<PlannedChange>();

        if (profile.UseHighPerformancePowerPlan)
        {
            var hw = await hardware.GetAsync(ct).ConfigureAwait(false);
            var plans = power.GetPlans();
            var target = PowerPlanAdvisor.RecommendForPerformance(plans, hw.IsLaptop, monitor.Sample().IsOnBattery);
            if (target is not null && !target.IsActive)
                changes.Add(new(ChangeKind.PowerPlan, PowerPlanChangeHandler.ActivePlanTarget, target.Id.ToString(),
                    $"Power Plan: {target.Name}", RiskLevel.Low));
        }

        if (profile.SuppressNotifications) changes.AddRange(TweakPlanner.Plan(TweakCatalog.NotificationsOff, registry));
        changes.AddRange(TweakPlanner.Plan(TweakCatalog.GameMode, registry));

        var all = processes.GetProcesses();
        var known = GameCatalog.FindById(game.GameId);
        if (profile.RaiseGamePriority && known?.HasKernelAntiCheat != true)
        {
            var gameProcess = all.FirstOrDefault(p => p.Pid == game.Pid);
            if (gameProcess is { Priority: < ProcessPriorityLevel.High })
                changes.Add(new(ChangeKind.ProcessPriority, ProcessPriorityChangeHandler.CreateTarget(gameProcess),
                    nameof(ProcessPriorityLevel.High), $"{game.GameName}: High ustuvorlik", RiskLevel.Low));
        }

        if (profile.LowerBackgroundPriority)
        {
            var background = all
                .Where(p => p.Pid != game.Pid && !p.IsProtected && ProcessSafetyPolicy.CanDeprioritizeDuringGame(p.Category))
                .Where(p => p.Priority is ProcessPriorityLevel.Normal or ProcessPriorityLevel.AboveNormal)
                .Where(p => p.CpuPercent >= 2 || p.WorkingSetBytes >= BackgroundMemoryThreshold)
                .OrderByDescending(p => p.CpuPercent)
                .Take(MaxBackgroundProcesses);
            changes.AddRange(background.Select(p => new PlannedChange(ChangeKind.ProcessPriority,
                ProcessPriorityChangeHandler.CreateTarget(p), nameof(ProcessPriorityLevel.BelowNormal),
                $"{p.Name}: vaqtincha BelowNormal", RiskLevel.Safe)));
        }

        return changes;
    }
}
