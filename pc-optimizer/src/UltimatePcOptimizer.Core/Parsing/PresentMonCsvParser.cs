using System.Globalization;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Parsing;

/// <summary>
/// Reads PresentMon CSV output: 2.6+ default (TimeInMs, MsBetweenPresents), --v2_metrics
/// (CPUStartTime, FrameTime) and 1.x / --v1_metrics (TimeInSeconds, MsBetweenPresents).
/// </summary>
public sealed class PresentMonCsvParser
{
    private int _timeIndex = -1;
    private int _frameTimeIndex = -1;
    private bool _timeInMilliseconds;

    public bool HasHeader => _frameTimeIndex >= 0;

    /// <summary>Returns a sample for data lines, or null for the header and unparseable lines.</summary>
    public FrameSample? ParseLine(string line)
    {
        if (string.IsNullOrWhiteSpace(line)) return null;
        var cells = line.Split(',');

        if (!HasHeader)
        {
            TryReadHeader(cells);
            return null;
        }

        if (cells.Length <= Math.Max(_timeIndex, _frameTimeIndex)) return null;
        if (!double.TryParse(cells[_frameTimeIndex], NumberStyles.Float, CultureInfo.InvariantCulture, out var frameTime)) return null;
        if (!double.TryParse(cells[_timeIndex], NumberStyles.Float, CultureInfo.InvariantCulture, out var time)) return null;
        return new FrameSample(_timeInMilliseconds ? time / 1000.0 : time, frameTime);
    }

    private void TryReadHeader(string[] cells)
    {
        int Find(string name) => Array.FindIndex(cells, c => c.Trim().Equals(name, StringComparison.OrdinalIgnoreCase));

        // Frame time column: 2.x --v2_metrics uses FrameTime; 1.x and the 2.6+ default use MsBetweenPresents.
        var frame = Find("FrameTime");
        if (frame < 0) frame = Find("MsBetweenPresents");
        if (frame < 0) return;

        // Timestamp column, in the unit its name states.
        foreach (var (name, inMs) in new[] { ("CPUStartTime", true), ("TimeInMs", true), ("CPUStartTimeInMs", true), ("TimeInSeconds", false), ("CPUStartTimeInSeconds", false) })
        {
            var time = Find(name);
            if (time < 0) continue;
            (_frameTimeIndex, _timeIndex, _timeInMilliseconds) = (frame, time, inMs);
            return;
        }
    }
}
