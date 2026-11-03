namespace UltimatePcOptimizer.Core.Changes;

public enum ChangeKind { RegistryValue, PowerPlan, StartupEntry, ProcessPriority, FileCleanup }

public enum RiskLevel { Safe, Low, Moderate }

public enum BatchKind { OneClick, GamingSession, Mode, Manual, Automatic }

/// <summary>A change that has been proposed but not applied. The user approves these individually.</summary>
public sealed record PlannedChange(
    ChangeKind Kind,
    string Target,
    string? NewValue,
    string Description,
    RiskLevel Risk,
    bool RecommendedByDefault = true);

/// <summary>The state of a setting before we touched it, so it can be restored exactly.</summary>
public sealed record PreviousState(bool Existed, string? Value)
{
    public static PreviousState Absent { get; } = new(false, null);
    public static PreviousState Of(string? value) => new(true, value);
}

/// <summary>A group of changes applied together. Undo reverts a whole batch, newest change first.</summary>
public sealed record ChangeBatch(
    Guid Id,
    BatchKind Kind,
    string Title,
    DateTimeOffset CreatedAt,
    string? RestorePointDescription,
    DateTimeOffset? RevertedAt)
{
    public static ChangeBatch Create(BatchKind kind, string title, string? restorePoint = null) =>
        new(Guid.NewGuid(), kind, title, DateTimeOffset.UtcNow, restorePoint, null);
}

/// <summary>A journaled change. Written to the database before the setting is modified.</summary>
public sealed record ChangeRecord(
    long Id,
    Guid BatchId,
    ChangeKind Kind,
    string Target,
    PreviousState Previous,
    string? NewValue,
    string Description,
    bool IsReversible,
    ChangeStatus Status,
    string? ResultInfo,
    DateTimeOffset AppliedAt,
    DateTimeOffset? RevertedAt);

public enum ChangeStatus { Pending, Applied, Failed, Reverted, RevertFailed }

public sealed record ChangeOutcome(PlannedChange Change, bool Success, string? Message, long? RecordId);

public sealed record RevertReport(Guid BatchId, int Reverted, int Failed, int Skipped, IReadOnlyList<string> Messages);
