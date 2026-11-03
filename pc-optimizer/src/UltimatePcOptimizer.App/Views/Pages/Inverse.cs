using System.Globalization;
using System.Windows.Data;

namespace UltimatePcOptimizer.App.Views.Pages;

/// <summary>Boolean negation for bindings such as IsEnabled = !IsApplied.</summary>
public sealed class Inverse : IValueConverter
{
    public static Inverse Instance { get; } = new();

    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) => value is not true;

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => value is not true;
}

/// <summary>True when a nullable sensor value is present.</summary>
public sealed class HasValue : IValueConverter
{
    public static HasValue Instance { get; } = new();

    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) => value is not null;

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => Binding.DoNothing;
}
