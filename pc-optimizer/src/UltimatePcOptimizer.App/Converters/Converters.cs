using System.Globalization;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using UltimatePcOptimizer.Core;
using UltimatePcOptimizer.Core.Analysis;
using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.App.Converters;

public sealed class BytesConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) => value switch
    {
        ulong u => ByteFormatter.Format(u),
        long l when l >= 0 => ByteFormatter.Format((ulong)l),
        double d when d >= 0 => ByteFormatter.Format((ulong)d),
        null => "—",
        _ => value.ToString() ?? string.Empty,
    };

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => Binding.DoNothing;
}

/// <summary>Formats nullable sensor values; null becomes "N/A". ConverterParameter is a format such as "{0:0}°C".</summary>
public sealed class NullableFormatConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        value is null ? "N/A" : string.Format(CultureInfo.InvariantCulture, parameter as string ?? "{0}", value);

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => Binding.DoNothing;
}

public sealed class BoolToVisibilityConverter : IValueConverter
{
    public bool Invert { get; set; }

    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        var flag = value switch
        {
            bool b => b,
            int i => i > 0,
            string s => s.Length > 0,
            null => false,
            _ => true,
        };
        return flag ^ Invert ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => Binding.DoNothing;
}

/// <summary>Maps risk, severity, health and impact enums to theme brushes.</summary>
public sealed class StateToBrushConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        var key = value switch
        {
            RiskLevel.Safe or HealthStatus.Excellent or StartupImpact.Low or ThrottleState.None or MemoryPressure.Low => "SuccessBrush",
            RiskLevel.Low or HealthStatus.Good or RecommendationSeverity.Info or StartupImpact.NotMeasured => "AccentBrush",
            RiskLevel.Moderate or HealthStatus.Fair or RecommendationSeverity.Suggestion or StartupImpact.Medium
                or ThrottleState.ThermalWarning or ThrottleState.PowerLimited or MemoryPressure.Moderate => "WarningBrush",
            HealthStatus.Poor or RecommendationSeverity.Important or StartupImpact.High or ThrottleState.ThermalThrottling
                or MemoryPressure.High or MemoryPressure.Critical => "DangerBrush",
            true => "SuccessBrush",
            false => "TextMutedBrush",
            _ => "TextMutedBrush",
        };
        return Application.Current.TryFindResource(key) as Brush ?? Brushes.Gray;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => Binding.DoNothing;
}

public sealed class EnumTextConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) => value switch
    {
        RiskLevel.Safe => "Xavfsiz",
        RiskLevel.Low => "Past xavf",
        RiskLevel.Moderate => "O'rtacha xavf",
        StartupImpact.High => "Yuqori",
        StartupImpact.Medium => "O'rtacha",
        StartupImpact.Low => "Past",
        StartupImpact.NotMeasured => "O'lchanmagan",
        DiskMediaType.Nvme => "NVMe SSD",
        DiskMediaType.Ssd => "SSD",
        DiskMediaType.Hdd => "HDD",
        DiskMediaType.Unknown => "Noma'lum",
        BatchKind.OneClick => "One Click",
        BatchKind.GamingSession => "Gaming Booster",
        BatchKind.Mode => "Rejim",
        BatchKind.Manual => "Qo'lda",
        BatchKind.Automatic => "Avtomatik",
        null => string.Empty,
        _ => value.ToString() ?? string.Empty,
    };

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => Binding.DoNothing;
}
