package com.mcbane23.parentalcontrol.child

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mcbane23.parentalcontrol.MainActivity
import com.mcbane23.parentalcontrol.R
import com.mcbane23.parentalcontrol.data.ChildLocation
import com.mcbane23.parentalcontrol.data.DeviceRepository
import com.mcbane23.parentalcontrol.data.FamilyRepository
import com.mcbane23.parentalcontrol.data.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Shares the child phone's position with the family.
 *
 * It runs as a foreground service with a permanent notification, which is what lets
 * Android keep delivering locations while the app is not on screen. On a parent's
 * phone it stops itself straight away.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var client: FusedLocationProviderClient
    private var started = false

    private var devices: DeviceRepository? = null
    private var familyId: String? = null
    private var childUid: String? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::publish)
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!LocationPermissions.hasForeground(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // Android refused, for example because the app is in the background without
            // "Allow all the time". The next time the app is opened it is started again.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch { share() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission") // Checked in onStartCommand.
    private suspend fun share() {
        // Wait until this phone is paired as a child. Until then there is nobody to report to.
        while (familyId == null) {
            val connected = runCatching { connect() }.getOrDefault(Connection.RETRY)
            if (connected == Connection.NOT_A_CHILD) {
                stopSelf()
                return
            }
            if (familyId == null) delay(RETRY_MS)
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        runCatching { client.lastLocation.await() }.getOrNull()?.let(::publish)

        // A parent tapping "Refresh" writes a new request time; answer with a precise fix.
        val serviceStart = System.currentTimeMillis()
        devices?.locateRequests(familyId!!, childUid!!)
            ?.catch { }
            ?.collect { requestedAt ->
                if (requestedAt > serviceStart - STALE_REQUEST_MS) {
                    runCatching {
                        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    }.getOrNull()?.let(::publish)
                }
            }
    }

    private enum class Connection { OK, RETRY, NOT_A_CHILD }

    private suspend fun connect(): Connection {
        if (FirebaseApp.getApps(this).isEmpty()) return Connection.NOT_A_CHILD
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) return Connection.RETRY
        val db = FirebaseFirestore.getInstance()
        val profile = FamilyRepository(auth, db).loadProfile() ?: return Connection.RETRY
        if (profile.role != Role.CHILD) return Connection.NOT_A_CHILD
        devices = DeviceRepository(db)
        childUid = profile.uid
        familyId = profile.familyId
        return Connection.OK
    }

    private fun publish(location: Location) {
        val family = familyId ?: return
        val uid = childUid ?: return
        val battery = getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        devices?.reportLocation(
            family,
            uid,
            ChildLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                timeMillis = location.time,
                batteryPercent = battery,
            ),
        )
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Protection", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Parental Control is active")
            .setContentText("This phone's location is shared with your family.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 120_000L
        private const val MIN_INTERVAL_MS = 60_000L
        private const val RETRY_MS = 30_000L
        private const val STALE_REQUEST_MS = 120_000L

        /** Starts sharing if the permission is there. Safe to call repeatedly. */
        fun start(context: Context) {
            if (!LocationPermissions.hasForeground(context)) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
            }
        }
    }
}

object LocationPermissions {
    fun hasForeground(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "Allow all the time". Needed for sharing to restart after a reboot without opening the app. */
    fun hasBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasAll(context: Context): Boolean = hasForeground(context) && hasBackground(context)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Restarts location sharing after the phone reboots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> LocationService.start(context)
        }
    }
}
