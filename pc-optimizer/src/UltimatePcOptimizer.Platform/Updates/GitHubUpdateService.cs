using System.Net.Http.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Parsing;

namespace UltimatePcOptimizer.Platform.Updates;

public sealed record UpdateOptions(string? Repository, Version CurrentVersion);

/// <summary>
/// Automatic update check against GitHub Releases. It only notifies; the user downloads and runs the
/// signed installer, nothing is installed silently.
/// </summary>
public sealed class GitHubUpdateService(HttpClient http, UpdateOptions options, ILogger<GitHubUpdateService> logger) : IUpdateService
{
    public async Task<UpdateInfo?> CheckAsync(CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(options.Repository) || !options.Repository.Contains('/')) return null;
        try
        {
            var release = await http.GetFromJsonAsync<Release>($"https://api.github.com/repos/{options.Repository}/releases/latest", ct).ConfigureAwait(false);
            var latest = ReleaseVersionParser.Parse(release?.TagName);
            if (release is null || latest is null || !ReleaseVersionParser.IsNewer(latest, options.CurrentVersion)) return null;

            var installer = release.Assets?.FirstOrDefault(a => a.Name?.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) == true);
            return new UpdateInfo($"{latest.Major}.{latest.Minor}.{Math.Max(0, latest.Build)}", release.Body ?? string.Empty, installer?.DownloadUrl ?? release.HtmlUrl ?? string.Empty);
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or System.Text.Json.JsonException)
        {
            logger.LogInformation("Update check failed: {Message}", ex.Message);
            return null;
        }
    }

    private sealed record Release(
        [property: JsonPropertyName("tag_name")] string? TagName,
        [property: JsonPropertyName("body")] string? Body,
        [property: JsonPropertyName("html_url")] string? HtmlUrl,
        [property: JsonPropertyName("assets")] IReadOnlyList<Asset>? Assets);

    private sealed record Asset(
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("browser_download_url")] string? DownloadUrl);
}
