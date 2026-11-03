using System.Diagnostics;
using System.Text;

namespace UltimatePcOptimizer.Platform.Native;

/// <summary>Runs Windows tools (powercfg, dism, wevtutil) with fixed argument lists — never through a shell.</summary>
internal static class ProcessRunner
{
    public sealed record Result(int ExitCode, string Output, string Error);

    public static async Task<Result> RunAsync(string fileName, IEnumerable<string> arguments, TimeSpan timeout, CancellationToken ct = default)
    {
        var psi = new ProcessStartInfo(ResolveSystemTool(fileName))
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };
        foreach (var a in arguments) psi.ArgumentList.Add(a);

        using var process = Process.Start(psi) ?? throw new InvalidOperationException($"Could not start {fileName}.");
        using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeoutCts.CancelAfter(timeout);

        var stdout = process.StandardOutput.ReadToEndAsync(timeoutCts.Token);
        var stderr = process.StandardError.ReadToEndAsync(timeoutCts.Token);
        try
        {
            await process.WaitForExitAsync(timeoutCts.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            try { process.Kill(entireProcessTree: true); } catch (InvalidOperationException) { }
            throw;
        }
        return new Result(process.ExitCode, await stdout.ConfigureAwait(false), await stderr.ConfigureAwait(false));
    }

    public static Result Run(string fileName, IEnumerable<string> arguments, TimeSpan timeout) =>
        RunAsync(fileName, arguments, timeout).GetAwaiter().GetResult();

    /// <summary>Always launch tools from System32 so a planted executable in the working directory cannot be picked up.</summary>
    private static string ResolveSystemTool(string fileName) =>
        Path.IsPathRooted(fileName) ? fileName : Path.Combine(Environment.SystemDirectory, fileName);
}
