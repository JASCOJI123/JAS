namespace UltimatePcOptimizer.Core.Games;

/// <summary>
/// A game the optimizer knows about. <see cref="HasKernelAntiCheat"/> makes the booster keep away
/// from the game process; <see cref="SupportsReflex"/> is null when support is unknown.
/// </summary>
public sealed record KnownGame(
    string Id,
    string DisplayName,
    IReadOnlyList<string> ExecutableNames,
    bool HasKernelAntiCheat,
    bool? SupportsReflex,
    bool IsCompetitive);

public static class GameCatalog
{
    public static IReadOnlyList<KnownGame> Games { get; } =
    [
        new("cs2", "Counter-Strike 2", ["cs2.exe"], false, true, true),
        new("pubg", "PUBG: Battlegrounds", ["TslGame.exe"], true, true, true),
        new("gta5", "Grand Theft Auto V", ["GTA5.exe", "GTA5_Enhanced.exe"], true, false, false),
        new("fortnite", "Fortnite", ["FortniteClient-Win64-Shipping.exe"], true, true, true),
        new("valorant", "VALORANT", ["VALORANT-Win64-Shipping.exe"], true, true, true),
        new("dota2", "Dota 2", ["dota2.exe"], false, null, true),
        new("cod", "Call of Duty", ["cod.exe", "ModernWarfare.exe", "BlackOps6.exe", "cod24-cod.exe", "BlackOpsColdWar.exe"], true, true, true),
        new("cyberpunk2077", "Cyberpunk 2077", ["Cyberpunk2077.exe"], false, true, false),
        new("rdr2", "Red Dead Redemption 2", ["RDR2.exe"], false, false, false),
        new("resident-evil", "Resident Evil", ["re2.exe", "re3.exe", "re4.exe", "re7.exe", "re8.exe", "re9.exe", "BH.exe"], false, null, false),
    ];

    private static readonly Dictionary<string, KnownGame> ByExecutable = Games
        .SelectMany(g => g.ExecutableNames.Select(e => (Exe: e, Game: g)))
        .ToDictionary(x => x.Exe, x => x.Game, StringComparer.OrdinalIgnoreCase);

    public static KnownGame? FindByExecutable(string executableName) =>
        ByExecutable.GetValueOrDefault(EnsureExe(executableName));

    public static KnownGame? FindById(string? id) => id is null ? null : Games.FirstOrDefault(g => g.Id == id);

    internal static string EnsureExe(string name) =>
        name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? name : name + ".exe";
}
