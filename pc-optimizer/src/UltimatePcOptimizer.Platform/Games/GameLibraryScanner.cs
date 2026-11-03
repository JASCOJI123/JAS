using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Parsing;

namespace UltimatePcOptimizer.Platform.Games;

/// <summary>
/// Game Library: reads the launchers' own manifest files (Steam, Epic Games, Riot). Game files are
/// only listed, never opened for writing.
/// </summary>
public sealed class GameLibraryScanner(ILogger<GameLibraryScanner> logger) : IGameLibraryScanner
{
    private const int MaxExeDepth = 4;
    private const int MaxExecutables = 20;

    private static readonly string[] NonGameExeFragments =
    [
        "unins", "setup", "install", "redist", "vc_redist", "vcredist", "dxsetup", "crash", "report", "helper",
        "easyanticheat", "battleye", "beservice", "ue4prereq", "ueprereq", "dotnet", "cefprocess", "webhelper", "updater",
    ];

    public Task<IReadOnlyList<InstalledGame>> ScanAsync(CancellationToken ct = default) => Task.Run<IReadOnlyList<InstalledGame>>(() =>
    {
        var games = new List<InstalledGame>();
        Try("Steam", () => games.AddRange(ScanSteam(ct)));
        Try("Epic", () => games.AddRange(ScanEpic(ct)));
        Try("Riot", () => games.AddRange(ScanRiot(ct)));
        return games.DistinctBy(g => g.Id).OrderBy(g => g.Name).ToList();
    }, ct);

    private void Try(string launcher, Action scan)
    {
        try { scan(); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException)
        {
            logger.LogWarning(ex, "{Launcher} library scan failed", launcher);
        }
    }

    private static IEnumerable<InstalledGame> ScanSteam(CancellationToken ct)
    {
        using var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Software\Valve\Steam");
        if (key?.GetValue("SteamPath") is not string steamPath) yield break;
        steamPath = steamPath.Replace('/', '\\');

        var libraries = new List<string> { steamPath };
        var foldersFile = Path.Combine(steamPath, "steamapps", "libraryfolders.vdf");
        if (File.Exists(foldersFile)) libraries.AddRange(LauncherManifestParsers.ParseSteamLibraryFolders(File.ReadAllText(foldersFile)));

        foreach (var library in libraries.Select(l => l.Replace('/', '\\')).Distinct(StringComparer.OrdinalIgnoreCase))
        {
            var apps = Path.Combine(library, "steamapps");
            if (!Directory.Exists(apps)) continue;
            foreach (var manifestFile in Directory.EnumerateFiles(apps, "appmanifest_*.acf"))
            {
                ct.ThrowIfCancellationRequested();
                var manifest = LauncherManifestParsers.ParseSteamAppManifest(File.ReadAllText(manifestFile));
                if (manifest is null) continue;
                var dir = Path.Combine(apps, "common", manifest.InstallDir);
                if (Directory.Exists(dir)) yield return Create($"steam:{manifest.AppId}", manifest.Name, GameLauncher.Steam, dir, null);
            }
        }
    }

    private static IEnumerable<InstalledGame> ScanEpic(CancellationToken ct)
    {
        var manifests = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), @"Epic\EpicGamesLauncher\Data\Manifests");
        if (!Directory.Exists(manifests)) yield break;
        foreach (var file in Directory.EnumerateFiles(manifests, "*.item"))
        {
            ct.ThrowIfCancellationRequested();
            var m = LauncherManifestParsers.ParseEpicManifest(File.ReadAllText(file));
            if (m is null || !Directory.Exists(m.InstallLocation)) continue;
            yield return Create($"epic:{m.AppName}", m.DisplayName, GameLauncher.Epic, m.InstallLocation,
                m.LaunchExecutable is null ? null : Path.GetFileName(m.LaunchExecutable));
        }
    }

    private static IEnumerable<InstalledGame> ScanRiot(CancellationToken ct)
    {
        var metadata = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), @"Riot Games\Metadata");
        if (!Directory.Exists(metadata)) yield break;
        foreach (var file in Directory.EnumerateFiles(metadata, "*.product_settings.yaml", SearchOption.AllDirectories))
        {
            ct.ThrowIfCancellationRequested();
            var productId = Path.GetFileName(file).Split('.')[0];
            if (productId.Equals("riot_client", StringComparison.OrdinalIgnoreCase)) continue;
            var product = LauncherManifestParsers.ParseRiotProductSettings(productId, File.ReadAllText(file));
            if (product is null || !Directory.Exists(product.InstallPath)) continue;
            var name = productId switch
            {
                "valorant" => "VALORANT",
                "league_of_legends" => "League of Legends",
                "bacon" => "Legends of Runeterra",
                _ => productId,
            };
            yield return Create($"riot:{productId}", name, GameLauncher.Riot, product.InstallPath, null);
        }
    }

    private static InstalledGame Create(string id, string name, GameLauncher launcher, string dir, string? preferredExe)
    {
        var executables = FindExecutables(dir);
        if (preferredExe is not null && !executables.Contains(preferredExe, StringComparer.OrdinalIgnoreCase)) executables.Insert(0, preferredExe);
        var known = executables.Select(GameCatalog.FindByExecutable).FirstOrDefault(k => k is not null)
                    ?? GameCatalog.Games.FirstOrDefault(g => name.Contains(g.DisplayName, StringComparison.OrdinalIgnoreCase));
        return new InstalledGame(id, name, launcher, dir, executables, known?.Id);
    }

    private static List<string> FindExecutables(string dir)
    {
        var result = new List<string>();
        var pending = new Queue<(string Dir, int Depth)>();
        pending.Enqueue((dir, 0));
        while (pending.Count > 0 && result.Count < MaxExecutables)
        {
            var (current, depth) = pending.Dequeue();
            try
            {
                foreach (var exe in Directory.EnumerateFiles(current, "*.exe"))
                {
                    var name = Path.GetFileName(exe);
                    if (!NonGameExeFragments.Any(f => name.Contains(f, StringComparison.OrdinalIgnoreCase))) result.Add(name);
                }
                if (depth < MaxExeDepth)
                    foreach (var sub in Directory.EnumerateDirectories(current))
                        if (!new DirectoryInfo(sub).Attributes.HasFlag(FileAttributes.ReparsePoint)) pending.Enqueue((sub, depth + 1));
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
        }
        return result.Distinct(StringComparer.OrdinalIgnoreCase).Take(MaxExecutables).ToList();
    }
}
