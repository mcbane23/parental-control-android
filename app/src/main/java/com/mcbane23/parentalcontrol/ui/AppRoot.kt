package com.mcbane23.parentalcontrol.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

    AppTheme {
        // The navy header screens draw behind the status bar and need light icons there.
        val navyHeader = state.screen is Screen.ParentHome || state.screen is Screen.ChildDetail ||
            state.screen is Screen.ChildHome
        val dark = isSystemInDarkTheme()
        val view = LocalView.current
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !dark && !navyHeader
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            // Each screen handles the top edge itself: a header fills it, other screens pad it.
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                // Screens fade into each other. The key is the kind of screen, so fresh data
                // on the same screen does not replay the fade.
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentKey = { it::class },
                    label = "screen",
                ) { screen ->
                when (screen) {
                    Screen.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
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
                        location = state.childLocation,
                        busy = state.busy,
                        onRefreshLocation = viewModel::refreshLocation,
                        alerts = state.childAlerts,
                        onSetRequireMobileData = viewModel::setRequireMobileData,
                        onSetParentPin = viewModel::setParentPin,
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
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                }
            }
        }
    }
}
