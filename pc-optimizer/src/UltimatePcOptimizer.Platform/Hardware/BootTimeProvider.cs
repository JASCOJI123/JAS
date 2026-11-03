using System.Globalization;
using System.Text.RegularExpressions;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Hardware;

/// <summary>Reads the last boot duration from the Diagnostics-Performance event log (event 100). Requires admin.</summary>
public sealed partial class BootTimeProvider : IBootTimeProvider
{
    [GeneratedRegex(@"<Data Name=['""]BootTime['""]>(?<ms>\d+)</Data>")]
    private static partial Regex BootTimeData();

    public async Task<TimeSpan?> GetLastBootDurationAsync(CancellationToken ct = default)
    {
        try
        {
            var result = await ProcessRunner.RunAsync("wevtutil.exe",
                ["qe", "Microsoft-Windows-Diagnostics-Performance/Operational", "/q:*[System[(EventID=100)]]", "/c:1", "/rd:true", "/f:xml"],
                TimeSpan.FromSeconds(15), ct).ConfigureAwait(false);
            var match = BootTimeData().Match(result.Output);
            return match.Success ? TimeSpan.FromMilliseconds(long.Parse(match.Groups["ms"].Value, CultureInfo.InvariantCulture)) : null;
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception or OperationCanceledException)
        {
            return null;
        }
    }
}
