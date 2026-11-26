package com.clony

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import top.niunaijun.blackbox.core.network.CloneProxyConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal data class ProxyGeoRegion(
    val countryIso: String,
    val latitude: Double,
    val longitude: Double
)

/** Resolves public network details through saved clone proxy configurations. */
internal object ProxyRegionResolver {
    private const val TAG = "ClonyProxyRegion"
    private const val GEO_HOST = "ipwho.is"
    private const val GEO_PORT = 443
    private const val IPV4_HOST = "api4.ipify.org"
    private const val IPV4_PORT = 443
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_HEADER_BYTES = 8 * 1024
    private const val MAX_RESPONSE_BYTES = 32 * 1024

    /** Returns the public IPv4 address visible through the configured proxy route. */
    @Throws(IOException::class)
    fun detectPublicIpv4(config: CloneProxyConfig, traceId: String): String {
        if (config.type != 1 && config.type != 2) {
            throw IOException("Choose a SOCKS5 or HTTP proxy before detecting its public IPv4 address.")
        }

        val started = SystemClock.elapsedRealtime()
        val endpoint = config.resolvedHost.ifEmpty { config.host }
        val endpointSource = if (config.resolvedHost.isEmpty()) "configured_host" else "resolved_host"
        val endpointKind = if (isIpv4Address(endpoint)) "ipv4" else "hostname"
        Log.i(TAG, "event=ipv4_detect_begin trace_id=$traceId proxy_type=${config.type} endpoint_kind=$endpointKind endpoint_source=$endpointSource")
        try {
            val addresses = traceStage(traceId, "ipv4_proxy_endpoint_resolve", "ipv4_stage") {
                InetAddress.getAllByName(endpoint).distinctBy(InetAddress::getHostAddress)
            }
            if (addresses.isEmpty()) throw IOException("Proxy hostname returned no addresses.")

            var lastError: IOException? = null
            addresses.forEachIndexed { index, address ->
                val proxySocket = Socket()
                try {
                    Log.i(
                        TAG,
                        "event=ipv4_proxy_endpoint_attempt trace_id=$traceId attempt=${index + 1} total=${addresses.size} endpoint_address=${address.hostAddress}"
                    )
                    traceStage(traceId, "ipv4_proxy_endpoint_connect", "ipv4_stage") {
                        proxySocket.connect(InetSocketAddress(address, config.port), CONNECT_TIMEOUT_MS)
                        proxySocket.soTimeout = READ_TIMEOUT_MS
                    }
                    when (config.type) {
                        1 -> connectSocks5(proxySocket, config, traceId, IPV4_HOST, IPV4_PORT, "ipv4_stage")
                        2 -> connectHttp(proxySocket, config, traceId, IPV4_HOST, IPV4_PORT, "ipv4_stage")
                    }
                    val ipv4 = requestPublicIpv4(proxySocket, traceId)
                    Log.i(
                        TAG,
                        "event=ipv4_proxy_endpoint_attempt trace_id=$traceId attempt=${index + 1} result=success endpoint_address=${address.hostAddress}"
                    )
                    Log.i(TAG, "event=ipv4_detect_complete trace_id=$traceId result=success elapsed_ms=${SystemClock.elapsedRealtime() - started}")
                    return ipv4
                } catch (error: IOException) {
                    lastError = error
                    Log.w(
                        TAG,
                        "event=ipv4_proxy_endpoint_attempt trace_id=$traceId attempt=${index + 1} result=failure endpoint_address=${address.hostAddress} error_type=${error.javaClass.simpleName} error_detail=${diagnosticDetail(error)}"
                    )
                } finally {
                    runCatching { proxySocket.close() }
                }
            }
            throw lastError ?: IOException("Proxy hostname returned no usable addresses.")
        } catch (error: IOException) {
            Log.w(TAG, "event=ipv4_detect_complete trace_id=$traceId result=failure error_type=${error.javaClass.simpleName} error_detail=${diagnosticDetail(error)} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            val detail = error.message?.takeIf(String::isNotBlank)
            throw IOException(
                detail?.let { "Could not detect the proxy's public IPv4 address: $it" }
                    ?: "Could not detect the proxy's public IPv4 address. Check the proxy connection and try again.",
                error
            )
        }
    }

    @Throws(IOException::class)
    fun detectRegion(config: CloneProxyConfig, traceId: String): ProxyGeoRegion {
        if (config.type != 1 && config.type != 2) {
            throw IOException("Choose a SOCKS5 or HTTP proxy before detecting its region.")
        }

        val started = SystemClock.elapsedRealtime()
        Log.i(TAG, "event=region_detect_begin trace_id=$traceId proxy_type=${config.type}")
        val proxySocket = Socket()
        try {
            val endpoint = config.resolvedHost.ifEmpty { config.host }
            traceStage(traceId, "proxy_endpoint_connect") {
                proxySocket.connect(InetSocketAddress(endpoint, config.port), CONNECT_TIMEOUT_MS)
                proxySocket.soTimeout = READ_TIMEOUT_MS
            }
            when (config.type) {
                1 -> connectSocks5(proxySocket, config, traceId, GEO_HOST, GEO_PORT)
                2 -> connectHttp(proxySocket, config, traceId, GEO_HOST, GEO_PORT)
            }
            val region = requestRegion(proxySocket, traceId)
            Log.i(TAG, "event=region_detect_complete trace_id=$traceId result=success country=${region.countryIso.uppercase(Locale.ROOT)} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            return region
        } catch (error: IOException) {
            Log.w(TAG, "event=region_detect_complete trace_id=$traceId result=failure error_type=${error.javaClass.simpleName} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            val detail = error.message?.takeIf(String::isNotBlank)
            throw IOException(
                detail?.let { "Could not detect the proxy's exit country or GPS location: $it" }
                    ?: "Could not detect the proxy's exit country or GPS location. Check the proxy connection and try again.",
                error
            )
        } finally {
            runCatching { proxySocket.close() }
        }
    }

    private fun connectSocks5(
        socket: Socket,
        config: CloneProxyConfig,
        traceId: String,
        destinationHost: String,
        destinationPort: Int,
        traceEvent: String = "region_stage"
    ) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val username = config.username.toByteArray(StandardCharsets.UTF_8)
        val password = config.password.toByteArray(StandardCharsets.UTF_8)
        val requiresAuth = username.isNotEmpty()
        val expectedMethod = if (requiresAuth) 2 else 0

        traceStage(traceId, "socks5_greeting", traceEvent) {
            output.write(if (requiresAuth) byteArrayOf(5, 1, 2) else byteArrayOf(5, 1, 0))
            output.flush()
            val reply = try {
                readExact(input, 2)
            } catch (error: SocketTimeoutException) {
                throw IOException("socks5_greeting_timeout", error)
            }
            val version = reply[0].toInt() and 0xff
            val method = reply[1].toInt() and 0xff
            if (version != 5 || method != expectedMethod) {
                throw IOException("socks5_method_rejected version=$version selected_method=$method expected_method=$expectedMethod")
            }
            reply
        }

        if (requiresAuth) {
            traceStage(traceId, "socks5_authentication", traceEvent) {
                val authRequest = ByteArray(3 + username.size + password.size)
                var offset = 0
                authRequest[offset++] = 1
                authRequest[offset++] = username.size.toByte()
                username.copyInto(authRequest, offset)
                offset += username.size
                authRequest[offset++] = password.size.toByte()
                password.copyInto(authRequest, offset)
                output.write(authRequest)
                output.flush()
                val reply = try {
                    readExact(input, 2)
                } catch (error: IOException) {
                    throw IOException(
                        "SOCKS5 authentication reply could not be read (${diagnosticDetail(error)}).",
                        error
                    )
                }
                val version = reply[0].toInt() and 0xff
                val status = reply[1].toInt() and 0xff
                if (version != 1 || status != 0) {
                    throw IOException("socks5_auth_rejected version=$version status=$status")
                }
                reply
            }
        }

        // Send the hostname as SOCKS5 ATYP=DOMAIN so DNS resolution happens at
        // the proxy side instead of through the phone's resolver.
        val destination = destinationHost.toByteArray(StandardCharsets.US_ASCII)
        traceStage(traceId, "socks5_destination_tunnel", traceEvent) {
            val request = ByteArray(7 + destination.size)
            request[0] = 5
            request[1] = 1
            request[2] = 0
            request[3] = 3
            request[4] = destination.size.toByte()
            destination.copyInto(request, 5)
            request[request.lastIndex - 1] = (destinationPort ushr 8).toByte()
            request[request.lastIndex] = destinationPort.toByte()
            output.write(request)
            output.flush()

            val reply = readExact(input, 4)
            val version = reply[0].toInt() and 0xff
            val status = reply[1].toInt() and 0xff
            if (version != 5 || status != 0) {
                throw IOException("socks5_tunnel_rejected version=$version reply_code=$status")
            }
            val addressLength = when (reply[3].toInt() and 0xff) {
                1 -> 4
                4 -> 16
                3 -> readExact(input, 1)[0].toInt() and 0xff
                else -> throw IOException("SOCKS5 returned an invalid address.")
            }
            readExact(input, addressLength + 2)
        }
    }

    private fun connectHttp(
        socket: Socket,
        config: CloneProxyConfig,
        traceId: String,
        destinationHost: String,
        destinationPort: Int,
        traceEvent: String = "region_stage"
    ) {
        val output = socket.getOutputStream()
        val authority = "$destinationHost:$destinationPort"
        val request = StringBuilder()
            .append("CONNECT ").append(authority).append(" HTTP/1.1\r\n")
            .append("Host: ").append(authority).append("\r\n")
        if (config.username.isNotEmpty()) {
            val credentials = "${config.username}:${config.password}"
                .toByteArray(StandardCharsets.UTF_8)
            request.append("Proxy-Authorization: Basic ")
                .append(Base64.encodeToString(credentials, Base64.NO_WRAP)).append("\r\n")
        }
        request.append("\r\n")
        traceStage(traceId, "http_connect_tunnel", traceEvent) {
            output.write(request.toString().toByteArray(StandardCharsets.US_ASCII))
            output.flush()

            val input = socket.getInputStream()
            val statusLine = readAsciiLine(input, MAX_HEADER_BYTES)
                ?: throw IOException("HTTP proxy closed the connection.")
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
                ?: throw IOException("HTTP proxy returned an invalid response.")
            var headerBytes = statusLine.length + 2
            while (true) {
                val line = readAsciiLine(input, MAX_HEADER_BYTES - headerBytes)
                    ?: throw IOException("HTTP proxy returned incomplete headers.")
                headerBytes += line.length + 2
                if (headerBytes > MAX_HEADER_BYTES) throw IOException("HTTP proxy headers are too large.")
                if (line.isEmpty()) break
            }
            if (status !in 200..299) throw IOException("http_connect_rejected status=$status")
        }
    }

    private fun requestRegion(tunnel: Socket, traceId: String): ProxyGeoRegion {
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(tunnel, GEO_HOST, GEO_PORT, true) as SSLSocket
        tls.soTimeout = READ_TIMEOUT_MS
        val parameters = tls.sslParameters
        parameters.endpointIdentificationAlgorithm = "HTTPS"
        tls.sslParameters = parameters
        traceStage(traceId, "tls_handshake") { tls.startHandshake() }

        tls.use { secureSocket ->
            val request = "GET /?fields=success,country_code,latitude,longitude HTTP/1.1\r\n" +
                "Host: $GEO_HOST\r\n" +
                "User-Agent: Clony/1.0\r\n" +
                "Accept: application/json\r\n" +
                "Accept-Encoding: identity\r\n" +
                "Connection: close\r\n\r\n"
            val response = traceStage(traceId, "region_http_request_response") {
                secureSocket.outputStream.write(request.toByteArray(StandardCharsets.US_ASCII))
                secureSocket.outputStream.flush()
                readHttpResponse(secureSocket.inputStream)
            }
            Log.i(TAG, "event=region_http_response trace_id=$traceId status=${response.status} body_bytes=${response.body.size}")
            if (response.status !in 200..299) throw IOException("Region lookup returned HTTP ${response.status}.")
            val payload = try {
                JSONObject(String(response.body, StandardCharsets.UTF_8))
            } catch (error: org.json.JSONException) {
                throw IOException("Region lookup returned invalid data.", error)
            }
            if (!payload.optBoolean("success", false)) throw IOException("Region lookup did not return a country.")
            val countryIso = payload.optString("country_code", "").trim().uppercase(Locale.ROOT)
            if (!countryIso.matches(Regex("[A-Z]{2}"))) throw IOException("Region lookup returned an invalid country code.")
            val latitude = payload.optDouble("latitude", Double.NaN)
            val longitude = payload.optDouble("longitude", Double.NaN)
            if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                !longitude.isFinite() || longitude !in -180.0..180.0
            ) {
                throw IOException("Region lookup did not return valid proxy location coordinates.")
            }
            return ProxyGeoRegion(countryIso.lowercase(Locale.ROOT), latitude, longitude)
        }
    }

    private fun requestPublicIpv4(tunnel: Socket, traceId: String): String {
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(tunnel, IPV4_HOST, IPV4_PORT, true) as SSLSocket
        tls.soTimeout = READ_TIMEOUT_MS
        val parameters = tls.sslParameters
        parameters.endpointIdentificationAlgorithm = "HTTPS"
        tls.sslParameters = parameters
        traceStage(traceId, "ipv4_tls_handshake", "ipv4_stage") { tls.startHandshake() }

        tls.use { secureSocket ->
            val request = "GET / HTTP/1.1\r\n" +
                "Host: $IPV4_HOST\r\n" +
                "User-Agent: Clony/1.0\r\n" +
                "Accept: text/plain\r\n" +
                "Accept-Encoding: identity\r\n" +
                "Connection: close\r\n\r\n"
            val response = traceStage(traceId, "ipv4_http_request_response", "ipv4_stage") {
                secureSocket.outputStream.write(request.toByteArray(StandardCharsets.US_ASCII))
                secureSocket.outputStream.flush()
                readHttpResponse(secureSocket.inputStream)
            }
            Log.i(TAG, "event=ipv4_http_response trace_id=$traceId status=${response.status} body_bytes=${response.body.size}")
            if (response.status !in 200..299) throw IOException("Public IPv4 lookup returned HTTP ${response.status}.")
            return String(response.body, StandardCharsets.UTF_8).trim()
                .takeIf(::isIpv4Address)
                ?: throw IOException("Public IPv4 lookup returned an invalid address.")
        }
    }

    private fun isIpv4Address(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all { it in '0'..'9' } &&
                (part.toIntOrNull() ?: -1) in 0..255
        }
    }

    private inline fun <T> traceStage(
        traceId: String,
        stage: String,
        traceEvent: String = "region_stage",
        block: () -> T
    ): T {
        val started = SystemClock.elapsedRealtime()
        return try {
            val value = block()
            Log.i(TAG, "event=$traceEvent trace_id=$traceId stage=$stage result=success elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            value
        } catch (error: Throwable) {
            Log.w(TAG, "event=$traceEvent trace_id=$traceId stage=$stage result=failure error_type=${error.javaClass.simpleName} error_detail=${diagnosticDetail(error)} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            throw error
        }
    }

    private fun diagnosticDetail(error: Throwable): String {
        var root = error
        while (root.cause != null && root.cause !== root) root = root.cause!!
        val message = root.message
            ?.replace(Regex("[\\r\\n\\t ]+"), "_")
            ?.take(120)
        return buildString {
            append(root.javaClass.simpleName)
            if (!message.isNullOrEmpty()) append(':').append(message)
        }
    }

    private data class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    private fun readHttpResponse(input: InputStream): HttpResponse {
        val statusLine = readAsciiLine(input, MAX_HEADER_BYTES)
            ?: throw IOException("Region lookup returned an empty response.")
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Region lookup returned an invalid response.")
        val headers = linkedMapOf<String, String>()
        var headerBytes = statusLine.length + 2
        while (true) {
            val line = readAsciiLine(input, MAX_HEADER_BYTES - headerBytes)
                ?: throw IOException("Region lookup returned incomplete headers.")
            headerBytes += line.length + 2
            if (headerBytes > MAX_HEADER_BYTES) throw IOException("Region lookup headers are too large.")
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase(Locale.ROOT)] =
                    line.substring(separator + 1).trim()
            }
        }

        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
            headers["content-length"] != null -> readExact(
                input,
                headers.getValue("content-length").toIntOrNull()
                    ?.takeIf { it in 0..MAX_RESPONSE_BYTES }
                    ?: throw IOException("Region lookup response is too large.")
            )
            else -> readToEnd(input)
        }
        return HttpResponse(status, headers, body)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input, 128) ?: throw IOException("Region lookup response ended early.")
            val chunkSize = sizeLine.substringBefore(';').trim().toLongOrNull(16)
                ?.takeIf { it in 0..MAX_RESPONSE_BYTES.toLong() }
                ?.toInt() ?: throw IOException("Region lookup returned an invalid chunk.")
            if (chunkSize == 0) {
                while (readAsciiLine(input, 8 * 1024)?.isNotEmpty() == true) Unit
                break
            }
            if (output.size() + chunkSize > MAX_RESPONSE_BYTES) throw IOException("Region lookup response is too large.")
            output.write(readExact(input, chunkSize))
            val ending = readExact(input, 2)
            if (ending[0] != '\r'.code.toByte() || ending[1] != '\n'.code.toByte()) {
                throw IOException("Region lookup returned malformed chunks.")
            }
        }
        return output.toByteArray()
    }

    private fun readToEnd(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("Region lookup response is too large.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(bytes, offset, size - offset)
            if (count < 0) throw IOException("Proxy response ended early.")
            offset += count
        }
        return bytes
    }

    private fun readAsciiLine(input: InputStream, maxBytes: Int): String? {
        val line = ByteArrayOutputStream()
        while (line.size() <= maxBytes) {
            val next = input.read()
            if (next < 0) return if (line.size() == 0) null else line.toString("US-ASCII")
            if (next == '\n'.code) {
                val bytes = line.toByteArray()
                val length = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return String(bytes, 0, length, StandardCharsets.US_ASCII)
            }
            line.write(next)
        }
        throw IOException("Proxy response headers are too large.")
    }
}
