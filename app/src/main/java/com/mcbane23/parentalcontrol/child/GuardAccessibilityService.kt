package com.mcbane23.parentalcontrol.child

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mcbane23.parentalcontrol.R
import com.mcbane23.parentalcontrol.data.AlertType
import com.mcbane23.parentalcontrol.data.BlockReason
import com.mcbane23.parentalcontrol.data.ChildRules
import com.mcbane23.parentalcontrol.data.DataLock
import com.mcbane23.parentalcontrol.data.DeviceRepository
import com.mcbane23.parentalcontrol.data.FamilyRepository
import com.mcbane23.parentalcontrol.data.Role
import com.mcbane23.parentalcontrol.data.RuleEngine
import com.mcbane23.parentalcontrol.data.SettingsGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs on the child phone for as long as the accessibility permission is granted.
 *
 * It sees which app comes to the front, checks it against the parent's rules and covers
 * it with [BlockedActivity] when it is not allowed. It also reports usage to the family,
 * locks apps while mobile data is switched off away from Wi-Fi, and asks for the parent
 * PIN on the settings screens that could remove or switch off this app.
 * On a parent's phone it does nothing.
 */
class GuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var familyId: String? = null
    private var childUid: String? = null
    private var devices: DeviceRepository? = null

    private var rules = ChildRules()
    private var rulesLoaded = false
    private var apps: Map<String, String> = emptyMap()
    private var exempt: Set<String> = emptySet()
    private var usage: Map<String, Long> = emptyMap()
    private var usageReadAt = 0L
    private var lastReportAt = 0L
    private var lastEventPackage: String? = null
    private var lastEventClass: String? = null

    /** True while apps are locked because the phone was cut off from mobile data. */
    private var dataLocked = false

    private var otherAppLabels: Set<String> = emptySet()
    private var guardJob: Job? = null
    private var lastPinPromptAt = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = connectionChanged()
        override fun onLost(network: Network) = connectionChanged()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = connectionChanged()
    }

    /** Fires when a system-wide setting changes, such as the mobile data or flight mode switch. */
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = connectionChanged()
    }

    override fun onServiceConnected() {
        LocationService.start(this)
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        }
        runCatching {
            contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, settingsObserver)
        }
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(TICK_MS)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        lastEventPackage = pkg
        lastEventClass = event.className?.toString()
        if (pkg in GUARDED_PACKAGES) watchSettings()
        scope.launch {
            runCatching {
                refreshUsage(maxAgeMs = EVENT_USAGE_MAX_AGE_MS)
                enforce(pkg)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // The service was switched off in Settings: from here on nothing is enforced.
        if (familyId != null) Alerts.log(this, AlertType.GUARD_OFF)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        runCatching { contentResolver.unregisterContentObserver(settingsObserver) }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun tick() {
        if (familyId == null) connect()
        if (familyId == null) return
        updateDataLock()
        if (!screenOn()) return
        refreshUsage(maxAgeMs = 0)
        // The PIN unlock can run out while a protected settings screen is still open.
        guardSettings()
        // Limits can run out while an app stays open, so check the app in front on every tick.
        val front = rootInActiveWindow?.packageName?.toString() ?: lastEventPackage
        if (front != null) enforce(front)
        if (System.currentTimeMillis() - lastReportAt >= REPORT_MS) report()
    }

    private fun screenOn(): Boolean = getSystemService(PowerManager::class.java).isInteractive

    /** Finds out which family this phone belongs to and starts listening for rule changes. */
    private suspend fun connect() {
        if (FirebaseApp.getApps(this).isEmpty()) return
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val profile = FamilyRepository(auth, db).loadProfile() ?: return
        if (profile.role != Role.CHILD) return
        val repo = DeviceRepository(db)
        devices = repo
        childUid = profile.uid
        familyId = profile.familyId
        Alerts.remember(this, profile.familyId, profile.uid)
        refreshApps()
        scope.launch {
            repo.rules(profile.familyId, profile.uid)
                .catch { /* Keep the last known rules; Firestore serves them from its cache. */ }
                .collect {
                    rules = it
                    rulesLoaded = true
                    GuardState.pinSalt = it.pinSalt
                    GuardState.pinHash = it.pinHash
                    updateDataLock()
                }
        }
    }

    private suspend fun refreshApps() = withContext(Dispatchers.Default) {
        val context = this@GuardAccessibilityService
        val launchable = UsageTracker.launchableApps(context)
        apps = launchable
        exempt = UsageTracker.exemptPackages(context)
        otherAppLabels = launchable
            .filterKeys { it != packageName && it !in GUARDED_PACKAGES }
            .values.toSet()
    }

    private suspend fun refreshUsage(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        if (now - usageReadAt < maxAgeMs) return
        usage = withContext(Dispatchers.Default) { UsageTracker.usageToday(this@GuardAccessibilityService) }
        usageReadAt = now
    }

    private fun enforce(pkg: String) {
        if (familyId == null) return
        if (pkg !in apps || pkg in exempt) return
        val reason = if (dataLocked) {
            BlockReason.MOBILE_DATA_OFF
        } else {
            val total = UsageTracker.countedTotal(usage, apps.keys - exempt)
            RuleEngine.check(pkg, rules, usage, total) ?: return
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(BlockedActivity.intent(this, apps[pkg] ?: pkg, reason))
    }

    // ---- Mobile data lock ----

    private fun connectionChanged() {
        // Called from system threads. Wait a moment so the new state has settled.
        scope.launch {
            delay(SETTLE_MS)
            runCatching { updateDataLock() }
        }
    }

    private fun shouldLockNow(): Boolean =
        rulesLoaded && DataLock.shouldLock(rules.requireMobileData, Connectivity.read(this))

    private suspend fun updateDataLock() {
        if (familyId == null) return
        var locked = shouldLockNow()
        if (locked && !dataLocked) {
            // Moving between two Wi-Fi networks looks like being cut off for a moment.
            delay(CONFIRM_MS)
            locked = shouldLockNow()
        }
        if (locked == dataLocked) return
        dataLocked = locked
        Alerts.log(this, if (locked) AlertType.DATA_OFF else AlertType.DATA_ON)
        if (locked && screenOn()) {
            startActivity(BlockedActivity.intent(this, "", BlockReason.MOBILE_DATA_OFF))
        }
    }

    // ---- Protected settings screens ----

    /** A settings screen fills in over a few moments, so look at it more than once. */
    private fun watchSettings() {
        guardJob?.cancel()
        guardJob = scope.launch {
            for (wait in GUARD_CHECK_DELAYS_MS) {
                delay(wait)
                if (runCatching { guardSettings() }.getOrDefault(false)) break
            }
        }
    }

    /** Closes a screen about this app and asks for the parent PIN. Returns true if it did. */
    private fun guardSettings(): Boolean {
        if (familyId == null || !GuardState.armed || GuardState.isUnlocked()) return false
        // Our own PIN screen is already in front.
        if (rootInActiveWindow?.packageName?.toString() == packageName) return false
        val ownLabel = getString(R.string.app_name)
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList())
            .ifEmpty { listOfNotNull(rootInActiveWindow) }
        for (root in roots) {
            val pkg = root.packageName?.toString() ?: continue
            if (pkg !in GUARDED_PACKAGES) continue
            // The "Allow Parental Control to ...?" question during setup is not a way out.
            if (pkg in PERMISSION_PACKAGES && lastEventClass?.contains("GrantPermissions") == true) continue
            if (SettingsGuard.isProtectedPage(visibleTexts(root), ownLabel, otherAppLabels)) {
                askForPin()
                return true
            }
        }
        return false
    }

    private fun visibleTexts(root: AccessibilityNodeInfo): List<String> {
        val texts = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < MAX_NODES) {
            val node = queue.removeFirst()
            seen++
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return texts
    }

    private fun askForPin() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPinPromptAt < PIN_PROMPT_GAP_MS) return
        lastPinPromptAt = now
        Alerts.log(this, AlertType.SETTINGS_BLOCKED, minGapMs = SETTINGS_ALERT_GAP_MS)
        // Leave the screen first, so nothing on it can be tapped while the PIN screen opens.
        performGlobalAction(GLOBAL_ACTION_BACK)
        startActivity(PinActivity.intent(this))
    }

    private suspend fun report() {
        val family = familyId ?: return
        val uid = childUid ?: return
        refreshApps()
        val snapshot = withContext(Dispatchers.Default) { UsageTracker.snapshot(this@GuardAccessibilityService) }
        devices?.report(family, uid, snapshot.status, snapshot.apps)
        lastReportAt = System.currentTimeMillis()
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val REPORT_MS = 120_000L
        const val EVENT_USAGE_MAX_AGE_MS = 15_000L

        const val SETTLE_MS = 500L
        const val CONFIRM_MS = 4_000L

        val GUARD_CHECK_DELAYS_MS = listOf(0L, 150L, 250L, 500L, 800L)
        const val MAX_NODES = 600
        const val PIN_PROMPT_GAP_MS = 1_500L
        const val SETTINGS_ALERT_GAP_MS = 10 * 60_000L

        val PERMISSION_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )

        /** System apps whose screens can uninstall, stop or switch off this app. */
        val GUARDED_PACKAGES = PERMISSION_PACKAGES + setOf(
            "com.android.settings",
            // Samsung keeps its accessibility settings and device care in separate apps.
            "com.samsung.accessibility",
            "com.samsung.android.lool",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )
    }
}
