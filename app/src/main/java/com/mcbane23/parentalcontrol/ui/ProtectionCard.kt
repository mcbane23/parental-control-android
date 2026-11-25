package com.mcbane23.parentalcontrol.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mcbane23.parentalcontrol.data.Alert
import com.mcbane23.parentalcontrol.data.AlertType
import com.mcbane23.parentalcontrol.data.ChildRules
import com.mcbane23.parentalcontrol.data.ParentPin

/** A parent's switches for keeping the child phone connected and the app in place. */
@Composable
fun ProtectionCard(
    rules: ChildRules,
    canManage: Boolean,
    busy: Boolean,
    onSetRequireMobileData: (Boolean) -> Unit,
    onSetParentPin: (String?) -> Unit,
) {
    var editingPin by remember { mutableStateOf(false) }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(Icons.Filled.Lock, "Protection")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Keep mobile data on", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Locks apps while mobile data is switched off or flight mode is on. " +
                            "Never on Wi-Fi. Phone calls still work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rules.requireMobileData,
                    onCheckedChange = onSetRequireMobileData,
                    enabled = canManage && !busy,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Parent PIN", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                StatusChip(if (rules.pinSet) "Set" else "Not set", if (rules.pinSet) Tone.Good else Tone.Attention)
            }
            Text(
                if (rules.pinSet) {
                    "Set. It is asked for on the child's phone before the screens that can uninstall " +
                        "or switch off Parental Control."
                } else {
                    "Not set. Until there is a PIN, those screens on the child's phone are not guarded."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingPin = true }, enabled = !busy, shape = MaterialTheme.shapes.small) {
                        Text(if (rules.pinSet) "Change PIN" else "Set PIN")
                    }
                    if (rules.pinSet) {
                        TextButton(onClick = { onSetParentPin(null) }, enabled = !busy) { Text("Remove PIN") }
                    }
                }
            }
        }
    }

    if (editingPin) {
        PinDialog(
            onDismiss = { editingPin = false },
            onSet = { pin ->
                onSetParentPin(pin)
                editingPin = false
            },
        )
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { new -> pin = new.filter(Char::isDigit).take(ParentPin.MAX_LENGTH) },
                label = { Text("${ParentPin.MIN_LENGTH} to ${ParentPin.MAX_LENGTH} digits") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSet(pin) }, enabled = ParentPin.isValid(pin)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What the child phone reported about tampering and being cut off, newest first. */
@Composable
fun AlertsCard(alerts: List<Alert>) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(Icons.Filled.Notifications, "Alerts")
            alerts.take(MAX_SHOWN).forEach { alert ->
                val time = DateUtils.getRelativeTimeSpanString(
                    alert.timeMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
                Column {
                    Text(describe(alert.type), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        time.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val MAX_SHOWN = 10

private fun describe(type: AlertType): String = when (type) {
    AlertType.DATA_OFF -> "Mobile data was cut off away from Wi-Fi. Apps were locked."
    AlertType.DATA_ON -> "The phone was connected again. Apps were unlocked."
    AlertType.ADMIN_OFF -> "Uninstall protection was switched off."
    AlertType.GUARD_OFF -> "The accessibility permission was switched off. Rules are not applied."
    AlertType.SETTINGS_BLOCKED -> "A protected settings screen was opened and closed again."
    AlertType.PIN_WRONG -> "The wrong PIN was entered several times."
    AlertType.PIN_USED -> "Protected settings were opened with the parent PIN."
}
