using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed partial class StartupViewModel(
    Notifier notifier,
    IStartupService startup,
    IBootTimeProvider bootTime,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : PageViewModel(notifier)
{
    private string _bootText = "—";

    public override string Key => "startup";
    public override string Title => "Startup Manager";
    public override string Icon => "";
    public override string Group => "Tizim";

    public ObservableCollection<StartupEntry> Entries { get; } = [];

    public string BootText { get => _bootText; private set => SetProperty(ref _bootText, value); }

    public string SummaryText => $"{Entries.Count(e => e.IsEnabled)} ta yoqilgan · {Entries.Count(e => e.IsEnabled && e.Impact == StartupImpact.High)} ta og'ir";

    public override Task OnNavigatedToAsync() => RefreshAsync();

    [RelayCommand]
    private Task RefreshAsync() => RunAsync(LoadAsync, "Startup dasturlar o'qilmoqda…");

    private async Task LoadAsync()
    {
        var entries = await Task.Run(startup.GetEntries);
        Entries.Clear();
        foreach (var e in entries.OrderByDescending(e => e.IsEnabled).ThenByDescending(e => e.Impact)) Entries.Add(e);
        OnPropertyChanged(nameof(SummaryText));
        var boot = await bootTime.GetLastBootDurationAsync();
        BootText = boot is { } b ? $"Oxirgi yuklanish: {b.TotalSeconds:0.0} s" : "Yuklanish vaqti mavjud emas";
    }

    [RelayCommand]
    private Task ToggleAsync(StartupEntry? entry) => RunAsync(async () =>
    {
        if (entry is null) return;
        var enable = !entry.IsEnabled;
        if (!enable && entry.IsProtected)
        {
            Status = $"{entry.Name} Windows uchun muhim ({entry.ProtectionReason}) — o'chirib bo'lmaydi.";
            return;
        }
        var verb = enable ? "yoqish" : "o'chirish";
        if (!await consent.ConfirmAsync(new ConsentRequest($"Startup: {entry.Name}", $"{entry.Name} dasturini Windows bilan ishga tushishdan {verb}?",
                [entry.Command], RiskLevel.Low))) return;

        var change = new PlannedChange(ChangeKind.StartupEntry, entry.Id, enable.ToString(), $"Startup {verb}: {entry.Name}", RiskLevel.Low);
        var report = await OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.Manual, $"Startup — {entry.Name}"), [change],
            createRestorePoint: false, consent, executor, restorePoints, CancellationToken.None);
        Notifier.Show(ReportText.Describe(report), report.Failed > 0);
        await LoadAsync();
    });
}
