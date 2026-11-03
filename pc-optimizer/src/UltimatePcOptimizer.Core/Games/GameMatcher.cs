using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Games;

/// <summary>Maps a process executable name to a game from the catalog or the user's library.</summary>
public sealed class GameMatcher
{
    private readonly Dictionary<string, (string Id, string Name)> _byExe = new(StringComparer.OrdinalIgnoreCase);

    public GameMatcher(IEnumerable<InstalledGame> library)
    {
        foreach (var game in GameCatalog.Games)
            foreach (var exe in game.ExecutableNames)
                _byExe[exe] = (game.Id, game.DisplayName);

        // Library entries override catalog names (e.g. the exact edition installed).
        foreach (var game in library)
            foreach (var exe in game.ExecutableNames)
                _byExe[GameCatalog.EnsureExe(exe)] = (game.KnownGameId ?? game.Id, game.Name);
    }

    public bool TryMatch(string processName, out string gameId, out string gameName)
    {
        if (_byExe.TryGetValue(GameCatalog.EnsureExe(processName), out var match))
        {
            (gameId, gameName) = match;
            return true;
        }
        gameId = gameName = string.Empty;
        return false;
    }

    public int Count => _byExe.Count;
}
