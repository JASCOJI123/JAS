namespace UltimatePcOptimizer.Core.Analysis;

public enum ThrottleState { Unknown, None, PowerLimited, ThermalWarning, ThermalThrottling }

public sealed record ThrottleAssessment(ThrottleState State, string Message);

/// <summary>
/// Heuristic throttling detection from temperature, clock and load. Sensors do not expose the
/// throttle flag uniformly across vendors, so results are presented as "likely".
/// </summary>
public static class ThrottlingAnalyzer
{
    public const double CpuThrottleTemperatureC = 95;
    public const double CpuWarningTemperatureC = 88;
    public const double GpuThrottleTemperatureC = 88;
    public const double GpuWarningTemperatureC = 82;

    public static ThrottleAssessment AnalyzeCpu(double? temperatureC, double? currentClockMhz, double maxClockMhz, double loadPercent, bool onBattery)
    {
        var clockRatio = currentClockMhz is > 0 && maxClockMhz > 0 ? currentClockMhz.Value / maxClockMhz : (double?)null;

        if (temperatureC >= CpuThrottleTemperatureC)
            return new(ThrottleState.ThermalThrottling, $"CPU {temperatureC:0}°C — termal throttling ehtimoli yuqori. Sovutishni tekshiring.");
        if (temperatureC >= CpuWarningTemperatureC && loadPercent >= 70 && clockRatio < 0.85)
            return new(ThrottleState.ThermalThrottling, $"CPU {temperatureC:0}°C va chastota pasaygan — termal throttling.");
        if (temperatureC >= CpuWarningTemperatureC)
            return new(ThrottleState.ThermalWarning, $"CPU harorati yuqori: {temperatureC:0}°C.");
        if (loadPercent >= 70 && clockRatio < 0.6)
            return new(ThrottleState.PowerLimited, onBattery
                ? "Batareyada ishlamoqda — CPU chastotasi quvvatni tejash uchun cheklangan."
                : "Yuklanish ostida CPU chastotasi past — quvvat cheklovi (Power Plan / BIOS) bo'lishi mumkin.");
        if (temperatureC is null && clockRatio is null)
            return new(ThrottleState.Unknown, "Sensor ma'lumoti mavjud emas.");
        return new(ThrottleState.None, "Throttling aniqlanmadi.");
    }

    public static ThrottleAssessment AnalyzeGpu(double? temperatureC, double? loadPercent)
    {
        if (temperatureC is null) return new(ThrottleState.Unknown, "GPU harorat sensori mavjud emas.");
        if (temperatureC >= GpuThrottleTemperatureC)
            return new(ThrottleState.ThermalThrottling, $"GPU {temperatureC:0}°C — termal throttling ehtimoli yuqori.");
        if (temperatureC >= GpuWarningTemperatureC && loadPercent >= 80)
            return new(ThrottleState.ThermalWarning, $"GPU harorati yuqori: {temperatureC:0}°C.");
        return new(ThrottleState.None, "Throttling aniqlanmadi.");
    }
}
