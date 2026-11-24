package com.mcbane23.parentalcontrol.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.mcbane23.parentalcontrol.data.Alert
import com.mcbane23.parentalcontrol.data.AppRule
import com.mcbane23.parentalcontrol.data.AppUsage
import com.mcbane23.parentalcontrol.data.ChildLocation
import com.mcbane23.parentalcontrol.data.ChildRules
import com.mcbane23.parentalcontrol.data.DeviceStatus
import com.mcbane23.parentalcontrol.data.Member

/** What the limit dialog is currently editing: the daily limit, or one app's limit. */
private data class LimitTarget(val title: String, val packageName: String?, val current: Int?)

@Composable
fun ChildDetailScreen(
    child: Member,
    canManage: Boolean,
    status: DeviceStatus?,
    apps: List<AppUsage>,
    rules: ChildRules,
    location: ChildLocation?,
    busy: Boolean,
    onRefreshLocation: () -> Unit,
    alerts: List<Alert>,
    onSetRequireMobileData: (Boolean) -> Unit,
    onSetParentPin: (String?) -> Unit,
    onSetDailyLimit: (Int?) -> Unit,
    onSetAppBlocked: (String, Boolean) -> Unit,
    onSetAppLimit: (String, Int?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<LimitTarget?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Back") }
            Text(child.name, style = MaterialTheme.typography.headlineMedium)
        }
        item { StatusCard(status) }
        item { LocationCard(child.name, location, canManage, busy, onRefreshLocation) }
        item { ProtectionCard(rules, canManage, busy, onSetRequireMobileData, onSetParentPin) }
        if (alerts.isNotEmpty()) {
            item { AlertsCard(alerts) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)
                    val used = formatDuration(status?.usedTodayMs ?: 0L)
                    val limit = rules.dailyLimitMinutes
                    Text(if (limit != null) "$used of ${formatMinutes(limit)}" else "$used, no daily limit")
                    if (canManage) {
                        OutlinedButton(
                            onClick = { editing = LimitTarget("Daily limit", null, limit) },
                        ) { Text(if (limit != null) "Change daily limit" else "Set daily limit") }
                    }
                }
            }
        }
        item { Text("Apps", style = MaterialTheme.typography.titleMedium) }
        if (apps.isEmpty()) {
            item { Text("The app list appears after the child's phone has finished setup.") }
        }
        items(apps, key = { it.packageName }) { app ->
            val rule = rules.apps[app.packageName] ?: AppRule()
            AppRow(
                app = app,
                rule = rule,
                canManage = canManage,
                onBlocked = { onSetAppBlocked(app.packageName, it) },
                onEditLimit = { editing = LimitTarget(app.label, app.packageName, rule.limitMinutes) },
            )
        }
    }

    editing?.let { target ->
        LimitDialog(
            title = target.title,
            current = target.current,
            onDismiss = { editing = null },
            onSet = { minutes ->
                if (target.packageName == null) onSetDailyLimit(minutes) else onSetAppLimit(target.packageName, minutes)
                editing = null
            },
        )
    }
}

@Composable
private fun StatusCard(status: DeviceStatus?) {
    val problem = when {
        status == null -> "No data yet. Open the app on the child's phone and finish its setup."
        !status.usageAccess -> "Usage access is switched off on the child's phone, so time is not being counted."
        !status.accessibility -> "The accessibility permission is switched off on the child's phone, so limits and blocks are not applied."
        !status.locationAccess -> "Location is not set to \"Allow all the time\" on the child's phone, so its position may be missing or old."
        !status.deviceAdmin -> "Uninstall protection is switched off on the child's phone, so the app can be removed there."
        !status.mobileData -> "Mobile data or flight mode cuts the child's phone off when it leaves Wi-Fi."
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (problem != null) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (problem != null) Text(problem)
            if (status != null && status.lastSeenMillis > 0) {
                val ago = DateUtils.getRelativeTimeSpanString(
                    status.lastSeenMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
                Text("Last report: $ago")
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppUsage,
    rule: AppRule,
    canManage: Boolean,
    onBlocked: (Boolean) -> Unit,
    onEditLimit: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                val used = if (app.usedTodayMs > 0) "${formatDuration(app.usedTodayMs)} today" else "Not used today"
                Text(
                    when {
                        rule.blocked -> "$used. Blocked"
                        rule.limitMinutes != null -> "$used. Limit ${formatMinutes(rule.limitMinutes)}"
                        else -> used
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (canManage) {
                TextButton(onClick = onEditLimit) { Text("Limit") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = rule.blocked, onCheckedChange = onBlocked)
                    Text("Block", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LimitDialog(
    title: String,
    current: Int?,
    onDismiss: () -> Unit,
    onSet: (Int?) -> Unit,
) {
    var text by remember { mutableStateOf(current?.toString().orEmpty()) }
    val minutes = text.toIntOrNull()?.takeIf { it in 1..1440 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                label = { Text("Minutes per day") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSet(minutes) }, enabled = minutes != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSet(null) }) { Text("Remove limit") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
