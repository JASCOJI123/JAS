using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Games;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Monitoring;

namespace UltimatePcOptimizer.Tests;

public class AnalysisTests
{
    [Fact]
    public void Frame_stats_compute_average_and_one_percent_low()
    {
        // 99 frames at 10 ms (100 FPS) and 1 frame at 50 ms.
        var frames = Enumerable.Repeat(10.0, 99).Append(50.0).ToArray();
        var stats = FrameStatsCalculator.Compute(frames);

        Assert.Equal(100, stats.FrameCount);
        Assert.Equal(100 * 1000.0 / (99 * 10 + 50), stats.AverageFps, 3);
        Assert.Equal(20, stats.OnePercentLowFps, 3);
        Assert.Equal(50, stats.P99FrameTimeMs, 3);
    }

    [Fact]
    public void Rolling_calculator_drops_frames_outside_the_window()
    {
        var calc = new FrameStatsCalculator(TimeSpan.FromSeconds(2));
        for (var i = 0; i < 300; i++) calc.Add(new FrameSample(i * 0.01, 10));      // 0–3 s at 100 FPS
        var stats = calc.GetStats();

        Assert.InRange(stats.FrameCount, 199, 202);
        Assert.Equal(100, stats.CurrentFps, 1);
    }

    [Fact]
    public void Benchmark_comparison_reports_percentage_change()
    {
        BenchmarkResult Make(double avg) => new(0, "g", "x", DateTimeOffset.UtcNow, TimeSpan.FromSeconds(60),
            new FrameStats(100, 0, avg, avg / 2, avg / 3, 1000 / avg, 1000 / avg * 2, 0), 50, 80, 70, 70, 50);
        var comparison = new BenchmarkComparison(Make(100), Make(120));
        Assert.Equal(20, comparison.AverageFpsChangePercent, 3);
    }

    [Fact]
    public void Desktop_prefers_ultimate_then_high_performance()
    {
        var ultimate = new PowerPlan(Guid.NewGuid(), "Ultimate Performance", false);
        var plans = new List<PowerPlan>
        {
            new(KnownPowerPlans.Balanced, "Balanced", true),
            new(KnownPowerPlans.HighPerformance, "High performance", false),
        };
        Assert.Equal(KnownPowerPlans.HighPerformance, PowerPlanAdvisor.RecommendForPerformance(plans, false, false)!.Id);
        plans.Add(ultimate);
        Assert.Equal(ultimate.Id, PowerPlanAdvisor.RecommendForPerformance(plans, false, false)!.Id);
        Assert.Equal(KnownPowerPlans.Balanced, PowerPlanAdvisor.RecommendForPerformance(plans, true, onBattery: true)!.Id);
    }

    [Fact]
    public void Throttling_is_detected_from_temperature_and_clock()
    {
        Assert.Equal(ThrottleState.ThermalThrottling, ThrottlingAnalyzer.AnalyzeCpu(97, 4000, 4700, 90, false).State);
        Assert.Equal(ThrottleState.ThermalThrottling, ThrottlingAnalyzer.AnalyzeCpu(90, 3000, 4700, 90, false).State);
        Assert.Equal(ThrottleState.PowerLimited, ThrottlingAnalyzer.AnalyzeCpu(60, 1500, 4700, 90, true).State);
        Assert.Equal(ThrottleState.None, ThrottlingAnalyzer.AnalyzeCpu(60, 4500, 4700, 90, false).State);
        Assert.Equal(ThrottleState.Unknown, ThrottlingAnalyzer.AnalyzeGpu(null, 50).State);
    }

    [Fact]
    public void Memory_pressure_levels()
    {
        const ulong gb = 1024UL * 1024 * 1024;
        Assert.Equal(MemoryPressure.Low, MemoryAnalyzer.Assess(new MemoryUsage(16 * gb, 10 * gb, 8 * gb, 32 * gb), null).Pressure);
        Assert.Equal(MemoryPressure.High, MemoryAnalyzer.Assess(new MemoryUsage(16 * gb, 2 * gb, 20 * gb, 32 * gb), null).Pressure);
        var noPagefile = MemoryAnalyzer.Assess(new MemoryUsage(16 * gb, 10 * gb, 8 * gb, 16 * gb), new PageFileStatus(false, 0, 0, 0, false));
        Assert.Contains(noPagefile.Advice, a => a.Contains("Pagefile"));
    }

    [Fact]
    public void Health_score_penalises_problems()
    {
        var good = HealthScoreCalculator.Calculate(new HealthInputs(SystemSnapshot.Empty, 0, 0, true, false, 50, ThrottleState.None, ThrottleState.None));
        Assert.Equal(100, good.Score);
        Assert.Equal(HealthStatus.Excellent, good.Status);

        var bad = HealthScoreCalculator.Calculate(new HealthInputs(SystemSnapshot.Empty, 5, 20UL * 1024 * 1024 * 1024, false, true, 5,
            ThrottleState.ThermalThrottling, ThrottleState.ThermalThrottling));
        Assert.True(bad.Score < 50);
    }

    [Fact]
    public void Recommendations_flag_low_refresh_rate_and_hdd()
    {
        var hw = TestData.Hardware() with
        {
            Displays = [new DisplayInfo("d", 1920, 1080, 60, 165)],
            Disks = [new DiskInfo("WD Blue", DiskMediaType.Hdd, 1, true)],
        };
        var recs = RecommendationEngine.Build(new RecommendationContext(hw, true, true, null, false, new DateTime(2026, 1, 1)));
        Assert.Contains(recs, r => r.Title.Contains("60 Hz"));
        Assert.Contains(recs, r => r.Title.Contains("HDD"));
    }

    [Fact]
    public void Graphics_profile_matches_gpu_and_game()
    {
        var hw = TestData.Hardware(vramGb: 8);
        var valorant = GameCatalog.FindById("valorant");
        var profile = GraphicsProfileAdvisor.Build(hw, valorant, "VALORANT");

        Assert.Equal(GraphicsPreset.Medium, profile.Preset);
        Assert.Contains("Reflex", profile.LatencyAdvice);
        Assert.True(profile.TargetFps >= 144);

        var entry = GraphicsProfileAdvisor.Build(TestData.Hardware(vramGb: 4, gpuName: "AMD Radeon RX 6500 XT"), null, "Other");
        Assert.Equal(GraphicsPreset.Low, entry.Preset);
        Assert.Contains("FSR", entry.Upscaler);
    }

    [Theory]
    [InlineData("NVIDIA GeForce RTX 4070", GpuVendor.Nvidia)]
    [InlineData("AMD Radeon RX 7800 XT", GpuVendor.Amd)]
    [InlineData("Intel(R) UHD Graphics 770", GpuVendor.Intel)]
    public void Gpu_vendor_detection(string name, GpuVendor vendor) => Assert.Equal(vendor, GpuVendorDetector.FromName(name));

    [Fact]
    public void Startup_impact_estimation()
    {
        Assert.Equal(StartupImpact.High, StartupImpactEstimator.Estimate("Steam", @"C:\Steam\steam.exe -silent", null));
        Assert.Equal(StartupImpact.High, StartupImpactEstimator.Estimate("X", "x.exe", 400UL * 1024 * 1024));
        Assert.Equal(StartupImpact.Low, StartupImpactEstimator.Estimate("X", "x.exe", 10UL * 1024 * 1024));
        Assert.Equal(StartupImpact.NotMeasured, StartupImpactEstimator.Estimate("X", "x.exe", null));
    }
}
