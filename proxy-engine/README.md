# NekoBox proxy engine

This Android library embeds the reusable Go `libcore` from NekoBox and uses its
sing-box engine for each Clony virtual app process. Clony keeps its per-copy
proxy settings. The engine exposes a local SOCKS5 inbound to Clony's socket
hook and sends those connections through the configured SOCKS5 or HTTP outbound.

The pinned NekoBox source expects companion sing-box and libneko repositories
and the MatsuriDayo gomobile fork. `build_core.sh` checks out the upstream
revisions used by NekoBox, builds `libcore.aar`, and Gradle packages it in Clony.
The build needs Go 1.24 or newer, Git, the Android SDK/NDK, and network access
to GitHub and the Go module proxy. The script selects Go 1.24.0 through Go's
toolchain support, even when a newer Go launcher is installed. The generated
AAR is a build output and is not checked into the repository.

Source revisions: [NekoBox](https://github.com/MatsuriDayo/NekoBoxForAndroid),
[sing-box](https://github.com/MatsuriDayo/sing-box) at
`aed32ee3066cdbc7d471e3e0415c5134088962df`, and
[libneko](https://github.com/MatsuriDayo/libneko) at
`1c47a3af71990a7b2192e03292b4d246c308ef0b`. NekoBox's upstream build uses the
`master2` branch of [gomobile](https://github.com/MatsuriDayo/gomobile).

DNS follows NekoBox's default shape: clone DNS requests use HTTPS DNS through
the proxy, with a direct HTTPS resolver reserved for bootstrapping the remote
resolver address.

NekoBox's core can accept UDP locally, but an upstream SOCKS5 server must still
support UDP ASSOCIATE to carry UDP through that outbound. When it does not,
sing-box reports the UDP flow failure; clone traffic does not fall back
directly. TCP continues through the configured proxy.

NekoBox and its `libcore` are GPL-3.0-or-later. Revision pins and the upstream
license are retained here and in `../THIRD_PARTY_NOTICES.md`.
