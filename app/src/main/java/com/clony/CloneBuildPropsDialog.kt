package com.clony

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import top.niunaijun.blackbox.core.CloneBuildPropsStore

internal data class CloneBuildProps(
    val deviceName: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val model: String = "",
    val product: String = "",
    val device: String = "",
    val board: String = "",
    val radio: String = "",
    val bootloader: String = "",
    val fingerprint: String = "",
    val build: String = "",
    val host: String = "",
    val time: String = "",
    val osVersion: String = "",
    val release: String = "",
    val sdkInt: String = "",
    val enablePlaceholders: Boolean = false
) {
    val overrideCount: Int
        get() = toValues().count(String::isNotBlank)

    fun toValues(): Array<String> = arrayOf(
        deviceName,
        manufacturer,
        brand,
        model,
        product,
        device,
        board,
        radio,
        bootloader,
        fingerprint,
        build,
        host,
        time,
        osVersion,
        release,
        sdkInt
    )

    fun normalized(): CloneBuildProps = copy(
        deviceName = deviceName.trim(),
        manufacturer = manufacturer.trim(),
        brand = brand.trim(),
        model = model.trim(),
        product = product.trim(),
        device = device.trim(),
        board = board.trim(),
        radio = radio.trim(),
        bootloader = bootloader.trim(),
        fingerprint = fingerprint.trim(),
        build = build.trim(),
        host = host.trim(),
        time = time.trim(),
        osVersion = osVersion.trim(),
        release = release.trim(),
        sdkInt = sdkInt.trim()
    )

    fun withDefaults(defaults: CloneBuildProps): CloneBuildProps = copy(
        deviceName = deviceName.ifBlank { defaults.deviceName },
        manufacturer = manufacturer.ifBlank { defaults.manufacturer },
        brand = brand.ifBlank { defaults.brand },
        model = model.ifBlank { defaults.model },
        product = product.ifBlank { defaults.product },
        device = device.ifBlank { defaults.device },
        board = board.ifBlank { defaults.board },
        radio = radio.ifBlank { defaults.radio },
        bootloader = bootloader.ifBlank { defaults.bootloader },
        fingerprint = fingerprint.ifBlank { defaults.fingerprint },
        build = build.ifBlank { defaults.build },
        host = host.ifBlank { defaults.host },
        time = time.ifBlank { defaults.time },
        osVersion = osVersion.ifBlank { defaults.osVersion },
        release = release.ifBlank { defaults.release },
        sdkInt = sdkInt.ifBlank { defaults.sdkInt }
    )

    companion object {
        fun fromValues(values: Array<String?>?): CloneBuildProps {
            if (values == null || values.size !in 14..16) return CloneBuildProps()
            return CloneBuildProps(
                deviceName = values[0].orEmpty(),
                manufacturer = values[1].orEmpty(),
                brand = values[2].orEmpty(),
                model = values[3].orEmpty(),
                product = values[4].orEmpty(),
                device = values[5].orEmpty(),
                board = values[6].orEmpty(),
                radio = values[7].orEmpty(),
                bootloader = values[8].orEmpty(),
                fingerprint = values[9].orEmpty(),
                build = values[10].orEmpty(),
                host = values[11].orEmpty(),
                time = values[12].orEmpty(),
                osVersion = values[13].orEmpty(),
                release = values.getOrNull(14).orEmpty(),
                sdkInt = values.getOrNull(15).orEmpty()
            )
        }

        fun deviceDefaults(context: Context): CloneBuildProps {
            val deviceName = Settings.Global.getString(context.contentResolver, "device_name")
                ?.takeIf(String::isNotBlank) ?: Build.MODEL
            return CloneBuildProps(
                deviceName = deviceName,
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                model = Build.MODEL,
                product = Build.PRODUCT,
                device = Build.DEVICE,
                board = Build.BOARD,
                radio = Build.getRadioVersion().orEmpty(),
                bootloader = Build.BOOTLOADER,
                fingerprint = Build.FINGERPRINT,
                build = Build.ID,
                host = Build.HOST,
                time = Build.TIME.toString(),
                osVersion = System.getProperty("os.version").orEmpty(),
                release = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT.toString()
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CloneBuildPropsDialog(
    appLabel: String,
    initialProps: CloneBuildProps,
    onDismiss: () -> Unit,
    onSave: (CloneBuildProps) -> Unit
) {
    val context = LocalContext.current
    val defaults = remember(context) { CloneBuildProps.deviceDefaults(context) }
    var props by remember(appLabel, initialProps) { mutableStateOf(initialProps) }
    var showProfiles by remember { mutableStateOf(false) }
    val normalized = props.normalized()
    val valid = CloneBuildPropsStore.isValid(normalized.toValues())
    val targetSdkInt = defaults.sdkInt.toIntOrNull()
    val matchingProfiles = if (targetSdkInt == null) {
        emptyList()
    } else {
        cloneBuildProfiles.filter { it.props.sdkInt.toIntOrNull() == targetSdkInt }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 780.dp).padding(horizontal = 20.dp)
        ) {
            Text("Build props", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Build props you set override Clony's virtual values for $appLabel. Leave a field blank to keep Clony's default.")
                val configuredSdk = props.sdkInt.toIntOrNull()
                val deviceSdk = defaults.sdkInt.toIntOrNull()
                if (configuredSdk != null && deviceSdk != null && configuredSdk != deviceSdk) {
                    Text(
                        "These saved props target API $configuredSdk; this device runs API $deviceSdk. Choose a matching profile or use device values to match the phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedButton(
                    onClick = { showProfiles = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose matching phone profile · ${matchingProfiles.size}")
                }
                BuildPropField("Device name", props.deviceName, defaults.deviceName) {
                    props = props.copy(deviceName = it)
                }
                BuildPropField("Manufacturer", props.manufacturer, defaults.manufacturer) {
                    props = props.copy(manufacturer = it)
                }
                BuildPropField("Brand", props.brand, defaults.brand) {
                    props = props.copy(brand = it)
                }
                BuildPropField("Model", props.model, defaults.model) {
                    props = props.copy(model = it)
                }
                BuildPropField("Product", props.product, defaults.product) {
                    props = props.copy(product = it)
                }
                BuildPropField("Device", props.device, defaults.device) {
                    props = props.copy(device = it)
                }
                BuildPropField("Board", props.board, defaults.board) {
                    props = props.copy(board = it)
                }
                BuildPropField("Radio", props.radio, defaults.radio) {
                    props = props.copy(radio = it)
                }
                BuildPropField("Bootloader", props.bootloader, defaults.bootloader) {
                    props = props.copy(bootloader = it)
                }
                BuildPropField("Fingerprint", props.fingerprint, defaults.fingerprint) {
                    props = props.copy(fingerprint = it)
                }
                BuildPropField("Build", props.build, defaults.build) {
                    props = props.copy(build = it)
                }
                BuildPropField("Host", props.host, defaults.host) {
                    props = props.copy(host = it)
                }
                BuildPropField("Time", props.time, defaults.time, isTime = true) {
                    props = props.copy(time = it)
                }
                BuildPropField("OS version", props.osVersion, defaults.osVersion) {
                    props = props.copy(osVersion = it)
                }
                BuildPropField("Build.VERSION.RELEASE", props.release, defaults.release) {
                    props = props.copy(release = it)
                }
                BuildPropField("Build.VERSION.SDK_INT", props.sdkInt, defaults.sdkInt, isSdkInt = true) {
                    props = props.copy(sdkInt = it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = props.enablePlaceholders,
                        onCheckedChange = { enabled ->
                            props = if (enabled) props.withDefaults(defaults).copy(enablePlaceholders = true)
                            else props.copy(enablePlaceholders = false)
                        }
                    )
                    Text("Enable placeholders")
                }
                if (props.enablePlaceholders) {
                    Text(
                        "Blank fields are filled with the current device values shown above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { props = CloneBuildProps() }) { Text("Reset") }
                    TextButton(onClick = { props = props.withDefaults(defaults) }) { Text("Use device values") }
                }
                if (!valid) {
                    Text(
                        "Values must be printable ASCII and no longer than 91 characters. Time must be a non-negative millisecond value, SDK int must be a positive integer, and fingerprints cannot contain spaces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(normalized) }, enabled = valid) { Text("Save") }
            }
        }
    }

    if (showProfiles) {
        CloneBuildProfilePicker(
            profiles = matchingProfiles,
            targetSdkInt = targetSdkInt,
            onDismiss = { showProfiles = false },
            onSelect = { profile ->
                props = profile.props
                showProfiles = false
            }
        )
    }

}

@Composable
private fun CloneBuildProfilePicker(
    profiles: List<CloneBuildProfile>,
    targetSdkInt: Int?,
    onDismiss: () -> Unit,
    onSelect: (CloneBuildProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a phone profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only profiles matching this phone's Android version are shown. You can edit the values before saving.")
                Text(
                    "${profiles.size} profiles for API ${targetSdkInt ?: "unknown"}. Most build IDs are test fixtures; the Nubia Z17 stock profile includes best-fit system-field estimates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profiles.isEmpty()) {
                    Text(
                        "No phone profiles are available for this Android version yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        profiles.forEach { profile ->
                            TextButton(
                                onClick = { onSelect(profile) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(profile.name)
                                    Text(
                                        profile.summary,
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
private fun BuildPropField(
    label: String,
    value: String,
    defaultValue: String,
    isTime: Boolean = false,
    isSdkInt: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(92)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (defaultValue.isNotEmpty()) ({ Text(defaultValue) }) else null,
        singleLine = true,
        keyboardOptions = if (isTime || isSdkInt) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        isError = (isTime && value.isNotBlank() && value.toLongOrNull()?.let { it >= 0 } != true) ||
                (isSdkInt && value.isNotBlank() && value.toIntOrNull()?.let { it > 0 } != true)
    )
}
