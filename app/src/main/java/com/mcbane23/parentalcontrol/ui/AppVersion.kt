package com.mcbane23.parentalcontrol.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Shows the installed version. Updates arrive through Google Play. */
@Composable
fun AppVersion(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }
    Text("App version $version", modifier, style = MaterialTheme.typography.bodySmall)
}
