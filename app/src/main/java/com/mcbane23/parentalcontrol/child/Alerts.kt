package com.mcbane23.parentalcontrol.child

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.mcbane23.parentalcontrol.data.Alert
import com.mcbane23.parentalcontrol.data.AlertLog
import com.mcbane23.parentalcontrol.data.AlertType
import com.mcbane23.parentalcontrol.data.DeviceRepository

/**
 * Records alerts on the child phone and sends them to the family.
 *
 * The list is kept on the phone as well, because an alert is often raised while the phone
 * is offline or from a place that has not loaded the family yet. Firestore sends the
 * queued write once the phone is back online.
 */
object Alerts {
    private const val PREFS = "guard"
    private const val KEY_FAMILY = "familyId"
    private const val KEY_CHILD = "childUid"
    private const val KEY_ALERTS = "alerts"

    /** Called once the phone knows which family it belongs to. */
    fun remember(context: Context, familyId: String, childUid: String) {
        prefs(context).edit().putString(KEY_FAMILY, familyId).putString(KEY_CHILD, childUid).apply()
    }

    /** @param minGapMs skip the alert when the same kind was already recorded this recently */
    @Synchronized
    fun log(context: Context, type: AlertType, minGapMs: Long = 0L) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val list = AlertLog.decode(prefs.getString(KEY_ALERTS, "").orEmpty())
        val last = list.lastOrNull { it.type == type }
        if (minGapMs > 0 && last != null && now - last.timeMillis in 0 until minGapMs) return
        val updated = AlertLog.add(list, Alert(type, now))
        prefs.edit().putString(KEY_ALERTS, AlertLog.encode(updated)).apply()

        val family = prefs.getString(KEY_FAMILY, null) ?: return
        val child = prefs.getString(KEY_CHILD, null) ?: return
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) return
            DeviceRepository(FirebaseFirestore.getInstance()).reportAlerts(family, child, updated)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
