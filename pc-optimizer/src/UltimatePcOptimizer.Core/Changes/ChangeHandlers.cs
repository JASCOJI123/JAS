using System.Globalization;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Safety;

namespace UltimatePcOptimizer.Core.Changes;

public sealed class RegistryChangeHandler(IRegistryAccess registry) : IChangeHandler
{
    public ChangeKind Kind => ChangeKind.RegistryValue;
    public bool IsReversible => true;

    public void Validate(PlannedChange change)
    {
        RegistryPolicy.EnsureAllowed(RegistryValueRef.Parse(change.Target));
        if (change.NewValue is null) throw new ArgumentException("A registry change requires a value.");
    }

    public Task<PreviousState> CaptureAsync(string target, CancellationToken ct)
    {
        var value = registry.Read(RegistryValueRef.Parse(target));
        return Task.FromResult(value is null ? PreviousState.Absent : PreviousState.Of(value));
    }

    public Task<string?> ApplyAsync(string target, string? newValue, CancellationToken ct)
    {
        registry.Write(RegistryValueRef.Parse(target), newValue ?? throw new ArgumentNullException(nameof(newValue)));
        return Task.FromResult<string?>(null);
    }

    public Task RevertAsync(string target, PreviousState previous, CancellationToken ct)
    {
        var reference = RegistryValueRef.Parse(target);
        if (previous.Existed && previous.Value is not null) registry.Write(reference, previous.Value);
        else registry.Delete(reference);
        return Task.CompletedTask;
    }
}

public sealed class PowerPlanChangeHandler(IPowerPlanService power) : IChangeHandler
{
    public const string ActivePlanTarget = "ActivePowerPlan";

    public ChangeKind Kind => ChangeKind.PowerPlan;
    public bool IsReversible => true;

    public void Validate(PlannedChange change)
    {
        if (!Guid.TryParse(change.NewValue, out var id)) throw new ArgumentException("Power plan id must be a GUID.");
        if (power.GetPlans().All(p => p.Id != id)) throw new InvalidOperationException("The power plan is not available on this PC.");
    }

    public Task<PreviousState> CaptureAsync(string target, CancellationToken ct)
    {
        var active = power.GetActivePlan();
        return Task.FromResult(active is null ? PreviousState.Absent : PreviousState.Of(active.Id.ToString()));
    }

    public Task<string?> ApplyAsync(string target, string? newValue, CancellationToken ct)
    {
        power.SetActivePlan(Guid.Parse(newValue!));
        return Task.FromResult<string?>(null);
    }

    public Task RevertAsync(string target, PreviousState previous, CancellationToken ct)
    {
        if (previous.Existed && Guid.TryParse(previous.Value, out var id)) power.SetActivePlan(id);
        return Task.CompletedTask;
    }
}

public sealed class StartupChangeHandler(IStartupService startup) : IChangeHandler
{
    public ChangeKind Kind => ChangeKind.StartupEntry;
    public bool IsReversible => true;

    public void Validate(PlannedChange change)
    {
        if (!bool.TryParse(change.NewValue, out _)) throw new ArgumentException("Startup change value must be true/false.");
        var entry = startup.GetEntries().FirstOrDefault(e => e.Id == change.Target)
                    ?? throw new InvalidOperationException("Startup entry not found.");
        if (entry.IsProtected && change.NewValue == bool.FalseString)
            throw new UnauthorizedAccessException($"'{entry.Name}' is required by Windows ({entry.ProtectionReason}).");
    }

    public Task<PreviousState> CaptureAsync(string target, CancellationToken ct)
    {
        var enabled = startup.IsEnabled(target);
        return Task.FromResult(enabled is null ? PreviousState.Absent : PreviousState.Of(enabled.Value.ToString()));
    }

    public Task<string?> ApplyAsync(string target, string? newValue, CancellationToken ct)
    {
        startup.SetEnabled(target, bool.Parse(newValue!));
        return Task.FromResult<string?>(null);
    }

    public Task RevertAsync(string target, PreviousState previous, CancellationToken ct)
    {
        if (previous.Existed && bool.TryParse(previous.Value, out var enabled)) startup.SetEnabled(target, enabled);
        return Task.CompletedTask;
    }
}

/// <summary>Target format: "pid|startTimeTicks|name". The start time prevents acting on a reused PID.</summary>
public sealed class ProcessPriorityChangeHandler(IProcessService processes) : IChangeHandler
{
    public ChangeKind Kind => ChangeKind.ProcessPriority;
    public bool IsReversible => true;

    public static string CreateTarget(ProcessInfo process) =>
        string.Join('|', process.Pid.ToString(CultureInfo.InvariantCulture),
            (process.StartTime?.ToUniversalTime().Ticks ?? 0).ToString(CultureInfo.InvariantCulture), process.Name);

    public void Validate(PlannedChange change)
    {
        var (_, _, name) = ParseTarget(change.Target);
        if (ProcessSafetyPolicy.IsAntiCheat(name)) throw new UnauthorizedAccessException("Anti-cheat processes are never modified.");
        if (!Enum.TryParse<ProcessPriorityLevel>(change.NewValue, out var level)) throw new ArgumentException("Unknown priority.");
        if (level > ProcessPriorityLevel.High) throw new ArgumentException("Realtime priority is not allowed.");
    }

    public Task<PreviousState> CaptureAsync(string target, CancellationToken ct)
    {
        var (pid, start, _) = ParseTarget(target);
        var priority = processes.GetPriority(pid, start);
        return Task.FromResult(priority is null ? PreviousState.Absent : PreviousState.Of(priority.Value.ToString()));
    }

    public Task<string?> ApplyAsync(string target, string? newValue, CancellationToken ct)
    {
        var (pid, start, name) = ParseTarget(target);
        if (!processes.SetPriority(pid, start, Enum.Parse<ProcessPriorityLevel>(newValue!)))
            throw new InvalidOperationException($"Could not change priority of {name}.");
        return Task.FromResult<string?>(null);
    }

    public Task RevertAsync(string target, PreviousState previous, CancellationToken ct)
    {
        var (pid, start, _) = ParseTarget(target);
        if (previous.Existed && Enum.TryParse<ProcessPriorityLevel>(previous.Value, out var level))
            processes.SetPriority(pid, start, level); // A process that already exited needs no restore.
        return Task.CompletedTask;
    }

    internal static (int Pid, DateTime? StartTime, string Name) ParseTarget(string target)
    {
        var parts = target.Split('|', 3);
        if (parts.Length != 3) throw new FormatException("Invalid process target.");
        var ticks = long.Parse(parts[1], CultureInfo.InvariantCulture);
        return (int.Parse(parts[0], CultureInfo.InvariantCulture),
            ticks == 0 ? null : new DateTime(ticks, DateTimeKind.Utc), parts[2]);
    }
}

/// <summary>Deleting junk cannot be undone; it is journaled for history only and always asks consent first.</summary>
public sealed class FileCleanupChangeHandler(ICleanupService cleanup) : IChangeHandler
{
    public ChangeKind Kind => ChangeKind.FileCleanup;
    public bool IsReversible => false;

    public void Validate(PlannedChange change)
    {
        if (!Enum.TryParse<CleanupCategory>(change.Target, out _)) throw new ArgumentException("Unknown cleanup category.");
    }

    public Task<PreviousState> CaptureAsync(string target, CancellationToken ct) => Task.FromResult(PreviousState.Absent);

    public async Task<string?> ApplyAsync(string target, string? newValue, CancellationToken ct)
    {
        var result = await cleanup.CleanAsync(Enum.Parse<CleanupCategory>(target), ct).ConfigureAwait(false);
        return string.Create(CultureInfo.InvariantCulture,
            $"{ByteFormatter.Format(result.FreedBytes)} freed, {result.DeletedFiles} files deleted, {result.SkippedFiles} skipped");
    }

    public Task RevertAsync(string target, PreviousState previous, CancellationToken ct) => Task.CompletedTask;
}
