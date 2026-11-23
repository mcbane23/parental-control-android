package com.mcbane23.parentalcontrol.child

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mcbane23.parentalcontrol.data.ChildRules
import com.mcbane23.parentalcontrol.data.DeviceRepository
import com.mcbane23.parentalcontrol.data.FamilyRepository
import com.mcbane23.parentalcontrol.data.Role
import com.mcbane23.parentalcontrol.data.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * it with [BlockedActivity] when it is not allowed. It also reports usage to the family.
 * On a parent's phone it does nothing.
 */
class GuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var familyId: String? = null
    private var childUid: String? = null
    private var devices: DeviceRepository? = null

    private var rules = ChildRules()
    private var apps: Map<String, String> = emptyMap()
    private var exempt: Set<String> = emptySet()
    private var usage: Map<String, Long> = emptyMap()
    private var usageReadAt = 0L
    private var lastReportAt = 0L
    private var lastEventPackage: String? = null

    override fun onServiceConnected() {
        LocationService.start(this)
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
        scope.launch {
            runCatching {
                refreshUsage(maxAgeMs = EVENT_USAGE_MAX_AGE_MS)
                enforce(pkg)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun tick() {
        if (familyId == null) connect()
        if (familyId == null) return
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        if (!screenOn) return
        refreshUsage(maxAgeMs = 0)
        // Limits can run out while an app stays open, so check the app in front on every tick.
        val front = rootInActiveWindow?.packageName?.toString() ?: lastEventPackage
        if (front != null) enforce(front)
        if (System.currentTimeMillis() - lastReportAt >= REPORT_MS) report()
    }

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
        refreshApps()
        scope.launch {
            repo.rules(profile.familyId, profile.uid)
                .catch { /* Keep the last known rules; Firestore serves them from its cache. */ }
                .collect { rules = it }
        }
    }

    private suspend fun refreshApps() = withContext(Dispatchers.Default) {
        val context = this@GuardAccessibilityService
        apps = UsageTracker.launchableApps(context)
        exempt = UsageTracker.exemptPackages(context)
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
        val total = UsageTracker.countedTotal(usage, apps.keys - exempt)
        val reason = RuleEngine.check(pkg, rules, usage, total) ?: return
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(BlockedActivity.intent(this, apps[pkg] ?: pkg, reason))
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
    }
}
