package com.clony

internal fun proxyProbeFailureMessage(type: Int, result: Int): String {
    val stage = result / 1000
    val code = result % 1000
    if (stage !in 1..4 || code == 0) {
        return if (type == 1) {
            "SOCKS5 proxy test failed (code $result). Check address and credentials."
        } else {
            "HTTP CONNECT test failed (code $result). Check address and credentials."
        }
    }
    return when (stage) {
        1 -> "Could not connect to the proxy (code $code). Check its address, port, and availability."
        2 -> if (type == 1) {
            if (code == 13) {
                "SOCKS5 handshake was rejected (EACCES 13). Confirm this endpoint is SOCKS5, not HTTP CONNECT, " +
                    "and check the proxy authentication method and credentials."
            } else {
                "SOCKS5 TCP tunnel failed (code $code). Check credentials and CONNECT support."
            }
        } else if (code == 71) {
            "This endpoint did not reply as an HTTP proxy. Check the proxy type; a SOCKS5 endpoint must use SOCKS5 mode."
        } else if (code == 407) {
            "HTTP proxy returned 407. It requires or rejected authentication; check the credentials. " +
                "This app sends Basic proxy authentication."
        } else if (code in 100..599) {
            "HTTP CONNECT returned HTTP $code. A 2xx response is required; check credentials and proxy destination rules."
        } else {
            "HTTP CONNECT failed (code $code). Check credentials and CONNECT support."
        }
        3 -> "SOCKS5 TCP tunnel worked, but the UDP control connection failed (code $code)."
        else -> if (code == 110) {
            "SOCKS5 TCP tunnel worked, but UDP ASSOCIATE timed out (code $code). " +
                "This proxy may not support UDP/QUIC relay."
        } else {
            "SOCKS5 TCP tunnel worked, but UDP ASSOCIATE was refused (code $code). " +
                "This proxy may not support UDP/QUIC relay."
        }
    }
}
