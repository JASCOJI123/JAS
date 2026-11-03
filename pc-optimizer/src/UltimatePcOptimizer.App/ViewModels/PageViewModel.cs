using CommunityToolkit.Mvvm.ComponentModel;
using UltimatePcOptimizer.App.Services;

namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>Base for every page: navigation metadata, busy state and uniform error reporting.</summary>
public abstract partial class PageViewModel(Notifier notifier) : ObservableObject
{
    [ObservableProperty] private bool _isBusy;
    [ObservableProperty] private string? _status;

    public abstract string Key { get; }
    public abstract string Title { get; }
    public abstract string Icon { get; }
    public virtual string Group => "Asosiy";

    protected Notifier Notifier { get; } = notifier;

    public virtual Task OnNavigatedToAsync() => Task.CompletedTask;

    public virtual void OnNavigatedFrom() { }

    /// <summary>Runs an operation with the busy flag set and reports failures instead of crashing.</summary>
    protected async Task RunAsync(Func<Task> action, string? busyStatus = null)
    {
        if (IsBusy) return;
        IsBusy = true;
        Status = busyStatus;
        try
        {
            await action();
        }
        catch (OperationCanceledException)
        {
            Status = "Bekor qilindi";
        }
        catch (Exception ex)
        {
            Status = ex.Message;
            Notifier.Show(ex.Message, isError: true);
        }
        finally
        {
            IsBusy = false;
            if (Status == busyStatus) Status = null;
        }
    }
}
