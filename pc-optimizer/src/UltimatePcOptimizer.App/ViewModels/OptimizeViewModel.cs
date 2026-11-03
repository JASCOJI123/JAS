using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>ONE CLICK OPTIMIZE: analyse → the user approves each change → restore point → apply.</summary>
public sealed partial class OptimizeViewModel(Notifier notifier, OneClickOptimizer optimizer, IConsentService consent, ISettingsStore settings)
    : PageViewModel(notifier)
{
    [ObservableProperty] private bool _hasPlan;
    [ObservableProperty] private bool _createRestorePoint = true;
    [ObservableProperty] private string? _resultText;

    public override string Key => "optimize";
    public override string Title => "One Click Optimize";
    public override string Icon => "";

    public ObservableCollection<StepItem> Steps { get; } = [];

    public override async Task OnNavigatedToAsync() =>
        CreateRestorePoint = (await settings.LoadAsync()).CreateRestorePointBeforeOptimization;

    [RelayCommand]
    private Task AnalyzeAsync() => RunAsync(async () =>
    {
        ResultText = null;
        var plan = await Task.Run(() => optimizer.AnalyzeAsync());
        Steps.Clear();
        foreach (var step in plan.Steps) Steps.Add(new StepItem(step));
        HasPlan = true;
        Status = plan.AllChanges.Any() ? null : "Hammasi optimal — o'zgartirish kerak emas.";
    }, "Tizim tahlil qilinmoqda…");

    [RelayCommand]
    private Task ApplyAsync() => RunAsync(async () =>
    {
        var approved = Steps.SelectMany(s => s.Changes).Where(c => c.IsSelected).Select(c => c.Change).ToList();
        if (approved.Count == 0)
        {
            Status = "Hech qaysi o'zgarish tanlanmagan.";
            return;
        }

        var confirmed = await consent.ConfirmAsync(new ConsentRequest(
            "Optimizatsiyani tasdiqlang",
            CreateRestorePoint ? "Avval Windows Restore Point yaratiladi. Quyidagi o'zgarishlar qo'llanadi:" : "Quyidagi o'zgarishlar qo'llanadi:",
            approved.Select(c => c.Description).ToList(),
            approved.Max(c => c.Risk)));
        if (!confirmed) return;

        var report = await Task.Run(() => optimizer.ApplyAsync(approved, CreateRestorePoint));
        ResultText = ReportText.Describe(report);
        Notifier.Show(ResultText, report.Failed > 0);
        HasPlan = false;
        Steps.Clear();
    }, "Optimizatsiya qo'llanmoqda…");

    [RelayCommand]
    private void SelectAll(string? value)
    {
        var select = value == "true";
        foreach (var change in Steps.SelectMany(s => s.Changes)) change.IsSelected = select;
    }
}
