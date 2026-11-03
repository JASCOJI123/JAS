using System.Globalization;
using Microsoft.Data.Sqlite;

namespace UltimatePcOptimizer.Data;

internal static class SqlMapping
{
    public static string ToDb(DateTimeOffset value) => value.ToUniversalTime().ToString("O", CultureInfo.InvariantCulture);

    public static object ToDb(DateTimeOffset? value) => value is null ? DBNull.Value : ToDb(value.Value);

    public static object ToDb(string? value) => value is null ? DBNull.Value : value;

    public static object ToDb(double? value) => value is null ? DBNull.Value : value.Value;

    public static DateTimeOffset ReadDate(SqliteDataReader r, int i) =>
        DateTimeOffset.Parse(r.GetString(i), CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind);

    public static DateTimeOffset? ReadNullableDate(SqliteDataReader r, int i) => r.IsDBNull(i) ? null : ReadDate(r, i);

    public static string? ReadNullableString(SqliteDataReader r, int i) => r.IsDBNull(i) ? null : r.GetString(i);

    public static double? ReadNullableDouble(SqliteDataReader r, int i) => r.IsDBNull(i) ? null : r.GetDouble(i);

    public static SqliteCommand Command(this SqliteConnection connection, string sql, params (string Name, object? Value)[] parameters)
    {
        var cmd = connection.CreateCommand();
        cmd.CommandText = sql;
        foreach (var (name, value) in parameters) cmd.Parameters.AddWithValue(name, value ?? DBNull.Value);
        return cmd;
    }
}
