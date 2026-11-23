package com.mcbane23.parentalcontrol.child

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager
import com.mcbane23.parentalcontrol.data.AppUsage
import com.mcbane23.parentalcontrol.data.DeviceStatus
import java.time.LocalDate
import java.time.ZoneId

/** Reads installed apps and today's usage from Android. Runs on the child phone. */
object UsageTracker {

    // Event type values from UsageEvents.Event. Named constants for 1 and 2 only exist from API 29.
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val DEVICE_SHUTDOWN = 26

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val mine = ComponentName(context, GuardAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    fun today(): String = LocalDate.now().toString()

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Apps that show up in the launcher, as package name to label. */
    fun launchableApps(context: Context): Map<String, String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
    }

    /**
     * Packages that are never blocked and never count towards the daily limit: this app,
     * the home screen, the phone app and system screens. Settings is exempt for now so a
     * phone cannot lock itself out during setup; it will be guarded by the parent PIN later.
     */
    fun exemptPackages(context: Context): Set<String> {
        val exempt = mutableSetOf(
            context.packageName,
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.incallui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(home, 0)
            .forEach { exempt += it.activityInfo.packageName }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { exempt += it }
        return exempt
    }

    /** Time each package spent in the foreground since local midnight, in milliseconds. */
    fun usageToday(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> {
        if (!hasUsageAccess(context)) return emptyMap()
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(startOfToday(), now) ?: return emptyMap()
        val list = mutableListOf<UsageEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            list += UsageEvent(event.eventType, event.packageName.orEmpty(), event.className.orEmpty(), event.timeStamp)
        }
        return sumForeground(list, now)
    }

    data class UsageEvent(val type: Int, val packageName: String, val className: String, val time: Long)

    /** Pairs each "resumed" with its "paused" and adds up the time in between, per package. */
    fun sumForeground(events: List<UsageEvent>, now: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val open = HashMap<Pair<String, String>, Long>() // (package, activity) -> resumed at
        fun close(key: Pair<String, String>, until: Long) {
            val since = open.remove(key) ?: return
            totals[key.first] = (totals[key.first] ?: 0L) + (until - since).coerceAtLeast(0L)
        }
        for (e in events) {
            when (e.type) {
                ACTIVITY_RESUMED -> open.getOrPut(e.packageName to e.className) { e.time }
                ACTIVITY_PAUSED -> close(e.packageName to e.className, e.time)
                // Screen off or shutdown ends everything that was still open.
                SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> open.keys.toList().forEach { close(it, e.time) }
            }
        }
        open.keys.toList().forEach { close(it, now) }
        return totals
    }

    /** Everything the child phone reports in one sync. */
    data class Snapshot(val status: DeviceStatus, val apps: List<AppUsage>, val usageMs: Map<String, Long>)

    fun snapshot(context: Context): Snapshot {
        val apps = launchableApps(context)
        val exempt = exemptPackages(context)
        val usage = usageToday(context)
        val counted = apps.keys - exempt
        val status = DeviceStatus(
            lastSeenMillis = System.currentTimeMillis(),
            day = today(),
            usedTodayMs = countedTotal(usage, counted),
            usageAccess = hasUsageAccess(context),
            accessibility = isAccessibilityEnabled(context),
        )
        val list = counted.map { AppUsage(it, apps.getValue(it), usage[it] ?: 0L) }
        return Snapshot(status, list, usage)
    }

    fun countedTotal(usage: Map<String, Long>, counted: Set<String>): Long =
        counted.sumOf { usage[it] ?: 0L }
}
