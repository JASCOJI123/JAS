using System.Text;

namespace UltimatePcOptimizer.Core.Parsing;

/// <summary>A node of Valve's KeyValues (VDF/ACF) format used by Steam's library files.</summary>
public sealed class KvNode
{
    public string? Value { get; init; }
    public Dictionary<string, KvNode> Children { get; } = new(StringComparer.OrdinalIgnoreCase);

    public KvNode? this[string key] => Children.GetValueOrDefault(key);

    public string? GetString(string key) => Children.GetValueOrDefault(key)?.Value;
}

public static class ValveKeyValueParser
{
    public static KvNode Parse(string text)
    {
        var root = new KvNode();
        var stack = new Stack<KvNode>();
        stack.Push(root);
        string? pendingKey = null;
        var i = 0;

        while (i < text.Length)
        {
            var c = text[i];
            if (char.IsWhiteSpace(c)) { i++; continue; }
            if (c == '/' && i + 1 < text.Length && text[i + 1] == '/')
            {
                while (i < text.Length && text[i] != '\n') i++;
                continue;
            }
            if (c == '{')
            {
                var node = new KvNode();
                stack.Peek().Children[pendingKey ?? string.Empty] = node;
                stack.Push(node);
                pendingKey = null;
                i++;
                continue;
            }
            if (c == '}')
            {
                if (stack.Count > 1) stack.Pop();
                pendingKey = null;
                i++;
                continue;
            }

            var token = ReadToken(text, ref i);
            if (pendingKey is null)
            {
                pendingKey = token;
            }
            else
            {
                stack.Peek().Children[pendingKey] = new KvNode { Value = token };
                pendingKey = null;
            }
        }
        return root;
    }

    private static string ReadToken(string text, ref int i)
    {
        var sb = new StringBuilder();
        if (text[i] == '"')
        {
            i++;
            while (i < text.Length && text[i] != '"')
            {
                if (text[i] == '\\' && i + 1 < text.Length)
                {
                    i++;
                    sb.Append(text[i] switch { 'n' => '\n', 't' => '\t', _ => text[i] });
                }
                else
                {
                    sb.Append(text[i]);
                }
                i++;
            }
            i++; // closing quote
        }
        else
        {
            while (i < text.Length && !char.IsWhiteSpace(text[i]) && text[i] is not ('{' or '}' or '"'))
                sb.Append(text[i++]);
        }
        return sb.ToString();
    }
}
