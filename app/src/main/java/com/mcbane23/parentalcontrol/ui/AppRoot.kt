package com.mcbane23.parentalcontrol.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcbane23.parentalcontrol.AppViewModel
import com.mcbane23.parentalcontrol.Screen
import com.mcbane23.parentalcontrol.data.Role

@Composable
fun AppRoot(viewModel: AppViewModel, activity: Activity) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val screen = state.screen) {
                    Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    Screen.FirebaseMissing -> FirebaseMissingScreen()
                    Screen.Welcome -> WelcomeScreen(
                        busy = state.busy,
                        onParent = { viewModel.signInAsParent(activity) },
                        onChild = viewModel::startChildPairing,
                    )
                    Screen.ParentSetup -> ParentSetupScreen(
                        busy = state.busy,
                        suggestedName = viewModel.suggestedName,
                        onCreateFamily = viewModel::createFamily,
                        onJoinFamily = viewModel::joinAsParent,
                        onBack = viewModel::backToWelcome,
                    )
                    Screen.ChildPairing -> ChildPairingScreen(
                        busy = state.busy,
                        onPair = viewModel::pairChildDevice,
                        onBack = viewModel::backToWelcome,
                    )
                    is Screen.ParentHome -> ParentHomeScreen(
                        family = screen.family,
                        myUid = screen.myUid,
                        members = state.members,
                        busy = state.busy,
                        pairingCode = state.pairingCode,
                        onAddChild = { viewModel.createPairingCode(Role.CHILD) },
                        onInviteParent = { viewModel.createPairingCode(Role.PARENT) },
                        onDismissCode = viewModel::dismissPairingCode,
                        onSetFullControl = viewModel::setFullControl,
                        onOpenChild = viewModel::openChild,
                        onSignOut = viewModel::backToWelcome,
                    )
                    is Screen.ChildDetail -> ChildDetailScreen(
                        child = screen.child,
                        canManage = screen.family.ownerUid == screen.myUid ||
                            state.members.firstOrNull { it.uid == screen.myUid }?.fullControl == true,
                        status = state.childStatus,
                        apps = state.childApps,
                        rules = state.childRules,
                        onSetDailyLimit = viewModel::setDailyLimit,
                        onSetAppBlocked = viewModel::setAppBlocked,
                        onSetAppLimit = viewModel::setAppLimit,
                        onBack = viewModel::closeChild,
                    )
                    is Screen.ChildHome -> ChildHomeScreen(
                        family = screen.family,
                        rules = state.childRules,
                        onReport = viewModel::reportOwnDevice,
                    )
                }
            }
        }
    }
}
