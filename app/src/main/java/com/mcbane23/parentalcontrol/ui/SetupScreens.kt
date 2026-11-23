package com.mcbane23.parentalcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcbane23.parentalcontrol.data.PairingCodes

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
fun FirebaseMissingScreen() {
    ScreenColumn {
        Text("Firebase is not set up", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This build has no google-services.json file. Follow docs/firebase-setup.md, " +
                "put the file in the app folder and build again.",
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun WelcomeScreen(busy: Boolean, onParent: () -> Unit, onChild: () -> Unit) {
    ScreenColumn {
        Text("Parental Control", style = MaterialTheme.typography.headlineMedium)
        Text("Who uses this phone?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onParent, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("I'm a parent")
        }
        Text(
            "Sign in with Google to manage your family.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onChild, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("This is my child's phone")
        }
        Text(
            "You will need a pairing code from a parent's phone.",
            style = MaterialTheme.typography.bodySmall,
        )
        UpdateCard()
    }
}

@Composable
fun ParentSetupScreen(
    busy: Boolean,
    suggestedName: String,
    onCreateFamily: (familyName: String, parentName: String) -> Unit,
    onJoinFamily: (code: String, parentName: String) -> Unit,
    onBack: () -> Unit,
) {
    var parentName by rememberSaveable { mutableStateOf(suggestedName) }
    var familyName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        Text("Set up your family", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = parentName,
            onValueChange = { parentName = it },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        Text("Start a new family", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = familyName,
            onValueChange = { familyName = it },
            label = { Text("Family name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onCreateFamily(familyName, parentName) },
            enabled = !busy && familyName.isNotBlank() && parentName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create family") }
        HorizontalDivider()
        Text("Or join one with an invite code", style = MaterialTheme.typography.titleMedium)
        CodeField(code = code, onChange = { code = it })
        OutlinedButton(
            onClick = { onJoinFamily(code, parentName) },
            enabled = !busy && PairingCodes.isWellFormed(code) && parentName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Join family") }
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
        Text("Pair this phone", style = MaterialTheme.typography.headlineSmall)
        Text(
            "On a parent's phone, tap \"Add child's phone\" and type the code shown there.",
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Child's name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CodeField(code = code, onChange = { code = it })
        Button(
            onClick = { onPair(code, childName) },
            enabled = !busy && PairingCodes.isWellFormed(code) && childName.isNotBlank(),
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
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
}
