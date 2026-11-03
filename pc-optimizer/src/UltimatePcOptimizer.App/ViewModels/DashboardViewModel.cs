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

public sealed partial class DashboardViewModel : PageViewModel
{
    private readonly MonitoringService _monitoring;
    private readonly HardwareProfileCache _hardware;
    private readonly IBootTimeProvider _bootTime;
    private readonly IStartupService _startup;
    private readonly CleanupStep _cleanup;
    private readonly IRegistryAccess _registry;
    private readonly IGpuSettingsService _gpu;
    private readonly IPowerPlanService _power;
    private HardwareProfile? _profile;

    [ObservableProperty] private SystemSnapshot _snapshot = SystemSnapshot.Empty;
    [ObservableProperty] private IReadOnlyList<double> _cpuHistory = [];
    [ObservableProperty] private IReadOnlyList<double> _gpuHistory = [];
    [ObservableProperty] private IReadOnlyList<double> _ramHistory = [];
    [ObservableProperty] private HealthScore? _health;
    [ObservableProperty] private string _bootTimeText = "—";
    [ObservableProperty] private string _cpuName = "—";
    [ObservableProperty] private string _gpuName = "—";
    [ObservableProperty] private string _performanceState = "Tahlil qilinmoqda…";

    public DashboardViewModel(Notifier notifier, MonitoringService monitoring, HardwareProfileCache hardware, IBootTimeProvider bootTime,
        IStartupService startup, CleanupStep cleanup, IRegistryAccess registry, IGpuSettingsService gpu, IPowerPlanService power,
        MainNavigation navigation) : base(notifier)
    {
        _monitoring = monitoring;
        _hardware = hardware;
        _bootTime = bootTime;
        _startup = startup;
        _cleanup = cleanup;
        _registry = registry;
        _gpu = gpu;
        _power = power;
        Navigation = navigation;
        _monitoring.SnapshotAvailable += (_, s) => Ui.Post(() => Update(s));
    }

    public override string Key => "dashboard";
    public override string Title => "Dashboard";
    public override string Icon => "";

    public MainNavigation Navigation { get; }

    public ObservableCollection<Recommendation> Recommendations { get; } = [];

    public string UptimeText => $"{(int)Snapshot.Uptime.TotalDays} kun {Snapshot.Uptime.Hours} soat {Snapshot.Uptime.Minutes} daq";

    private void Update(SystemSnapshot snapshot)
    {
        Snapshot = snapshot;
        var history = _monitoring.History;
        CpuHistory = history.Select(s => s.CpuLoadPercent).ToList();
        GpuHistory = history.Select(s => s.GpuLoadPercent ?? 0).ToList();
        RamHistory = history.Select(s => s.Memory.UsedPercent).ToList();
        OnPropertyChanged(nameof(UptimeText));
        PerformanceState = DescribeState(snapshot);
    }

    private static string DescribeState(SystemSnapshot s)
    {
        if (s.Memory.UsedPercent >= 90 || s.CpuLoadPercent >= 95) return "Yuqori yuklanish";
        if (s.CpuTemperatureC >= ThrottlingAnalyzer.CpuWarningTemperatureC || s.GpuTemperatureC >= ThrottlingAnalyzer.GpuWarningTemperatureC) return "Harorat yuqori";
        if (s.CpuLoadPercent >= 70) return "Faol ishlamoqda";
        return "Barqaror";
    }

    public override async Task OnNavigatedToAsync()
    {
        if (_profile is not null) return;
        await RefreshAsync();
    }

    [RelayCommand]
    private Task RefreshAsync() => RunAsync(async () =>
    {
        _profile = await _hardware.GetAsync();
        CpuName = _profile.Cpu.Name;
        GpuName = _profile.PrimaryGpu?.Name ?? "—";

        var boot = await _bootTime.GetLastBootDurationAsync();
        BootTimeText = boot is { } b ? $"{b.TotalSeconds:0.0} s" : "Mavjud emas";

        var snapshot = _monitoring.Latest;
        var heavyStartup = await Task.Run(() => _startup.GetEntries().Count(e => e.IsEnabled && !e.IsProtected && e.Impact == StartupImpact.High));
        var junk = (await _cleanup.ScanAsync(CancellationToken.None)).Aggregate(0UL, (s, r) => s + r.SizeBytes);
        var gameMode = TweakPlanner.IsApplied(TweakCatalog.GameMode, _registry);
        var systemDrive = new DriveInfo(Path.GetPathRoot(Environment.SystemDirectory)!);
        var freePercent = systemDrive.TotalSize == 0 ? 100 : systemDrive.AvailableFreeSpace * 100.0 / systemDrive.TotalSize;

        var cpuThrottle = ThrottlingAnalyzer.AnalyzeCpu(snapshot.CpuTemperatureC, snapshot.CpuClockMhz, _profile.Cpu.MaxClockMhz, snapshot.CpuLoadPercent, snapshot.IsOnBattery);
        var gpuThrottle = ThrottlingAnalyzer.AnalyzeGpu(snapshot.GpuTemperatureC, snapshot.GpuLoadPercent);
        Health = HealthScoreCalculator.Calculate(new HealthInputs(snapshot, heavyStartup, junk, gameMode,
            _profile.SystemDisk?.MediaType == DiskMediaType.Hdd, freePercent, cpuThrottle.State, gpuThrottle.State));

        var active = await Task.Run(_power.GetActivePlan);
        Recommendations.Clear();
        foreach (var r in RecommendationEngine.Build(new RecommendationContext(_profile, _gpu.GetHardwareAcceleratedScheduling(), gameMode,
                     active, snapshot.IsOnBattery, DateTime.Today)).OrderByDescending(r => r.Severity).Take(4))
            Recommendations.Add(r);
    }, "Tizim tahlil qilinmoqda…");

    [RelayCommand]
    private void Open(string? key)
    {
        if (key is not null) Navigation.NavigateTo(key);
    }
}
