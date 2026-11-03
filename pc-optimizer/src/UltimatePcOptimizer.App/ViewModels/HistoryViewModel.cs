using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>System Restore, Optimization History and Undo Optimization.</summary>
public sealed partial class HistoryViewModel(
    Notifier notifier,
    IChangeJournal journal,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : PageViewModel(notifier)
{
    [ObservableProperty] private ChangeBatch? _selectedBatch;

    public override string Key => "history";
    public override string Title => "Restore & History";
    public override string Icon => "";
    public override string Group => "Tizim";

    public ObservableCollection<ChangeBatch> Batches { get; } = [];
    public ObservableCollection<ChangeRecord> Records { get; } = [];

    public override Task OnNavigatedToAsync() => RunAsync(LoadAsync);

    private async Task LoadAsync()
    {
        var selectedId = SelectedBatch?.Id;
        Batches.Clear();
        foreach (var b in await journal.GetBatchesAsync(200)) Batches.Add(b);
        SelectedBatch = Batches.FirstOrDefault(b => b.Id == selectedId) ?? Batches.FirstOrDefault();
    }

    partial void OnSelectedBatchChanged(ChangeBatch? value) => _ = LoadRecordsAsync(value);

    private async Task LoadRecordsAsync(ChangeBatch? batch)
    {
        Records.Clear();
        if (batch is null) return;
        foreach (var r in await journal.GetRecordsAsync(batch.Id)) Records.Add(r);
    }

    [RelayCommand]
    private Task UndoAsync(ChangeBatch? batch) => RunAsync(async () =>
    {
        batch ??= SelectedBatch;
        if (batch is null || batch.RevertedAt is not null) return;
        var records = await journal.GetRecordsAsync(batch.Id);
        var reversible = records.Where(r => r.IsReversible && r.Status != ChangeStatus.Reverted).Select(r => r.Description).ToList();
        if (reversible.Count == 0)
        {
            Status = "Bu guruhda qaytariladigan o'zgarish yo'q (o'chirilgan fayllarni qaytarib bo'lmaydi).";
            return;
        }
        if (!await consent.ConfirmAsync(new ConsentRequest($"Undo: {batch.Title}", "Quyidagi sozlamalar avvalgi holatiga qaytariladi:", reversible, RiskLevel.Safe)))
            return;

        var report = await Task.Run(() => executor.RevertBatchAsync(batch.Id));
        Notifier.Show(report.Failed == 0 ? $"{report.Reverted} ta sozlama tiklandi" : $"{report.Failed} ta sozlamani tiklab bo'lmadi: {string.Join("; ", report.Messages)}",
            report.Failed > 0);
        await LoadAsync();
    }, "Tiklanmoqda…");

    [RelayCommand]
    private Task CreateRestorePointAsync() => RunAsync(async () =>
    {
        var result = await restorePoints.CreateAsync("Ultimate PC Optimizer — qo'lda");
        Notifier.Show(result.Message, !result.Success);
    }, "Restore Point yaratilmoqda…");

    [RelayCommand]
    private void OpenSystemRestore() => restorePoints.OpenSystemRestore();
}
