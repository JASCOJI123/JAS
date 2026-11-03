using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed partial class SettingsViewModel(Notifier notifier, ISettingsStore store, IUpdateService updates) : PageViewModel(notifier)
{
    private AppSettings _loaded = new();

    [ObservableProperty] private bool _autoOptimizationEnabled;
    [ObservableProperty] private bool _autoApplySafeCleanup;
    [ObservableProperty] private int _autoOptimizationIntervalHours = 24;
    [ObservableProperty] private bool _createRestorePoint = true;
    [ObservableProperty] private bool _checkForUpdates = true;
    [ObservableProperty] private string _latencyHosts = string.Empty;
    [ObservableProperty] private UpdateInfo? _update;

    public override string Key => "settings";
    public override string Title => "Settings";
    public override string Icon => "";
    public override string Group => "Tizim";

    public string Version => typeof(SettingsViewModel).Assembly.GetName().Version?.ToString(3) ?? "1.0.0";
    public string DataFolder => App.DataDirectory;

    public override async Task OnNavigatedToAsync()
    {
        _loaded = await store.LoadAsync();
        AutoOptimizationEnabled = _loaded.AutoOptimizationEnabled;
        AutoApplySafeCleanup = _loaded.AutoApplySafeCleanup;
        AutoOptimizationIntervalHours = _loaded.AutoOptimizationIntervalHours;
        CreateRestorePoint = _loaded.CreateRestorePointBeforeOptimization;
        CheckForUpdates = _loaded.CheckForUpdates;
        LatencyHosts = string.Join(", ", _loaded.LatencyHosts);
    }

    [RelayCommand]
    private Task SaveAsync() => RunAsync(async () =>
    {
        var hosts = LatencyHosts.Split([',', ' ', ';'], StringSplitOptions.RemoveEmptyEntries)
            .Where(h => Uri.CheckHostName(h) != UriHostNameType.Unknown).Distinct().Take(5).ToList();
        _loaded = (await store.LoadAsync()) with
        {
            AutoOptimizationEnabled = AutoOptimizationEnabled,
            AutoApplySafeCleanup = AutoOptimizationEnabled && AutoApplySafeCleanup,
            AutoOptimizationIntervalHours = Math.Clamp(AutoOptimizationIntervalHours, 1, 24 * 30),
            CreateRestorePointBeforeOptimization = CreateRestorePoint,
            CheckForUpdates = CheckForUpdates,
            LatencyHosts = hosts.Count == 0 ? ["1.1.1.1"] : hosts,
        };
        await store.SaveAsync(_loaded);
        Notifier.Show("Sozlamalar saqlandi");
    });

    [RelayCommand]
    private Task CheckUpdatesAsync() => RunAsync(async () =>
    {
        Update = await updates.CheckAsync();
        Status = Update is null ? "Siz eng so'nggi versiyadan foydalanyapsiz (yoki yangilanish manbai sozlanmagan)." : null;
    }, "Yangilanishlar tekshirilmoqda…");

    [RelayCommand]
    private void OpenUpdate()
    {
        if (Update?.DownloadUrl is { Length: > 0 } url && Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps)
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
    }

    [RelayCommand]
    private void OpenDataFolder() => Process.Start(new ProcessStartInfo(DataFolder) { UseShellExecute = true });
}
