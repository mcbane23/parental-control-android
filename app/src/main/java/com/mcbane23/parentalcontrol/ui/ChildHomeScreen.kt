package com.mcbane23.parentalcontrol.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    LaunchedEffect(resumes) {
        val fresh = withContext(Dispatchers.Default) { UsageTracker.snapshot(context) }
        snapshot = fresh
        onReport(fresh.status, fresh.apps)
    }

    val status = snapshot?.status
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Family: ${family.name}", style = MaterialTheme.typography.headlineSmall)

        if (status != null && (!status.usageAccess || !status.accessibility)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
            Text("A parent should do these steps. Both are needed for limits to work.")
        }
        if (status != null && !status.usageAccess) {
            SetupCard(
                title = "1. Usage access",
                body = "Lets the app count how long each app is used. Find Parental Control in the list and switch it on.",
                button = "Open usage access",
                onClick = { context.open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
        }
        if (status != null && !status.accessibility) {
            SetupCard(
                title = "2. Accessibility",
                body = "Lets the app see which app is open, so it can block it. Under Installed apps, " +
                    "choose Parental Control and switch it on.\n\n" +
                    "If Android says the setting is restricted: open App info below, tap the three dots " +
                    "at the top right, choose \"Allow restricted settings\", then try again.",
                button = "Open accessibility",
                onClick = { context.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                secondButton = "App info",
                onSecondClick = {
                    context.open(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                val used = status?.usedTodayMs ?: 0L
                val limit = rules.dailyLimitMinutes
                if (limit == null) {
                    Text("${formatDuration(used)} used. No daily limit is set.")
                } else {
                    val left = (limit * 60_000L - used).coerceAtLeast(0L)
                    Text("${formatDuration(used)} used of ${formatMinutes(limit)}.")
                    Text(if (left > 0) "${formatDuration(left)} left." else "Time is up for today.")
                }
            }
        }

        val limited = rules.apps.filterValues { it.blocked || it.limitMinutes != null }
        if (limited.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("App rules", style = MaterialTheme.typography.titleMedium)
                    val labels = snapshot?.apps.orEmpty().associate { it.packageName to it.label }
                    limited.forEach { (pkg, rule) ->
                        val name = labels[pkg] ?: pkg
                        val limit = rule.limitMinutes
                        Text(
                            when {
                                rule.blocked -> "$name: blocked"
                                limit != null -> {
                                    val used = snapshot?.usageMs?.get(pkg) ?: 0L
                                    "$name: ${formatDuration(used)} of ${formatMinutes(limit)}"
                                }
                                else -> name
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
    secondButton: String? = null,
    onSecondClick: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
            Button(onClick = onClick) { Text(button) }
            if (secondButton != null) TextButton(onClick = onSecondClick) { Text(secondButton) }
        }
    }
}

private fun Context.open(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
