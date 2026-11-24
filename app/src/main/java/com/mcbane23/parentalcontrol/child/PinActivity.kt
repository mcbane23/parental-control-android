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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcbane23.parentalcontrol.data.AlertType
import com.mcbane23.parentalcontrol.data.ParentPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown over a settings screen that could remove or switch off the app. The right parent
 * PIN opens those screens for a few minutes; anything else leads back to the home screen.
 */
class PinActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!GuardState.armed) {
            finish()
            return
        }

        setContent {
            BackHandler { goHome() }
            val scope = rememberCoroutineScope()
            var pin by remember { mutableStateOf("") }
            var message by remember { mutableStateOf<String?>(null) }
            var checking by remember { mutableStateOf(false) }
            var waitSeconds by remember { mutableIntStateOf(GuardState.lockoutSeconds()) }

            LaunchedEffect(waitSeconds > 0) {
                while (waitSeconds > 0) {
                    delay(1_000)
                    waitSeconds = GuardState.lockoutSeconds()
                }
            }

            fun submit() {
                val salt = GuardState.pinSalt
                val hash = GuardState.pinHash
                if (salt == null || hash == null) {
                    finish()
                    return
                }
                checking = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { ParentPin.matches(pin, salt, hash) }
                    checking = false
                    if (ok) {
                        GuardState.unlock()
                        Alerts.log(this@PinActivity, AlertType.PIN_USED)
                        finish()
                    } else {
                        pin = ""
                        message = "Wrong PIN."
                        if (GuardState.wrongAttempt()) {
                            Alerts.log(this@PinActivity, AlertType.PIN_WRONG)
                            waitSeconds = GuardState.lockoutSeconds()
                        }
                    }
                }
            }

            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().imePadding().padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Parent PIN needed",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "This screen can remove or switch off Parental Control. A parent can open it " +
                                "with the PIN. After that these screens can be opened for 5 minutes.",
                            textAlign = TextAlign.Center,
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { new ->
                                pin = new.filter(Char::isDigit).take(ParentPin.MAX_LENGTH)
                                message = null
                            },
                            label = { Text("PIN") },
                            singleLine = true,
                            enabled = waitSeconds == 0 && !checking,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        val note = if (waitSeconds > 0) "Too many wrong PINs. Try again in $waitSeconds s." else message
                        if (note != null) Text(note, color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = { submit() },
                            enabled = waitSeconds == 0 && !checking && ParentPin.isValid(pin),
                        ) { Text("Unlock") }
                        TextButton(onClick = ::goHome) { Text("Cancel") }
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
        fun intent(context: Context): Intent =
            Intent(context, PinActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
