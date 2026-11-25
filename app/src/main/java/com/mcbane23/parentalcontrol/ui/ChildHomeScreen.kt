package com.mcbane23.parentalcontrol.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mcbane23.parentalcontrol.child.AdminReceiver
import com.mcbane23.parentalcontrol.child.LocationPermissions
import com.mcbane23.parentalcontrol.child.LocationService
import com.mcbane23.parentalcontrol.child.UsageTracker
import com.mcbane23.parentalcontrol.data.AppUsage
import com.mcbane23.parentalcontrol.data.ChildRules
import com.mcbane23.parentalcontrol.data.DeviceStatus
import com.mcbane23.parentalcontrol.data.Family
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChildHomeScreen(
    family: Family,
    rules: ChildRules,
    onReport: (DeviceStatus, List<AppUsage>) -> Unit,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<UsageTracker.Snapshot?>(null) }

    // Re-read permissions and usage every time the screen comes back, for example
    // after returning from Settings.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    // Android answers permission requests through these; bumping the counter re-reads the state.
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resumes++ }
    val askBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { resumes++ }

    LaunchedEffect(resumes) {
        LocationService.start(context)
        val fresh = withContext(Dispatchers.Default) { UsageTracker.snapshot(context) }
        snapshot = fresh
        onReport(fresh.status, fresh.apps)
    }

    val status = snapshot?.status
    Column(Modifier.fillMaxSize()) {
    AppHeader(title = family.name, subtitle = "This phone is managed by your family")
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val setupDone = status == null ||
            (status.usageAccess && status.accessibility && status.locationAccess && status.deviceAdmin)
        if (status != null && !setupDone) {
            val done = listOf(status.usageAccess, status.accessibility, status.locationAccess, status.deviceAdmin)
                .count { it }
            AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A parent should do these steps. All of them are needed for the rules, location and protection to work.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    UsageBar(done / 4f)
                    Text("$done of 4 steps done", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (status != null && !status.usageAccess) {
            SetupCard(
                icon = Icons.Filled.DateRange,
                title = "1. Usage access",
                body = "Lets the app count how long each app is used. Find Parental Control in the list and switch it on.",
                button = "Open usage access",
                onClick = { context.open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
        }
        if (status != null && !status.accessibility) {
            SetupCard(
                icon = Icons.Filled.Settings,
                title = "2. Accessibility",
                body = "Lets the app see which app is open, so it can block it. Under Installed apps, " +
                    "choose Parental Control and switch it on.\n\n" +
                    "If Android says the setting is restricted: open App info below, tap the three dots " +
                    "at the top right, choose \"Allow restricted settings\", then try again.",
                button = "Open accessibility",
                onClick = { context.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                secondButton = "App info",
                onSecondClick = { context.openAppInfo() },
            )
        }

        if (status != null && !status.locationAccess) {
            if (!LocationPermissions.hasForeground(context)) {
                SetupCard(
                    icon = Icons.Filled.LocationOn,
                    title = "3. Location",
                    body = "Lets parents see where this phone is. Choose \"While using the app\" and " +
                        "\"Precise\", and allow notifications so the sharing notice can be shown.",
                    button = "Allow location",
                    onClick = {
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        askLocation.launch(permissions.toTypedArray())
                    },
                    secondButton = "App info",
                    onSecondClick = { context.openAppInfo() },
                )
            } else {
                SetupCard(
                    icon = Icons.Filled.LocationOn,
                    title = "3. Location, all the time",
                    body = "Choose \"Allow all the time\", so location keeps working when the app is closed " +
                        "and after the phone restarts. If nothing opens, use App info, then Permissions, then Location.",
                    button = "Allow all the time",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    secondButton = "App info",
                    onSecondClick = { context.openAppInfo() },
                )
            }
        }

        if (status != null && !status.deviceAdmin) {
            SetupCard(
                icon = Icons.Filled.Lock,
                title = "4. Uninstall protection",
                body = "Stops the app from being uninstalled from this phone. Tap Activate on the next screen. " +
                    "If the parent PIN is asked for first, enter it and tap this button again.",
                button = "Switch on protection",
                onClick = { context.open(AdminReceiver.activationIntent(context)) },
            )
        }

        AppCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardHeader(Icons.Filled.Lock, "Protection")
                Text(
                    if (rules.requireMobileData) {
                        "Mobile data must stay on. Away from Wi-Fi, apps are locked while mobile data is " +
                            "switched off or flight mode is on."
                    } else {
                        "Mobile data may be switched off."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    if (rules.pinSet) {
                        "Removing or switching off this app needs the parent PIN."
                    } else {
                        "No parent PIN yet. A parent sets it from their own phone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        AppCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(Icons.Filled.DateRange, "Today")
                val used = status?.usedTodayMs ?: 0L
                val limit = rules.dailyLimitMinutes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (limit != null) UsageRing(used / (limit * 60_000f))
                    Column(Modifier.weight(1f)) {
                        if (limit == null) {
                            Text("${formatDuration(used)} used.", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "No daily limit is set.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val left = (limit * 60_000L - used).coerceAtLeast(0L)
                            Text(
                                if (left > 0) "${formatDuration(left)} left." else "Time is up for today.",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "${formatDuration(used)} used of ${formatMinutes(limit)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val limited = rules.apps.filterValues { it.blocked || it.limitMinutes != null }
        if (limited.isNotEmpty()) {
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardHeader(Icons.Filled.Menu, "App rules")
                    val labels = snapshot?.apps.orEmpty().associate { it.packageName to it.label }
                    limited.forEach { (pkg, rule) ->
                        val name = labels[pkg] ?: pkg
                        val limit = rule.limitMinutes
                        val used = snapshot?.usageMs?.get(pkg) ?: 0L
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                when {
                                    rule.blocked -> StatusChip("Blocked", Tone.Attention)
                                    limit != null -> Text(
                                        "${formatDuration(used)} of ${formatMinutes(limit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!rule.blocked && limit != null) UsageBar(used / (limit * 60_000f))
                        }
                    }
                }
            }
        }

        AppVersion(Modifier.padding(start = 4.dp))
    }
    }
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
    secondButton: String? = null,
    onSecondClick: () -> Unit = {},
) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(icon, title)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onClick, shape = MaterialTheme.shapes.small) { Text(button) }
                if (secondButton != null) TextButton(onClick = onSecondClick) { Text(secondButton) }
            }
        }
    }
}

private fun Context.openAppInfo() {
    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

private fun Context.open(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
