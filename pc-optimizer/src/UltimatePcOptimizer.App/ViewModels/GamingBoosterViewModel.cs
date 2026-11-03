using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Gaming;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed partial class GameProfileItem(GameProfile profile) : ObservableObject
{
    [ObservableProperty] private bool _autoBoost = profile.AutoBoost;
    [ObservableProperty] private bool _raiseGamePriority = profile.RaiseGamePriority;
    [ObservableProperty] private bool _lowerBackgroundPriority = profile.LowerBackgroundPriority;
    [ObservableProperty] private bool _suppressNotifications = profile.SuppressNotifications;
    [ObservableProperty] private bool _useHighPerformancePowerPlan = profile.UseHighPerformancePowerPlan;

    public string GameId => profile.GameId;
    public string GameName => profile.GameName;
    public bool HasKernelAntiCheat => GameCatalog.FindById(profile.GameId)?.HasKernelAntiCheat == true;

    public GameProfile ToProfile() => profile with
    {
        AutoBoost = AutoBoost,
        RaiseGamePriority = RaiseGamePriority && !HasKernelAntiCheat,
        LowerBackgroundPriority = LowerBackgroundPriority,
        SuppressNotifications = SuppressNotifications,
        UseHighPerformancePowerPlan = UseHighPerformancePowerPlan,
    };
}

public sealed partial class GamingBoosterViewModel : PageViewModel
{
    private readonly GamingSessionManager _sessions;
    private readonly GameDetectionService _detection;
    private readonly IGameProfileStore _profiles;
    private readonly ISettingsStore _settings;
    private readonly IConsentService _consent;

    [ObservableProperty] private bool _isSessionActive;
    [ObservableProperty] private string _sessionText = "Faol sessiya yo'q";
    [ObservableProperty] private bool _autoDetection;
    [ObservableProperty] private bool _autoBoostNewGames;
    private bool _loading;
    [ObservableProperty] private RunningGame? _selectedRunningGame;

    public GamingBoosterViewModel(Notifier notifier, GamingSessionManager sessions, GameDetectionService detection,
        IGameProfileStore profiles, ISettingsStore settings, IConsentService consent) : base(notifier)
    {
        _sessions = sessions;
        _detection = detection;
        _profiles = profiles;
        _settings = settings;
        _consent = consent;
        _sessions.SessionChanged += (_, _) => Ui.Post(UpdateSession);
        _detection.GameStarted += (_, _) => Ui.Post(RefreshRunning);
        _detection.GameStopped += (_, _) => Ui.Post(RefreshRunning);
    }

    public override string Key => "booster";
    public override string Title => "Gaming Booster";
    public override string Icon => "";
    public override string Group => "Gaming";

    public ObservableCollection<RunningGame> RunningGames { get; } = [];
    public ObservableCollection<GameProfileItem> Profiles { get; } = [];
    public ObservableCollection<string> SessionChanges { get; } = [];
    public IReadOnlyList<KnownGame> SupportedGames => GameCatalog.Games;

    public override async Task OnNavigatedToAsync()
    {
        var settings = await _settings.LoadAsync();
        _loading = true;
        AutoDetection = settings.GameAutoDetection;
        AutoBoostNewGames = settings.AutoBoostNewGames == true;
        _loading = false;
        RefreshRunning();
        UpdateSession();
        Profiles.Clear();
        foreach (var p in await _profiles.GetAllAsync()) Profiles.Add(new GameProfileItem(p));
    }

    private void RefreshRunning()
    {
        RunningGames.Clear();
        foreach (var g in _detection.RunningGames) RunningGames.Add(g);
        SelectedRunningGame ??= RunningGames.FirstOrDefault();
    }

    private void UpdateSession()
    {
        var session = _sessions.ActiveSession;
        IsSessionActive = session is not null;
        SessionText = session is null ? "Faol sessiya yo'q" : $"{session.Game.GameName} uchun Gaming Mode faol ({session.StartedAt.ToLocalTime():HH:mm})";
        SessionChanges.Clear();
        if (session is null) return;
        foreach (var o in session.Outcomes) SessionChanges.Add(o.Success ? o.Change.Description : $"{o.Change.Description} — {o.Message}");
    }

    partial void OnAutoDetectionChanged(bool value) => _ = SaveDetectionAsync(value);

    partial void OnAutoBoostNewGamesChanged(bool value)
    {
        if (!_loading) _ = SaveAutoBoostAsync(value);
    }

    /// <summary>Turning auto boost on (with consent) also enables it for games that already have a profile.</summary>
    private async Task SaveAutoBoostAsync(bool enabled)
    {
        try
        {
            if (enabled && !await _consent.ConfirmAsync(new ConsentRequest("Avtomatik Gaming Mode",
                    "Barcha aniqlangan o'yinlar ishga tushganda Gaming Mode avtomatik yoqiladi va o'yin yopilganda tiklanadi.",
                    GamingSessionManager.DefaultBoostActions, Core.Changes.RiskLevel.Low)))
            {
                _loading = true;
                AutoBoostNewGames = false;
                _loading = false;
                return;
            }

            var settings = await _settings.LoadAsync();
            await _settings.SaveAsync(settings with { AutoBoostNewGames = enabled });
            if (enabled)
            {
                foreach (var profile in await _profiles.GetAllAsync())
                    if (!profile.AutoBoost) await _profiles.SaveAsync(profile with { AutoBoost = true });
                Profiles.Clear();
                foreach (var p in await _profiles.GetAllAsync()) Profiles.Add(new GameProfileItem(p));
            }
            Notifier.Show(enabled ? "Avtomatik Gaming Mode yoqildi" : "Avtomatik Gaming Mode o'chirildi");
        }
        catch (Exception ex)
        {
            Notifier.Show(ex.Message, isError: true);
        }
    }

    private async Task SaveDetectionAsync(bool enabled)
    {
        var settings = await _settings.LoadAsync();
        if (settings.GameAutoDetection == enabled) return;
        await _settings.SaveAsync(settings with { GameAutoDetection = enabled });
        if (enabled) _detection.Start(TimeSpan.FromSeconds(2));
        else _detection.Stop();
    }

    [RelayCommand]
    private Task BoostNowAsync() => RunAsync(async () =>
    {
        await Task.Run(_detection.Poll);
        RefreshRunning();
        var game = SelectedRunningGame;
        if (game is null)
        {
            Status = "Ishlayotgan o'yin topilmadi. O'yinni ishga tushiring yoki Game Library'ga qo'shing.";
            return;
        }

        var known = GameCatalog.FindById(game.GameId);
        var profile = await _profiles.GetAsync(game.GameId)
                      ?? GameProfile.CreateDefault(game.GameId, game.GameName, known?.HasKernelAntiCheat ?? false, autoBoost: false);
        if (!await _consent.ConfirmAsync(new ConsentRequest($"{game.GameName} uchun Gaming Booster",
                "O'yin yopilganda barcha sozlamalar avtomatik tiklanadi.", GamingSessionManager.DescribeProfile(profile), Core.Changes.RiskLevel.Low)))
            return;
        await Task.Run(() => _sessions.StartSessionAsync(game, profile, CancellationToken.None));
    }, "Gaming Mode yoqilmoqda…");

    [RelayCommand]
    private Task EndSessionAsync() => RunAsync(async () =>
    {
        var report = await Task.Run(() => _sessions.EndSessionAsync(CancellationToken.None));
        if (report is not null) Notifier.Show($"Sozlamalar tiklandi: {report.Reverted} ta", report.Failed > 0);
    }, "Sozlamalar tiklanmoqda…");

    [RelayCommand]
    private Task SaveProfileAsync(GameProfileItem? item) => RunAsync(async () =>
    {
        if (item is null) return;
        await _profiles.SaveAsync(item.ToProfile());
        Notifier.Show($"{item.GameName} profili saqlandi");
    });
}
