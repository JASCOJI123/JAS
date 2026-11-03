using System.Windows;
using System.Windows.Media;
using UltimatePcOptimizer.App.Converters;
using UltimatePcOptimizer.Core.Abstractions;

namespace UltimatePcOptimizer.App.Views;

public partial class ConfirmDialog : Window
{
    public ConfirmDialog(ConsentRequest request)
    {
        InitializeComponent();
        TitleText.Text = request.Title;
        MessageText.Text = request.Message;
        DetailsList.ItemsSource = request.Details;
        DetailsList.Visibility = request.Details.Count == 0 ? Visibility.Collapsed : Visibility.Visible;
        RiskText.Text = (string)new EnumTextConverter().Convert(request.Risk, typeof(string), null, System.Globalization.CultureInfo.CurrentCulture);
        RiskText.Foreground = (Brush)new StateToBrushConverter().Convert(request.Risk, typeof(Brush), null, System.Globalization.CultureInfo.CurrentCulture);
        MouseLeftButtonDown += (_, _) => DragMove();
    }

    private void OnConfirm(object sender, RoutedEventArgs e) => DialogResult = true;

    private void OnCancel(object sender, RoutedEventArgs e) => DialogResult = false;
}
