package com.clony

import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.niunaijun.blackbox.core.CloneHardwareIdentityStore
import top.niunaijun.blackbox.core.CloneNetworkIdentityStore

internal data class CloneHardwareIdentitySettings(
    val hideCpuGpuInfo: Boolean = false,
    val randomizeBatteryLevel: Boolean = false
) {
    fun toStoreOptions() = CloneHardwareIdentityStore.Options(hideCpuGpuInfo, randomizeBatteryLevel)

    companion object {
        fun fromStore(options: CloneHardwareIdentityStore.Options) = CloneHardwareIdentitySettings(
            hideCpuGpuInfo = options.hideCpuGpuInfo,
            randomizeBatteryLevel = options.randomizeBatteryLevel
        )
    }
}

internal data class CloneNetworkIdentitySettings(
    val changeTelephonyIds: Boolean = true,
    val hideSimOperatorInfo: Boolean = false,
    val changeWifiMac: Boolean = true,
    val hideWifiInfo: Boolean = false,
    val hideDnsServers: Boolean = false,
    val simulateSimProfile: Boolean = false,
    val simCarrierName: String = "",
    val simCountryIso: String = "",
    val simOperatorCode: String = "",
    val matchProxyRegion: Boolean = false,
    val proxyCountryIso: String = ""
) {
    fun toStoreOptions() = CloneNetworkIdentityStore.Options(
        changeTelephonyIds,
        hideSimOperatorInfo,
        changeWifiMac,
        hideWifiInfo,
        hideDnsServers,
        simulateSimProfile,
        "",
        simCarrierName,
        simCountryIso,
        simOperatorCode,
        matchProxyRegion,
        proxyCountryIso
    )

    companion object {
        fun fromStore(options: CloneNetworkIdentityStore.Options) = CloneNetworkIdentitySettings(
            changeTelephonyIds = options.changeTelephonyIds,
            hideSimOperatorInfo = options.hideSimOperatorInfo,
            changeWifiMac = options.changeWifiMac,
            hideWifiInfo = options.hideWifiInfo,
            hideDnsServers = options.hideDnsServers,
            simulateSimProfile = options.simulateSimProfile,
            simCarrierName = options.simCarrierName,
            simCountryIso = options.simCountryIso,
            simOperatorCode = options.simOperatorCode,
            matchProxyRegion = options.matchProxyRegion,
            proxyCountryIso = options.proxyCountryIso
        )
    }
}

@Composable
internal fun IdentityTrackingOperationsScreen(
    appLabel: String,
    editingCloneName: String?,
    onOpenNetworkRadioIdentity: () -> Unit,
    onOpenConnectionOperatorInfo: () -> Unit,
    onOpenDeviceProfileHardware: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            IdentityScreenIntro(
                title = "Identity & tracking operations",
                description = "Review the device and connection details a copy can share with apps."
            )
        }
        item {
            Text(
                "OPERATIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
        item {
            DetailActionCard(
                title = "Network & radio identity",
                description = "Mobile identifiers and network addresses",
                value = "Open",
                onClick = onOpenNetworkRadioIdentity
            )
        }
        item {
            DetailActionCard(
                title = "Connection & operator info",
                description = "Hide connection, SIM and carrier details",
                value = "Open",
                onClick = onOpenConnectionOperatorInfo
            )
        }
        item {
            DetailActionCard(
                title = "Device profile & hardware",
                description = "Control the hardware details a copy can see",
                value = "Open",
                onClick = onOpenDeviceProfileHardware
            )
        }
        item {
            Text(
                editingCloneName?.let { "Settings for $it" } ?: "Settings for new $appLabel copies",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
internal fun DeviceProfileHardwareScreen(
    appLabel: String,
    editingCloneName: String?,
    settings: CloneHardwareIdentitySettings,
    onSettingsChange: (CloneHardwareIdentitySettings) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            IdentityScreenIntro(
                title = "Device profile & hardware",
                description = if (editingCloneName != null) {
                    "Choose which hardware details $editingCloneName shares. Changes apply when the copy starts again."
                } else {
                    "Set hardware privacy defaults for new $appLabel copies. Existing copies keep their saved settings."
                }
            )
        }
        item { IdentityGroupHeading("Hardware details") }
        item {
            IdentityToggleCard(
                title = "Hide CPU / GPU information",
                description = "Return generic CPU details and hide common graphics vendor and renderer identifiers.",
                checked = settings.hideCpuGpuInfo,
                onCheckedChange = { onSettingsChange(settings.copy(hideCpuGpuInfo = it)) }
            )
        }
        item {
            IdentityToggleCard(
                title = "Randomize battery level",
                description = "Use a stable private charge level and temperature for this copy.",
                checked = settings.randomizeBatteryLevel,
                onCheckedChange = { onSettingsChange(settings.copy(randomizeBatteryLevel = it)) }
            )
        }
    }
}

@Composable
internal fun NetworkRadioIdentityScreen(
    appLabel: String,
    editingCloneName: String?,
    settings: CloneNetworkIdentitySettings,
    onSettingsChange: (CloneNetworkIdentitySettings) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            IdentityScreenIntro(
                title = "Network & radio identity",
                description = if (editingCloneName != null) {
                    "Configure network and radio values for $editingCloneName. Changes apply the next time the copy starts."
                } else {
                    "Set network and radio defaults for new $appLabel copies. Existing copies keep their saved settings."
                }
            )
        }
        item { IdentityGroupHeading("Mobile radio") }
        item {
            IdentityToggleCard(
                title = "Change IMEI / IMSI",
                description = "Use private values for this copy. When off, IMEI and IMSI are hidden unless SIM profile emulation is active, which reports synthetic values. Clony never returns the phone's real hardware IDs.",
                checked = settings.changeTelephonyIds,
                onCheckedChange = { onSettingsChange(settings.copy(changeTelephonyIds = it)) }
            )
        }
        item { IdentityGroupHeading("Wi-Fi") }
        item {
            IdentityToggleCard(
                title = "Change Wi-Fi MAC address",
                description = "Give each copy a stable, private Wi-Fi MAC. When off, TikViral returns Android's redacted MAC value.",
                checked = settings.changeWifiMac,
                onCheckedChange = { onSettingsChange(settings.copy(changeWifiMac = it)) }
            )
        }
        item { IdentityGroupHeading("Other network identifiers") }
        item {
            IdentityStatusCard(
                title = "Change Bluetooth MAC address",
                description = "TikViral does not currently replace Bluetooth addresses.",
                status = "Unavailable",
                active = false
            )
        }
        item {
            IdentityStatusCard(
                title = "Change Ethernet MAC address",
                description = "TikViral does not currently replace Ethernet MAC addresses.",
                status = "Unavailable",
                active = false
            )
        }
    }
}

@Composable
internal fun ConnectionOperatorInfoScreen(
    appLabel: String,
    editingCloneName: String?,
    settings: CloneNetworkIdentitySettings,
    onSettingsChange: (CloneNetworkIdentitySettings) -> Unit
) {
    val simProfiles = remember { SimProfileDirectory.allProfiles() }
    val selectedSimProfile = remember(settings.simCountryIso, simProfiles) {
        SimProfileDirectory.findByIso(settings.simCountryIso)
    }
    var showSimProfilePicker by remember { mutableStateOf(false) }
    var enableProfileAfterPick by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            IdentityScreenIntro(
                title = "Connection & operator info",
                description = if (editingCloneName != null) {
                    "Choose which connection and carrier details $editingCloneName can share. Changes apply when the copy starts again."
                } else {
                    "Set connection and carrier privacy defaults for new $appLabel copies. Existing copies keep their saved settings."
                }
            )
        }
        item { IdentityGroupHeading("Connection details") }
        item {
            IdentityStatusCard(
                title = "SIM and network country",
                description = if (settings.simulateSimProfile && selectedSimProfile != null) {
                    "This copy reports ${selectedSimProfile.countryName}, ${selectedSimProfile.carrierName}, MCC/MNC ${selectedSimProfile.operatorCode}, a phone number, a ready SIM state, and synthetic SIM identifiers."
                } else if (settings.simulateSimProfile) {
                    "The saved SIM profile is not in the current catalog. Choose a supported country to continue."
                } else if (settings.matchProxyRegion) {
                    "This copy reports ${settings.proxyCountryIso.uppercase(Locale.ROOT)} for SIM and network country, and suppresses local SIM, operator and cell details."
                } else {
                    "This copy reports the device's SIM and network country."
                },
                status = when {
                    settings.simulateSimProfile && selectedSimProfile != null -> "Simulated"
                    settings.simulateSimProfile -> "Choose country"
                    settings.matchProxyRegion -> "Proxy matched"
                    else -> "Device region"
                },
                active = settings.simulateSimProfile || settings.matchProxyRegion
            )
        }
        item {
            IdentityToggleCard(
                title = "Simulate SIM profile",
                description = if (settings.simulateSimProfile && selectedSimProfile != null) {
                    "Report the carrier, country, MCC/MNC, SPN, phone number, and ready SIM state. IMSI, ICCID, and IMEI are synthetic. GPS and proxy routing stay unchanged."
                } else {
                    "Choose a country to use its largest mobile carrier by subscribers. GPS and proxy routing stay unchanged."
                },
                checked = settings.simulateSimProfile,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onSettingsChange(settings.copy(simulateSimProfile = false))
                    } else if (selectedSimProfile != null) {
                        onSettingsChange(settings.copy(simulateSimProfile = true))
                    } else {
                        enableProfileAfterPick = true
                        showSimProfilePicker = true
                    }
                }
            )
        }
        item {
            DetailActionCard(
                title = "Country and carrier",
                description = selectedSimProfile?.let {
                    "${it.carrierName} · MCC/MNC ${it.operatorCode}"
                } ?: "Choose a supported country and Clony will select its leading carrier.",
                value = selectedSimProfile?.countryName ?: "Choose",
                onClick = { showSimProfilePicker = true }
            )
        }
        item {
            IdentityToggleCard(
                title = "Hide Wi-Fi info",
                description = "Hide SSID and BSSID, and return no scan or saved-network list to the copy.",
                checked = settings.hideWifiInfo,
                onCheckedChange = { onSettingsChange(settings.copy(hideWifiInfo = it)) }
            )
        }
        item {
            IdentityToggleCard(
                title = "Hide DNS servers",
                description = "Hide DNS server addresses from connectivity APIs. DNS lookups continue over the copy's current connection.",
                checked = settings.hideDnsServers,
                onCheckedChange = { onSettingsChange(settings.copy(hideDnsServers = it)) }
            )
        }
    }

    if (showSimProfilePicker) {
        SimProfilePickerDialog(
            profiles = simProfiles,
            onDismiss = {
                showSimProfilePicker = false
                enableProfileAfterPick = false
            },
            onSelect = { profile ->
                onSettingsChange(
                    settings.copy(
                        simulateSimProfile = settings.simulateSimProfile || enableProfileAfterPick,
                        simCarrierName = profile.carrierName,
                        simCountryIso = profile.countryIso,
                        simOperatorCode = profile.operatorCode
                    )
                )
                showSimProfilePicker = false
                enableProfileAfterPick = false
            }
        )
    }
}

@Composable
private fun SimProfilePickerDialog(
    profiles: List<SimCarrierProfile>,
    onDismiss: () -> Unit,
    onSelect: (SimCarrierProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredProfiles = remember(query, profiles) {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) profiles
        else profiles.filter { profile ->
            profile.countryName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                profile.countryIso.contains(normalizedQuery, ignoreCase = true) ||
                profile.carrierName.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a country") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search country or carrier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (filteredProfiles.isEmpty()) {
                        item {
                            Text(
                                "No supported country matches that search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filteredProfiles, key = SimCarrierProfile::countryIso) { profile ->
                            TextButton(
                                onClick = { onSelect(profile) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        "${profile.countryName} (${profile.countryIso.uppercase(Locale.ROOT)})",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "${profile.carrierName} · ${profile.operatorCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun IdentityToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title }
            )
        }
    }
}

@Composable
private fun IdentityScreenIntro(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun IdentityGroupHeading(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun IdentityStatusCard(
    title: String,
    description: String,
    status: String,
    active: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    status,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
