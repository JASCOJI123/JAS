using CommunityToolkit.Mvvm.ComponentModel;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Gaming;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed partial class MainViewModel : ObservableObject
{
    [ObservableProperty] private PageViewModel? _currentPage;
    [ObservableProperty] private string _sessionBadge = string.Empty;

    public MainViewModel(IEnumerable<PageViewModel> pages, MainNavigation navigation, Notifier notifier, GamingSessionManager sessions)
    {
        Pages = pages.ToList();
        Notifier = notifier;
        navigation.Requested += (_, key) => CurrentPage = Pages.FirstOrDefault(p => p.Key == key) ?? CurrentPage;
        sessions.SessionChanged += (_, _) => Ui.Post(() =>
            SessionBadge = sessions.ActiveSession is { } s ? $"🎮 {s.Game.GameName} — Gaming Mode" : string.Empty);
        CurrentPage = Pages.FirstOrDefault();
    }

    public IReadOnlyList<PageViewModel> Pages { get; }

    public Notifier Notifier { get; }

    partial void OnCurrentPageChanged(PageViewModel? oldValue, PageViewModel? newValue)
    {
        oldValue?.OnNavigatedFrom();
        if (newValue is not null) _ = SafeNavigateAsync(newValue);
    }

    private async Task SafeNavigateAsync(PageViewModel page)
    {
        try
        {
            await page.OnNavigatedToAsync();
        }
        catch (Exception ex)
        {
            Notifier.Show(ex.Message, isError: true);
        }
    }
}
