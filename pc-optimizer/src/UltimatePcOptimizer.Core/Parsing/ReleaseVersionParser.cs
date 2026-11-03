using System.Text.RegularExpressions;

namespace UltimatePcOptimizer.Core.Parsing;

public static partial class ReleaseVersionParser
{
    [GeneratedRegex(@"(?<v>\d+\.\d+(\.\d+){0,2})")]
    private static partial Regex VersionPattern();

    /// <summary>Extracts a version from release tags such as "v1.2.0" or "pc-optimizer-v1.2.0".</summary>
    public static Version? Parse(string? tag)
    {
        if (string.IsNullOrWhiteSpace(tag)) return null;
        var match = VersionPattern().Match(tag);
        return match.Success && Version.TryParse(match.Groups["v"].Value, out var version) ? version : null;
    }

    /// <summary>Compares on major.minor.build only (assembly versions carry a 4th revision part).</summary>
    public static bool IsNewer(Version candidate, Version current) =>
        Normalize(candidate).CompareTo(Normalize(current)) > 0;

    private static Version Normalize(Version v) => new(v.Major, v.Minor, Math.Max(0, v.Build));
}
