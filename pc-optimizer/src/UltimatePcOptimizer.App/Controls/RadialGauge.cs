using System.Globalization;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace UltimatePcOptimizer.App.Controls;

/// <summary>A 270° neon arc gauge. Value changes animate smoothly.</summary>
public sealed class RadialGauge : FrameworkElement
{
    private const double SweepDegrees = 270;
    private const double StartDegrees = 135;

    public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(
        nameof(Value), typeof(double), typeof(RadialGauge), new PropertyMetadata(0d, OnValueChanged));

    private static readonly DependencyProperty AnimatedValueProperty = DependencyProperty.Register(
        "AnimatedValue", typeof(double), typeof(RadialGauge), new FrameworkPropertyMetadata(0d, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty MaximumProperty = DependencyProperty.Register(
        nameof(Maximum), typeof(double), typeof(RadialGauge), new FrameworkPropertyMetadata(100d, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty LabelProperty = DependencyProperty.Register(
        nameof(Label), typeof(string), typeof(RadialGauge), new FrameworkPropertyMetadata(string.Empty, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty CaptionProperty = DependencyProperty.Register(
        nameof(Caption), typeof(string), typeof(RadialGauge), new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty UnitProperty = DependencyProperty.Register(
        nameof(Unit), typeof(string), typeof(RadialGauge), new FrameworkPropertyMetadata("%", FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty IsAvailableProperty = DependencyProperty.Register(
        nameof(IsAvailable), typeof(bool), typeof(RadialGauge), new FrameworkPropertyMetadata(true, FrameworkPropertyMetadataOptions.AffectsRender));

    public double Value { get => (double)GetValue(ValueProperty); set => SetValue(ValueProperty, value); }
    public double Maximum { get => (double)GetValue(MaximumProperty); set => SetValue(MaximumProperty, value); }
    public string Label { get => (string)GetValue(LabelProperty); set => SetValue(LabelProperty, value); }
    public string? Caption { get => (string?)GetValue(CaptionProperty); set => SetValue(CaptionProperty, value); }
    public string Unit { get => (string)GetValue(UnitProperty); set => SetValue(UnitProperty, value); }
    public bool IsAvailable { get => (bool)GetValue(IsAvailableProperty); set => SetValue(IsAvailableProperty, value); }

    private static void OnValueChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        var gauge = (RadialGauge)d;
        var animation = new DoubleAnimation((double)e.NewValue, TimeSpan.FromMilliseconds(450))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        gauge.BeginAnimation(AnimatedValueProperty, animation);
    }

    protected override Size MeasureOverride(Size availableSize)
    {
        var size = double.IsInfinity(availableSize.Width) ? 150 : Math.Min(availableSize.Width, double.IsInfinity(availableSize.Height) ? availableSize.Width : availableSize.Height);
        return new Size(size, size);
    }

    protected override void OnRender(DrawingContext dc)
    {
        var size = Math.Min(ActualWidth, ActualHeight);
        if (size <= 0) return;
        var thickness = Math.Max(6, size * 0.075);
        var center = new Point(ActualWidth / 2, ActualHeight / 2);
        var radius = size / 2 - thickness;

        var track = new Pen(Brush("SurfaceAltBrush", Brushes.DimGray), thickness) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
        dc.DrawGeometry(null, track, Arc(center, radius, StartDegrees, SweepDegrees));

        var value = (double)GetValue(AnimatedValueProperty);
        var fraction = IsAvailable && Maximum > 0 ? Math.Clamp(value / Maximum, 0, 1) : 0;
        if (fraction > 0.002)
        {
            var valuePen = new Pen(ValueBrush(fraction), thickness) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
            dc.DrawGeometry(null, valuePen, Arc(center, radius, StartDegrees, SweepDegrees * fraction));
        }

        var dpi = VisualTreeHelper.GetDpi(this).PixelsPerDip;
        var face = new Typeface(new FontFamily("Segoe UI"), FontStyles.Normal, FontWeights.Bold, FontStretches.Normal);
        var text = IsAvailable ? $"{value.ToString("0", CultureInfo.InvariantCulture)}{Unit}" : "N/A";
        var main = new FormattedText(text, CultureInfo.InvariantCulture, FlowDirection.LeftToRight, face, size * 0.19, Brush("TextBrush", Brushes.White), dpi);
        dc.DrawText(main, new Point(center.X - main.Width / 2, center.Y - main.Height / 2 - size * 0.04));

        var labelFace = new Typeface(new FontFamily("Segoe UI"), FontStyles.Normal, FontWeights.SemiBold, FontStretches.Normal);
        var label = new FormattedText(Label, CultureInfo.CurrentCulture, FlowDirection.LeftToRight, labelFace, size * 0.085, Brush("TextMutedBrush", Brushes.Gray), dpi);
        dc.DrawText(label, new Point(center.X - label.Width / 2, center.Y + main.Height / 2 - size * 0.02));

        if (!string.IsNullOrEmpty(Caption))
        {
            var caption = new FormattedText(Caption, CultureInfo.CurrentCulture, FlowDirection.LeftToRight, labelFace, size * 0.075, Brush("AccentBrush", Brushes.Cyan), dpi);
            dc.DrawText(caption, new Point(center.X - caption.Width / 2, center.Y + radius * 0.78 - caption.Height / 2));
        }
    }

    private Brush ValueBrush(double fraction)
    {
        if (fraction >= 0.9) return Brush("DangerBrush", Brushes.Red);
        if (fraction >= 0.75) return Brush("WarningBrush", Brushes.Orange);
        return Brush("AccentGradientBrush", Brushes.Cyan);
    }

    private Brush Brush(string key, Brush fallback) => TryFindResource(key) as Brush ?? fallback;

    private static Geometry Arc(Point center, double radius, double startDegrees, double sweepDegrees)
    {
        static Point At(Point c, double r, double degrees)
        {
            var rad = degrees * Math.PI / 180;
            return new Point(c.X + r * Math.Cos(rad), c.Y + r * Math.Sin(rad));
        }

        var start = At(center, radius, startDegrees);
        var end = At(center, radius, startDegrees + sweepDegrees);
        var figure = new PathFigure { StartPoint = start, IsClosed = false };
        figure.Segments.Add(new ArcSegment(end, new Size(radius, radius), 0, sweepDegrees > 180, SweepDirection.Clockwise, true));
        var geometry = new PathGeometry([figure]);
        geometry.Freeze();
        return geometry;
    }
}
