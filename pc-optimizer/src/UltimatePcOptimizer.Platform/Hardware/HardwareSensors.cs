using LibreHardwareMonitor.Hardware;
using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Hardware;

public sealed record SensorReadings(
    double? CpuTemperatureC,
    double? CpuClockMhz,
    double? GpuTemperatureC,
    double? GpuLoadPercent,
    double? GpuClockMhz,
    double? VramUsedMb,
    double? VramTotalMb);

/// <summary>
/// Temperature, clock and GPU sensors through LibreHardwareMonitor. CPU sensors need administrator
/// rights and the LibreHardwareMonitor driver; when unavailable the values stay null and the UI shows "N/A".
/// </summary>
public sealed class HardwareSensors : IDisposable
{
    private readonly Computer _computer;
    private readonly Lock _lock = new();
    private readonly ILogger<HardwareSensors> _logger;
    private bool _opened;

    public HardwareSensors(ILogger<HardwareSensors> logger)
    {
        _logger = logger;
        _computer = new Computer { IsCpuEnabled = true, IsGpuEnabled = true };
    }

    public SensorReadings Read()
    {
        lock (_lock)
        {
            try
            {
                if (!_opened)
                {
                    _computer.Open();
                    _opened = true;
                }
                _computer.Accept(new UpdateVisitor());
                return Collect();
            }
            catch (Exception ex)
            {
                _logger.LogDebug(ex, "Sensor read failed");
                return new SensorReadings(FallbackCpuTemperature(), null, null, null, null, null, null);
            }
        }
    }

    private SensorReadings Collect()
    {
        double? cpuTemp = null, cpuClock = null, gpuTemp = null, gpuLoad = null, gpuClock = null, vramUsed = null, vramTotal = null;
        var gpuFound = false;

        foreach (var hw in _computer.Hardware)
        {
            var sensors = hw.Sensors.Concat(hw.SubHardware.SelectMany(s => s.Sensors)).ToList();
            if (hw.HardwareType == HardwareType.Cpu)
            {
                cpuTemp ??= Value(sensors, SensorType.Temperature, "CPU Package", "Core (Tctl/Tdie)", "Core (Tctl)", "Core Average", "Core Max");
                var clocks = sensors.Where(s => s.SensorType == SensorType.Clock && s.Name.StartsWith("Core #", StringComparison.Ordinal) && s.Value > 0)
                    .Select(s => (double)s.Value!.Value).ToList();
                if (clocks.Count > 0) cpuClock ??= clocks.Average();
            }
            else if (!gpuFound && hw.HardwareType is HardwareType.GpuNvidia or HardwareType.GpuAmd or HardwareType.GpuIntel)
            {
                // Prefer the discrete GPU: LibreHardwareMonitor lists NVIDIA/AMD before Intel integrated graphics.
                gpuFound = hw.HardwareType != HardwareType.GpuIntel;
                gpuTemp = Value(sensors, SensorType.Temperature, "GPU Core", "GPU Hot Spot") ?? gpuTemp;
                gpuLoad = Value(sensors, SensorType.Load, "GPU Core", "D3D 3D") ?? gpuLoad;
                gpuClock = Value(sensors, SensorType.Clock, "GPU Core") ?? gpuClock;
                vramUsed = Value(sensors, SensorType.SmallData, "GPU Memory Used", "D3D Dedicated Memory Used") ?? vramUsed;
                vramTotal = Value(sensors, SensorType.SmallData, "GPU Memory Total", "D3D Dedicated Memory Total") ?? vramTotal;
            }
        }

        return new SensorReadings(cpuTemp ?? FallbackCpuTemperature(), cpuClock, gpuTemp, gpuLoad, gpuClock, vramUsed, vramTotal);
    }

    private static double? Value(IEnumerable<ISensor> sensors, SensorType type, params string[] names)
    {
        foreach (var name in names)
        {
            var sensor = sensors.FirstOrDefault(s => s.SensorType == type && s.Name == name && s.Value.HasValue);
            if (sensor?.Value is { } v && !float.IsNaN(v)) return v;
        }
        return null;
    }

    /// <summary>ACPI thermal zone — less accurate, used only when LibreHardwareMonitor has no CPU sensor.</summary>
    private static double? FallbackCpuTemperature()
    {
        var zone = Wmi.Query("SELECT CurrentTemperature FROM MSAcpi_ThermalZoneTemperature", @"root\wmi").FirstOrDefault();
        var tenthsKelvin = zone?.U64("CurrentTemperature") ?? 0;
        if (tenthsKelvin == 0) return null;
        var celsius = tenthsKelvin / 10.0 - 273.15;
        return celsius is > 0 and < 125 ? celsius : null;
    }

    public void Dispose()
    {
        lock (_lock)
        {
            if (_opened) _computer.Close();
            _opened = false;
        }
    }

    private sealed class UpdateVisitor : IVisitor
    {
        public void VisitComputer(IComputer computer) => computer.Traverse(this);

        public void VisitHardware(IHardware hardware)
        {
            hardware.Update();
            foreach (var sub in hardware.SubHardware) sub.Accept(this);
        }

        public void VisitSensor(ISensor sensor) { }
        public void VisitParameter(IParameter parameter) { }
    }
}
