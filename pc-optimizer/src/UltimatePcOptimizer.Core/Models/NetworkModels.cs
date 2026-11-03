namespace UltimatePcOptimizer.Core.Models;

public sealed record LatencyStats(string Host, int Sent, int Received, double MinMs, double AverageMs, double MaxMs, double JitterMs)
{
    public double PacketLossPercent => Sent == 0 ? 0 : (Sent - Received) * 100.0 / Sent;
}

public sealed record NetworkAdapterInfo(string Name, string Description, string Type, long SpeedBitsPerSecond, bool IsUp, string? Gateway, IReadOnlyList<string> DnsServers);

public sealed record ConnectionDiagnostics(
    IReadOnlyList<NetworkAdapterInfo> Adapters,
    bool HasActiveAdapter,
    bool HasGateway,
    bool DnsResolves,
    bool InternetReachable,
    LatencyStats? GatewayLatency,
    LatencyStats? InternetLatency);
