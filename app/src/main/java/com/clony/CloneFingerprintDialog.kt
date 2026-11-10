package com.clony

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.niunaijun.blackbox.core.CloneFingerprintStore

@Composable
internal fun CloneFingerprintDialog(
    app: AppTile,
    currentFingerprint: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var fingerprint by remember(app.packageName, app.virtualUserId, currentFingerprint) {
        mutableStateOf(currentFingerprint.orEmpty())
    }
    val normalized = fingerprint.trim()
    val valid = normalized.isEmpty() || CloneFingerprintStore.isValid(normalized)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device fingerprint") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Set the Android build fingerprint for ${app.displayName}. It will take effect when this copy starts.")
                OutlinedTextField(
                    value = fingerprint,
                    onValueChange = { fingerprint = it.take(CloneFingerprintStore.MAX_LENGTH + 1) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Build fingerprint") },
                    placeholder = { Text("google/oriole/oriole:12/...:user/release-keys") },
                    supportingText = {
                        Text(
                            when {
                                !valid -> "Use printable characters with no spaces."
                                normalized.isEmpty() -> "Leave empty to use Clony's default."
                                else -> "${normalized.length}/${CloneFingerprintStore.MAX_LENGTH} characters"
                            }
                        )
                    },
                    isError = !valid,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(normalized.ifEmpty { null }) },
                enabled = valid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) { Text("Use default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
