using Microsoft.Extensions.DependencyInjection;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.App.ViewModels;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Gaming;
using UltimatePcOptimizer.Core.Monitoring;
using UltimatePcOptimizer.Core.Optimization;
using UltimatePcOptimizer.Data;
using UltimatePcOptimizer.Platform.Cleanup;
using UltimatePcOptimizer.Platform.Frames;
using UltimatePcOptimizer.Platform.Games;
using UltimatePcOptimizer.Platform.Gpu;
using UltimatePcOptimizer.Platform.Hardware;
using UltimatePcOptimizer.Platform.Network;
using UltimatePcOptimizer.Platform.Power;
using UltimatePcOptimizer.Platform.Processes;
using UltimatePcOptimizer.Platform.Registry;
using UltimatePcOptimizer.Platform.Restore;
using UltimatePcOptimizer.Platform.Startup;
using UltimatePcOptimizer.Platform.Updates;

namespace UltimatePcOptimizer.App.Composition;

/// <summary>Composition root: binds Core abstractions to the Windows platform and SQLite implementations.</summary>
public static class ServiceRegistration
{
    public static IServiceCollection AddPlatform(this IServiceCollection services)
    {
        services.AddSingleton<HardwareSensors>();
        services.AddSingleton<ISystemMonitor, WindowsSystemMonitor>();
        services.AddSingleton<WindowsHardwareDetector>();
        services.AddSingleton<IHardwareDetector>(sp => sp.GetRequiredService<WindowsHardwareDetector>());
        services.AddSingleton<IPageFileProvider>(sp => sp.GetRequiredService<WindowsHardwareDetector>());
        services.AddSingleton<IBootTimeProvider, BootTimeProvider>();
        services.AddSingleton<IProcessService, WindowsProcessService>();
        services.AddSingleton<IStartupService, WindowsStartupService>();
        services.AddSingleton<ICleanupService, WindowsCleanupService>();
        services.AddSingleton<IPowerPlanService, WindowsPowerPlanService>();
        services.AddSingleton<IRegistryAccess, WindowsRegistryAccess>();
        services.AddSingleton<IRestorePointService, WindowsRestorePointService>();
        services.AddSingleton<IGpuSettingsService, WindowsGpuSettingsService>();
        services.AddSingleton<IDriverInfoService, WindowsDriverInfoService>();
        services.AddSingleton<INetworkDiagnostics, NetworkDiagnosticsService>();
        services.AddSingleton<IGameLibraryScanner, GameLibraryScanner>();
        services.AddSingleton<IFrameTimeSource, PresentMonFrameSource>();
        services.AddSingleton<IUpdateService, GitHubUpdateService>();
        return services;
    }

    public static IServiceCollection AddData(this IServiceCollection services, string databasePath)
    {
        services.AddSingleton(new SqliteDatabase(databasePath));
        services.AddSingleton<IChangeJournal, ChangeJournalRepository>();
        services.AddSingleton<IGameProfileStore, GameProfileRepository>();
        services.AddSingleton<IGameLibraryStore, GameLibraryRepository>();
        services.AddSingleton<IBenchmarkStore, BenchmarkRepository>();
        services.AddSingleton<ISettingsStore, SettingsRepository>();
        return services;
    }

    public static IServiceCollection AddCore(this IServiceCollection services)
    {
        services.AddSingleton<IChangeHandler, RegistryChangeHandler>();
        services.AddSingleton<IChangeHandler, PowerPlanChangeHandler>();
        services.AddSingleton<IChangeHandler, StartupChangeHandler>();
        services.AddSingleton<IChangeHandler, ProcessPriorityChangeHandler>();
        services.AddSingleton<IChangeHandler, FileCleanupChangeHandler>();
        services.AddSingleton<ChangeExecutor>();

        services.AddSingleton<HardwareProfileCache>();
        services.AddSingleton<CleanupStep>();
        services.AddSingleton<IOptimizationStep>(sp => sp.GetRequiredService<CleanupStep>());
        services.AddSingleton<IOptimizationStep, StartupStep>();
        services.AddSingleton<IOptimizationStep, BackgroundProcessStep>();
        services.AddSingleton<IOptimizationStep, PowerPlanStep>();
        services.AddSingleton<IOptimizationStep, GamingSettingsStep>();
        services.AddSingleton<IOptimizationStep, VisualEffectsStep>();
        services.AddSingleton<OneClickOptimizer>();
        services.AddSingleton<ModeService>();
        services.AddSingleton<AutoOptimizationService>();

        services.AddSingleton<MonitoringService>();
        services.AddSingleton<GameDetectionService>();
        services.AddSingleton<GamingSessionManager>();
        return services;
    }

    public static IServiceCollection AddUi(this IServiceCollection services)
    {
        services.AddSingleton<Notifier>();
        services.AddSingleton<IConsentService, DialogConsentService>();
        services.AddSingleton<OverlayService>();
        services.AddSingleton<MainNavigation>();

        // Registration order is the sidebar order.
        AddPage<DashboardViewModel>(services);
        AddPage<OptimizeViewModel>(services);
        AddPage<ModesViewModel>(services);
        AddPage<GamingBoosterViewModel>(services);
        AddPage<FpsBoosterViewModel>(services);
        AddPage<GameLibraryViewModel>(services);
        AddPage<FpsMonitorViewModel>(services);
        AddPage<CpuViewModel>(services);
        AddPage<GpuViewModel>(services);
        AddPage<RamViewModel>(services);
        AddPage<ProcessesViewModel>(services);
        AddPage<CleanerViewModel>(services);
        AddPage<StartupViewModel>(services);
        AddPage<HistoryViewModel>(services);
        AddPage<SystemInfoViewModel>(services);
        AddPage<NetworkViewModel>(services);
        AddPage<SettingsViewModel>(services);

        services.AddSingleton<MainViewModel>();
        services.AddSingleton<MainWindow>();
        return services;
    }

    private static void AddPage<T>(IServiceCollection services) where T : PageViewModel
    {
        services.AddSingleton<T>();
        services.AddSingleton<PageViewModel>(sp => sp.GetRequiredService<T>());
    }
}
