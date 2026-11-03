namespace UltimatePcOptimizer.Core.Safety;

/// <summary>
/// Guards file deletion. Files are deleted only inside explicitly approved roots, never through
/// links, and only when they are old enough that no running program is likely to need them.
/// </summary>
public sealed class CleanupPathPolicy
{
    private readonly IReadOnlyList<string> _approvedRoots;

    public CleanupPathPolicy(IEnumerable<string> approvedRoots, TimeSpan minimumAge)
    {
        _approvedRoots = approvedRoots
            .Where(r => !string.IsNullOrWhiteSpace(r))
            .Select(NormalizeDirectory)
            .Where(r => !IsForbiddenRoot(r))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
        MinimumAge = minimumAge;
    }

    public TimeSpan MinimumAge { get; }

    public IReadOnlyList<string> ApprovedRoots => _approvedRoots;

    public bool IsInsideApprovedRoot(string path)
    {
        if (string.IsNullOrWhiteSpace(path)) return false;
        string full;
        try { full = Path.GetFullPath(path); }
        catch (Exception ex) when (ex is ArgumentException or NotSupportedException or PathTooLongException) { return false; }

        return _approvedRoots.Any(root =>
            full.StartsWith(root, StringComparison.OrdinalIgnoreCase) && full.Length > root.Length);
    }

    public bool IsOldEnough(DateTime lastWriteUtc, DateTime nowUtc) => nowUtc - lastWriteUtc >= MinimumAge;

    private static readonly HashSet<string> ForbiddenRelative = new(StringComparer.OrdinalIgnoreCase)
    {
        "", @"\windows", @"\windows\system32", @"\windows\syswow64", @"\program files",
        @"\program files (x86)", @"\users", @"\programdata",
    };

    /// <summary>Drive roots, the Windows directory, Program Files and bare user profiles can never be approved.</summary>
    public static bool IsForbiddenRoot(string directory)
    {
        var path = directory.Replace('/', '\\').TrimEnd('\\');
        if (path.Length == 0) return true;
        var rest = path.Length >= 2 && path[1] == ':' ? path[2..] : path;
        if (ForbiddenRelative.Contains(rest)) return true;
        var parts = rest.Split('\\', StringSplitOptions.RemoveEmptyEntries);
        return parts.Length == 2 && parts[0].Equals("users", StringComparison.OrdinalIgnoreCase);
    }

    private static string NormalizeDirectory(string path)
    {
        var full = Path.GetFullPath(path);
        return full.EndsWith(Path.DirectorySeparatorChar) ? full : full + Path.DirectorySeparatorChar;
    }
}
