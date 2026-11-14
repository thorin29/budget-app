package com.budget.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.budget.app.AppContainer
import com.budget.app.data.SessionState
import com.budget.app.ui.month.MonthScreen
import com.budget.app.ui.settings.SettingsScreen
import com.budget.app.ui.setup.SetupScreen
import com.budget.app.ui.theme.Budget

/** One session state decides what is on screen. */
@Composable
fun AppRoot(container: AppContainer) {
    val state by container.session.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Budget.colors.background)) {
        when (val s = state) {
            SessionState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is SessionState.SignedOut -> SetupScreen(container, presetServer = s.server, reason = null)
            is SessionState.NeedsSignIn -> SetupScreen(container, presetServer = s.server, reason = s.reason, presetUser = s.username)
            is SessionState.SignedIn -> {
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(container, s, onBack = { showSettings = false })
                } else {
                    MonthScreen(container, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
