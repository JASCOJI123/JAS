using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Analysis;

public enum PowerPlanPreference { Balanced, HighPerformance, Maximum }

public static class PowerPlanAdvisor
{
    /// <summary>
    /// Plan for best game performance. Laptops on battery stay on Balanced; desktops prefer
    /// Ultimate Performance when the user has it, otherwise High Performance.
    /// Returns null when no better plan exists (e.g. Modern Standby PCs expose only Balanced).
    /// </summary>
    public static PowerPlan? RecommendForPerformance(IReadOnlyList<PowerPlan> plans, bool isLaptop, bool onBattery)
    {
        if (onBattery) return Find(plans, PowerPlanPreference.Balanced);
        return isLaptop
            ? Find(plans, PowerPlanPreference.HighPerformance)
            : Find(plans, PowerPlanPreference.Maximum) ?? Find(plans, PowerPlanPreference.HighPerformance);
    }

    public static PowerPlan? Find(IReadOnlyList<PowerPlan> plans, PowerPlanPreference preference) => preference switch
    {
        PowerPlanPreference.Balanced => plans.FirstOrDefault(p => p.Id == KnownPowerPlans.Balanced),
        PowerPlanPreference.HighPerformance => plans.FirstOrDefault(p => p.Id == KnownPowerPlans.HighPerformance),
        // Ultimate Performance is usually a duplicated scheme with its own GUID, so match by name as well.
        PowerPlanPreference.Maximum => plans.FirstOrDefault(p => p.Id == KnownPowerPlans.UltimatePerformance)
                                       ?? plans.FirstOrDefault(p => p.Name.Contains("Ultimate", StringComparison.OrdinalIgnoreCase)),
        _ => null,
    };

    /// <summary>Like <see cref="Find"/> but degrades gracefully: Maximum → High → Balanced.</summary>
    public static PowerPlan? FindWithFallback(IReadOnlyList<PowerPlan> plans, PowerPlanPreference preference) => preference switch
    {
        PowerPlanPreference.Maximum => Find(plans, PowerPlanPreference.Maximum) ?? FindWithFallback(plans, PowerPlanPreference.HighPerformance),
        PowerPlanPreference.HighPerformance => Find(plans, PowerPlanPreference.HighPerformance) ?? Find(plans, PowerPlanPreference.Balanced),
        _ => Find(plans, PowerPlanPreference.Balanced),
    };
}
