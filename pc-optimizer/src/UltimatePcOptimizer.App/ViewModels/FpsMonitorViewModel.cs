using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Gaming;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Monitoring;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed record CaptureTarget(int Pid, string Name, bool IsGame)
{
    public override string ToString() => IsGame ? $"🎮 {Name} ({Pid})" : $"{Name} ({Pid})";
}

/// <summary>FPS Monitoring + benchmark (before/after optimization comparison).</summary>
public sealed partial class FpsMonitorViewModel : PageViewModel
{
    private const int FrameHistoryLength = 240;
    private readonly IFrameTimeSource _frames;
    private readonly IProcessService _processes;
    private readonly GameDetectionService _detection;
    private readonly IBenchmarkStore _benchmarks;
    private readonly OverlayService _overlay;
    private readonly FrameStatsCalculator _calculator = new();
    private readonly Queue<double> _frameTimes = new();
    private readonly Lock _lock = new();
    private CancellationTokenSource? _capture;
    private BenchmarkRecorder? _recorder;
    private long _lastUiUpdate;

    [ObservableProperty] private CaptureTarget? _selectedTarget;
    [ObservableProperty] private bool _isCapturing;
    [ObservableProperty] private FrameStats _stats = FrameStats.Empty;
    [ObservableProperty] private SystemSnapshot _snapshot = SystemSnapshot.Empty;
    [ObservableProperty] private IReadOnlyList<double> _frameTimeHistory = [];
    [ObservableProperty] private bool _isOverlayVisible;
    [ObservableProperty] private bool _isBenchmarkRunning;
    [ObservableProperty] private string _benchmarkLabel = "Optimizatsiyadan oldin";
    [ObservableProperty] private int _benchmarkSeconds = 60;
    [ObservableProperty] private double _benchmarkProgress;
    [ObservableProperty] private BenchmarkResult? _compareBefore;
    [ObservableProperty] private BenchmarkResult? _compareAfter;
    [ObservableProperty] private BenchmarkComparison? _comparison;

    public FpsMonitorViewModel(Notifier notifier, IFrameTimeSource frames, IProcessService processes, GameDetectionService detection,
        IBenchmarkStore benchmarks, MonitoringService monitoring, OverlayService overlay) : base(notifier)
    {
        _frames = frames;
        _processes = processes;
        _detection = detection;
        _benchmarks = benchmarks;
        _overlay = overlay;
        monitoring.SnapshotAvailable += (_, s) =>
        {
            if (!IsCapturing) return;
            lock (_lock) _recorder?.AddSnapshot(s);
            Ui.Post(() => Snapshot = s);
        };
    }

    public override string Key => "monitor";
    public override string Title => "FPS Monitoring";
    public override string Icon => "";
    public override string Group => "Gaming";

    public ObservableCollection<CaptureTarget> Targets { get; } = [];
    public ObservableCollection<BenchmarkResult> Results { get; } = [];
    public IReadOnlyList<string> Labels { get; } = ["Optimizatsiyadan oldin", "Optimizatsiyadan keyin"];
    public IReadOnlyList<int> Durations { get; } = [30, 60, 120];
    public string? FrameSourceWarning => _frames.IsAvailable ? null : _frames.UnavailableReason;

    public override async Task OnNavigatedToAsync()
    {
        await RefreshTargetsAsync();
        Results.Clear();
        foreach (var r in await _benchmarks.GetRecentAsync(30)) Results.Add(r);
        OnPropertyChanged(nameof(FrameSourceWarning));
    }

    [RelayCommand]
    private async Task RefreshTargetsAsync()
    {
        var processes = await Task.Run(() =>
        {
            _detection.Poll();
            return _processes.GetProcesses();
        });
        var games = _detection.RunningGames.Select(g => new CaptureTarget(g.Pid, g.GameName, true));
        var others = processes
            .Where(p => !p.IsProtected && p.Category is ProcessCategory.User or ProcessCategory.Game && p.WorkingSetBytes > 150UL * 1024 * 1024)
            .Select(p => new CaptureTarget(p.Pid, p.Name, false));
        Targets.Clear();
        foreach (var t in games.Concat(others).DistinctBy(t => t.Pid)) Targets.Add(t);
        SelectedTarget ??= Targets.FirstOrDefault();
    }

    [RelayCommand]
    private void StartCapture()
    {
        if (IsCapturing || SelectedTarget is null) return;
        if (!_frames.IsAvailable)
        {
            Status = _frames.UnavailableReason;
            return;
        }

        _calculator.Reset();
        lock (_lock) _frameTimes.Clear();
        _capture = new CancellationTokenSource();
        IsCapturing = true;
        Status = $"{SelectedTarget.Name} kuzatilmoqda…";
        _ = Task.Run(() => CaptureLoopAsync(SelectedTarget.Pid, _capture.Token));
    }

    private async Task CaptureLoopAsync(int pid, CancellationToken ct)
    {
        try
        {
            await foreach (var sample in _frames.CaptureAsync(pid, ct))
            {
                FrameStats stats;
                double[] history;
                lock (_lock)
                {
                    _calculator.Add(sample);
                    _recorder?.AddFrame(sample);
                    _frameTimes.Enqueue(sample.FrameTimeMs);
                    while (_frameTimes.Count > FrameHistoryLength) _frameTimes.Dequeue();
                    if (Environment.TickCount64 - _lastUiUpdate < 250) continue;
                    _lastUiUpdate = Environment.TickCount64;
                    stats = _calculator.GetStats();
                    history = _frameTimes.ToArray();
                }
                Ui.Post(() =>
                {
                    Stats = stats;
                    FrameTimeHistory = history;
                    _overlay.Update(stats, Snapshot);
                });
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            Notifier.Show($"FPS monitoring to'xtadi: {ex.Message}", isError: true);
        }
        finally
        {
            Ui.Post(() =>
            {
                IsCapturing = false;
                Status = "Monitoring to'xtatildi";
            });
        }
    }

    [RelayCommand]
    private void StopCapture() => _capture?.Cancel();

    partial void OnIsOverlayVisibleChanged(bool value)
    {
        if (value) _overlay.Show();
        else _overlay.Hide();
    }

    [RelayCommand]
    private async Task RunBenchmarkAsync()
    {
        if (IsBenchmarkRunning) return;
        if (!IsCapturing) StartCapture();
        if (!IsCapturing || SelectedTarget is null) return;

        IsBenchmarkRunning = true;
        lock (_lock) _recorder = new BenchmarkRecorder(SelectedTarget.Name, BenchmarkLabel);
        try
        {
            for (var elapsed = 0; elapsed < BenchmarkSeconds && IsCapturing; elapsed++)
            {
                BenchmarkProgress = elapsed * 100.0 / BenchmarkSeconds;
                await Task.Delay(1000);
            }

            BenchmarkResult result;
            lock (_lock)
            {
                result = _recorder!.Complete();
                _recorder = null;
            }
            if (result.Frames.FrameCount == 0)
            {
                Status = "Benchmark vaqtida kadrlar qayd etilmadi.";
                return;
            }
            var id = await _benchmarks.SaveAsync(result);
            Results.Insert(0, result with { Id = id });
            Notifier.Show($"Benchmark: o'rtacha {result.Frames.AverageFps:0} FPS, 1% low {result.Frames.OnePercentLowFps:0} FPS");
        }
        finally
        {
            BenchmarkProgress = 0;
            IsBenchmarkRunning = false;
        }
    }

    partial void OnCompareBeforeChanged(BenchmarkResult? value) => UpdateComparison();
    partial void OnCompareAfterChanged(BenchmarkResult? value) => UpdateComparison();

    private void UpdateComparison() =>
        Comparison = CompareBefore is not null && CompareAfter is not null ? new BenchmarkComparison(CompareBefore, CompareAfter) : null;

    public override void OnNavigatedFrom()
    {
        // Capture keeps running in the background so the overlay continues to work while gaming.
    }
}
