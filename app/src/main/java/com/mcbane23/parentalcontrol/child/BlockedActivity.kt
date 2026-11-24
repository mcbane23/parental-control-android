package com.mcbane23.parentalcontrol.child

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcbane23.parentalcontrol.data.BlockReason
import com.mcbane23.parentalcontrol.data.DataLock
import kotlinx.coroutines.delay

/** Full-screen message shown over an app the rules do not allow right now. */
class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val reason = runCatching { BlockReason.valueOf(intent.getStringExtra(EXTRA_REASON).orEmpty()) }
            .getOrDefault(BlockReason.BLOCKED)

        setContent {
            BackHandler { goHome() }
            if (reason == BlockReason.MOBILE_DATA_OFF) {
                // Close by itself as soon as the phone is connected again.
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1_000)
                        if (!DataLock.shouldLock(true, Connectivity.read(this@BlockedActivity))) {
                            finish()
                            break
                        }
                    }
                }
            }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when (reason) {
                                BlockReason.BLOCKED -> "$appLabel is blocked"
                                BlockReason.APP_LIMIT -> "Time is up for $appLabel"
                                BlockReason.DAILY_LIMIT -> "Screen time is up for today"
                                BlockReason.MOBILE_DATA_OFF -> "Mobile data is off"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            when (reason) {
                                BlockReason.BLOCKED -> "A parent has blocked this app."
                                BlockReason.APP_LIMIT -> "Today's limit for this app has been reached."
                                BlockReason.DAILY_LIMIT -> "Today's limit has been reached. Phone calls still work."
                                BlockReason.MOBILE_DATA_OFF ->
                                    "Apps are locked until this phone is connected again. Swipe down from the " +
                                        "top of the screen and switch Mobile data on and Flight mode off, or " +
                                        "connect to Wi-Fi. Phone calls still work."
                            },
                            textAlign = TextAlign.Center,
                        )
                        if (reason == BlockReason.MOBILE_DATA_OFF) {
                            Button(onClick = ::openNetworkSettings) { Text("Open network settings") }
                            OutlinedButton(onClick = ::goHome) { Text("OK") }
                        } else {
                            Button(onClick = ::goHome) { Text("OK") }
                        }
                    }
                }
            }
        }
    }

    private fun openNetworkSettings() {
        val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        runCatching { startActivity(panel) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_REASON = "reason"

        fun intent(context: Context, appLabel: String, reason: BlockReason): Intent =
            Intent(context, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_LABEL, appLabel)
                .putExtra(EXTRA_REASON, reason.name)
    }
}
