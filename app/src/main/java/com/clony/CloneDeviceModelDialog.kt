package com.clony

import androidx.compose.foundation.layout.Column
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
import top.niunaijun.blackbox.core.CloneDeviceModelStore

@Composable
internal fun CloneDeviceModelDialog(
    app: AppTile,
    currentModel: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var model by remember(app.packageName, app.virtualUserId, currentModel) {
        mutableStateOf(currentModel.orEmpty())
    }
    val normalized = model.trim()
    val valid = normalized.isEmpty() || CloneDeviceModelStore.isValid(normalized)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Phone model") },
        text = {
            Column {
                Text("Set the model name reported by ${app.displayName}. Leave it empty to keep the current default behavior.")
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it.take(CloneDeviceModelStore.MAX_LENGTH + 1) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    placeholder = { Text("Pixel 8a") },
                    supportingText = {
                        Text(
                            when {
                                !valid -> "Use printable characters, up to ${CloneDeviceModelStore.MAX_LENGTH} characters."
                                normalized.isEmpty() -> "The model override is off."
                                else -> "${normalized.length}/${CloneDeviceModelStore.MAX_LENGTH} characters"
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
