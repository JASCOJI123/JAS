using System.Globalization;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using UltimatePcOptimizer.Core;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.App.Views;

public partial class FpsOverlayWindow : Window
{
    private const int GwlExStyle = -20;
    private const int WsExTransparent = 0x20;
    private const int WsExToolWindow = 0x80;
    private const int WsExNoActivate = 0x08000000;

    public FpsOverlayWindow() => InitializeComponent();

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        // Click-through and hidden from Alt+Tab so it never steals input from the game.
        var hwnd = new WindowInteropHelper(this).Handle;
        var style = GetWindowLongPtr(hwnd, GwlExStyle);
        SetWindowLongPtr(hwnd, GwlExStyle, style | WsExTransparent | WsExToolWindow | WsExNoActivate);
    }

    public void Update(FrameStats stats, SystemSnapshot s)
    {
        var c = CultureInfo.InvariantCulture;
        FpsText.Text = string.Format(c, "{0:0} FPS", stats.CurrentFps);
        LowText.Text = string.Format(c, "AVG {0:0}  1% {1:0}", stats.AverageFps, stats.OnePercentLowFps);
        FrameText.Text = string.Format(c, "Frametime {0:0.0} ms", stats.LastFrameTimeMs);
        CpuText.Text = string.Format(c, "CPU {0:0}%  {1}", s.CpuLoadPercent, s.CpuTemperatureC is { } ct ? $"{ct:0}°C" : "");
        GpuText.Text = string.Format(c, "GPU {0}  {1}", s.GpuLoadPercent is { } g ? $"{g:0}%" : "N/A", s.GpuTemperatureC is { } gt ? $"{gt:0}°C" : "");
        RamText.Text = $"RAM {s.Memory.UsedPercent:0}%" + (s.Vram is { } v ? $"  VRAM {ByteFormatter.Format(v.UsedBytes)}" : string.Empty);
    }

    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW")]
    private static extern nint GetWindowLongPtr(nint hwnd, int index);

    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW")]
    private static extern nint SetWindowLongPtr(nint hwnd, int index, nint value);
}
