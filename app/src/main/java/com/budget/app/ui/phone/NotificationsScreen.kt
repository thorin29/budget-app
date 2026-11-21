package com.budget.app.ui.phone

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.AppContainer
import com.budget.app.data.AlertKind
import com.budget.app.data.reminders.BillReminderWorker
import com.budget.app.ui.common.SectionLabel
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import kotlinx.coroutines.launch

/** More → Notifications: every kind, off until turned on, and quiet hours. */
@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit) {
    val c = Budget.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val on by container.alertsOn.collectAsState(initial = emptySet())
    val quiet by container.quietHours.collectAsState(initial = true)
    var asking by remember { mutableStateOf<AlertKind?>(null) }
    // Turning the first one on asks Android for permission to post.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asking?.let { k -> if (granted) scope.launch { container.setAlert(k, true) } }
        asking = null
    }
    val blocked = on.isNotEmpty() && !BillReminderWorker.canPost(context)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                PhIcon(Ph.CARET_LEFT, "Back", c.ink)
            }
            Text("Notifications", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (blocked) Note("Notifications are blocked for Ledger in Android settings.", c.danger)
        AlertKind.entries.groupBy { it.group }.forEach { (group, kinds) ->
            SectionLabel(group, c.muted, Modifier.padding(start = 8.dp))
            Group {
                kinds.forEachIndexed { i, k ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Toggle(k.label, k.detail, k in on) { turnOn ->
                        if (turnOn && Build.VERSION.SDK_INT >= 33 && !BillReminderWorker.canPost(context)) {
                            asking = k
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            scope.launch { container.setAlert(k, turnOn) }
                        }
                    }
                }
            }
        }
        SectionLabel("Quiet hours", c.muted, Modifier.padding(start = 8.dp))
        Group {
            Toggle("9 PM – 7 AM", "Held until morning", quiet) { scope.launch { container.setQuietHours(it) } }
        }
        Box(Modifier.height(8.dp).navigationBarsPadding())
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val c = Budget.colors
    Column(Modifier.fillMaxWidth().padding(start = 8.dp).background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp))) { content() }
}

@Composable
private fun Toggle(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Budget.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = c.muted, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
        )
    }
}
