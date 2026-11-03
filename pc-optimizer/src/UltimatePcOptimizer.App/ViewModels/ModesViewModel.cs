using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed partial class TweakItem(TweakDefinition tweak, bool applied) : ObservableObject
{
    [ObservableProperty] private bool _isSelected = applied;

    public TweakDefinition Tweak { get; } = tweak;
    public bool IsApplied { get; } = applied;
}

public sealed partial class ModesViewModel(
    Notifier notifier,
    ModeService modes,
    IRegistryAccess registry,
    ISettingsStore settings,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : PageViewModel(notifier)
{
    [ObservableProperty] private string _activeMode = "—";
    [ObservableProperty] private PowerPlanPreference _customPlan = PowerPlanPreference.Balanced;

    public override string Key => "modes";
    public override string Title => "Optimization Modes";
    public override string Icon => "";

    public IReadOnlyList<ModeDefinition> Modes => ModeCatalog.Modes.Where(m => m.Mode != OptimizationMode.Custom).ToList();
    public IReadOnlyList<PowerPlanPreference> PlanOptions { get; } = Enum.GetValues<PowerPlanPreference>();
    public ObservableCollection<TweakItem> Tweaks { get; } = [];

    public override async Task OnNavigatedToAsync()
    {
        ActiveMode = (await settings.LoadAsync()).ActiveMode;
        Tweaks.Clear();
        foreach (var t in TweakCatalog.All.Where(t => t.Id != TweakCatalog.NotificationsOff.Id))
            Tweaks.Add(new TweakItem(t, TweakPlanner.IsApplied(t, registry)));
    }

    [RelayCommand]
    private Task ApplyModeAsync(ModeDefinition? mode) => RunAsync(async () =>
    {
        if (mode is null) return;
        await ApplyAsync(mode, modes.Plan(mode));
    });

    [RelayCommand]
    private Task ApplyCustomAsync() => RunAsync(async () =>
    {
        var custom = ModeCatalog.Get(OptimizationMode.Custom);
        var selected = Tweaks.Where(t => t.IsSelected && !t.IsApplied).Select(t => t.Tweak.Id).ToList();
        await ApplyAsync(custom, modes.Plan(custom, CustomPlan, selected));
    });

    private async Task ApplyAsync(ModeDefinition mode, IReadOnlyList<PlannedChange> changes)
    {
        if (changes.Count == 0)
        {
            Status = $"{mode.Title}: tizim allaqachon shu holatda.";
            await SaveActiveAsync(mode);
            return;
        }
        if (!await consent.ConfirmAsync(new ConsentRequest(mode.Title, mode.Description, changes.Select(c => c.Description).ToList(), changes.Max(c => c.Risk))))
            return;
        var stored = await settings.LoadAsync();
        var report = await Task.Run(() => OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.Mode, mode.Title), changes,
            stored.CreateRestorePointBeforeOptimization, consent, executor, restorePoints, CancellationToken.None));
        if (!report.Cancelled) await SaveActiveAsync(mode);
        Notifier.Show(ReportText.Describe(report), report.Failed > 0);
        await OnNavigatedToAsync();
    }

    private async Task SaveActiveAsync(ModeDefinition mode)
    {
        var stored = await settings.LoadAsync();
        await settings.SaveAsync(stored with { ActiveMode = mode.Title });
        ActiveMode = mode.Title;
    }
}
