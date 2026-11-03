namespace UltimatePcOptimizer.Core.Models;

public enum ProcessCategory { System, Security, AntiCheat, Game, Launcher, Browser, Communication, Background, User }

public enum ProcessPriorityLevel { Idle, BelowNormal, Normal, AboveNormal, High }

public sealed record ProcessInfo(
    int Pid,
    string Name,
    string? ExecutablePath,
    ulong WorkingSetBytes,
    double CpuPercent,
    int SessionId,
    DateTime? StartTime,
    ProcessPriorityLevel? Priority,
    ProcessCategory Category,
    bool IsProtected);

public sealed record ProcessIdentity(int Pid, string Name, DateTime? StartTime);
