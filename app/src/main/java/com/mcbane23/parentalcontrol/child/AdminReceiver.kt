package com.mcbane23.parentalcontrol.child

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.mcbane23.parentalcontrol.data.AlertType

/**
 * Makes the app a device admin app on the child phone. It asks for no admin powers at all:
 * the only effect is that Android refuses to uninstall the app while this is active.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Parental Control can be uninstalled after this. The parents will be told."

    override fun onDisabled(context: Context, intent: Intent) {
        Alerts.log(context, AlertType.ADMIN_OFF)
    }

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        fun isActive(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(component(context)) == true

        /** Opens Android's own screen where the person confirms switching protection on. */
        fun activationIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops Parental Control from being uninstalled from this phone without a parent.",
                )
    }
}
