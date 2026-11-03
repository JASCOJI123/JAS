using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Monitoring;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>Auto PC Detection, configuration-based recommendations and driver information.</summary>
public sealed partial class SystemInfoViewModel(
    Notifier notifier,
    HardwareProfileCache hardware,
    IDriverInfoService drivers,
    IGpuSettingsService gpu,
    IRegistryAccess registry,
    IPowerPlanService power,
    MonitoringService monitoring,
    MainNavigation navigation) : PageViewModel(notifier)
{
    [ObservableProperty] private HardwareProfile? _profile;

    public override string Key => "system";
    public override string Title => "Auto PC Detection";
    public override string Icon => "";
    public override string Group => "Tizim";

    public ObservableCollection<Recommendation> Recommendations { get; } = [];
    public ObservableCollection<DriverInfo> Drivers { get; } = [];

    public override Task OnNavigatedToAsync() => Profile is null ? DetectAsync() : Task.CompletedTask;

    [RelayCommand]
    private Task DetectAsync() => RunAsync(async () =>
    {
        hardware.Invalidate();
        Profile = await hardware.GetAsync();
        var active = await Task.Run(power.GetActivePlan);
        Recommendations.Clear();
        foreach (var r in RecommendationEngine.Build(new RecommendationContext(Profile, gpu.GetHardwareAcceleratedScheduling(),
                     TweakPlanner.IsApplied(TweakCatalog.GameMode, registry), active, monitoring.Latest.IsOnBattery, DateTime.Today)))
            Recommendations.Add(r);

        Status = "Drayverlar o'qilmoqda…";
        Drivers.Clear();
        foreach (var d in await drivers.GetDriversAsync()) Drivers.Add(d);
        Status = null;
    }, "Kompyuter konfiguratsiyasi aniqlanmoqda…");

    [RelayCommand]
    private void Open(string? key)
    {
        if (key is not null) navigation.NavigateTo(key);
    }
}
