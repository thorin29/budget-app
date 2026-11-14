package com.budget.app.ui.setup

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.AppContainer
import com.budget.app.data.LocalNetwork
import com.budget.app.data.ServerUrl
import com.budget.app.data.remote.ApiException
import com.budget.app.ui.common.Banner
import com.budget.app.ui.common.Panel
import com.budget.app.ui.theme.Budget
import kotlinx.coroutines.launch
import java.io.IOException

private enum class Step { SERVER, CODE, ACCOUNT }

/**
 * Adding this phone, in three short steps:
 *  1. the server address (filled in by the web's QR code),
 *  2. the one-time code from Settings → Sign-in & people → Add a phone (also from the QR),
 *  3. username and password.
 * The password is never stored; it is spent on the spot for this phone's token.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    presetServer: String?,
    reason: String?,
    presetUser: String? = null,
) {
    val c = Budget.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(Step.SERVER.name) }
    var server by rememberSaveable { mutableStateOf(presetServer ?: "") }
    var code by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf(presetUser ?: "") }
    var password by remember { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(Build.MODEL ?: "Phone") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverVersion by rememberSaveable { mutableStateOf<String?>(null) }

    // Opened from the web's QR code: fill in server and code, and skip ahead.
    val link by container.pendingEnrollLink.collectAsState()
    LaunchedEffect(link) {
        link?.let {
            server = it.server
            code = it.code
            step = Step.SERVER.name
            container.pendingEnrollLink.value = null
        }
    }

    fun explain(e: Exception): String = when (e) {
        is ApiException -> when {
            e.status == 429 -> "Too many attempts. Wait a few minutes and try again."
            e.code == "validation_failed" -> e.message ?: "Check what you entered."
            else -> e.message ?: "The server refused that."
        }
        is IOException -> e.message?.takeIf { it.contains("Budget") || it.contains("server (API") }
            ?: "Couldn't reach the server. Check the address and your connection."
        else -> e.message ?: "Something went wrong."
    }

    fun checkServer() {
        val normalized = ServerUrl.normalize(server)
        if (normalized == null) {
            error = "Enter the address you open Budget at, like budget.example.com."
            return
        }
        server = normalized
        busy = true
        error = null
        scope.launch {
            try {
                serverVersion = container.session.checkServer(normalized)
                step = Step.CODE.name
            } catch (e: Exception) {
                error = explain(e)
            } finally {
                busy = false
            }
        }
    }

    val localPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) checkServer() else error = "Budget needs permission to reach a server on your home network."
    }

    fun submitAccount() {
        busy = true
        error = null
        scope.launch {
            try {
                container.session.enroll(server, username, password, code.trim().uppercase(), deviceName.trim().ifEmpty { "Phone" })
                password = ""
                container.sync.flush()
            } catch (e: Exception) {
                error = explain(e)
                // The code is single-use; a refused one usually means make a new one.
                if (e is ApiException && e.message?.contains("code", ignoreCase = true) == true) step = Step.CODE.name
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(c.accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.size(10.dp))
                Text("Budget", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }

            if (reason != null) Banner(reason, c.warn, c.warnSoft)

            Panel {
                when (Step.valueOf(step)) {
                    Step.SERVER -> {
                        Text("Your Budget server", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(
                            "The address you open Budget at. Scanning the QR code under Settings → Sign-in & people → Add a phone on the web fills this in.",
                            color = c.muted, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("Server address") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val normalized = ServerUrl.normalize(server)
                                if (normalized != null && LocalNetwork.needsPermission(context, normalized)) {
                                    localPermission.launch(LocalNetwork.PERMISSION)
                                } else {
                                    checkServer()
                                }
                            },
                            enabled = !busy && server.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "Checking…" else "Continue") }
                    }

                    Step.CODE -> {
                        Text("One-time code", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(
                            "On the web, open Settings → Sign-in & people and choose Add a phone. Type the code it shows (it lasts 15 minutes).",
                            color = c.muted, fontSize = 14.sp,
                        )
                        serverVersion?.let { Text("Connected to Budget $it at $server", color = c.accentText, fontSize = 13.sp) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().take(9) },
                            label = { Text("Code, like ABCD-EFGH") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { step = Step.SERVER.name; error = null }) { Text("Back") }
                            Button(
                                onClick = { step = Step.ACCOUNT.name; error = null },
                                enabled = code.replace("-", "").trim().length == 8,
                                modifier = Modifier.weight(1f),
                            ) { Text("Continue") }
                        }
                    }

                    Step.ACCOUNT -> {
                        Text("Sign in", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = c.ink)
                        Text("Your Budget username and password. The password isn't kept on the phone.", color = c.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it.take(60) },
                            label = { Text("Name for this phone") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { step = Step.CODE.name; error = null }) { Text("Back") }
                            Button(
                                onClick = { submitAccount() },
                                enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                                } else {
                                    Text("Add this phone")
                                }
                            }
                        }
                    }
                }
            }

            error?.let { Banner(it, c.danger, c.dangerSoft) }
        }
    }
}
