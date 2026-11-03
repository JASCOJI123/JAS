using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using UltimatePcOptimizer.App.Services;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Gaming;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Optimization;

namespace UltimatePcOptimizer.App.ViewModels;

public sealed partial class GameLibraryViewModel(
    Notifier notifier,
    IGameLibraryScanner scanner,
    IGameLibraryStore library,
    IGameProfileStore profiles,
    GameDetectionService detection,
    ChangeExecutor executor,
    IRestorePointService restorePoints,
    IConsentService consent) : PageViewModel(notifier)
{
    [ObservableProperty] private InstalledGame? _selectedGame;

    public override string Key => "library";
    public override string Title => "Game Library";
    public override string Icon => "";
    public override string Group => "Gaming";

    public ObservableCollection<InstalledGame> Games { get; } = [];

    public override async Task OnNavigatedToAsync()
    {
        if (Games.Count > 0) return;
        var stored = await library.GetAllAsync();
        if (stored.Count == 0) await ScanAsync();
        else Load(stored);
    }

    private void Load(IEnumerable<InstalledGame> games)
    {
        Games.Clear();
        foreach (var g in games) Games.Add(g);
        detection.UpdateLibrary(Games);
    }

    [RelayCommand]
    private Task ScanAsync() => RunAsync(async () =>
    {
        var found = await scanner.ScanAsync();
        await library.ReplaceDetectedAsync(found);
        Load(await library.GetAllAsync());
        Status = $"{found.Count} ta o'yin topildi (Steam, Epic Games, Riot)";
    }, "Steam, Epic va Riot kutubxonalari skanerlanmoqda…");

    [RelayCommand]
    private Task AddManualAsync() => RunAsync(async () =>
    {
        var dialog = new OpenFileDialog { Filter = "O'yin (*.exe)|*.exe", Title = "O'yin faylini tanlang" };
        if (dialog.ShowDialog() != true) return;
        var exe = Path.GetFileName(dialog.FileName);
        var game = new InstalledGame($"manual:{exe.ToLowerInvariant()}", Path.GetFileNameWithoutExtension(exe), GameLauncher.Manual,
            Path.GetDirectoryName(dialog.FileName)!, [exe], GameCatalog.FindByExecutable(exe)?.Id);
        await library.AddManualAsync(game);
        Load(await library.GetAllAsync());
    });

    /// <summary>Creates (or opens) the per-game profile used by the Gaming Booster.</summary>
    [RelayCommand]
    private Task CreateProfileAsync(InstalledGame? game) => RunAsync(async () =>
    {
        if (game is null) return;
        var id = game.KnownGameId ?? game.Id;
        if (await profiles.GetAsync(id) is null)
        {
            var known = GameCatalog.FindById(game.KnownGameId);
            await profiles.SaveAsync(GameProfile.CreateDefault(id, game.Name, known?.HasKernelAntiCheat ?? false, autoBoost: true));
        }
        Notifier.Show($"{game.Name} profili yaratildi — o'yin ishga tushganda Gaming Booster avtomatik yoqiladi");
    });

    /// <summary>Windows' per-app graphics preference (Settings → Display → Graphics) for hybrid-GPU PCs.</summary>
    [RelayCommand]
    private Task PreferHighPerformanceGpuAsync(InstalledGame? game) => RunAsync(async () =>
    {
        if (game is null || game.ExecutableNames.Count == 0) return;
        var exePath = FindExecutable(game);
        if (exePath is null)
        {
            Status = "O'yin fayli topilmadi.";
            return;
        }
        var assignment = TweakCatalog.HighPerformanceGpuFor(exePath);
        var change = new PlannedChange(ChangeKind.RegistryValue, assignment.Value.ToString(), assignment.Data,
            $"{game.Name}: yuqori unumli GPU", RiskLevel.Safe);
        if (!await consent.ConfirmAsync(new ConsentRequest("GPU tanlovi", $"{game.Name} har doim yuqori unumli GPU'da ishlaydi.", [exePath], RiskLevel.Safe)))
            return;
        var report = await OneClickOptimizer.ApplyBatchAsync(ChangeBatch.Create(BatchKind.Manual, $"GPU tanlovi — {game.Name}"), [change],
            createRestorePoint: false, consent, executor, restorePoints, CancellationToken.None);
        Notifier.Show(ReportText.Describe(report), report.Failed > 0);
    });

    private static string? FindExecutable(InstalledGame game)
    {
        var known = GameCatalog.FindById(game.KnownGameId);
        var preferred = game.ExecutableNames.FirstOrDefault(e => known?.ExecutableNames.Contains(e, StringComparer.OrdinalIgnoreCase) == true)
                        ?? game.ExecutableNames[0];
        try
        {
            return Directory.EnumerateFiles(game.InstallDirectory, preferred, SearchOption.AllDirectories).FirstOrDefault();
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            return null;
        }
    }
}
