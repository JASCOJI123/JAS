using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Parsing;

namespace UltimatePcOptimizer.Tests;

public class ParserTests
{
    [Fact]
    public void Parses_current_steam_library_folders()
    {
        const string vdf = """
            "libraryfolders"
            {
                "0"
                {
                    "path"      "C:\\Program Files (x86)\\Steam"
                    "apps" { "730" "123" }
                }
                "1"
                {
                    "path"      "D:\\SteamLibrary"
                }
            }
            """;
        Assert.Equal([@"C:\Program Files (x86)\Steam", @"D:\SteamLibrary"], LauncherManifestParsers.ParseSteamLibraryFolders(vdf));
    }

    [Fact]
    public void Parses_legacy_steam_library_folders()
    {
        const string vdf = "\"LibraryFolders\"\n{\n\t\"TimeNextStatsReport\"\t\"1\"\n\t\"1\"\t\"E:\\\\Games\\\\Steam\"\n}";
        Assert.Equal([@"E:\Games\Steam"], LauncherManifestParsers.ParseSteamLibraryFolders(vdf));
    }

    [Fact]
    public void Parses_steam_app_manifest_and_skips_redistributables()
    {
        var cs2 = LauncherManifestParsers.ParseSteamAppManifest("""
            "AppState" { "appid" "730" "name" "Counter-Strike 2" "installdir" "Counter-Strike Global Offensive" }
            """);
        Assert.Equal(new SteamAppManifest("730", "Counter-Strike 2", "Counter-Strike Global Offensive"), cs2);

        Assert.Null(LauncherManifestParsers.ParseSteamAppManifest("""
            "AppState" { "appid" "228980" "name" "Steamworks Common Redistributables" "installdir" "Steamworks Shared" }
            """));
    }

    [Fact]
    public void Parses_epic_manifest()
    {
        var m = LauncherManifestParsers.ParseEpicManifest("""
            { "AppName": "Fortnite", "DisplayName": "Fortnite", "InstallLocation": "C:\\Epic\\Fortnite",
              "LaunchExecutable": "FortniteGame/Binaries/Win64/FortniteLauncher.exe", "AppCategories": ["public", "games"] }
            """);
        Assert.NotNull(m);
        Assert.Equal(@"C:\Epic\Fortnite", m.InstallLocation);
        Assert.Null(LauncherManifestParsers.ParseEpicManifest("""{ "AppName": "UE_5", "DisplayName": "Unreal", "InstallLocation": "x", "AppCategories": ["engines"] }"""));
        Assert.Null(LauncherManifestParsers.ParseEpicManifest("not json"));
    }

    [Fact]
    public void Parses_riot_product_settings()
    {
        var p = LauncherManifestParsers.ParseRiotProductSettings("valorant", "product_install_full_path: \"C:/Riot Games/VALORANT/live\"\nother: 1");
        Assert.Equal(@"C:\Riot Games\VALORANT\live", p?.InstallPath);
    }

    [Fact]
    public void Game_matcher_finds_catalog_and_library_games()
    {
        var library = new[] { new InstalledGame("steam:123", "Indie Game", GameLauncher.Steam, @"D:\Indie", ["Indie.exe"], null) };
        var matcher = new GameMatcher(library);

        Assert.True(matcher.TryMatch("cs2", out var id, out _));
        Assert.Equal("cs2", id);
        Assert.True(matcher.TryMatch("Indie", out id, out var name));
        Assert.Equal(("steam:123", "Indie Game"), (id, name));
        Assert.False(matcher.TryMatch("notepad", out _, out _));
    }

    [Fact]
    public void Catalog_contains_required_games()
    {
        string[] required = ["cs2", "pubg", "gta5", "fortnite", "valorant", "dota2", "cod", "cyberpunk2077", "rdr2", "resident-evil"];
        Assert.All(required, id => Assert.NotNull(GameCatalog.FindById(id)));
    }
}

public class PlatformParserTests
{
    [Fact]
    public void Parses_powercfg_list_including_localized_output()
    {
        const string output = """
            Existing Power Schemes (* Active)
            -----------------------------------
            Power Scheme GUID: 381b4222-f694-41f0-9685-ff5bb260df2e  (Balanced) *
            Power Scheme GUID: 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c  (High performance)
            Схема управления питанием GUID: a1841308-3541-4fab-bc81-f71556f20b4a  (Экономия энергии)
            """;
        var plans = PowerCfgParser.ParseList(output);

        Assert.Equal(3, plans.Count);
        Assert.True(plans[0].IsActive);
        Assert.Equal("High performance", plans[1].Name);
        Assert.False(plans[1].IsActive);
        Assert.Equal("Экономия энергии", plans[2].Name);
    }

    [Theory]
    [InlineData("\"C:\\Program Files\\App\\app.exe\" --minimized", @"C:\Program Files\App\app.exe")]
    [InlineData(@"C:\Tools\tool.exe /background", @"C:\Tools\tool.exe")]
    [InlineData(@"C:\Program Files\No Quotes\x.exe -a", @"C:\Program Files\No Quotes\x.exe")]
    public void Extracts_executable_from_startup_command(string command, string expected) =>
        Assert.Equal(expected, CommandLineParser.ExtractExecutable(command));

    [Fact]
    public void Parses_presentmon_v2_csv()
    {
        var parser = new PresentMonCsvParser();
        Assert.Null(parser.ParseLine("Application,ProcessID,SwapChainAddress,PresentRuntime,CPUStartTime,FrameTime,CPUBusy"));
        var sample = parser.ParseLine("cs2.exe,1234,0x1,DXGI,1500.5,6.94,3.1");
        Assert.Equal(new FrameSample(1.5005, 6.94), sample);
    }

    [Fact]
    public void Parses_presentmon_2_6_default_csv()
    {
        var parser = new PresentMonCsvParser();
        parser.ParseLine("Application,ProcessID,SwapChainAddress,PresentRuntime,SyncInterval,PresentFlags,AllowsTearing,PresentMode,TimeInMs,MsBetweenSimulationStart,MsBetweenPresents,MsInPresentAPI");
        Assert.Equal(new FrameSample(2.5, 8.33), parser.ParseLine("game.exe,1,0x1,DXGI,0,0,1,Hardware: Independent Flip,2500,8.1,8.33,0.2"));
    }

    [Fact]
    public void Parses_presentmon_v1_csv()
    {
        var parser = new PresentMonCsvParser();
        parser.ParseLine("Application,ProcessID,TimeInSeconds,MsBetweenPresents");
        Assert.Equal(new FrameSample(2.25, 16.6), parser.ParseLine("game.exe,1,2.25,16.6"));
        Assert.Null(parser.ParseLine("game.exe,1,NA,NA"));
    }
}

public class ReleaseVersionTests
{
    [Theory]
    [InlineData("v1.2.0", "1.2.0")]
    [InlineData("pc-optimizer-v2.0.1", "2.0.1")]
    [InlineData("1.3", "1.3")]
    public void Parses_release_tags(string tag, string expected) =>
        Assert.Equal(Version.Parse(expected), ReleaseVersionParser.Parse(tag));

    [Fact]
    public void Ignores_revision_when_comparing()
    {
        Assert.False(ReleaseVersionParser.IsNewer(new Version(1, 0, 0), new Version(1, 0, 0, 0)));
        Assert.True(ReleaseVersionParser.IsNewer(new Version(1, 0, 1), new Version(1, 0, 0, 0)));
        Assert.Null(ReleaseVersionParser.Parse("latest"));
    }
}
