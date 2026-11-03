using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Parsing;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Power;

public sealed class WindowsPowerPlanService : IPowerPlanService
{
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(15);

    public IReadOnlyList<PowerPlan> GetPlans()
    {
        var result = ProcessRunner.Run("powercfg.exe", ["/list"], Timeout);
        return PowerCfgParser.ParseList(result.Output);
    }

    public PowerPlan? GetActivePlan() => GetPlans().FirstOrDefault(p => p.IsActive);

    public void SetActivePlan(Guid planId)
    {
        // The GUID is formatted by us, so no user text ever reaches the command line.
        var result = ProcessRunner.Run("powercfg.exe", ["/setactive", planId.ToString("D")], Timeout);
        if (result.ExitCode != 0)
            throw new InvalidOperationException($"powercfg failed ({result.ExitCode}): {result.Error.Trim()}{result.Output.Trim()}");
    }
}
