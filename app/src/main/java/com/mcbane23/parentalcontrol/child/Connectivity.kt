package com.mcbane23.parentalcontrol.child

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.mcbane23.parentalcontrol.data.ConnectionState

/** Reads how the phone is connected. None of this needs a permission the person has to grant. */
object Connectivity {

    fun read(context: Context): ConnectionState {
        val telephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val tm = context.getSystemService(TelephonyManager::class.java)
        return ConnectionState(
            telephony = telephony && tm != null,
            simPresent = tm != null && simPresent(tm),
            wifi = onWifi(context),
            mobileDataOn = tm != null && mobileDataOn(context, tm),
            airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
        )
    }

    /** True when any SIM slot, including an eSIM, holds a SIM. */
    private fun simPresent(tm: TelephonyManager): Boolean = runCatching {
        val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tm.activeModemCount
        } else {
            @Suppress("DEPRECATION")
            tm.phoneCount
        }
        (0 until slots.coerceAtLeast(1)).any { tm.getSimState(it) != TelephonyManager.SIM_STATE_ABSENT }
    }.getOrElse { tm.simState != TelephonyManager.SIM_STATE_ABSENT }

    private fun mobileDataOn(context: Context, tm: TelephonyManager): Boolean = runCatching {
        val subscription = SubscriptionManager.getDefaultDataSubscriptionId()
        val forData = if (subscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            tm.createForSubscriptionId(subscription)
        } else {
            tm
        }
        forData.isDataEnabled
    }.getOrElse {
        Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1
    }

    /** Wi-Fi counts even without working internet; a cable connection on a tablet counts too. */
    private fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
