package com.mcbane23.parentalcontrol.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Usage reports written by a child phone and rules written by parents.
 *
 * Firestore layout, under families/{familyId}/devices/{childUid}:
 *  - (the document itself)  -> lastSeen, day, usedTodayMs, usageAccess, accessibility
 *  - reports/usage          -> day, apps: { package: { label, ms } }
 *  - reports/location       -> lat, lng, accuracy, time, battery
 *  - settings/locate        -> at (a parent asking for a fresh position)
 *  - settings/rules         -> dailyLimitMinutes, apps: { package: { blocked, limitMinutes } }
 *
 * Usage and rules each live in a single document so that a sync is one write, not one per app.
 */
class DeviceRepository(private val db: FirebaseFirestore) {

    private fun device(familyId: String, childUid: String): DocumentReference =
        db.collection("families").document(familyId).collection("devices").document(childUid)

    private fun usageDoc(familyId: String, childUid: String) =
        device(familyId, childUid).collection("reports").document("usage")

    private fun locationDoc(familyId: String, childUid: String) =
        device(familyId, childUid).collection("reports").document("location")

    private fun locateDoc(familyId: String, childUid: String) =
        device(familyId, childUid).collection("settings").document("locate")

    private fun rulesDoc(familyId: String, childUid: String) =
        device(familyId, childUid).collection("settings").document("rules")

    /**
     * Called on the child phone. Does not wait for the server: Firestore queues the write
     * and sends it when the phone is online.
     */
    fun report(familyId: String, childUid: String, status: DeviceStatus, apps: List<AppUsage>) {
        val batch = db.batch()
        batch.set(
            device(familyId, childUid),
            mapOf(
                "lastSeen" to FieldValue.serverTimestamp(),
                "day" to status.day,
                "usedTodayMs" to status.usedTodayMs,
                "usageAccess" to status.usageAccess,
                "accessibility" to status.accessibility,
                "locationAccess" to status.locationAccess,
            ),
        )
        batch.set(
            usageDoc(familyId, childUid),
            mapOf(
                "day" to status.day,
                "apps" to apps.associate {
                    it.packageName to mapOf("label" to it.label, "ms" to it.usedTodayMs)
                },
            ),
        )
        batch.commit()
    }

    fun status(familyId: String, childUid: String): Flow<DeviceStatus?> =
        device(familyId, childUid).snapshots { doc ->
            if (!doc.exists()) return@snapshots null
            DeviceStatus(
                lastSeenMillis = doc.getTimestamp("lastSeen", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.toDate()?.time ?: 0L,
                day = doc.getString("day").orEmpty(),
                usedTodayMs = doc.getLong("usedTodayMs") ?: 0L,
                usageAccess = doc.getBoolean("usageAccess") == true,
                accessibility = doc.getBoolean("accessibility") == true,
                // Phones on an older app version do not report this; do not warn about them.
                locationAccess = doc.getBoolean("locationAccess") != false,
            )
        }

    fun apps(familyId: String, childUid: String): Flow<List<AppUsage>> =
        usageDoc(familyId, childUid).snapshots { doc ->
            val apps = doc.get("apps") as? Map<*, *> ?: emptyMap<Any, Any>()
            apps.mapNotNull { (pkg, value) ->
                val fields = value as? Map<*, *> ?: return@mapNotNull null
                AppUsage(
                    packageName = pkg.toString(),
                    label = fields["label"]?.toString() ?: pkg.toString(),
                    usedTodayMs = (fields["ms"] as? Number)?.toLong() ?: 0L,
                )
            }.sortedWith(compareByDescending<AppUsage> { it.usedTodayMs }.thenBy { it.label.lowercase() })
        }

    fun rules(familyId: String, childUid: String): Flow<ChildRules> =
        rulesDoc(familyId, childUid).snapshots { doc ->
            val apps = doc.get("apps") as? Map<*, *> ?: emptyMap<Any, Any>()
            ChildRules(
                dailyLimitMinutes = doc.getLong("dailyLimitMinutes")?.toInt(),
                apps = apps.mapNotNull { (pkg, value) ->
                    val fields = value as? Map<*, *> ?: return@mapNotNull null
                    pkg.toString() to AppRule(
                        blocked = fields["blocked"] == true,
                        limitMinutes = (fields["limitMinutes"] as? Number)?.toInt(),
                    )
                }.toMap(),
            )
        }

    /** Called on the child phone. Queued by Firestore when offline, like [report]. */
    fun reportLocation(familyId: String, childUid: String, location: ChildLocation) {
        locationDoc(familyId, childUid).set(
            mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "accuracy" to location.accuracyMeters,
                "time" to location.timeMillis,
                "battery" to location.batteryPercent,
            ),
        )
    }

    fun location(familyId: String, childUid: String): Flow<ChildLocation?> =
        locationDoc(familyId, childUid).snapshots { doc ->
            val lat = doc.getDouble("lat")
            val lng = doc.getDouble("lng")
            if (lat == null || lng == null) return@snapshots null
            ChildLocation(
                latitude = lat,
                longitude = lng,
                accuracyMeters = doc.getDouble("accuracy")?.toFloat(),
                timeMillis = doc.getLong("time") ?: 0L,
                batteryPercent = doc.getLong("battery")?.toInt(),
            )
        }

    /** A parent asks the child phone for a fresh, precise position. */
    suspend fun requestLocation(familyId: String, childUid: String) {
        locateDoc(familyId, childUid).set(mapOf("at" to FieldValue.serverTimestamp())).await()
    }

    /** On the child phone: the time of the latest request, in milliseconds. */
    fun locateRequests(familyId: String, childUid: String): Flow<Long> =
        locateDoc(familyId, childUid).snapshots { doc ->
            doc.getTimestamp("at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
        }

    suspend fun setDailyLimit(familyId: String, childUid: String, minutes: Int?) {
        rulesDoc(familyId, childUid)
            .set(mapOf("dailyLimitMinutes" to minutes), SetOptions.merge())
            .await()
    }

    suspend fun setAppRule(familyId: String, childUid: String, packageName: String, rule: AppRule) {
        val value = mapOf("blocked" to rule.blocked, "limitMinutes" to rule.limitMinutes)
        rulesDoc(familyId, childUid)
            .set(mapOf("apps" to mapOf(packageName to value)), SetOptions.merge())
            .await()
    }

    private fun <T> DocumentReference.snapshots(map: (DocumentSnapshot) -> T): Flow<T> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(map(snapshot))
        }
        awaitClose { registration.remove() }
    }
}
