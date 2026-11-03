using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Monitoring;

/// <summary>Samples the system on a fixed interval and keeps a short history for charts.</summary>
public sealed class MonitoringService(ISystemMonitor monitor, ILogger<MonitoringService> logger) : IDisposable
{
    public const int HistoryLength = 120;

    private readonly Queue<SystemSnapshot> _history = new(HistoryLength);
    private readonly Lock _lock = new();
    private CancellationTokenSource? _cts;

    public event EventHandler<SystemSnapshot>? SnapshotAvailable;

    public SystemSnapshot Latest { get; private set; } = SystemSnapshot.Empty;

    public IReadOnlyList<SystemSnapshot> History
    {
        get { lock (_lock) return _history.ToList(); }
    }

    public void Start(TimeSpan interval)
    {
        if (_cts is not null) return;
        _cts = new CancellationTokenSource();
        _ = Task.Run(() => RunAsync(interval, _cts.Token));
    }

    private async Task RunAsync(TimeSpan interval, CancellationToken ct)
    {
        using var timer = new PeriodicTimer(interval);
        try
        {
            do
            {
                try
                {
                    var snapshot = monitor.Sample();
                    lock (_lock)
                    {
                        if (_history.Count == HistoryLength) _history.Dequeue();
                        _history.Enqueue(snapshot);
                    }
                    Latest = snapshot;
                    SnapshotAvailable?.Invoke(this, snapshot);
                }
                catch (Exception ex)
                {
                    logger.LogWarning(ex, "Monitoring sample failed");
                }
            }
            while (await timer.WaitForNextTickAsync(ct).ConfigureAwait(false));
        }
        catch (OperationCanceledException) { }
    }

    public void Stop()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;
    }

    public void Dispose() => Stop();
}
