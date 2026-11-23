package com.clony

/** Clone-time settings for routing a copy through its proxy's automatically detected region. */
internal data class CloneFeedRegionSettings(
    val socks5Proxy: String = "",
    val detectedCountryIso: String = "",
    val savedProxyType: Int = 0
) {
    val isConfigured: Boolean
        get() = socks5Proxy.isNotBlank()
}
