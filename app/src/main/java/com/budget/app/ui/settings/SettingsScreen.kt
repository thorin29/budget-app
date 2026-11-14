package com.budget.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.AppContainer
import com.budget.app.BuildConfig
import com.budget.app.data.SessionState
import com.budget.app.data.update.UpdateInstaller
import com.budget.app.ui.common.Panel
import com.budget.app.ui.theme.Budget
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, session: SessionState.SignedIn, onBack: () -> Unit) {
    val c = Budget.colors
    val scope = rememberCoroutineScope()
    val update by container.updates.available.collectAsState()
    val checking by container.updates.checking.collectAsState()
    val checkError by container.updates.lastError.collectAsState()
    val install by container.installer.state.collectAsState()
    val pending by container.local.queue.collectAsState(initial = emptyList())
    var checkedOnce by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    // Back from the "install unknown apps" screen: carry on if it was granted.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val u = container.updates.available.value
        if (container.installer.canInstall() && u != null) scope.launch { container.installer.downloadAndInstall(u.apkUrl) }
        else container.installer.reset()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
        }

        Panel {
            Text("Signed in", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Line("Account", session.username)
            Line("This phone", session.deviceName)
            Line("Server", session.server)
            Spacer(Modifier.height(8.dp))
            Text(
                "To sign this phone out from somewhere else, use Settings → Sign-in & people on the web. Changing your password there signs out every phone.",
                fontSize = 13.sp,
                color = c.muted,
            )
        }

        Panel {
            Text("App", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Line("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Line("Built", BuildConfig.BUILD_DATE)
            Spacer(Modifier.height(10.dp))
            val u = update
            when {
                u != null -> {
                    Text("Version ${u.versionName} is available.", color = c.accentText, fontWeight = FontWeight.SemiBold)
                    if (u.notes.isNotBlank()) Text(u.notes.trim(), fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    when (val s = install) {
                        is UpdateInstaller.State.Downloading -> {
                            Text("Downloading… ${s.pct}%", fontSize = 13.sp, color = c.muted)
                            LinearProgressIndicator(progress = { s.pct / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                        UpdateInstaller.State.Installing -> Text("Opening the installer…", fontSize = 13.sp, color = c.muted)
                        UpdateInstaller.State.NeedsPermission -> {
                            Text("Allow Budget to install updates (once), then come back.", fontSize = 13.sp, color = c.muted)
                            Button(onClick = { permission.launch(container.installer.permissionIntent()) }) { Text("Allow installs") }
                        }
                        is UpdateInstaller.State.Error -> {
                            Text(s.message, fontSize = 13.sp, color = c.danger)
                            Button(onClick = { scope.launch { container.installer.downloadAndInstall(u.apkUrl) } }) { Text("Try again") }
                        }
                        UpdateInstaller.State.Idle -> Button(onClick = { scope.launch { container.installer.downloadAndInstall(u.apkUrl) } }) {
                            Text("Update to ${u.versionName}")
                        }
                    }
                }
                else -> {
                    if (checkedOnce && !checking && checkError == null) Text("You have the latest version.", fontSize = 13.sp, color = c.muted)
                    checkError?.let { Text(it, fontSize = 13.sp, color = c.danger) }
                    OutlinedButton(
                        onClick = {
                            checkedOnce = true
                            scope.launch { container.updates.check() }
                        },
                        enabled = !checking,
                    ) { Text(if (checking) "Checking…" else "Check for updates") }
                }
            }
        }

        Panel {
            Text("Sign out", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                if (pending.isEmpty()) "Removes this phone's sign-in and everything it saved."
                else "${pending.size} change${if (pending.size == 1) " hasn't" else "s haven't"} been sent yet and will be lost.",
                fontSize = 13.sp,
                color = if (pending.isEmpty()) c.muted else c.danger,
            )
            Spacer(Modifier.height(10.dp))
            if (confirmSignOut) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { container.session.signOut() } }) { Text("Yes, sign out") }
                    TextButton(onClick = { confirmSignOut = false }) { Text("Keep") }
                }
            } else {
                OutlinedButton(onClick = { confirmSignOut = true }) { Text("Sign out of this phone") }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val c = Budget.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(0.35f))
        Text(value, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(0.65f))
    }
}
