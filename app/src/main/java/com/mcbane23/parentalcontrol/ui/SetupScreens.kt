package com.mcbane23.parentalcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcbane23.parentalcontrol.data.PairingCodes

/** A centred, scrolling column that stays at a readable width on tablets. */
@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/** The icon, title and one line of explanation that open a setup screen. */
@Composable
private fun ScreenIntro(icon: ImageVector, title: String, body: String?, tone: Tone = Tone.Accent) {
    IconBubble(icon, tone = tone, size = 72.dp)
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    if (body != null) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun FirebaseMissingScreen() {
    ScreenColumn {
        ScreenIntro(
            icon = Icons.Filled.Warning,
            title = "Firebase is not set up",
            body = "This build has no google-services.json file. Follow docs/firebase-setup.md, " +
                "put the file in the app folder and build again.",
            tone = Tone.Attention,
        )
    }
}

@Composable
fun WelcomeScreen(busy: Boolean, onParent: () -> Unit, onChild: () -> Unit, onDemo: () -> Unit) {
    ScreenColumn {
        ScreenIntro(Icons.Filled.Lock, "Parental Control", "Who uses this phone?")
        Spacer(Modifier.height(8.dp))
        ChoiceCard(
            icon = Icons.Filled.Person,
            title = "I'm a parent",
            body = "Sign in with Google to manage your family.",
            enabled = !busy,
            onClick = onParent,
        )
        ChoiceCard(
            icon = Icons.Filled.Face,
            title = "This is my child's phone",
            body = "You will need a pairing code from a parent's phone.",
            enabled = !busy,
            onClick = onChild,
        )
        TextButton(onClick = onDemo, enabled = !busy) { Text("Try the parent demo") }
        AppVersion()
    }
}

/** One of the big choices on the welcome screen. */
@Composable
private fun ChoiceCard(icon: ImageVector, title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    AppCard(onClick = onClick, enabled = enabled) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBubble(icon, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ParentSetupScreen(
    busy: Boolean,
    suggestedName: String,
    onCreateFamily: (familyName: String, parentName: String) -> Unit,
    onJoinFamily: (code: String, parentName: String) -> Unit,
    onDemo: () -> Unit,
    onBack: () -> Unit,
) {
    var parentName by rememberSaveable { mutableStateOf(suggestedName) }
    var familyName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        ScreenIntro(Icons.Filled.Person, "Set up your family", null)
        OutlinedTextField(
            value = parentName,
            onValueChange = { parentName = it },
            label = { Text("Your name") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        AppCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Start a new family", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text("Family name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onCreateFamily(familyName, parentName) },
                    enabled = !busy && familyName.isNotBlank() && parentName.isNotBlank(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create family") }
            }
        }
        AppCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Or join one with an invite code", style = MaterialTheme.typography.titleMedium)
                CodeField(code = code, onChange = { code = it })
                OutlinedButton(
                    onClick = { onJoinFamily(code, parentName) },
                    enabled = !busy && PairingCodes.isWellFormed(code) && parentName.isNotBlank(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Join family") }
            }
        }
        TextButton(onClick = onDemo, enabled = !busy) { Text("Try the parent demo first") }
        TextButton(onClick = onBack, enabled = !busy) { Text("Sign out") }
    }
}

@Composable
fun ChildPairingScreen(
    busy: Boolean,
    onPair: (code: String, childName: String) -> Unit,
    onBack: () -> Unit,
) {
    var childName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        ScreenIntro(
            icon = Icons.Filled.Phone,
            title = "Pair this phone",
            body = "On a parent's phone, tap \"Add child's phone\" and type the code shown there.",
        )
        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Child's name") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        CodeField(code = code, onChange = { code = it })
        Button(
            onClick = { onPair(code, childName) },
            enabled = !busy && PairingCodes.isWellFormed(code) && childName.isNotBlank(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Pair") }
        TextButton(onClick = onBack, enabled = !busy) { Text("Back") }
    }
}

@Composable
private fun CodeField(code: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = code,
        onValueChange = { onChange(PairingCodes.normalize(it).take(PairingCodes.LENGTH)) },
        label = { Text("Code") },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
}
