package com.clony

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    val simPhoneNumber: String = "",
    val simCarrierName: String = "",
    val simCountryIso: String = "",
    val simOperatorCode: String = ""
) {
    fun toStoreOptions() = CloneNetworkIdentityStore.Options(
        changeTelephonyIds,
        hideSimOperatorInfo,
        changeWifiMac,
        hideWifiInfo,
        hideDnsServers,
        simulateSimProfile,
        simPhoneNumber,
        simCarrierName,
        simCountryIso,
        simOperatorCode
    )

    companion object {
        fun fromStore(options: CloneNetworkIdentityStore.Options) = CloneNetworkIdentitySettings(
            changeTelephonyIds = options.changeTelephonyIds,
            hideSimOperatorInfo = options.hideSimOperatorInfo,
            changeWifiMac = options.changeWifiMac,
            hideWifiInfo = options.hideWifiInfo,
            hideDnsServers = options.hideDnsServers,
            simulateSimProfile = options.simulateSimProfile,
            simPhoneNumber = options.simPhoneNumber,
            simCarrierName = options.simCarrierName,
            simCountryIso = options.simCountryIso,
            simOperatorCode = options.simOperatorCode
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
                description = "Use private values for this copy. When off, IMEI and IMSI are hidden. TikViral never returns the phone's real hardware IDs.",
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
    packageName: String,
    editingCloneName: String?,
    settings: CloneNetworkIdentitySettings,
    onSettingsChange: (CloneNetworkIdentitySettings) -> Unit,
    onLoadUaeSample: () -> Unit
) {
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
            IdentityToggleCard(
                title = "Hide Wi-Fi info",
                description = "Hide SSID and BSSID, and return no scan or saved-network list to the copy.",
                checked = settings.hideWifiInfo,
                onCheckedChange = { onSettingsChange(settings.copy(hideWifiInfo = it)) }
            )
        }
//        if (packageName == CloneNetworkIdentityStore.SIM_PROFILE_TEST_PACKAGE) {
            item { IdentityGroupHeading("Dubai Curtains test SIM profile") }
            item {
                IdentityToggleCard(
                    title = "Use editable test SIM values",
                    description = "Return these values to Dubai Curtains telephony APIs for this copy only. This does not change the device SIM or carrier connection.",
                    checked = settings.simulateSimProfile,
                    onCheckedChange = { onSettingsChange(settings.copy(simulateSimProfile = it)) }
                )
            }
            if (settings.simulateSimProfile) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onLoadUaeSample, modifier = Modifier.fillMaxWidth()) {
                            Text("Auto-fill UAE sample from internet")
                        }
                        Text(
                            "Carrier and operator code come from mcc-mnc.org. Phone uses a non-routable test placeholder; replace it with your own test value if needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = settings.simPhoneNumber,
                            onValueChange = { onSettingsChange(settings.copy(simPhoneNumber = it)) },
                            label = { Text("Phone number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.simCarrierName,
                            onValueChange = { onSettingsChange(settings.copy(simCarrierName = it)) },
                            label = { Text("Carrier / operator name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.simCountryIso,
                            onValueChange = { onSettingsChange(settings.copy(simCountryIso = it.take(2).lowercase())) },
                            label = { Text("Country ISO (2 letters)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.simOperatorCode,
                            onValueChange = { value ->
                                onSettingsChange(settings.copy(simOperatorCode = value.filter(Char::isDigit).take(6)))
                            },
                            label = { Text("Operator code (MCC + MNC)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
//        }
        item {
            IdentityToggleCard(
                title = "Hide DNS servers",
                description = "Hide DNS server addresses from connectivity APIs. DNS lookups continue over the copy's current connection.",
                checked = settings.hideDnsServers,
                onCheckedChange = { onSettingsChange(settings.copy(hideDnsServers = it)) }
            )
        }
        item { IdentityGroupHeading("Operator details") }
        item {
            IdentityToggleCard(
                title = "Hide SIM & operator info",
                description = "Hide SIM serial, phone number, carrier name and country, operator codes, and cell tower details.",
                checked = settings.hideSimOperatorInfo,
                onCheckedChange = { onSettingsChange(settings.copy(hideSimOperatorInfo = it)) }
            )
        }
    }
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
