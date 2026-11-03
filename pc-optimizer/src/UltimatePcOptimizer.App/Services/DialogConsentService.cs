using System.Windows;
using UltimatePcOptimizer.App.Views;
using UltimatePcOptimizer.Core.Abstractions;

namespace UltimatePcOptimizer.App.Services;

/// <summary>Every consent request is shown as a modal dialog owned by the main window.</summary>
public sealed class DialogConsentService : IConsentService
{
    public Task<bool> ConfirmAsync(ConsentRequest request) => Ui.InvokeAsync(() =>
    {
        var owner = Application.Current.MainWindow;
        var dialog = new ConfirmDialog(request);
        if (owner is { IsLoaded: true }) dialog.Owner = owner;
        return Task.FromResult(dialog.ShowDialog() == true);
    });
}
