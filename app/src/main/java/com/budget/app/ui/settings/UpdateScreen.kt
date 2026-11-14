package com.budget.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.budget.app.data.update.UpdateInstaller
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.theme.UpdatePill
import kotlinx.coroutines.launch

/** Installed version, what's new, and download & install, as in the Kairos app. */
@Composable
fun UpdateScreen(container: AppContainer, onBack: () -> Unit) {
    val c = Budget.colors
    val scope = rememberCoroutineScope()
    val update by container.updates.available.collectAsState()
    val checking by container.updates.checking.collectAsState()
    val checkError by container.updates.lastError.collectAsState()
    val install by container.installer.state.collectAsState()
    var checkedOnce by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

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
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { PhIcon(Ph.CARET_LEFT, "Back", c.ink) }
            Text("Update", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }

        Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Line("Installed", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Line("Built", BuildConfig.BUILD_DATE)
            }

            val u = update
            if (u != null) {
                Column(
                    Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhIcon(Ph.DOWNLOAD, null, UpdatePill)
                        Text("Update available", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Version ${u.versionName}", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (u.notes.isNotBlank()) {
                        Text("What's new", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(u.notes.trim(), color = c.ink, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    when (val s = install) {
                        is UpdateInstaller.State.Downloading -> {
                            Text("Downloading… ${s.pct}%", fontSize = 13.sp, color = c.muted)
                            LinearProgressIndicator(progress = { s.pct / 100f }, modifier = Modifier.fillMaxWidth(), color = UpdatePill, trackColor = c.raised)
                        }
                        UpdateInstaller.State.Installing -> Text("Opening the installer…", fontSize = 13.sp, color = c.muted)
                        UpdateInstaller.State.NeedsPermission -> {
                            Text("Allow Budget to install apps (once), then come back.", fontSize = 13.sp, color = c.muted)
                            BlueButton("Allow installs") { permission.launch(container.installer.permissionIntent()) }
                        }
                        is UpdateInstaller.State.Error -> {
                            Text(s.message, fontSize = 13.sp, color = c.danger)
                            BlueButton("Try again") { scope.launch { container.installer.downloadAndInstall(u.apkUrl) } }
                        }
                        UpdateInstaller.State.Idle -> BlueButton("Download & install") {
                            scope.launch { container.installer.downloadAndInstall(u.apkUrl) }
                        }
                    }
                }
            } else {
                Text("You're on the latest version.", color = c.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            if (checkedOnce) checkError?.let { Text(it, color = c.danger, fontSize = 13.sp) }
            OutlinedButton(
                onClick = {
                    checkedOnce = true
                    scope.launch { container.updates.check() }
                },
                enabled = !checking,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp),
            ) { Text(if (checking) "Checking…" else "Check for updates", color = c.ink, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val c = Budget.colors
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BlueButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = UpdatePill, contentColor = androidx.compose.ui.graphics.Color.White),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}
