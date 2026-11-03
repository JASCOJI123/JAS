using Microsoft.Win32;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Parsing;
using UltimatePcOptimizer.Core.Safety;

namespace UltimatePcOptimizer.Platform.Startup;

/// <summary>
/// Startup Manager. Enabling/disabling uses the same "StartupApproved" mechanism as Task Manager,
/// so the original Run entry is never deleted and Windows' own UI shows the same state.
/// </summary>
public sealed class WindowsStartupService(IProcessService processes) : IStartupService
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string ApprovedRoot = @"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved";

    private sealed record Source(StartupLocation Location, RegistryHive Hive, RegistryView View, string ApprovedSubKey, string? Folder);

    private static IEnumerable<Source> Sources()
    {
        yield return new(StartupLocation.CurrentUserRun, RegistryHive.CurrentUser, RegistryView.Default, "Run", null);
        yield return new(StartupLocation.LocalMachineRun, RegistryHive.LocalMachine, RegistryView.Registry64, "Run", null);
        yield return new(StartupLocation.LocalMachineRun32, RegistryHive.LocalMachine, RegistryView.Registry32, "Run32", null);
        yield return new(StartupLocation.CurrentUserStartupFolder, RegistryHive.CurrentUser, RegistryView.Default, "StartupFolder",
            Environment.GetFolderPath(Environment.SpecialFolder.Startup));
        yield return new(StartupLocation.CommonStartupFolder, RegistryHive.LocalMachine, RegistryView.Registry64, "StartupFolder",
            Environment.GetFolderPath(Environment.SpecialFolder.CommonStartup));
    }

    public IReadOnlyList<StartupEntry> GetEntries()
    {
        var memoryByPath = processes.GetProcesses()
            .Where(p => p.ExecutablePath is not null)
            .GroupBy(p => p.ExecutablePath!, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(g => g.Key, g => g.Aggregate(0UL, (s, p) => s + p.WorkingSetBytes), StringComparer.OrdinalIgnoreCase);

        var entries = new List<StartupEntry>();
        foreach (var source in Sources())
        {
            foreach (var (name, command) in ReadItems(source))
            {
                var exe = source.Folder is null ? CommandLineParser.ExtractExecutable(command) : command;
                ulong? memory = exe is not null && memoryByPath.TryGetValue(exe, out var m) ? m : null;
                var reason = StartupSafetyPolicy.GetProtectionReason(name, command);
                entries.Add(new StartupEntry(
                    CreateId(source.Location, name),
                    Path.GetFileNameWithoutExtension(name) is { Length: > 0 } display && source.Folder is not null ? display : name,
                    command,
                    exe,
                    source.Location,
                    ReadApproved(source, name) ?? true,
                    StartupImpactEstimator.Estimate(name, command, memory),
                    memory,
                    reason is not null,
                    reason));
            }
        }
        return entries;
    }

    public bool? IsEnabled(string entryId)
    {
        var (location, name) = ParseId(entryId);
        var source = Sources().First(s => s.Location == location);
        return ReadItems(source).Any(i => i.Name == name) ? ReadApproved(source, name) ?? true : null;
    }

    public void SetEnabled(string entryId, bool enabled)
    {
        var (location, name) = ParseId(entryId);
        var source = Sources().First(s => s.Location == location);
        if (!ReadItems(source).Any(i => i.Name == name)) throw new InvalidOperationException("Startup entry no longer exists.");

        // 12 bytes: first byte 0x02 = enabled, 0x03 = disabled, followed by the FILETIME of the change.
        var data = new byte[12];
        data[0] = enabled ? (byte)0x02 : (byte)0x03;
        if (!enabled) BitConverter.GetBytes(DateTime.UtcNow.ToFileTimeUtc()).CopyTo(data, 4);

        using var baseKey = RegistryKey.OpenBaseKey(source.Hive, source.View);
        using var key = baseKey.CreateSubKey($@"{ApprovedRoot}\{source.ApprovedSubKey}", writable: true);
        key.SetValue(name, data, RegistryValueKind.Binary);
    }

    private static IEnumerable<(string Name, string Command)> ReadItems(Source source)
    {
        if (source.Folder is not null)
        {
            if (!Directory.Exists(source.Folder)) yield break;
            foreach (var file in Directory.EnumerateFiles(source.Folder))
            {
                var fileName = Path.GetFileName(file);
                if (fileName.Equals("desktop.ini", StringComparison.OrdinalIgnoreCase)) continue;
                yield return (fileName, file);
            }
            yield break;
        }

        using var baseKey = RegistryKey.OpenBaseKey(source.Hive, source.View);
        using var key = baseKey.OpenSubKey(RunKey);
        if (key is null) yield break;
        foreach (var valueName in key.GetValueNames())
        {
            if (string.IsNullOrEmpty(valueName)) continue;
            if (key.GetValue(valueName) is string command) yield return (valueName, command);
        }
    }

    private static bool? ReadApproved(Source source, string name)
    {
        using var baseKey = RegistryKey.OpenBaseKey(source.Hive, source.View);
        using var key = baseKey.OpenSubKey($@"{ApprovedRoot}\{source.ApprovedSubKey}");
        if (key?.GetValue(name) is not byte[] { Length: > 0 } data) return null;
        return (data[0] & 0x01) == 0; // even = enabled (0x02, 0x06), odd = disabled (0x03, 0x07)
    }

    private static string CreateId(StartupLocation location, string name) => $"{location}|{name}";

    private static (StartupLocation Location, string Name) ParseId(string id)
    {
        var separator = id.IndexOf('|');
        if (separator <= 0 || !Enum.TryParse<StartupLocation>(id[..separator], out var location))
            throw new FormatException("Invalid startup entry id.");
        return (location, id[(separator + 1)..]);
    }
}
