using System.Text.RegularExpressions;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Parsing;

/// <summary>Parses "powercfg /list". Works on localized Windows: only the GUID, "(name)" and the "*" marker are used.</summary>
public static partial class PowerCfgParser
{
    [GeneratedRegex(@"(?<id>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\s*(\((?<name>[^)]*)\))?\s*(?<active>\*)?")]
    private static partial Regex SchemeLine();

    public static IReadOnlyList<PowerPlan> ParseList(string output)
    {
        var plans = new List<PowerPlan>();
        foreach (var line in output.Split('\n'))
        {
            var m = SchemeLine().Match(line);
            if (!m.Success) continue;
            var id = Guid.Parse(m.Groups["id"].Value);
            var name = m.Groups["name"].Success ? m.Groups["name"].Value.Trim() : id.ToString();
            plans.Add(new PowerPlan(id, name, m.Groups["active"].Success));
        }
        return plans;
    }
}
