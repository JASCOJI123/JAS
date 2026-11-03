using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed partial class CleanupItem(CleanupCategory category) : ObservableObject
{
    [ObservableProperty] private bool _isSelected = CleanupCategoryInfo.OneClickDefaults.Contains(category);
    [ObservableProperty] private ulong _sizeBytes;
    [ObservableProperty] private int _fileCount;
    [ObservableProperty] private string? _note = CleanupCategoryInfo.Advice(category);
    [ObservableProperty] private bool _sizeIsEstimate;

    public CleanupCategory Category { get; } = category;
    public string Title => CleanupCategoryInfo.Title(Category);
    public bool RequiresExplicitConsent => CleanupCategoryInfo.RequiresExplicitConsent(Category);
}

public sealed partial class CleanerViewModel(
    Notifier notifier,
    ICleanupService cleanup,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : PageViewModel(notifier)
{
    [ObservableProperty] private string _totalText = "—";

    public override string Key => "cleaner";
    public override string Title => "Windows Cleaner";
    public override string Icon => "";
    public override string Group => "Tizim";

    public ObservableCollection<CleanupItem> Items { get; } =
        new(Enum.GetValues<CleanupCategory>().Select(c => new CleanupItem(c)));

    public override Task OnNavigatedToAsync() => Items.All(i => i.SizeBytes == 0) ? ScanAsync() : Task.CompletedTask;

    [RelayCommand]
    private Task ScanAsync() => RunAsync(ScanCoreAsync, "Skanerlanmoqda…");

    private async Task ScanCoreAsync()
    {
        foreach (var item in Items)
        {
            Status = $"Skanerlanmoqda: {item.Title}";
            var result = await cleanup.ScanAsync(item.Category);
            item.SizeBytes = result.SizeBytes;
            item.FileCount = result.FileCount;
            item.SizeIsEstimate = result.SizeIsEstimate;
            item.Note = result.Note ?? CleanupCategoryInfo.Advice(item.Category);
        }
        TotalText = ByteFormatter.Format(Items.Aggregate(0UL, (s, i) => s + i.SizeBytes));
        Status = null;
    }

    [RelayCommand]
    private Task CleanAsync() => RunAsync(async () =>
    {
        var selected = Items.Where(i => i.IsSelected).ToList();
        if (selected.Count == 0) return;

        var details = selected.Select(i => $"{i.Title} — {ByteFormatter.Format(i.SizeBytes)}{(i.SizeIsEstimate ? " (taxminiy)" : string.Empty)}").ToList();
        var risk = selected.Any(i => i.RequiresExplicitConsent) ? RiskLevel.Moderate : RiskLevel.Safe;
        if (!await consent.ConfirmAsync(new ConsentRequest("Tozalashni tasdiqlang",
                "O'chirilgan fayllarni qaytarib bo'lmaydi. Ishlatilayotgan fayllar avtomatik o'tkazib yuboriladi.", details, risk)))
            return;

        var changes = selected.Select(i => new PlannedChange(ChangeKind.FileCleanup, i.Category.ToString(), null, i.Title, RiskLevel.Safe)).ToList();
        var report = await OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.Manual, "Windows Cleaner"), changes,
            createRestorePoint: false, consent, executor, restorePoints, CancellationToken.None);
        var freed = string.Join("; ", report.Outcomes.Where(o => o.Success && o.Message is not null).Select(o => $"{o.Change.Description}: {o.Message}"));
        Notifier.Show(report.Failed > 0 ? ReportText.Describe(report) : "Tozalash yakunlandi");
        await ScanCoreAsync();
        Status = freed;
    }, "Tozalanmoqda…");
}
