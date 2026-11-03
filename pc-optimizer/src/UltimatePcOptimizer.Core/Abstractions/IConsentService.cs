using UltimatePcOptimizer.Core.Changes;

namespace UltimatePcOptimizer.Core.Abstractions;

public sealed record ConsentRequest(string Title, string Message, IReadOnlyList<string> Details, RiskLevel Risk);

/// <summary>Asks the user before a change is made. Implemented by the UI.</summary>
public interface IConsentService
{
    Task<bool> ConfirmAsync(ConsentRequest request);
}
