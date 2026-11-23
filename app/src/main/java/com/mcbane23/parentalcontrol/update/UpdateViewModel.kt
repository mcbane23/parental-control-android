package com.mcbane23.parentalcontrol.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState
    data class Installing(val manifest: UpdateManifest) : UpdateState
    data class Failed(val message: String, val manifest: UpdateManifest? = null) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updater = AppUpdater(application)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installedVersionName: String = updater.installedVersionName

    init {
        // Look once when the app opens. A failure here stays quiet; the button reports errors.
        check(silent = true)
        viewModelScope.launch {
            UpdateInstallReceiver.failure.collect { message ->
                if (message != null) {
                    val manifest = (_state.value as? UpdateState.Installing)?.manifest
                    _state.value = UpdateState.Failed(message, manifest)
                    UpdateInstallReceiver.failure.value = null
                }
            }
        }
    }

    fun check(silent: Boolean = false) {
        if (isBusy()) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value = try {
                val manifest = updater.fetchManifest()
                if (manifest.isNewerThan(updater.installedVersionCode)) {
                    UpdateState.Available(manifest)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed(describe(e))
            }
        }
    }

    fun update(manifest: UpdateManifest) {
        if (isBusy()) return
        _state.value = UpdateState.Downloading(manifest, 0f)
        viewModelScope.launch {
            try {
                val apk = updater.download(manifest) { progress ->
                    _state.value = UpdateState.Downloading(manifest, progress)
                }
                _state.value = UpdateState.Installing(manifest)
                updater.install(apk)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(describe(e), manifest)
            }
        }
    }

    private fun isBusy(): Boolean = when (_state.value) {
        UpdateState.Checking, is UpdateState.Downloading -> true
        else -> false
    }

    private fun describe(e: Exception): String = when (e) {
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "No internet connection. Try again later."
        else -> e.message ?: "Something went wrong."
    }
}
