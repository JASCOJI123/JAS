using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>FPS Booster: GPU checks, HAGS/Reflex status, graphics profiles and FPS-affecting background load.</summary>
public sealed partial class FpsBoosterViewModel(
    Notifier notifier,
    HardwareProfileCache hardware,
    IGpuSettingsService gpuSettings,
    IProcessService processes,
    IRegistryAccess registry,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : PageViewModel(notifier)
{
    [ObservableProperty] private string _gpuName = "—";
    [ObservableProperty] private string _gpuVendor = "—";
    [ObservableProperty] private string _driverVersion = "—";
    [ObservableProperty] private string _hagsText = "—";
    [ObservableProperty] private string _reflexText = "—";
    [ObservableProperty] private string _gameModeText = "—";
    [ObservableProperty] private string _gameDvrText = "—";
    [ObservableProperty] private KnownGame? _selectedGame;
    [ObservableProperty] private GraphicsProfile? _profile;

    public override string Key => "fps";
    public override string Title => "FPS Booster";
    public override string Icon => "";
    public override string Group => "Gaming";

    public IReadOnlyList<KnownGame> Games => GameCatalog.Games;
    public ObservableCollection<ProcessInfo> FpsImpactProcesses { get; } = [];

    public override Task OnNavigatedToAsync() => RefreshAsync();

    [RelayCommand]
    private Task RefreshAsync() => RunAsync(LoadAsync, "GPU va tizim tekshirilmoqda…");

    private async Task LoadAsync()
    {
        var hw = await hardware.GetAsync();
        var gpu = hw.PrimaryGpu;
        GpuName = gpu?.Name ?? "Aniqlanmadi";
        GpuVendor = gpu?.Vendor.ToString() ?? "—";
        DriverVersion = gpu is null ? "—" : $"{gpu.DriverVersion} ({gpu.DriverDate:yyyy-MM-dd})";
        HagsText = gpuSettings.GetHardwareAcceleratedScheduling() switch
        {
            true => "Yoqilgan",
            false => "O'chiq — Settings → Graphics'da sinab ko'rish mumkin",
            null => "Qo'llab-quvvatlanmaydi / noma'lum",
        };
        ReflexText = gpu?.Vendor == Core.Models.GpuVendor.Nvidia
            ? "NVIDIA GPU — Reflex qo'llab-quvvatlaydigan o'yinlarda (CS2, Valorant, Fortnite, CoD…) o'yin sozlamalarida yoqing"
            : "NVIDIA Reflex faqat NVIDIA GPU'larda. AMD uchun Radeon Anti-Lag tavsiya etiladi.";
        GameModeText = TweakPlanner.IsApplied(TweakCatalog.GameMode, registry) ? "Yoqilgan" : "O'chiq";
        GameDvrText = TweakPlanner.IsApplied(TweakCatalog.GameDvrOff, registry) ? "O'chiq (yaxshi)" : "Fonda yozib bormoqda";

        // Two samples are needed for CPU usage.
        await Task.Run(processes.GetProcesses);
        await Task.Delay(800);
        var list = await Task.Run(processes.GetProcesses);
        FpsImpactProcesses.Clear();
        foreach (var p in list.Where(p => !p.IsProtected && p.Category != ProcessCategory.Game && p.CpuPercent >= 1)
                     .OrderByDescending(p => p.CpuPercent).Take(8))
            FpsImpactProcesses.Add(p);

        SelectedGame ??= Games.FirstOrDefault();
        BuildProfile(hw);
    }

    partial void OnSelectedGameChanged(KnownGame? value) => _ = RebuildAsync();

    private async Task RebuildAsync() => BuildProfile(await hardware.GetAsync());

    private void BuildProfile(HardwareProfile hw) =>
        Profile = SelectedGame is null ? null : GraphicsProfileAdvisor.Build(hw, SelectedGame, SelectedGame.DisplayName);

    [RelayCommand]
    private void OpenGraphicsSettings() => gpuSettings.OpenGraphicsSettings();

    [RelayCommand]
    private Task OptimizeWindowsGraphicsAsync() => RunAsync(async () =>
    {
        var changes = TweakPlanner.Plan(TweakCatalog.GameMode, registry).Concat(TweakPlanner.Plan(TweakCatalog.GameDvrOff, registry)).ToList();
        if (changes.Count == 0)
        {
            Status = "Windows grafik sozlamalari allaqachon optimal.";
            return;
        }
        if (!await consent.ConfirmAsync(new ConsentRequest("Windows grafik sozlamalari", "Quyidagilar o'zgartiriladi:",
                changes.Select(c => c.Description).ToList(), RiskLevel.Low))) return;

        var report = await OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.Manual, "FPS Booster — Windows grafik"),
            changes, createRestorePoint: false, consent, executor, restorePoints, CancellationToken.None);
        Notifier.Show(ReportText.Describe(report), report.Failed > 0);
        await LoadAsync();
    });
}
