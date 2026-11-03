namespace UltimatePcOptimizer.Core.Parsing;

public static class CommandLineParser
{
    /// <summary>Extracts the executable path from a startup command such as "\"C:\a b\x.exe\" -min" or "C:\x.exe /s".</summary>
    public static string? ExtractExecutable(string? command)
    {
        if (string.IsNullOrWhiteSpace(command)) return null;
        var text = Environment.ExpandEnvironmentVariables(command.Trim());

        if (text.StartsWith('"'))
        {
            var end = text.IndexOf('"', 1);
            return end > 1 ? text[1..end] : null;
        }

        var exe = text.IndexOf(".exe", StringComparison.OrdinalIgnoreCase);
        if (exe > 0) return text[..(exe + 4)];

        var space = text.IndexOf(' ');
        return space > 0 ? text[..space] : text;
    }
}
