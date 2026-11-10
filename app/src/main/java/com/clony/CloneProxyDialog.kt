package com.clony

import android.util.Log
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.NativeCore
import top.niunaijun.blackbox.core.network.CloneProxyConfig
import top.niunaijun.blackbox.core.network.CloneProxyStore

private fun proxyProbeFailureMessage(type: Int, result: Int): String {
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
            "SOCKS5 TCP tunnel failed (code $code). Check credentials and CONNECT support."
        } else if (code == 71) {
            "This endpoint did not reply as an HTTP proxy. Check the proxy type; a SOCKS5 endpoint must use SOCKS5 mode."
        } else if (code in 100..599) {
            "HTTP CONNECT returned HTTP $code. A 2xx response is required; check credentials and proxy destination rules."
        } else {
            "HTTP CONNECT failed (code $code). Check credentials and CONNECT support."
        }
        3 -> "SOCKS5 TCP tunnel worked, but the UDP control connection failed (code $code)."
        else -> "SOCKS5 TCP tunnel worked, but UDP ASSOCIATE was refused (code $code). " +
                "This proxy cannot relay UDP/QUIC."
    }
}

@Composable
internal fun CloneProxyDialog(app: AppTile, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = app.virtualUserId
    var type by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(app.packageName, userId) {
        if (userId == null) {
            message = "Proxy settings are available for cloned apps only."
            busy = false
            return@LaunchedEffect
        }
        try {
            val config = withContext(Dispatchers.IO) {
                CloneProxyStore.read(context, app.packageName, userId)
            }
            type = config.type
            input = config.input()
            loaded = true
        } catch (error: Exception) {
            message = error.message ?: "Couldn't load this copy's proxy settings."
        } finally {
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Network · ${app.label} Copy ${app.copyNumber ?: 1}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("No proxy", "SOCKS5", "HTTP CONNECT").forEachIndexed { index, label ->
                    Row {
                        RadioButton(
                            selected = type == index,
                            onClick = { type = index; message = null },
                            enabled = !busy && loaded
                        )
                        TextButton(
                            onClick = { type = index; message = null },
                            enabled = !busy && loaded
                        ) {
                            Text(label)
                        }
                    }
                }

                if (type == 0) {
                    Text("Uses your phone’s normal Wi-Fi or mobile-data connection.")
                } else {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; message = null },
                        enabled = !busy && loaded,
                        label = { Text("IP:port:user:pass") },
                        supportingText = { Text("IPv4. Use IP:port if no authentication is needed.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Text(
                        if (type == 1) {
                            "SOCKS5 can relay TCP and UDP/QUIC when the server supports UDP ASSOCIATE. " +
                                    "DNS uses your phone’s resolver; this is not a VPN."
                        } else {
                            "HTTP CONNECT sends TCP through the proxy. UDP/QUIC uses your phone’s normal network, " +
                                    "not the proxy. DNS uses your phone’s resolver; this is not a VPN."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        enabled = !busy && loaded,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val config = CloneProxyConfig.parse(type, input)
                                    val result = withContext(Dispatchers.IO) {
                                        NativeCore.probeProxy(
                                            config.type,
                                            config.host,
                                            config.port,
                                            config.username.toByteArray(Charsets.UTF_8),
                                            config.password.toByteArray(Charsets.UTF_8)
                                        )
                                    }
                                    message = if (result == 0) {
                                        if (config.type == 1) {
                                            "Proxy accepted a TCP tunnel and SOCKS5 UDP relay. This does not verify all clone traffic."
                                        } else {
                                            "Proxy accepted a TCP tunnel to 1.1.1.1:443. UDP/QUIC still uses your phone’s normal network."
                                        }
                                    } else {
                                        proxyProbeFailureMessage(config.type, result)
                                    }
                                    val resultStatus = if (result == 0) "success" else "failure"
                                    val transport = if (config.type == 1) "socks5_tcp_udp" else "http_connect_tcp"
                                    Log.i("ClonyCloneProbe", "event=proxy_transport_probe"
                                        + " result=$resultStatus code=$result destination=1.1.1.1:443"
                                        + " transport=$transport"
                                        + " package=${app.packageName} userId=${app.virtualUserId ?: -1}"
                                        + " endpoint_or_credentials_logged=false")
                                } catch (error: Exception) {
                                    message = error.message ?: "Proxy tunnel test failed."
                                    Log.w("ClonyCloneProbe", "event=proxy_tunnel_probe result=error"
                                        + " exception=${error.javaClass.simpleName}"
                                        + " package=${app.packageName}"
                                        + " userId=${app.virtualUserId ?: -1}")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text("Test proxy connection")
                    }
                }

                Text(
                    "Saving stops this copy so its next launch uses the new setting.",
                    style = MaterialTheme.typography.bodySmall
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && loaded,
                onClick = {
                    val selectedUserId = userId ?: return@TextButton
                    scope.launch {
                        busy = true
                        try {
                            val config = CloneProxyConfig.parse(type, input)
                            withContext(Dispatchers.IO) {
                                BlackBoxCore.get().stopPackage(app.packageName, selectedUserId)
                                CloneProxyStore.save(context, app.packageName, selectedUserId, config)
                            }
                            onSaved()
                        } catch (error: Exception) {
                            message = error.message ?: "Couldn't save proxy settings."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
