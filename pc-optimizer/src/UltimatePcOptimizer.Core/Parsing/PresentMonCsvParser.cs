using System.Globalization;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Parsing;

/// <summary>
/// Reads PresentMon CSV output. Supports PresentMon 2.x columns (CPUStartTime, FrameTime — ms)
/// and the 1.x / --v1_metrics columns (TimeInSeconds, MsBetweenPresents).
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

        var frame = Find("FrameTime");
        var time = Find("CPUStartTime");
        if (frame >= 0 && time >= 0)
        {
            (_frameTimeIndex, _timeIndex, _timeInMilliseconds) = (frame, time, true);
            return;
        }

        frame = Find("MsBetweenPresents");
        time = Find("TimeInSeconds");
        if (frame >= 0 && time >= 0) (_frameTimeIndex, _timeIndex, _timeInMilliseconds) = (frame, time, false);
    }
}
