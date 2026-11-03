using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using UltimatePcOptimizer.Core.Abstractions;
using UltimatePcOptimizer.Core.Models;

namespace UltimatePcOptimizer.Platform.Network;

/// <summary>Internet Connection Diagnostics and Network Latency Monitoring (ICMP ping, read-only).</summary>
public sealed class NetworkDiagnosticsService : INetworkDiagnostics
{
    private const int TimeoutMs = 2000;

    public async Task<LatencyStats> MeasureLatencyAsync(string host, int count, CancellationToken ct = default)
    {
        using var ping = new Ping();
        var times = new List<double>(count);
        for (var i = 0; i < count; i++)
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                var reply = await ping.SendPingAsync(host, TimeSpan.FromMilliseconds(TimeoutMs), cancellationToken: ct).ConfigureAwait(false);
                if (reply.Status == IPStatus.Success) times.Add(reply.RoundtripTime);
            }
            catch (PingException) { }
            if (i < count - 1) await Task.Delay(200, ct).ConfigureAwait(false);
        }

        if (times.Count == 0) return new LatencyStats(host, count, 0, 0, 0, 0, 0);
        var jitter = times.Count < 2 ? 0 : times.Zip(times.Skip(1), (a, b) => Math.Abs(b - a)).Average();
        return new LatencyStats(host, count, times.Count, times.Min(), times.Average(), times.Max(), jitter);
    }

    public async Task<ConnectionDiagnostics> DiagnoseAsync(IReadOnlyList<string> internetHosts, CancellationToken ct = default)
    {
        var adapters = NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.NetworkInterfaceType is not (NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel))
            .Select(n =>
            {
                var props = n.GetIPProperties();
                var gateway = props.GatewayAddresses.Select(g => g.Address)
                    .FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork && !a.Equals(IPAddress.Any));
                return new NetworkAdapterInfo(n.Name, n.Description, n.NetworkInterfaceType.ToString(), n.Speed,
                    n.OperationalStatus == OperationalStatus.Up, gateway?.ToString(),
                    props.DnsAddresses.Select(d => d.ToString()).ToList());
            })
            .ToList();

        var active = adapters.Where(a => a.IsUp).ToList();
        var gatewayAddress = active.Select(a => a.Gateway).FirstOrDefault(g => g is not null);

        bool dns;
        try
        {
            dns = (await Dns.GetHostAddressesAsync("www.microsoft.com", ct).ConfigureAwait(false)).Length > 0;
        }
        catch (SocketException)
        {
            dns = false;
        }

        var gatewayLatency = gatewayAddress is null ? null : await MeasureLatencyAsync(gatewayAddress, 4, ct).ConfigureAwait(false);
        var internetLatency = internetHosts.Count == 0 ? null : await MeasureLatencyAsync(internetHosts[0], 6, ct).ConfigureAwait(false);

        return new ConnectionDiagnostics(adapters, active.Count > 0, gatewayAddress is not null, dns,
            internetLatency is { Received: > 0 }, gatewayLatency, internetLatency);
    }
}
