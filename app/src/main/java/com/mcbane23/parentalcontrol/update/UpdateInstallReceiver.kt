package com.mcbane23.parentalcontrol.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Receives the result of an install started by [AppUpdater.install]. */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants the person to confirm, or to allow installs from this app first.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    failure.value = "Android did not show the install screen."
                }
            }
            // On success the system replaces and stops the app, so there is nothing to show.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> failure.value = "The update was cancelled."
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                failure.value = "The update could not be installed" +
                    (if (detail.isNullOrBlank()) " (status $status)." else ": $detail")
            }
        }
    }

    companion object {
        const val ACTION = "com.mcbane23.parentalcontrol.update.INSTALL_RESULT"

        /** Set when an install fails. The update screen shows it and then clears it. */
        val failure = MutableStateFlow<String?>(null)
    }
}
