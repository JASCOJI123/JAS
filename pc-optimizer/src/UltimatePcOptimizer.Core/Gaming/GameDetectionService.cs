using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Gaming;

/// <summary>Watches running processes and raises events when a known or library game starts or exits.</summary>
public sealed class GameDetectionService(IProcessService processes, ILogger<GameDetectionService> logger) : IDisposable
{
    private readonly Dictionary<int, RunningGame> _running = [];
    private readonly Lock _lock = new();
    private GameMatcher _matcher = new([]);
    private CancellationTokenSource? _cts;

    public event EventHandler<RunningGame>? GameStarted;
    public event EventHandler<RunningGame>? GameStopped;

    public IReadOnlyList<RunningGame> RunningGames
    {
        get { lock (_lock) return _running.Values.ToList(); }
    }

    public bool IsRunning => _cts is not null;

    public void UpdateLibrary(IEnumerable<InstalledGame> library) => _matcher = new GameMatcher(library);

    public void Start(TimeSpan interval)
    {
        if (_cts is not null) return;
        _cts = new CancellationTokenSource();
        var token = _cts.Token;
        _ = Task.Run(() => RunAsync(interval, token), token);
    }

    public void Stop()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;
    }

    private async Task RunAsync(TimeSpan interval, CancellationToken ct)
    {
        using var timer = new PeriodicTimer(interval);
        try
        {
            do
            {
                try { Poll(); }
                catch (Exception ex) { logger.LogWarning(ex, "Game detection poll failed"); }
            }
            while (await timer.WaitForNextTickAsync(ct).ConfigureAwait(false));
        }
        catch (OperationCanceledException) { }
    }

    /// <summary>One detection pass. Public for tests and for an immediate refresh from the UI.</summary>
    public void Poll()
    {
        var current = processes.GetProcessIdentities();
        var started = new List<RunningGame>();
        var stopped = new List<RunningGame>();

        lock (_lock)
        {
            var alive = new HashSet<int>();
            foreach (var p in current)
            {
                if (!_matcher.TryMatch(p.Name, out var id, out var name)) continue;
                alive.Add(p.Pid);
                if (_running.ContainsKey(p.Pid)) continue;
                var game = new RunningGame(p.Pid, p.Name, null, id, name, p.StartTime);
                _running[p.Pid] = game;
                started.Add(game);
            }
            foreach (var pid in _running.Keys.Where(pid => !alive.Contains(pid)).ToList())
            {
                stopped.Add(_running[pid]);
                _running.Remove(pid);
            }
        }

        foreach (var game in stopped) GameStopped?.Invoke(this, game);
        foreach (var game in started) GameStarted?.Invoke(this, game);
    }

    public void Dispose() => Stop();
}
