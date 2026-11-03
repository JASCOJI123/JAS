using UltimatePcOptimizer.App.Views;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.App.Services;

/// <summary>
/// Owns the in-game FPS overlay window. The overlay is a separate top-most, click-through window;
/// it never hooks or injects into the game (works with borderless/windowed modes).
/// </summary>
public sealed class OverlayService
{
    private FpsOverlayWindow? _window;

    public void Show() => Ui.Post(() =>
    {
        _window ??= new FpsOverlayWindow();
        _window.Show();
    });

    public void Hide() => Ui.Post(() =>
    {
        _window?.Close();
        _window = null;
    });

    public void Update(FrameStats stats, SystemSnapshot snapshot) => _window?.Update(stats, snapshot);
}
