package com.mcbane23.parentalcontrol.child

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcbane23.parentalcontrol.data.BlockReason

/** Full-screen message shown over an app the rules do not allow right now. */
class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val reason = runCatching { BlockReason.valueOf(intent.getStringExtra(EXTRA_REASON).orEmpty()) }
            .getOrDefault(BlockReason.BLOCKED)

        setContent {
            BackHandler { goHome() }
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
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            when (reason) {
                                BlockReason.BLOCKED -> "A parent has blocked this app."
                                BlockReason.APP_LIMIT -> "Today's limit for this app has been reached."
                                BlockReason.DAILY_LIMIT -> "Today's limit has been reached. Phone calls still work."
                            },
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = ::goHome) { Text("OK") }
                    }
                }
            }
        }
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
