# Clone proxy trace

The `ClonyCloneProxy` and `ClonyCloneProxySettings` log tags record saved settings, proxy configuration, and runtime routing for a cloned process. They log the proxy type, UDP-relay setting, destination address family and port, and result. They do not log proxy endpoints, credentials, packet contents, or destination IP addresses.

Run **Test proxy connection** in the clone's Network dialog to get a `ClonyCloneProbe` event. Then launch or refresh the cloned app while capturing runtime route events:

```sh
adb logcat -v threadtime ClonyCloneProxy:I ClonyCloneProbe:I '*:S'
```

For HTTP CONNECT, expected events include:

- `event=proxy_configure result=success transport=http_connect_tcp`
- `event=tcp_route result=tunneled ... destination_port=443`
- UDP send and receive hooks are disabled; no UDP route event is expected.

For SOCKS5, UDP/QUIC relay is always enabled. Expected events include:

- `event=proxy_configure result=success transport=socks5_tcp_udp`
- `event=dns_route result=remote_resolution transport=socks5_domain` when the clone resolves a public hostname
- `event=tcp_route result=tunneled transport=socks5_tcp ... elapsed_ms=...`
- `event=socks5_udp_associate result=success`
- `event=udp_route result=proxied transport=socks5_udp ...`, with `reason=socks5_udp_domain` for remotely resolved hostnames

SOCKS5 destination hostnames are represented by synthetic addresses inside the clone and sent to the proxy as SOCKS5 domain targets. This avoids sending those destination lookups to the phone resolver. The proxy endpoint hostname is resolved when the proxy is saved, outside the clone startup path, and the numeric IPv4 address is passed to the native hook. Older settings without a saved address use a bounded local lookup (1.2 seconds). Hostnames that are local, single-label, numeric, or non-ASCII continue through the phone resolver.

If the server does not support UDP ASSOCIATE, expect `event=socks5_udp_associate result=failure`; TikTok UDP sends then fail instead of bypassing the proxy.

TCP route `elapsed_ms` covers the proxy connection and handshake. If configuration succeeds but no `tcp_route` or `udp_route` appears, the clone did not reach an observed socket route during that capture; check the clone startup trace and Android ANR/crash records next. TCP routing is synchronous in the hook, with 1.5 second connect and handshake limits to bound stalls.

The UDP event confirms the app sent UDP outside HTTP CONNECT. The absence of a UDP event only means no observed UDP send occurred during that capture; it does not prove TikTok has no other network paths. TCP events intentionally omit the destination IP, so use the proxy provider's connection log if you need to verify the server-side CONNECT destinations.
