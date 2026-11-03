using CommunityToolkit.Mvvm.ComponentModel;

namespace UltimatePcOptimizer.App.Services;

/// <summary>In-app toast banner shown at the bottom of the main window.</summary>
public sealed partial class Notifier : ObservableObject
{
    private CancellationTokenSource? _hide;

    [ObservableProperty] private string? _message;
    [ObservableProperty] private bool _isError;
    [ObservableProperty] private bool _isVisible;

    public void Show(string message, bool isError = false) => Ui.Post(async () =>
    {
        _hide?.Cancel();
        _hide = new CancellationTokenSource();
        var token = _hide.Token;
        Message = message;
        IsError = isError;
        IsVisible = true;
        try
        {
            await Task.Delay(TimeSpan.FromSeconds(isError ? 7 : 4.5), token);
            IsVisible = false;
        }
        catch (TaskCanceledException) { }
    });
}
