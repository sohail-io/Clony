package com.clony

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SocksEndpoint(
    val server: String,
    val port: Int,
    val username: String,
    val password: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NekoBoxTabScreen(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    var endpointText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<ProxyGroup>>(emptyList()) }
    var selectedGroupId by remember { mutableStateOf(DataStore.selectedGroup) }
    var profiles by remember { mutableStateOf<List<ProxyEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var profileRevision by remember { mutableStateOf(0) }
    var selectedProxyId by remember { mutableStateOf(DataStore.selectedProxy) }
    var serviceState by remember { mutableStateOf(DataStore.serviceState) }

    val connection = remember {
        SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    }
    val connectionCallback = remember(connection, activity) {
        object : SagerConnection.Callback {
            override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
                serviceState = state
                if (!msg.isNullOrBlank()) message = msg
            }

            override fun onServiceConnected(service: ISagerNetService) {
                serviceState = runCatching {
                    BaseService.State.values()[service.state]
                }.getOrDefault(DataStore.serviceState)
            }

            override fun onServiceDisconnected() {
                serviceState = BaseService.State.Idle
            }

            override fun onBinderDied() {
                connection.disconnect(activity)
                connection.connect(activity, this)
            }

            override fun cbSpeedUpdate(stats: SpeedDisplayData) = Unit

            override fun cbTrafficUpdate(data: TrafficData) = Unit

            override fun cbSelectorUpdate(id: Long) {
                selectedProxyId = id
            }

            override fun missingPlugin(profileName: String, pluginName: String) {
                message = "${profileName} requires plugin ${pluginName}."
            }
        }
    }
    DisposableEffect(activity, connection, connectionCallback) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> connection.updateConnectionId(
                    SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND
                )
                Lifecycle.Event.ON_STOP -> connection.updateConnectionId(
                    SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND
                )
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(lifecycleObserver)
        connection.connect(activity, connectionCallback)
        onDispose {
            activity.lifecycle.removeObserver(lifecycleObserver)
            connection.disconnect(activity)
        }
    }

    val profileListener = remember {
        object : ProfileManager.Listener {
            override suspend fun onAdd(profile: ProxyEntity) {
                withContext(Dispatchers.Main.immediate) { profileRevision += 1 }
            }

            override suspend fun onUpdated(data: TrafficData) = Unit

            override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {
                if (!noTraffic) withContext(Dispatchers.Main.immediate) { profileRevision += 1 }
            }

            override suspend fun onRemoved(groupId: Long, profileId: Long) {
                withContext(Dispatchers.Main.immediate) { profileRevision += 1 }
            }
        }
    }
    DisposableEffect(profileListener) {
        ProfileManager.addListener(profileListener)
        onDispose { ProfileManager.removeListener(profileListener) }
    }

    LaunchedEffect(Unit) {
        val loadedGroups = withContext(Dispatchers.IO) {
            SagerDatabase.groupDao.allGroups().ifEmpty { listOf(DataStore.currentGroup()) }
        }
        groups = loadedGroups
        val activeGroup = loadedGroups.firstOrNull { it.id == DataStore.selectedGroup }
            ?: loadedGroups.first()
        selectedGroupId = activeGroup.id
        DataStore.selectedGroup = activeGroup.id
    }

    LaunchedEffect(selectedGroupId, profileRevision) {
        if (selectedGroupId <= 0L) return@LaunchedEffect
        isLoading = true
        profiles = withContext(Dispatchers.IO) {
            SagerDatabase.proxyDao.getByGroup(selectedGroupId)
        }
        selectedProxyId = DataStore.selectedProxy
        isLoading = false
    }

    val startService = rememberLauncherForActivityResult(
        contract = VpnRequestActivity.StartService()
    ) { permissionDenied ->
        if (permissionDenied) message = "VPN permission was not granted."
    }

    fun selectProfile(profile: ProxyEntity) {
        val changed = DataStore.selectedProxy != profile.id
        DataStore.selectedProxy = profile.id
        DataStore.currentProfile = profile.id
        selectedProxyId = profile.id
        if (changed && serviceState.canStop) SagerNet.reloadService()
    }

    fun connectProfile(profile: ProxyEntity) {
        if (serviceState.canStop && DataStore.selectedProxy == profile.id) {
            SagerNet.stopService()
            return
        }
        selectProfile(profile)
        if (!serviceState.canStop) {
            startService.launch(null)
        }
    }

    fun addEndpoint() {
        val parsed = runCatching { parseSocksEndpoint(endpointText) }
        val endpoint = parsed.getOrElse {
            message = it.message ?: "Enter server:port:username:password."
            return
        }
        if (selectedGroupId <= 0L) {
            message = "No profile group is available yet."
            return
        }
        isSaving = true
        message = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ProfileManager.createProfile(
                        selectedGroupId,
                        SOCKSBean().apply {
                            serverAddress = endpoint.server
                            serverPort = endpoint.port
                            username = endpoint.username
                            password = endpoint.password
                            protocol = SOCKSBean.PROTOCOL_SOCKS5
                        }
                    )
                }
            }.onSuccess {
                endpointText = ""
                message = "SOCKS5 profile added."
            }.onFailure {
                message = it.message ?: "Could not add this profile."
            }
            isSaving = false
        }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Proxies", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            serviceState.connected -> "Connected"
                            serviceState.canStop -> "Connecting"
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (serviceState.canStop) {
                    TextButton(onClick = { SagerNet.stopService() }) { Text("Disconnect") }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Quick add SOCKS5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = endpointText,
                        onValueChange = {
                            endpointText = it
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Server · port · username · password") },
                        placeholder = { Text("proxy.example.com:1080:user:pass") }
                    )
                    Text(
                        "Enter server:port:username:password. Use brackets for IPv6 hosts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp)
                        Button(onClick = ::addEndpoint, enabled = endpointText.isNotBlank() && !isSaving) {
                            Text("Add profile")
                        }
                    }
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (groups.isNotEmpty()) {
                val selectedGroupIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)
                ScrollableTabRow(selectedTabIndex = selectedGroupIndex, edgePadding = 16.dp) {
                    groups.forEachIndexed { index, group ->
                        Tab(
                            selected = index == selectedGroupIndex,
                            onClick = {
                                selectedGroupId = group.id
                                DataStore.selectedGroup = group.id
                            },
                            text = {
                                Text(group.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Profiles", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${profiles.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
                profiles.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No profiles in this group", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add a SOCKS5 endpoint above to get started.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isSelected = selectedProxyId == profile.id
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(profile.displayName(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${profile.displayType()} · ${profile.displayAddress()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { selectProfile(profile) },
                                        enabled = !isSelected
                                    ) { Text(if (isSelected) "Selected" else "Select") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(onClick = { connectProfile(profile) }) {
                                        Text(
                                            if (serviceState.canStop && isSelected) "Disconnect"
                                            else if (serviceState.canStop) "Switch to profile"
                                            else "Connect"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseSocksEndpoint(input: String): SocksEndpoint {
    val value = input.trim()
    require(value.isNotEmpty()) { "Enter server:port:username:password." }

    val server: String
    val credentials: List<String>
    if (value.startsWith("[")) {
        val closingBracket = value.indexOf(']')
        require(closingBracket > 1 && value.getOrNull(closingBracket + 1) == ':') {
            "For IPv6, use [address]:port:username:password."
        }
        server = value.substring(1, closingBracket)
        credentials = value.substring(closingBracket + 2).split(':', limit = 3)
    } else {
        val parts = value.split(':', limit = 4)
        require(parts.size == 4) { "Use server:port:username:password." }
        server = parts[0].trim()
        credentials = parts.drop(1)
    }

    require(server.isNotBlank()) { "Server is required." }
    require(credentials.size == 3) { "Use server:port:username:password." }
    val port = credentials[0].trim().toIntOrNull()
        ?: throw IllegalArgumentException("Port must be between 1 and 65535.")
    require(port in 1..65535) { "Port must be between 1 and 65535." }
    val username = credentials[1].trim()
    val password = credentials[2]
    require(username.isNotBlank()) { "Username is required." }
    require(password.isNotBlank()) { "Password is required." }
    return SocksEndpoint(server, port, username, password)
}
