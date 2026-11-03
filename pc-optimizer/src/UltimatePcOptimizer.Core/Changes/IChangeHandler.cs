namespace UltimatePcOptimizer.Core.Changes;

/// <summary>Knows how to capture, apply and revert one kind of change.</summary>
public interface IChangeHandler
{
    ChangeKind Kind { get; }
    bool IsReversible { get; }
    /// <summary>Validates the change before anything is journaled or applied. Throws when not allowed.</summary>
    void Validate(PlannedChange change);
    Task<PreviousState> CaptureAsync(string target, CancellationToken ct);
    /// <summary>Applies the change; may return human-readable result information.</summary>
    Task<string?> ApplyAsync(string target, string? newValue, CancellationToken ct);
    Task RevertAsync(string target, PreviousState previous, CancellationToken ct);
}
