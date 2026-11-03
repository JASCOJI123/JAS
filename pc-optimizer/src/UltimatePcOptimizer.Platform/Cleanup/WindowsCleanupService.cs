using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Safety;
using UltimatePcOptimizer.Platform.Native;

namespace UltimatePcOptimizer.Platform.Cleanup;

/// <summary>
/// Windows Cleaner. Each category maps to fixed, well-known junk locations. Files are deleted only
/// through <see cref="CleanupPathPolicy"/>: inside approved roots, no links, old enough, not locked.
/// </summary>
public sealed class WindowsCleanupService(ILogger<WindowsCleanupService> logger) : ICleanupService
{
    private sealed record Target(IReadOnlyList<string> Roots, TimeSpan MinimumAge, string Pattern = "*", Func<bool>? Blocked = null, string? BlockedReason = null);

    private static readonly string[] BrowserProcesses = ["chrome", "msedge", "firefox", "brave", "opera", "opera_gx", "vivaldi"];

    private static string Local => Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
    private static string CommonData => Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData);
    private static string WindowsDir => Environment.GetFolderPath(Environment.SpecialFolder.Windows);

    public Task<CleanupScanResult> ScanAsync(CleanupCategory category, CancellationToken ct = default) => Task.Run(() => Scan(category, ct), ct);

    public Task<CleanupResult> CleanAsync(CleanupCategory category, CancellationToken ct = default) => category switch
    {
        CleanupCategory.RecycleBin => Task.Run(EmptyRecycleBin, ct),
        CleanupCategory.WindowsUpdateCleanup => RunComponentCleanupAsync(ct),
        _ => Task.Run(() => Clean(category, ct), ct),
    };

    private static Target? GetTarget(CleanupCategory category) => category switch
    {
        CleanupCategory.SystemTemporaryFiles => new([
            Path.Combine(CommonData, @"Microsoft\Windows\WER\ReportArchive"),
            Path.Combine(CommonData, @"Microsoft\Windows\WER\ReportQueue"),
            Path.Combine(Local, @"Microsoft\Windows\WER\ReportArchive"),
            Path.Combine(Local, @"Microsoft\Windows\WER\ReportQueue"),
            Path.Combine(Local, "CrashDumps"),
        ], TimeSpan.Zero),
        CleanupCategory.WindowsTemp => new([Path.Combine(WindowsDir, "Temp")], TimeSpan.FromHours(24)),
        CleanupCategory.UserTemp => new([Path.GetTempPath()], TimeSpan.FromHours(24)),
        CleanupCategory.DirectXShaderCache => new([
            Path.Combine(Local, "D3DSCache"),
            Path.Combine(Local, @"NVIDIA\DXCache"),
            Path.Combine(Local, @"NVIDIA\GLCache"),
            Path.Combine(Local, @"AMD\DxCache"),
            Path.Combine(Local, @"AMD\DxcCache"),
            Path.Combine(Local, @"AMD\GLCache"),
            Path.Combine(Local, @"Intel\ShaderCache"),
        ], TimeSpan.Zero),
        CleanupCategory.ThumbnailCache => new([Path.Combine(Local, @"Microsoft\Windows\Explorer")], TimeSpan.Zero, "thumbcache_*.db"),
        CleanupCategory.BrowserCache => new(BrowserCacheRoots(), TimeSpan.Zero, "*",
            () => BrowserProcesses.Any(IsRunning), "Brauzer ochiq — avval uni yoping."),
        _ => null,
    };

    private static bool IsRunning(string processName)
    {
        var processes = Process.GetProcessesByName(processName);
        foreach (var p in processes) p.Dispose();
        return processes.Length > 0;
    }

    private static List<string> BrowserCacheRoots()
    {
        var roots = new List<string>();
        foreach (var userData in new[] { @"Google\Chrome\User Data", @"Microsoft\Edge\User Data", @"BraveSoftware\Brave-Browser\User Data", @"Vivaldi\User Data" })
        {
            var dir = Path.Combine(Local, userData);
            if (!Directory.Exists(dir)) continue;
            foreach (var profile in Directory.EnumerateDirectories(dir))
            {
                var name = Path.GetFileName(profile);
                if (name != "Default" && !name.StartsWith("Profile ", StringComparison.Ordinal)) continue;
                roots.Add(Path.Combine(profile, "Cache"));
                roots.Add(Path.Combine(profile, "Code Cache"));
                roots.Add(Path.Combine(profile, "GPUCache"));
            }
        }
        var firefox = Path.Combine(Local, @"Mozilla\Firefox\Profiles");
        if (Directory.Exists(firefox))
            roots.AddRange(Directory.EnumerateDirectories(firefox).Select(p => Path.Combine(p, "cache2")));
        return roots;
    }

    private CleanupScanResult Scan(CleanupCategory category, CancellationToken ct)
    {
        switch (category)
        {
            case CleanupCategory.RecycleBin:
                var info = new NativeMethods.ShQueryRbInfo { Size = (uint)Marshal.SizeOf<NativeMethods.ShQueryRbInfo>() };
                return NativeMethods.SHQueryRecycleBin(null, ref info) == 0
                    ? new CleanupScanResult(category, (ulong)Math.Max(0, info.SizeBytes), (int)info.NumItems, false, null)
                    : new CleanupScanResult(category, 0, 0, false, "Savat ma'lumotini o'qib bo'lmadi");
            case CleanupCategory.WindowsUpdateCleanup:
                // DISM decides what is removable; the download cache size is shown as an estimate only.
                var (size, count) = Measure(new Target([Path.Combine(WindowsDir, @"SoftwareDistribution\Download")], TimeSpan.FromDays(10)), ct);
                return new CleanupScanResult(category, size, count, true, CleanupCategoryInfo.Advice(category));
        }

        var target = GetTarget(category);
        if (target is null) return new CleanupScanResult(category, 0, 0, false, null);
        var (bytes, files) = Measure(target, ct);
        var note = target.Blocked?.Invoke() == true ? target.BlockedReason : CleanupCategoryInfo.Advice(category);
        return new CleanupScanResult(category, bytes, files, false, note);
    }

    private (ulong Bytes, int Files) Measure(Target target, CancellationToken ct)
    {
        var policy = new CleanupPathPolicy(target.Roots, target.MinimumAge);
        ulong bytes = 0;
        var files = 0;
        foreach (var file in EnumerateCandidates(policy, target.Pattern, ct))
        {
            bytes += (ulong)file.Length;
            files++;
        }
        return (bytes, files);
    }

    private CleanupResult Clean(CleanupCategory category, CancellationToken ct)
    {
        var target = GetTarget(category) ?? throw new ArgumentOutOfRangeException(nameof(category));
        if (target.Blocked?.Invoke() == true) return new CleanupResult(category, 0, 0, 0, target.BlockedReason);

        var policy = new CleanupPathPolicy(target.Roots, target.MinimumAge);
        ulong freed = 0;
        int deleted = 0, skipped = 0;

        foreach (var file in EnumerateCandidates(policy, target.Pattern, ct).ToList())
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                var length = file.Length;
                if (file.IsReadOnly) file.IsReadOnly = false;
                file.Delete();
                freed += (ulong)length;
                deleted++;
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
                skipped++; // in use or protected: leave it alone
            }
        }

        RemoveEmptyDirectories(policy);
        logger.LogInformation("Cleaned {Category}: {Deleted} files, {Freed} bytes, {Skipped} skipped", category, deleted, freed, skipped);
        return new CleanupResult(category, freed, deleted, skipped, null);
    }

    private static IEnumerable<FileInfo> EnumerateCandidates(CleanupPathPolicy policy, string pattern, CancellationToken ct)
    {
        var now = DateTime.UtcNow;
        foreach (var root in policy.ApprovedRoots)
        {
            if (!Directory.Exists(root)) continue;
            var pending = new Stack<DirectoryInfo>();
            pending.Push(new DirectoryInfo(root));
            while (pending.Count > 0)
            {
                ct.ThrowIfCancellationRequested();
                var dir = pending.Pop();
                FileSystemInfo[] children;
                try { children = dir.GetFileSystemInfos(); }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { continue; }

                foreach (var child in children)
                {
                    // Never follow junctions or symlinks: they can point outside the approved root.
                    if (child.Attributes.HasFlag(FileAttributes.ReparsePoint)) continue;
                    if (child is DirectoryInfo sub) { pending.Push(sub); continue; }
                    if (child is not FileInfo file) continue;
                    if (pattern != "*" && !FileNameMatches(file.Name, pattern)) continue;
                    if (file.Attributes.HasFlag(FileAttributes.System)) continue;
                    if (!policy.IsInsideApprovedRoot(file.FullName) || !policy.IsOldEnough(file.LastWriteTimeUtc, now)) continue;
                    yield return file;
                }
            }
        }
    }

    private static bool FileNameMatches(string name, string pattern)
    {
        var star = pattern.IndexOf('*');
        if (star < 0) return name.Equals(pattern, StringComparison.OrdinalIgnoreCase);
        return name.StartsWith(pattern[..star], StringComparison.OrdinalIgnoreCase) &&
               name.EndsWith(pattern[(star + 1)..], StringComparison.OrdinalIgnoreCase);
    }

    private static void RemoveEmptyDirectories(CleanupPathPolicy policy)
    {
        foreach (var root in policy.ApprovedRoots.Where(Directory.Exists))
        {
            IEnumerable<string> dirs;
            try { dirs = Directory.EnumerateDirectories(root, "*", SearchOption.AllDirectories).OrderByDescending(d => d.Length).ToList(); }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { continue; }

            foreach (var dir in dirs)
            {
                try
                {
                    var info = new DirectoryInfo(dir);
                    if (info.Attributes.HasFlag(FileAttributes.ReparsePoint) || !policy.IsInsideApprovedRoot(dir)) continue;
                    if (!info.EnumerateFileSystemInfos().Any()) info.Delete();
                }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
            }
        }
    }

    private static CleanupResult EmptyRecycleBin()
    {
        var info = new NativeMethods.ShQueryRbInfo { Size = (uint)Marshal.SizeOf<NativeMethods.ShQueryRbInfo>() };
        NativeMethods.SHQueryRecycleBin(null, ref info);
        var hr = NativeMethods.SHEmptyRecycleBin(0, null, NativeMethods.SherbNoConfirmation | NativeMethods.SherbNoProgressUi | NativeMethods.SherbNoSound);
        // S_OK, or E_UNEXPECTED when already empty.
        return hr is 0 or unchecked((int)0x8000FFFF)
            ? new CleanupResult(CleanupCategory.RecycleBin, (ulong)Math.Max(0, info.SizeBytes), (int)info.NumItems, 0, null)
            : new CleanupResult(CleanupCategory.RecycleBin, 0, 0, (int)info.NumItems, $"Savatni bo'shatib bo'lmadi (0x{hr:X8})");
    }

    /// <summary>Uses Windows' own servicing tool; the result is measured as free space gained on the system drive.</summary>
    private async Task<CleanupResult> RunComponentCleanupAsync(CancellationToken ct)
    {
        var root = Path.GetPathRoot(WindowsDir)!;
        var before = new DriveInfo(root).AvailableFreeSpace;
        var result = await ProcessRunner.RunAsync("dism.exe", ["/Online", "/Cleanup-Image", "/StartComponentCleanup", "/Quiet", "/NoRestart"],
            TimeSpan.FromMinutes(45), ct).ConfigureAwait(false);
        var freed = (ulong)Math.Max(0, new DriveInfo(root).AvailableFreeSpace - before);
        logger.LogInformation("DISM StartComponentCleanup exit {Code}, freed {Freed}", result.ExitCode, freed);
        return new CleanupResult(CleanupCategory.WindowsUpdateCleanup, freed, 0, 0,
            result.ExitCode == 0 ? null : $"DISM kodi {result.ExitCode}");
    }
}
