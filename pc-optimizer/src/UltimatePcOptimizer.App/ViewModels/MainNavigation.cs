namespace UltimatePcOptimizer.App.ViewModels;

/// <summary>Lets pages request navigation without depending on the main view model.</summary>
public sealed class MainNavigation
{
    public event EventHandler<string>? Requested;

    public void NavigateTo(string key) => Requested?.Invoke(this, key);
}
