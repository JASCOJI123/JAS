using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Core.Analysis;

/// <summary>Hardware detection is slow (WMI); it runs once and is shared by every module.</summary>
public sealed class HardwareProfileCache(IHardwareDetector detector)
{
    private readonly SemaphoreSlim _gate = new(1, 1);
    private HardwareProfile? _profile;

    public async Task<HardwareProfile> GetAsync(CancellationToken ct = default)
    {
        if (_profile is not null) return _profile;
        await _gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            return _profile ??= await detector.DetectAsync(ct).ConfigureAwait(false);
        }
        finally
        {
            _gate.Release();
        }
    }

    public void Invalidate() => _profile = null;
}
