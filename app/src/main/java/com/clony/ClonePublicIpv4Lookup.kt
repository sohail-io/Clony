package com.clony

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Looks up the phone's public IPv4 through its default, unproxied network route. */
internal object ClonePublicIpv4Lookup {
    private const val IPV4_ENDPOINT = "https://api4.ipify.org"
    private const val NETWORK_TIMEOUT_MS = 8_000

    suspend fun fetchPhonePublicIpv4(): String? = withContext(Dispatchers.IO) {
        try {
            fetchWithoutProxy()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchWithoutProxy(): String? {
        val connection = (URL(IPV4_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            useCaches = false
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText().trim() }
                .takeIf(::isIpv4Address)
        } finally {
            connection.disconnect()
        }
    }

    private fun isIpv4Address(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all { it in '0'..'9' } &&
                (part.toIntOrNull() ?: -1) in 0..255
        }
    }
}
