package com.mcbane23.parentalcontrol.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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

    AdaptivePanes(
        header = { AppHeader(title = child.name, onBack = onBack) },
        first = {
            item { StatusCard(status) }
            item { LocationCard(child.name, location, canManage, busy, onRefreshLocation) }
            item { ProtectionCard(rules, canManage, busy, onSetRequireMobileData, onSetParentPin) }
            if (alerts.isNotEmpty()) {
                item { AlertsCard(alerts) }
            }
        },
        second = {
            item {
                val usedMs = status?.usedTodayMs ?: 0L
                val limit = rules.dailyLimitMinutes
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardHeader(Icons.Filled.DateRange, "Today")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (limit != null) UsageRing(usedMs / (limit * 60_000f))
                            Column(Modifier.weight(1f)) {
                                Text(formatDuration(usedMs), style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    if (limit != null) "of ${formatMinutes(limit)} daily limit" else "No daily limit",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (canManage) {
                            OutlinedButton(
                                onClick = { editing = LimitTarget("Daily limit", null, limit) },
                                shape = MaterialTheme.shapes.small,
                            ) { Text(if (limit != null) "Change daily limit" else "Set daily limit") }
                        }
                    }
                }
            }
            item { SectionHeader("Apps") }
            if (apps.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Menu,
                        title = "No apps yet",
                        body = "The app list appears after the child's phone has finished setup.",
                    )
                }
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
        },
    )

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
    val lastReport = if (status != null && status.lastSeenMillis > 0) {
        val ago = DateUtils.getRelativeTimeSpanString(
            status.lastSeenMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
        "Last report: $ago"
    } else {
        null
    }
    AppCard(
        containerColor = if (problem != null) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (problem != null) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                IconBubble(Icons.Filled.CheckCircle, tone = Tone.Good, size = 36.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    problem ?: "Rules and protection are working",
                    style = if (problem != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                )
                if (lastReport != null) Text(lastReport, style = MaterialTheme.typography.bodySmall)
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
    val used = if (app.usedTodayMs > 0) "${formatDuration(app.usedTodayMs)} today" else "Not used today"
    val limit = rule.limitMinutes
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(app.label, size = 36.dp, tone = if (rule.blocked) Tone.Attention else Tone.Accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        rule.blocked -> "$used. Blocked"
                        limit != null -> "$used. Limit ${formatMinutes(limit)}"
                        else -> used
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (limit != null && !rule.blocked) {
                    UsageBar(app.usedTodayMs / (limit * 60_000f), Modifier.padding(top = 4.dp))
                }
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
