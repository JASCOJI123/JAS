using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>Internet Connection Diagnostics and Network Latency Monitoring.</summary>
public sealed partial class NetworkViewModel(Notifier notifier, INetworkDiagnostics network, ISettingsStore settings) : PageViewModel(notifier)
{
    private readonly Queue<double> _latency = new();
    private CancellationTokenSource? _monitor;

    [ObservableProperty] private ConnectionDiagnostics? _diagnostics;
    [ObservableProperty] private LatencyStats? _current;
    [ObservableProperty] private IReadOnlyList<double> _latencyHistory = [];
    [ObservableProperty] private bool _isMonitoring;
    [ObservableProperty] private string _host = "1.1.1.1";

    public override string Key => "network";
    public override string Title => "Network";
    public override string Icon => "";
    public override string Group => "Tizim";

    public ObservableCollection<NetworkAdapterInfo> Adapters { get; } = [];

    public override async Task OnNavigatedToAsync()
    {
        Host = (await settings.LoadAsync()).LatencyHosts.FirstOrDefault() ?? Host;
        if (Diagnostics is null) await DiagnoseAsync();
    }

    [RelayCommand]
    private Task DiagnoseAsync() => RunAsync(async () =>
    {
        var hosts = (await settings.LoadAsync()).LatencyHosts;
        Diagnostics = await network.DiagnoseAsync(hosts);
        Adapters.Clear();
        foreach (var a in Diagnostics.Adapters.Where(a => a.IsUp)) Adapters.Add(a);
    }, "Ulanish tekshirilmoqda…");

    [RelayCommand]
    private void ToggleMonitoring()
    {
        if (IsMonitoring)
        {
            _monitor?.Cancel();
            return;
        }
        _monitor = new CancellationTokenSource();
        IsMonitoring = true;
        _latency.Clear();
        _ = MonitorAsync(Host, _monitor.Token);
    }

    private async Task MonitorAsync(string host, CancellationToken ct)
    {
        try
        {
            while (!ct.IsCancellationRequested)
            {
                var stats = await network.MeasureLatencyAsync(host, 3, ct);
                _latency.Enqueue(stats.Received == 0 ? 0 : stats.AverageMs);
                while (_latency.Count > 60) _latency.Dequeue();
                Current = stats;
                LatencyHistory = _latency.ToArray();
                await Task.Delay(1000, ct);
            }
        }
        catch (OperationCanceledException) { }
        finally
        {
            IsMonitoring = false;
        }
    }

    public override void OnNavigatedFrom() => _monitor?.Cancel();
}
