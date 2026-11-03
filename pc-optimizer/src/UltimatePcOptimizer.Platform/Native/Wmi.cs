using System.Globalization;
using System.Management;

namespace UltimatePcOptimizer.Platform.Native;

/// <summary>Small helper around System.Management that never throws for missing properties.</summary>
internal static class Wmi
{
    public static List<Dictionary<string, object?>> Query(string wql, string scope = @"root\cimv2")
    {
        var results = new List<Dictionary<string, object?>>();
        try
        {
            using var searcher = new ManagementObjectSearcher(scope, wql);
            using var collection = searcher.Get();
            foreach (var item in collection)
            {
                using (item)
                {
                    var row = new Dictionary<string, object?>(StringComparer.OrdinalIgnoreCase);
                    foreach (var prop in item.Properties) row[prop.Name] = prop.Value;
                    results.Add(row);
                }
            }
        }
        catch (Exception ex) when (ex is ManagementException or UnauthorizedAccessException or System.Runtime.InteropServices.COMException)
        {
            // WMI classes differ between Windows editions; a missing class simply yields no rows.
        }
        return results;
    }

    public static string Str(this Dictionary<string, object?> row, string key) =>
        row.TryGetValue(key, out var v) && v is not null ? Convert.ToString(v, CultureInfo.InvariantCulture)?.Trim() ?? string.Empty : string.Empty;

    public static ulong U64(this Dictionary<string, object?> row, string key) =>
        row.TryGetValue(key, out var v) && v is not null ? Convert.ToUInt64(v, CultureInfo.InvariantCulture) : 0;

    public static int I32(this Dictionary<string, object?> row, string key) =>
        row.TryGetValue(key, out var v) && v is not null ? Convert.ToInt32(v, CultureInfo.InvariantCulture) : 0;

    public static bool Bool(this Dictionary<string, object?> row, string key) =>
        row.TryGetValue(key, out var v) && v is bool b && b;

    public static DateTime? Date(this Dictionary<string, object?> row, string key)
    {
        var text = row.Str(key);
        if (text.Length == 0) return null;
        try { return ManagementDateTimeConverter.ToDateTime(text); }
        catch (ArgumentOutOfRangeException) { return null; }
    }
}
