using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Analysis;

public enum HealthStatus { Excellent, Good, Fair, Poor }

public sealed record HealthFactor(string Name, int Penalty, string Detail);

public sealed record HealthScore(int Score, HealthStatus Status, IReadOnlyList<HealthFactor> Factors)
{
    public string StatusText => Status switch
    {
        HealthStatus.Excellent => "A'lo",
        HealthStatus.Good => "Yaxshi",
        HealthStatus.Fair => "O'rtacha",
        _ => "Yomon",
    };
}

public sealed record HealthInputs(
    SystemSnapshot Snapshot,
    int EnabledHeavyStartupItems,
    ulong JunkBytes,
    bool GameModeEnabled,
    bool SystemDiskIsHdd,
    double SystemDiskFreePercent,
    ThrottleState CpuThrottle,
    ThrottleState GpuThrottle);

/// <summary>System Health Score from 0 to 100. Each factor subtracts a capped penalty.</summary>
public static class HealthScoreCalculator
{
    public static HealthScore Calculate(HealthInputs input)
    {
        var factors = new List<HealthFactor>();
        void Add(string name, int penalty, string detail)
        {
            if (penalty > 0) factors.Add(new HealthFactor(name, penalty, detail));
        }

        var ram = input.Snapshot.Memory.UsedPercent;
        Add("RAM", ram >= 90 ? 15 : ram >= 80 ? 8 : 0, $"RAM {ram:0}% band");

        var cpu = input.Snapshot.CpuLoadPercent;
        Add("CPU yuklanishi", cpu >= 90 ? 10 : cpu >= 70 ? 5 : 0, $"Bo'sh turganda CPU {cpu:0}%");

        Add("CPU harorati", input.CpuThrottle switch
        {
            ThrottleState.ThermalThrottling => 15,
            ThrottleState.ThermalWarning => 7,
            ThrottleState.PowerLimited => 4,
            _ => 0,
        }, "CPU throttling / harorat");

        Add("GPU harorati", input.GpuThrottle switch
        {
            ThrottleState.ThermalThrottling => 12,
            ThrottleState.ThermalWarning => 5,
            _ => 0,
        }, "GPU throttling / harorat");

        Add("Startup", Math.Min(input.EnabledHeavyStartupItems * 3, 12), $"{input.EnabledHeavyStartupItems} ta og'ir startup dastur");

        const double gb = 1024d * 1024 * 1024;
        var junkGb = input.JunkBytes / gb;
        Add("Keraksiz fayllar", junkGb >= 10 ? 10 : junkGb >= 3 ? 6 : junkGb >= 1 ? 3 : 0, $"{junkGb:0.0} GB keraksiz fayl");

        Add("Game Mode", input.GameModeEnabled ? 0 : 5, "Windows Game Mode o'chiq");
        Add("Disk", input.SystemDiskIsHdd ? 10 : 0, "Tizim diski HDD");
        Add("Disk joyi", input.SystemDiskFreePercent < 10 ? 10 : input.SystemDiskFreePercent < 20 ? 4 : 0,
            $"Tizim diskida {input.SystemDiskFreePercent:0}% bo'sh joy");

        var score = Math.Clamp(100 - factors.Sum(f => f.Penalty), 0, 100);
        var status = score >= 85 ? HealthStatus.Excellent : score >= 70 ? HealthStatus.Good : score >= 50 ? HealthStatus.Fair : HealthStatus.Poor;
        return new HealthScore(score, status, factors);
    }
}
