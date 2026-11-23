package com.mcbane23.parentalcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcbane23.parentalcontrol.update.UpdateManifest
import com.mcbane23.parentalcontrol.update.UpdateState
import com.mcbane23.parentalcontrol.update.UpdateViewModel

/** Shows the installed version and lets the person check for and install a newer one. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier, viewModel: UpdateViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App version ${viewModel.installedVersionName}", style = MaterialTheme.typography.titleMedium)
            when (val s = state) {
                UpdateState.Idle -> CheckButton(viewModel)
                UpdateState.Checking -> {
                    Text("Checking for an update…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                UpdateState.UpToDate -> {
                    Text("You have the latest version.")
                    CheckButton(viewModel)
                }
                is UpdateState.Available -> {
                    Text("Version ${s.manifest.versionName} is available.")
                    if (s.manifest.notes.isNotBlank()) {
                        Text(s.manifest.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    UpdateButton(viewModel, s.manifest, "Update now")
                }
                is UpdateState.Downloading -> {
                    Text("Downloading version ${s.manifest.versionName}…")
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Installing -> {
                    Text(
                        "Installing version ${s.manifest.versionName}. Confirm if Android asks. " +
                            "The app closes when the update finishes, so open it again afterwards.",
                    )
                    UpdateButton(viewModel, s.manifest, "Try again")
                }
                is UpdateState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    if (s.manifest != null) {
                        UpdateButton(viewModel, s.manifest, "Try again")
                    } else {
                        CheckButton(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckButton(viewModel: UpdateViewModel) {
    OutlinedButton(onClick = { viewModel.check() }, modifier = Modifier.fillMaxWidth()) {
        Text("Check for update")
    }
}

@Composable
private fun UpdateButton(viewModel: UpdateViewModel, manifest: UpdateManifest, label: String) {
    Button(onClick = { viewModel.update(manifest) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
