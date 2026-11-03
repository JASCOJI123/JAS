using System.Windows;

namespace UltimatePcOptimizer.App.Services;

/// <summary>Marshals work from background services (monitoring, game detection) onto the UI thread.</summary>
public static class Ui
{
    public static void Post(Action action)
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null || dispatcher.HasShutdownStarted) return;
        if (dispatcher.CheckAccess()) action();
        else dispatcher.BeginInvoke(action);
    }

    public static Task<T> InvokeAsync<T>(Func<Task<T>> func)
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null) return Task.FromResult(default(T)!);
        return dispatcher.CheckAccess() ? func() : dispatcher.InvokeAsync(func).Task.Unwrap();
    }
}
