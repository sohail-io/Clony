# Clone proxy trace

The `ClonyTrace`, `ClonyProxyRegion`, `ClonyCloneProbe`, `ClonyCloneProxy`, `ClonyCloneProxySettings`, and `ClonyCloneIdentity` tags trace clone setup, proxy preflight, runtime routing, and feed identity application. Events include stage, result, elapsed time, address family, destination port, byte counts, and error codes. DNS hostnames are represented by hashes. These traces do not log proxy endpoints, credentials, coordinates, packet contents, or destination IP addresses.

Capture these tags before testing the proxy and opening the cloned app:

```sh
adb logcat -v threadtime ClonyTrace:I ClonyProxyRegion:I ClonyCloneProbe:I ClonyCloneProxy:I ClonyCloneProxySettings:I ClonyCloneIdentity:I Forest_GeckoFetcher:E Forest_GeckoXAdapter:E Forest_TTNetDepender:I '*:S'
```

The Kotlin `feed_proxy_preflight` and `region_stage` events share a `trace_id`. Native TCP events use a `route_id` and report endpoint-connect time separately from SOCKS5 handshake time. `tcp_io` records first application write/read and socket close summaries without recording payloads. TikTok TTNet/Gecko errors can be aligned by timestamp and virtual-process PID.

For HTTP CONNECT, expected events include:

- `event=proxy_configure result=success transport=http_connect_tcp`
- `event=tcp_route result=tunneled ... destination_port=443`
- UDP send and receive hooks are disabled; no UDP route event is expected.

For SOCKS5, UDP/QUIC relay is always enabled. Expected events include:

- `event=proxy_configure result=success transport=socks5_tcp_udp`
- `event=dns_route result=remote_resolution transport=socks5_domain` when the clone resolves a public hostname
- `event=tcp_route result=tunneled transport=socks5_tcp ... endpoint_connect_ms=... handshake_ms=... elapsed_ms=...`
- `event=tcp_io phase=first_write` and `event=tcp_io phase=first_read`, followed by `event=tcp_socket_close` byte and error totals
- `event=socks5_udp_associate result=success stage=complete elapsed_ms=...`
- `event=udp_route result=proxied transport=socks5_udp ...`, with `reason=socks5_udp_domain` for remotely resolved hostnames

SOCKS5 destination hostnames are represented by synthetic addresses inside the clone and sent to the proxy as SOCKS5 domain targets. This avoids sending those destination lookups to the phone resolver. `event=dns_route result=remote_resolution` confirms the native resolver handled a query; `result=system_resolver` identifies a hostname that used the phone resolver. The proxy endpoint hostname is resolved when the proxy is saved, outside the clone startup path, and the numeric IPv4 address is passed to the native hook. Older settings without a saved address use a bounded local lookup (1.2 seconds). Hostnames that are local, single-label, numeric, or non-ASCII continue through the phone resolver.

If the server does not support UDP ASSOCIATE, expect `event=socks5_udp_associate result=failure`; TikTok UDP sends then fail instead of bypassing the proxy.

TCP route `elapsed_ms` covers the proxy connection and handshake. If configuration succeeds but no `tcp_route` or `udp_route` appears, the clone did not reach an observed socket route during that capture; check the clone startup trace and Android ANR/crash records next. TCP routing is synchronous in the hook, with separate 2-second limits for endpoint connection and SOCKS handshake.

`tcp_route` failures include the phase, such as `proxy_endpoint_connect`, `socks5_greeting_read`, `socks5_auth_read`, or `socks5_target_connect_reply`. `tcp_io` reports first payload timing and aggregate byte counts, which separates slow tunnel establishment from a tunnel that connects but receives no response. The UDP event confirms the app sent UDP outside HTTP CONNECT. The absence of a UDP event only means no observed UDP send occurred during that capture; it does not prove TikTok has no other network paths. TCP events intentionally omit the destination IP, so use the proxy provider's connection log if you need to verify the server-side CONNECT destinations.
