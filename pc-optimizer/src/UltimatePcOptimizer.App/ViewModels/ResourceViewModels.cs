using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Monitoring;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>Shared live-monitoring plumbing for the RAM, CPU and GPU pages.</summary>
public abstract partial class LiveResourceViewModel : PageViewModel
{
    private readonly MonitoringService _monitoring;
    private bool _active;

    [ObservableProperty] private SystemSnapshot _snapshot = SystemSnapshot.Empty;
    [ObservableProperty] private IReadOnlyList<double> _history = [];

    protected LiveResourceViewModel(Notifier notifier, MonitoringService monitoring) : base(notifier)
    {
        _monitoring = monitoring;
        _monitoring.SnapshotAvailable += (_, s) =>
        {
            if (_active) Ui.Post(() => OnSnapshot(s));
        };
    }

    public override string Group => "Resurslar";

    protected abstract double Select(SystemSnapshot snapshot);

    protected virtual void OnSnapshot(SystemSnapshot snapshot)
    {
        Snapshot = snapshot;
        History = _monitoring.History.Select(Select).ToList();
    }

    public override Task OnNavigatedToAsync()
    {
        _active = true;
        OnSnapshot(_monitoring.Latest);
        return Task.CompletedTask;
    }

    public override void OnNavigatedFrom() => _active = false;
}

public sealed partial class RamViewModel(Notifier notifier, MonitoringService monitoring, IProcessService processes, IPageFileProvider pageFile)
    : LiveResourceViewModel(notifier, monitoring)
{
    private PageFileStatus? _pageFile;

    [ObservableProperty] private MemoryAssessment? _assessment;
    [ObservableProperty] private string _pageFileText = "—";

    public override string Key => "ram";
    public override string Title => "RAM Optimizer";
    public override string Icon => "";

    public ObservableCollection<ProcessInfo> TopProcesses { get; } = [];

    protected override double Select(SystemSnapshot s) => s.Memory.UsedPercent;

    protected override void OnSnapshot(SystemSnapshot snapshot)
    {
        base.OnSnapshot(snapshot);
        Assessment = MemoryAnalyzer.Assess(snapshot.Memory, _pageFile);
    }

    public override async Task OnNavigatedToAsync()
    {
        await base.OnNavigatedToAsync();
        await RefreshAsync();
    }

    [RelayCommand]
    private Task RefreshAsync() => RunAsync(async () =>
    {
        _pageFile = await Task.Run(pageFile.GetStatus);
        PageFileText = _pageFile switch
        {
            null => "Ma'lumot yo'q",
            { Exists: false } => "Pagefile o'chirilgan",
            var p => $"{Core.ByteFormatter.Format(p.AllocatedBytes)} ajratilgan · {Core.ByteFormatter.Format(p.CurrentUsageBytes)} ishlatilmoqda · " +
                     (p.AutomaticallyManaged ? "Avtomatik boshqariladi" : "Qo'lda sozlangan"),
        };
        var list = await Task.Run(processes.GetProcesses);
        TopProcesses.Clear();
        foreach (var p in list.OrderByDescending(p => p.WorkingSetBytes).Take(15)) TopProcesses.Add(p);
        OnSnapshot(Snapshot);
    }, "RAM tahlil qilinmoqda…");
}

public sealed partial class CpuViewModel(
    Notifier notifier,
    MonitoringService monitoring,
    HardwareProfileCache hardware,
    IProcessService processes,
    IPowerPlanService power,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : LiveResourceViewModel(notifier, monitoring)
{
    private CpuInfo? _cpu;

    [ObservableProperty] private string _cpuName = "—";
    [ObservableProperty] private string _coresText = "—";
    [ObservableProperty] private ThrottleAssessment? _throttle;
    [ObservableProperty] private PowerPlan? _selectedPlan;

    public override string Key => "cpu";
    public override string Title => "CPU Optimizer";
    public override string Icon => "";

    public ObservableCollection<PowerPlan> Plans { get; } = [];
    public ObservableCollection<ProcessInfo> TopProcesses { get; } = [];

    protected override double Select(SystemSnapshot s) => s.CpuLoadPercent;

    protected override void OnSnapshot(SystemSnapshot snapshot)
    {
        base.OnSnapshot(snapshot);
        if (_cpu is not null)
            Throttle = ThrottlingAnalyzer.AnalyzeCpu(snapshot.CpuTemperatureC, snapshot.CpuClockMhz, _cpu.MaxClockMhz, snapshot.CpuLoadPercent, snapshot.IsOnBattery);
    }

    public override async Task OnNavigatedToAsync()
    {
        await base.OnNavigatedToAsync();
        await RefreshAsync();
    }

    [RelayCommand]
    private Task RefreshAsync() => RunAsync(LoadCpuAsync, "CPU tahlil qilinmoqda…");

    private async Task LoadCpuAsync()
    {
        _cpu = (await hardware.GetAsync()).Cpu;
        CpuName = _cpu.Name;
        CoresText = $"{_cpu.Cores} yadro · {_cpu.Threads} oqim · max {_cpu.MaxClockMhz} MHz";
        var plans = await Task.Run(power.GetPlans);
        Plans.Clear();
        foreach (var p in plans) Plans.Add(p);
        SelectedPlan = Plans.FirstOrDefault(p => p.IsActive);

        await Task.Run(processes.GetProcesses);
        await Task.Delay(800);
        var list = await Task.Run(processes.GetProcesses);
        TopProcesses.Clear();
        foreach (var p in list.OrderByDescending(p => p.CpuPercent).Take(12)) TopProcesses.Add(p);
    }

    [RelayCommand]
    private Task ApplyPlanAsync() => RunAsync(async () =>
    {
        if (SelectedPlan is null || SelectedPlan.IsActive) return;
        var change = new PlannedChange(ChangeKind.PowerPlan, PowerPlanChangeHandler.ActivePlanTarget, SelectedPlan.Id.ToString(),
            $"Power Plan: {SelectedPlan.Name}", RiskLevel.Low);
        if (!await consent.ConfirmAsync(new ConsentRequest("Power Plan'ni o'zgartirish", $"Faol reja '{SelectedPlan.Name}' ga o'zgartiriladi.", [], RiskLevel.Low)))
            return;
        var report = await OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.Manual, $"Power Plan — {SelectedPlan.Name}"),
            [change], createRestorePoint: false, consent, executor, restorePoints, CancellationToken.None);
        Notifier.Show(ReportText.Describe(report), report.Failed > 0);
        await LoadCpuAsync();
    });
}

public sealed partial class GpuViewModel(Notifier notifier, MonitoringService monitoring, HardwareProfileCache hardware, MainNavigation navigation)
    : LiveResourceViewModel(notifier, monitoring)
{
    [ObservableProperty] private ThrottleAssessment? _throttle;
    [ObservableProperty] private GpuInfo? _primary;

    public override string Key => "gpu";
    public override string Title => "GPU Optimizer";
    public override string Icon => "";

    public ObservableCollection<GpuInfo> Gpus { get; } = [];

    protected override double Select(SystemSnapshot s) => s.GpuLoadPercent ?? 0;

    protected override void OnSnapshot(SystemSnapshot snapshot)
    {
        base.OnSnapshot(snapshot);
        Throttle = ThrottlingAnalyzer.AnalyzeGpu(snapshot.GpuTemperatureC, snapshot.GpuLoadPercent);
    }

    public override async Task OnNavigatedToAsync()
    {
        await base.OnNavigatedToAsync();
        var hw = await hardware.GetAsync();
        Primary = hw.PrimaryGpu;
        Gpus.Clear();
        foreach (var g in hw.Gpus) Gpus.Add(g);
    }

    [RelayCommand]
    private void OpenProfiles() => navigation.NavigateTo("fps");
}
