using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>Background Process Manager. Nothing is closed without an explicit user action and confirmation.</summary>
public sealed partial class ProcessesViewModel(
    Notifier notifier,
    IProcessService processes,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : PageViewModel(notifier)
{
    private IReadOnlyList<ProcessInfo> _all = [];

    [ObservableProperty] private string _filter = string.Empty;
    [ObservableProperty] private bool _hideSystem = true;
    [ObservableProperty] private ProcessInfo? _selected;
    [ObservableProperty] private ProcessPriorityLevel _selectedPriority = ProcessPriorityLevel.BelowNormal;

    public override string Key => "processes";
    public override string Title => "Background Processes";
    public override string Icon => "";
    public override string Group => "Resurslar";

    public ObservableCollection<ProcessInfo> Processes { get; } = [];
    public IReadOnlyList<ProcessPriorityLevel> Priorities { get; } = Enum.GetValues<ProcessPriorityLevel>();

    public override Task OnNavigatedToAsync() => RefreshAsync();

    partial void OnFilterChanged(string value) => ApplyFilter();
    partial void OnHideSystemChanged(bool value) => ApplyFilter();

    [RelayCommand]
    private Task RefreshAsync() => RunAsync(LoadAsync, "Jarayonlar o'qilmoqda…");

    private async Task LoadAsync()
    {
        await Task.Run(processes.GetProcesses);
        await Task.Delay(700);
        _all = await Task.Run(processes.GetProcesses);
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        Processes.Clear();
        foreach (var p in _all
                     .Where(p => !HideSystem || !p.IsProtected)
                     .Where(p => Filter.Length == 0 || p.Name.Contains(Filter, StringComparison.OrdinalIgnoreCase))
                     .OrderByDescending(p => p.CpuPercent).ThenByDescending(p => p.WorkingSetBytes))
            Processes.Add(p);
    }

    [RelayCommand]
    private Task SetPriorityAsync() => RunAsync(async () =>
    {
        if (Selected is not { IsProtected: false } p)
        {
            Status = "Tizim, xavfsizlik va anti-cheat jarayonlarini o'zgartirib bo'lmaydi.";
            return;
        }
        var change = new PlannedChange(ChangeKind.ProcessPriority, ProcessPriorityChangeHandler.CreateTarget(p), SelectedPriority.ToString(),
            $"{p.Name}: {SelectedPriority} ustuvorlik", RiskLevel.Low);
        if (!await consent.ConfirmAsync(new ConsentRequest("Ustuvorlikni o'zgartirish", $"{p.Name} ustuvorligi {SelectedPriority} ga o'zgartiriladi (jarayon yopilguncha amal qiladi).", [], RiskLevel.Low)))
            return;
        var report = await OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.Manual, $"Ustuvorlik — {p.Name}"), [change],
            createRestorePoint: false, consent, executor, restorePoints, CancellationToken.None);
        Notifier.Show(ReportText.Describe(report), report.Failed > 0);
        await LoadAsync();
    });

    [RelayCommand]
    private Task EndProcessAsync() => RunAsync(async () =>
    {
        if (Selected is not { } p) return;
        if (p.IsProtected)
        {
            Status = "Bu jarayon himoyalangan.";
            return;
        }
        if (!await consent.ConfirmAsync(new ConsentRequest($"{p.Name} jarayonini yopish",
                "Saqlanmagan ma'lumotlar yo'qolishi mumkin. Davom etilsinmi?", [p.ExecutablePath ?? p.Name], RiskLevel.Moderate)))
            return;
        if (!processes.TryTerminate(p.Pid, out var error)) Notifier.Show(error ?? "Jarayonni yopib bo'lmadi", isError: true);
        await LoadAsync();
    });
}
