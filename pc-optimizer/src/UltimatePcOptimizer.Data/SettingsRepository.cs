using System.Text.Json;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Data;

/// <summary>Stores <see cref="AppSettings"/> as one JSON document; unknown/missing fields fall back to defaults.</summary>
public sealed class SettingsRepository(SqliteDatabase db) : ISettingsStore
{
    private const string Key = "app";

    public async Task<AppSettings> LoadAsync(CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command("SELECT value FROM settings WHERE key = $key;", ("$key", Key));
        var json = await cmd.ExecuteScalarAsync(ct).ConfigureAwait(false) as string;
        if (json is null) return new AppSettings();
        try
        {
            return JsonSerializer.Deserialize<AppSettings>(json) ?? new AppSettings();
        }
        catch (JsonException)
        {
            return new AppSettings();
        }
    }

    public async Task SaveAsync(AppSettings settings, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct).ConfigureAwait(false);
        await using var cmd = c.Command(
            "INSERT INTO settings (key, value) VALUES ($key, $value) ON CONFLICT (key) DO UPDATE SET value = excluded.value;",
            ("$key", Key), ("$value", JsonSerializer.Serialize(settings)));
        await cmd.ExecuteNonQueryAsync(ct).ConfigureAwait(false);
    }
}
