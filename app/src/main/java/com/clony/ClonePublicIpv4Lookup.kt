package com.clony

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.core.network.CloneProxyStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Looks up the public IPv4 address for a clone using that clone's saved network route. */
internal object ClonePublicIpv4Lookup {
    private const val IPV4_ENDPOINT = "https://api4.ipify.org"
    private const val NETWORK_TIMEOUT_MS = 8_000

    /** Looks up the phone's public IPv4 through its default, unproxied network route. */
    suspend fun fetchPhonePublicIpv4(): String? = withContext(Dispatchers.IO) {
        try {
            fetchWithoutProxy()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetch(context: Context, app: AppTile): String? {
        val userId = app.virtualUserId ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val proxy = CloneProxyStore.read(appContext, app.packageName, userId)
                when (proxy.type) {
                    0 -> fetchWithoutProxy()
                    1, 2 -> ProxyRegionResolver.detectPublicIpv4(
                        proxy,
                        UUID.randomUUID().toString().take(8)
                    )
                    else -> null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
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
