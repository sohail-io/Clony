package com.clony

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
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
import top.niunaijun.blackbox.core.network.CloneProxyConfig
import java.util.Locale

private data class ProxyEndpoint(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NekoBoxTabScreen(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    var proxyName by remember { mutableStateOf("") }
    var proxyAddress by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var profileMenuId by remember { mutableStateOf<Long?>(null) }
    var profileToDelete by remember { mutableStateOf<ProxyEntity?>(null) }
    var deletingProfileId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<ProxyGroup>>(emptyList()) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
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
                message = "$profileName requires plugin $pluginName."
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

    fun disconnectProxy() {
        val stoppedByService = runCatching { connection.service?.stop() == true }
            .getOrDefault(false)
        if (!stoppedByService) SagerNet.stopService()
    }

    fun selectProfile(profile: ProxyEntity) {
        val changed = DataStore.selectedProxy != profile.id
        DataStore.selectedProxy = profile.id
        DataStore.currentProfile = profile.id
        selectedProxyId = profile.id
        if (changed && serviceState.canStop) SagerNet.reloadService()
    }

    fun connectProfile(profile: ProxyEntity) {
        // Use the same selection state as the card label. The service callback can
        // update selectedProxyId independently of DataStore.selectedProxy, so
        // checking DataStore here can turn a visible Disconnect action into a reload.
        if (serviceState.canStop && selectedProxyId == profile.id) {
            disconnectProxy()
            return
        }
        selectProfile(profile)
        if (!serviceState.canStop) {
            startService.launch(null)
        }
    }

    fun dismissAddDialog() {
        if (isSaving) return
        showAddDialog = false
        dialogError = null
    }

    fun saveProxy() {
        if (isSaving) return
        val name = proxyName.trim()
        if (name.isBlank()) {
            dialogError = "Enter a name for this proxy."
            return
        }
        val endpoint = runCatching { parseProxyAddress(proxyAddress) }.getOrElse {
            dialogError = it.message ?: "Enter a valid proxy address."
            return
        }
        if (selectedGroupId <= 0L) {
            dialogError = "No profile group is available yet."
            return
        }

        val bean = SOCKSBean().apply {
            serverAddress = endpoint.host
            serverPort = endpoint.port
            username = endpoint.username
            password = endpoint.password
            protocol = SOCKSBean.PROTOCOL_SOCKS5
            this.name = name
        }
        isSaving = true
        dialogError = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val existingProfiles = SagerDatabase.proxyDao.getAll()
                    val duplicateName = existingProfiles.any { existing ->
                        runCatching {
                            existing.requireBean().name?.trim()?.equals(name, ignoreCase = true) == true
                        }.getOrDefault(false)
                    }
                    if (duplicateName) {
                        error("A proxy with this name already exists.")
                    }
                    val normalizedHost = normalizeProxyHost(endpoint.host)
                    val duplicateAddress = existingProfiles.any { existing ->
                        runCatching {
                            val existingBean = existing.requireBean()
                            normalizeProxyHost(existingBean.serverAddress.orEmpty()) == normalizedHost &&
                                existingBean.serverPort == endpoint.port
                        }.getOrDefault(false)
                    }
                    if (duplicateAddress) {
                        error("A proxy with this address already exists.")
                    }
                    val warning = probeCloneProxy(
                        CloneProxyConfig(
                            1,
                            endpoint.host,
                            endpoint.port,
                            endpoint.username,
                            endpoint.password
                        )
                    )
                    ProfileManager.createProfile(selectedGroupId, bean)
                    warning
                }
            }
            result.onSuccess { warning ->
                proxyName = ""
                proxyAddress = ""
                showAddDialog = false
                message = warning?.let { "Proxy verified and profile added. $it" }
                    ?: "Proxy verified and profile added."
            }.onFailure { error ->
                dialogError = error.message ?: "Could not verify and add this proxy."
            }
            isSaving = false
        }
    }

    val connectionLabel = when {
        serviceState.connected -> "Connected"
        serviceState.canStop -> "Connecting"
        else -> "Disconnected"
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Profiles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${profiles.size} saved · $connectionLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "${profiles.size}",
                        Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (groups.size > 1) {
                    Box {
                        TextButton(onClick = { groupMenuExpanded = true }) { Text("Group") }
                        DropdownMenu(
                            expanded = groupMenuExpanded,
                            onDismissRequest = { groupMenuExpanded = false }
                        ) {
                            groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.proxyTabName()) },
                                    onClick = {
                                        selectedGroupId = group.id
                                        DataStore.selectedGroup = group.id
                                        groupMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (serviceState.canStop) {
                    TextButton(onClick = ::disconnectProxy) { Text("Disconnect") }
                }
            }

            message?.let { statusMessage ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            statusMessage,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { message = null }) { Text("Dismiss") }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    profiles.isEmpty() -> Column(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("↗", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No profiles yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add a named SOCKS5 or HTTP proxy to get started.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            val isSelected = selectedProxyId == profile.id
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(46.dp),
                                            shape = RoundedCornerShape(15.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                val typeLabel = profile.displayType().uppercase()
                                                Text(
                                                    if (typeLabel.contains("SOCKS")) "S5" else typeLabel.take(2),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                profile.displayName(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                "${profile.displayType()} · ${profile.displayAddress()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isSelected) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    "Selected",
                                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { profileMenuId = profile.id },
                                                enabled = deletingProfileId == null,
                                                modifier = Modifier.semantics {
                                                    contentDescription = "Options for ${profile.displayName()}"
                                                }
                                            ) {
                                                Text("⋮", style = MaterialTheme.typography.headlineSmall)
                                            }
                                            DropdownMenu(
                                                expanded = profileMenuId == profile.id,
                                                onDismissRequest = { profileMenuId = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete proxy") },
                                                    enabled = deletingProfileId == null,
                                                    onClick = {
                                                        profileMenuId = null
                                                        profileToDelete = profile
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { selectProfile(profile) },
                                            enabled = !isSelected
                                        ) {
                                            Text(if (isSelected) "Current profile" else "Select")
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Button(onClick = { connectProfile(profile) }) {
                                            Text(
                                                if (serviceState.canStop && isSelected) "Disconnect"
                                                else if (serviceState.canStop) "Switch"
                                                else "Connect"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        dialogError = null
                        showAddDialog = true
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp)
                        .semantics { contentDescription = "Add proxy" }
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal)
                }
            }

            val tabGroups = groups.filterNot { it.isDefaultProxyGroup() }
            if (tabGroups.isNotEmpty()) {
                val selectedGroupIndex = tabGroups.indexOfFirst { it.id == selectedGroupId }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedGroupIndex.coerceAtLeast(0),
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        indicator = {}
                    ) {
                        tabGroups.forEachIndexed { index, group ->
                            Tab(
                                selected = index == selectedGroupIndex,
                                onClick = {
                                    selectedGroupId = group.id
                                    DataStore.selectedGroup = group.id
                                },
                                text = {
                                    Text(
                                        group.proxyTabName(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = ::dismissAddDialog,
            title = { Text("Add proxy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = proxyName,
                        onValueChange = {
                            proxyName = it
                            dialogError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Name") },
                        placeholder = { Text("Work proxy") },
                        enabled = !isSaving
                    )
                    OutlinedTextField(
                        value = proxyAddress,
                        onValueChange = {
                            proxyAddress = it
                            dialogError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = dialogError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        label = { Text("Proxy address") },
                        placeholder = { Text("host:port:username:password") },
                        enabled = !isSaving
                    )
                    Text(
                        "Use host:port:username:password. Wrap IPv6 hosts in brackets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    dialogError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = ::saveProxy,
                    enabled = proxyName.isNotBlank() && proxyAddress.isNotBlank() && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "Testing proxy…" else "Add proxy")
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismissAddDialog, enabled = !isSaving) { Text("Cancel") }
            }
        )
    }

    profileToDelete?.let { profile ->
        val profileName = runCatching { profile.displayName() }.getOrDefault("this proxy")
        AlertDialog(
            onDismissRequest = {
                if (deletingProfileId == null) profileToDelete = null
            },
            title = { Text("Delete proxy?") },
            text = { Text("Delete $profileName from saved profiles?") },
            confirmButton = {
                TextButton(
                    enabled = deletingProfileId == null,
                    onClick = {
                        if (deletingProfileId != null) return@TextButton
                        deletingProfileId = profile.id
                        if (serviceState.canStop &&
                            (selectedProxyId == profile.id || DataStore.selectedProxy == profile.id)
                        ) {
                            disconnectProxy()
                        }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ProfileManager.deleteProfile(profile.groupId, profile.id)
                                }
                            }.onSuccess {
                                message = "Proxy profile deleted."
                                profileToDelete = null
                            }.onFailure { error ->
                                message = error.message ?: "Could not delete this proxy profile."
                            }
                            deletingProfileId = null
                        }
                    }
                ) {
                    if (deletingProfileId == profile.id) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = deletingProfileId == null,
                    onClick = { profileToDelete = null }
                ) { Text("Cancel") }
            }
        )
    }
}

private fun ProxyGroup.proxyTabName(): String {
    val label = displayName()
    return if (ungrouped || label.equals("Ungrouped", ignoreCase = true)) "Default" else label
}

private fun ProxyGroup.isDefaultProxyGroup(): Boolean {
    val label = displayName()
    return ungrouped || label.equals("Ungrouped", ignoreCase = true) ||
        label.equals("Default", ignoreCase = true)
}

private fun normalizeProxyHost(host: String): String =
    host.trim().removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT)

private fun parseProxyAddress(input: String): ProxyEndpoint {
    val value = input.trim()
    require(value.isNotEmpty()) { "Enter host:port:username:password." }

    val host: String
    val parts: List<String>
    if (value.startsWith("[")) {
        val closingBracket = value.indexOf(']')
        require(closingBracket > 1 && value.getOrNull(closingBracket + 1) == ':') {
            "For IPv6, use [address]:port:username:password."
        }
        host = value.substring(1, closingBracket)
        parts = value.substring(closingBracket + 2).split(':', limit = 3)
    } else {
        val fields = value.split(':', limit = 4)
        require(fields.size == 4) { "Use host:port:username:password." }
        host = fields[0].trim()
        parts = fields.drop(1)
    }

    require(host.isNotBlank()) { "Proxy host is required." }
    require(parts.size == 3) { "Use host:port:username:password." }
    val port = parts[0].trim().toIntOrNull()
        ?: throw IllegalArgumentException("Port must be between 1 and 65535.")
    require(port in 1..65535) { "Port must be between 1 and 65535." }
    val username = parts[1].trim()
    val password = parts[2]
    require(username.isNotBlank()) { "Username is required." }
    require(password.isNotBlank()) { "Password is required." }
    return ProxyEndpoint(host, port, username, password)
}
