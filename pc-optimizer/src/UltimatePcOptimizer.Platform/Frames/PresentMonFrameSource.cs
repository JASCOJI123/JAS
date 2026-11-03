using System.Diagnostics;
using System.Globalization;
using System.Runtime.CompilerServices;
using System.Threading.Channels;
using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Parsing;

namespace UltimatePcOptimizer.Platform.Frames;

/// <summary>
/// FPS capture through Intel PresentMon (open source, ETW based). PresentMon only observes Windows
/// present events; it does not inject into the game, so it is safe with anti-cheat systems.
/// The PresentMon console executable is expected in the "tools" folder next to the application.
/// </summary>
public sealed class PresentMonFrameSource(ILogger<PresentMonFrameSource> logger) : IFrameTimeSource
{
    private const string SessionName = "UltimatePcOptimizer";

    private static string ToolsDirectory => Path.Combine(AppContext.BaseDirectory, "tools");

    private static string? ExecutablePath =>
        Directory.Exists(ToolsDirectory)
            ? Directory.EnumerateFiles(ToolsDirectory, "PresentMon*.exe").OrderDescending().FirstOrDefault()
            : null;

    public bool IsAvailable => ExecutablePath is not null;

    public string? UnavailableReason => IsAvailable
        ? null
        : $"PresentMon topilmadi. github.com/GameTechDev/PresentMon dan console versiyasini yuklab, {ToolsDirectory} papkasiga joylang.";

    public async IAsyncEnumerable<FrameSample> CaptureAsync(int processId, [EnumeratorCancellation] CancellationToken ct)
    {
        var exe = ExecutablePath ?? throw new InvalidOperationException(UnavailableReason);
        var psi = new ProcessStartInfo(exe)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        foreach (var arg in new[]
                 {
                     "--process_id", processId.ToString(CultureInfo.InvariantCulture),
                     "--output_stdout", "--no_console_stats", "--terminate_on_proc_exit",
                     "--stop_existing_session", "--session_name", SessionName,
                 })
            psi.ArgumentList.Add(arg);

        using var process = Process.Start(psi) ?? throw new InvalidOperationException("PresentMon could not be started.");
        var channel = Channel.CreateUnbounded<FrameSample>(new UnboundedChannelOptions { SingleReader = true, SingleWriter = true });
        var parser = new PresentMonCsvParser();

        var reader = Task.Run(async () =>
        {
            try
            {
                while (await process.StandardOutput.ReadLineAsync(ct).ConfigureAwait(false) is { } line)
                {
                    if (parser.ParseLine(line) is { } sample) channel.Writer.TryWrite(sample);
                }
            }
            catch (OperationCanceledException) { }
            finally
            {
                channel.Writer.TryComplete();
            }
        }, CancellationToken.None);

        try
        {
            await foreach (var sample in channel.Reader.ReadAllAsync(ct).ConfigureAwait(false))
                yield return sample;
        }
        finally
        {
            if (!process.HasExited)
            {
                try { process.Kill(); } catch (InvalidOperationException) { }
            }
            await reader.ConfigureAwait(false);
            logger.LogInformation("PresentMon capture for PID {Pid} stopped", processId);
        }
    }
}
