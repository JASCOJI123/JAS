using System.Text.Json;

namespace UltimatePcOptimizer.Core.Parsing;

public sealed record SteamAppManifest(string AppId, string Name, string InstallDir);

public sealed record EpicManifest(string AppName, string DisplayName, string InstallLocation, string? LaunchExecutable);

public sealed record RiotProduct(string ProductId, string InstallPath);

public static class LauncherManifestParsers
{
    /// <summary>Steam runtime/redistributable app ids that are not games.</summary>
    private static readonly HashSet<string> SteamNonGames = ["228980", "1070560", "1391110", "1628350", "1493710", "250820"];

    /// <summary>Library paths from steamapps/libraryfolders.vdf (both the old and the current layout).</summary>
    public static IReadOnlyList<string> ParseSteamLibraryFolders(string vdf)
    {
        var root = ValveKeyValueParser.Parse(vdf);
        var folders = root["libraryfolders"] ?? root["LibraryFolders"];
        if (folders is null) return [];

        var result = new List<string>();
        foreach (var (key, node) in folders.Children)
        {
            if (!int.TryParse(key, out _)) continue;
            var path = node.Value ?? node.GetString("path");
            if (!string.IsNullOrWhiteSpace(path)) result.Add(path);
        }
        return result;
    }

    public static SteamAppManifest? ParseSteamAppManifest(string acf)
    {
        var state = ValveKeyValueParser.Parse(acf)["AppState"];
        var id = state?.GetString("appid");
        var name = state?.GetString("name");
        var dir = state?.GetString("installdir");
        if (id is null || name is null || dir is null || SteamNonGames.Contains(id)) return null;
        if (name.Contains("Redistributable", StringComparison.OrdinalIgnoreCase) ||
            name.StartsWith("Proton", StringComparison.OrdinalIgnoreCase) ||
            name.StartsWith("Steam Linux Runtime", StringComparison.OrdinalIgnoreCase)) return null;
        return new SteamAppManifest(id, name, dir);
    }

    /// <summary>Epic Games Launcher writes one JSON *.item file per installed app.</summary>
    public static EpicManifest? ParseEpicManifest(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            string? Get(string name) => root.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

            var appName = Get("AppName");
            var display = Get("DisplayName");
            var location = Get("InstallLocation");
            if (appName is null || display is null || location is null) return null;
            // Engine installs and plugins are not games.
            if (root.TryGetProperty("AppCategories", out var cats) && cats.ValueKind == JsonValueKind.Array &&
                cats.EnumerateArray().Any(c => c.GetString() is "engines" or "plugins")) return null;
            return new EpicManifest(appName, display, location, Get("LaunchExecutable"));
        }
        catch (JsonException)
        {
            return null;
        }
    }

    /// <summary>Riot Client keeps "product_install_full_path" in each *.product_settings.yaml.</summary>
    public static RiotProduct? ParseRiotProductSettings(string productId, string yaml)
    {
        foreach (var rawLine in yaml.Split('\n'))
        {
            var line = rawLine.Trim();
            const string key = "product_install_full_path:";
            if (!line.StartsWith(key, StringComparison.Ordinal)) continue;
            var value = line[key.Length..].Trim().Trim('"', '\'');
            return value.Length == 0 ? null : new RiotProduct(productId, value.Replace('/', '\\'));
        }
        return null;
    }
}
