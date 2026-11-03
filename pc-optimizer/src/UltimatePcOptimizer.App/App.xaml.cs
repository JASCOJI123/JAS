using System.Net.Http;
using System.Windows;
using System.Windows.Threading;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.App.Composition;
using UltimatePcOptimizer.App.Logging;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Gaming;
using UltimatePcOptimizer.Core.Monitoring;
using UltimatePcOptimizer.Core.Optimization;
using UltimatePcOptimizer.Data;
using UltimatePcOptimizer.Platform.Updates;

namespace UltimatePcOptimizer.App;

public partial class App : Application
{
    private const string SingleInstanceName = @"Local\UltimatePcOptimizer.SingleInstance";
    private Mutex? _singleInstance;
    private IHost? _host;
    private ILogger<App>? _logger;

    public static string DataDirectory { get; } =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "UltimatePcOptimizer");

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        _singleInstance = new Mutex(true, SingleInstanceName, out var isFirst);
        if (!isFirst)
        {
            MessageBox.Show("Ultimate PC Optimizer allaqachon ishlamoqda.", "Ultimate PC Optimizer", MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

        DispatcherUnhandledException += OnDispatcherUnhandledException;
        TaskScheduler.UnobservedTaskException += (_, args) =>
        {
            _logger?.LogError(args.Exception, "Unobserved task exception");
            args.SetObserved();
        };
        AppDomain.CurrentDomain.UnhandledException += (_, args) => _logger?.LogCritical(args.ExceptionObject as Exception, "Fatal exception");

        try
        {
            _host = BuildHost();
            _logger = _host.Services.GetRequiredService<ILogger<App>>();
            var settings = await InitializeAsync(_host.Services);

            var window = _host.Services.GetRequiredService<MainWindow>();
            MainWindow = window;
            window.Show();

            // Asked once, here, before any game is detected: a prompt shown while a fullscreen game runs stays hidden behind it.
            if (settings.AutoBoostNewGames is null) await AskAutoBoostAsync(_host.Services);

            if (settings.GameAutoDetection) _host.Services.GetRequiredService<GameDetectionService>().Start(TimeSpan.FromSeconds(2));

            _ = RunBackgroundTasksAsync(_host.Services);
        }
        catch (Exception ex)
        {
            _logger?.LogCritical(ex, "Startup failed");
            MessageBox.Show($"Dasturni ishga tushirib bo'lmadi:\n{ex.Message}", "Ultimate PC Optimizer", MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    private static IHost BuildHost()
    {
        var builder = Host.CreateApplicationBuilder(new HostApplicationBuilderSettings
        {
            ContentRootPath = AppContext.BaseDirectory,
            DisableDefaults = true,
        });
        builder.Configuration.AddJsonFile("appsettings.json", optional: true).AddEnvironmentVariables("UPO_");

        builder.Logging.ClearProviders();
        builder.Logging.AddConfiguration(builder.Configuration.GetSection("Logging"));
        builder.Logging.AddProvider(new FileLoggerProvider(Path.Combine(DataDirectory, "logs")));

        var version = typeof(App).Assembly.GetName().Version ?? new Version(1, 0, 0);
        builder.Services.AddSingleton(new UpdateOptions(builder.Configuration["Updates:Repository"], version));
        builder.Services.AddSingleton(_ =>
        {
            var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
            http.DefaultRequestHeaders.UserAgent.ParseAdd($"UltimatePcOptimizer/{version.ToString(3)}");
            return http;
        });

        builder.Services
            .AddPlatform()
            .AddData(Path.Combine(DataDirectory, "optimizer.db"))
            .AddCore()
            .AddUi();
        return builder.Build();
    }

    private static async Task<Core.Models.AppSettings> InitializeAsync(IServiceProvider services)
    {
        await services.GetRequiredService<SqliteDatabase>().MigrateAsync();

        // Settings changed by a gaming session that never ended (crash, power loss) are restored first.
        var sessions = services.GetRequiredService<GamingSessionManager>();
        await sessions.RecoverAsync();

        var settings = await services.GetRequiredService<ISettingsStore>().LoadAsync();
        services.GetRequiredService<MonitoringService>().Start(TimeSpan.FromMilliseconds(Math.Clamp(settings.MonitoringIntervalMs, 500, 5000)));

        var detection = services.GetRequiredService<GameDetectionService>();
        detection.UpdateLibrary(await services.GetRequiredService<IGameLibraryStore>().GetAllAsync());
        sessions.Attach(detection);
        return settings;
    }

    private static async Task AskAutoBoostAsync(IServiceProvider services)
    {
        var accepted = await services.GetRequiredService<IConsentService>().ConfirmAsync(new ConsentRequest(
            "Gaming Booster",
            "O'yin (CS2, Valorant, Dota 2 va boshqalar) ishga tushganda Gaming Mode avtomatik yoqilsinmi? O'yin yopilganda barcha sozlamalar avtomatik tiklanadi. Keyin Gaming Booster bo'limida o'zgartirish mumkin.",
            GamingSessionManager.DefaultBoostActions,
            Core.Changes.RiskLevel.Low));
        var store = services.GetRequiredService<ISettingsStore>();
        await store.SaveAsync((await store.LoadAsync()) with { AutoBoostNewGames = accepted });
    }

    private async Task RunBackgroundTasksAsync(IServiceProvider services)
    {
        var notifier = services.GetRequiredService<Notifier>();
        try
        {
            var auto = await services.GetRequiredService<AutoOptimizationService>().RunIfDueAsync();
            if (auto is { JunkBytesFound: > 0 })
                notifier.Show(auto.CategoriesCleaned > 0
                    ? $"Automatic Optimization: {Core.ByteFormatter.Format(auto.JunkBytesFound)} keraksiz fayl tozalandi"
                    : $"{Core.ByteFormatter.Format(auto.JunkBytesFound)} keraksiz fayl topildi — One Click Optimize'ni ishga tushiring");

            var settings = await services.GetRequiredService<ISettingsStore>().LoadAsync();
            if (settings.CheckForUpdates && await services.GetRequiredService<IUpdateService>().CheckAsync() is { } update)
                notifier.Show($"Yangi versiya mavjud: {update.Version}. Settings bo'limidan yuklab oling.");
        }
        catch (Exception ex)
        {
            _logger?.LogWarning(ex, "Background startup tasks failed");
        }
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        _logger?.LogError(e.Exception, "Unhandled UI exception");
        _host?.Services.GetService<Notifier>()?.Show($"Kutilmagan xato: {e.Exception.Message}", isError: true);
        e.Handled = true;
    }

    /// <summary>Windows is signing out or shutting down: restore gaming-session settings before the process dies.</summary>
    protected override void OnSessionEnding(SessionEndingCancelEventArgs e)
    {
        base.OnSessionEnding(e);
        EndGamingSession();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        EndGamingSession();
        if (_host is not null)
        {
            _host.Services.GetService<MonitoringService>()?.Stop();
            _host.Services.GetService<GameDetectionService>()?.Stop();
            _host.Dispose();
        }
        _singleInstance?.Dispose();
        base.OnExit(e);
    }

    private void EndGamingSession()
    {
        var sessions = _host?.Services.GetService<GamingSessionManager>();
        if (sessions?.ActiveSession is null) return;
        try
        {
            Task.Run(() => sessions.EndSessionAsync(CancellationToken.None)).Wait(TimeSpan.FromSeconds(10));
        }
        catch (AggregateException ex)
        {
            _logger?.LogError(ex, "Failed to restore gaming session on exit; it will be restored on next start");
        }
    }
}
