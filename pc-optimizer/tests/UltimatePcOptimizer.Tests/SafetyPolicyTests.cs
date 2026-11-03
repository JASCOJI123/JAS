using UltimatePcOptimizer.Core.Changes;
using UltimatePcOptimizer.Core.Models;
using UltimatePcOptimizer.Core.Optimization;
using UltimatePcOptimizer.Core.Safety;

namespace UltimatePcOptimizer.Tests;

public class SafetyPolicyTests
{
    [Fact]
    public void Every_catalog_tweak_is_on_the_registry_allow_list()
    {
        foreach (var tweak in TweakCatalog.All)
            foreach (var assignment in tweak.Assignments)
                Assert.True(RegistryPolicy.IsAllowed(assignment.Value), assignment.Value.DisplayPath);
        Assert.True(RegistryPolicy.IsAllowed(TweakCatalog.HighPerformanceGpuFor(@"C:\Games\game.exe").Value));
    }

    [Theory]
    [InlineData(RegistryHive.LocalMachine, @"SOFTWARE\Policies\Microsoft\Windows Defender", "DisableAntiSpyware")]
    [InlineData(RegistryHive.LocalMachine, @"SYSTEM\CurrentControlSet\Services\wuauserv", "Start")]
    [InlineData(RegistryHive.CurrentUser, @"Software\Microsoft\Windows\CurrentVersion\Run", "Anything")]
    public void Dangerous_registry_values_are_not_allowed(RegistryHive hive, string key, string value) =>
        Assert.False(RegistryPolicy.IsAllowed(new RegistryValueRef(hive, key, value, RegistryValueType.DWord)));

    [Fact]
    public void Registry_reference_round_trips()
    {
        var reference = new RegistryValueRef(RegistryHive.CurrentUser, @"Software\Microsoft\DirectX\UserGpuPreferences",
            @"C:\Games\My Game\game.exe", RegistryValueType.String);
        Assert.Equal(reference, RegistryValueRef.Parse(reference.ToString()));
    }

    [Theory]
    [InlineData("vgc", ProcessCategory.AntiCheat)]
    [InlineData("EasyAntiCheat.exe", ProcessCategory.AntiCheat)]
    [InlineData("MsMpEng", ProcessCategory.Security)]
    [InlineData("lsass", ProcessCategory.System)]
    [InlineData("chrome", ProcessCategory.Browser)]
    [InlineData("Discord", ProcessCategory.Communication)]
    [InlineData("notepad", ProcessCategory.User)]
    public void Processes_are_classified(string name, ProcessCategory expected) =>
        Assert.Equal(expected, ProcessSafetyPolicy.Classify(name, sessionId: 1, isKnownGame: false));

    [Fact]
    public void Session_zero_services_are_system_and_protected()
    {
        var category = ProcessSafetyPolicy.Classify("SomeVendorService", sessionId: 0, isKnownGame: false);
        Assert.True(ProcessSafetyPolicy.IsProtected(category));
    }

    [Theory]
    [InlineData(@"C:\")]
    [InlineData(@"C:\Windows")]
    [InlineData(@"C:\Windows\System32\")]
    [InlineData(@"C:\Program Files")]
    [InlineData(@"C:\Users")]
    [InlineData(@"C:\Users\Ali")]
    public void Critical_directories_can_never_be_cleanup_roots(string path) =>
        Assert.True(CleanupPathPolicy.IsForbiddenRoot(path));

    [Theory]
    [InlineData(@"C:\Windows\Temp")]
    [InlineData(@"C:\Users\Ali\AppData\Local\Temp")]
    public void Temp_directories_are_valid_cleanup_roots(string path) =>
        Assert.False(CleanupPathPolicy.IsForbiddenRoot(path));

    [Fact]
    public void Cleanup_policy_only_allows_paths_inside_roots()
    {
        var root = Path.Combine(Path.GetTempPath(), "upo-root");
        var policy = new CleanupPathPolicy([root], TimeSpan.FromHours(24));

        Assert.True(policy.IsInsideApprovedRoot(Path.Combine(root, "a", "b.tmp")));
        Assert.False(policy.IsInsideApprovedRoot(root));
        Assert.False(policy.IsInsideApprovedRoot(Path.Combine(root, "..", "outside.txt")));
        Assert.False(policy.IsInsideApprovedRoot(root + "-sibling" + Path.DirectorySeparatorChar + "x"));
    }

    [Fact]
    public void Security_and_input_startup_items_are_protected()
    {
        Assert.NotNull(StartupSafetyPolicy.GetProtectionReason("SecurityHealth", @"%windir%\system32\SecurityHealthSystray.exe"));
        Assert.Null(StartupSafetyPolicy.GetProtectionReason("Spotify", @"C:\Users\a\AppData\Roaming\Spotify\Spotify.exe /minimized"));
    }

    [Fact]
    public void Startup_handler_refuses_to_disable_protected_entries()
    {
        var entry = new StartupEntry("id1", "SecurityHealth", "x", null, StartupLocation.LocalMachineRun, true, StartupImpact.Low, null, true, "Windows Security");
        var handler = new StartupChangeHandler(new SingleStartup(entry));
        Assert.Throws<UnauthorizedAccessException>(() =>
            handler.Validate(new PlannedChange(ChangeKind.StartupEntry, "id1", bool.FalseString, "x", RiskLevel.Low)));
    }

    [Fact]
    public void Realtime_priority_and_anti_cheat_targets_are_rejected()
    {
        var handler = new ProcessPriorityChangeHandler(new FakeProcesses());
        Assert.Throws<UnauthorizedAccessException>(() =>
            handler.Validate(new PlannedChange(ChangeKind.ProcessPriority, "5|0|vgc", "High", "x", RiskLevel.Low)));
        Assert.Throws<ArgumentException>(() =>
            handler.Validate(new PlannedChange(ChangeKind.ProcessPriority, "5|0|game", "Realtime", "x", RiskLevel.Low)));
    }

    private sealed class SingleStartup(StartupEntry entry) : Core.Abstractions.IStartupService
    {
        public IReadOnlyList<StartupEntry> GetEntries() => [entry];
        public bool? IsEnabled(string entryId) => entry.IsEnabled;
        public void SetEnabled(string entryId, bool enabled) { }
    }
}
