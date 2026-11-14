package com.budget.app.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budget.app.AppContainer
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.sync.Overlay
import com.budget.app.ui.common.Banner
import com.budget.app.ui.common.Panel
import com.budget.app.ui.common.Pill
import com.budget.app.ui.theme.Budget
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private data class Section(val key: String, val title: String, val summary: String, val rows: List<MonthEntry>, val danger: Boolean = false)

@Composable
fun MonthScreen(container: AppContainer, onOpenSettings: () -> Unit) {
    val vm: MonthViewModel = viewModel { MonthViewModel(container) }
    val ui by vm.ui.collectAsState()
    val pending by vm.pending.collectAsState()
    val problems by vm.problems.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val update by container.updates.available.collectAsState()
    val c = Budget.colors

    var selected by remember { mutableStateOf<MonthEntry?>(null) }
    var editingBalance by remember { mutableStateOf(false) }

    val view = remember(ui.view, pending) { ui.view?.let { Overlay.apply(it, pending) } }
    val isCurrent = YearMonth.now() == YearMonth.of(ui.year, ui.month)

    val sections = remember(view) {
        if (view == null) {
            emptyList()
        } else {
            buildList {
                if (view.income.isNotEmpty()) {
                    add(Section("income", "Income", "${Money.format(view.incomeReceivedCents)} of ${Money.format(view.incomeExpectedCents)}", view.income))
                }
                if (view.carried.isNotEmpty()) {
                    add(Section("carried", "Unpaid from earlier", "${Money.format(view.carriedCents)} owed", view.carried, danger = true))
                }
                view.halves.forEach { h ->
                    add(Section("half-${h.half.index}", h.half.label, "${Money.format(h.paidCents)} paid · ${Money.format(h.remainingCents)} left", h.entries))
                }
                if (view.surplusItems.isNotEmpty()) {
                    add(Section("surplus", "Paid from what's left over", "${Money.format(view.surplusBudgetedCents)} planned", view.surplusItems))
                }
            }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.shift(-1) }) { Text("‹", fontSize = 22.sp) }
                    Text(
                        Dates.monthTitle(ui.year, ui.month),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    TextButton(onClick = { vm.shift(1) }) { Text("›", fontSize = 22.sp) }
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isCurrent) TextButton(onClick = { vm.today() }) { Text("Back to this month") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.load() }, enabled = !ui.loading) { Text(if (ui.loading) "Loading…" else "Refresh") }
                }
                if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        if (ui.offline) item { Banner("Offline. Showing what was last loaded; changes will send when you're back.", c.warn, c.warnSoft) }
        if (pending.isNotEmpty()) {
            item {
                Banner(
                    if (syncing) "Sending ${pending.size} change${if (pending.size == 1) "" else "s"}…"
                    else "${pending.size} change${if (pending.size == 1) "" else "s"} waiting to send",
                    c.info,
                    c.infoSoft,
                )
            }
        }
        if (problems.isNotEmpty()) {
            item {
                Panel {
                    Text("Not saved", fontWeight = FontWeight.SemiBold, color = c.danger)
                    problems.forEach { p ->
                        Text("${p.label}: ${p.message}", fontSize = 14.sp, color = c.ink, modifier = Modifier.padding(top = 6.dp))
                    }
                    TextButton(onClick = { vm.dismissProblems() }) { Text("Dismiss") }
                }
            }
        }
        update?.let { u -> item { Banner("Update available: ${u.versionName}. Open Settings to install.", c.accentText, c.accentSoft, Modifier.clickable { onOpenSettings() }) } }
        ui.error?.let { e -> item { Banner(e, c.danger, c.dangerSoft) } }

        if (view != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            label = "In ${view.billsAccountName ?: "bills"}",
                            value = view.billsBalanceCents?.let { Money.format(it) } ?: "Add",
                            detail = "Tap to update",
                            modifier = Modifier.weight(1f).clickable { editingBalance = true },
                        )
                        StatCard(
                            label = "Free after 1st half",
                            value = view.transferAfterFirstCents?.let { Money.format(it) } ?: "—",
                            detail = null,
                            danger = (view.transferAfterFirstCents ?: 0) < 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            label = "Free after the month",
                            value = view.transferAfterSecondCents?.let { Money.format(it) } ?: "—",
                            detail = "${Money.format(view.totalRemainingCents)} still owed",
                            danger = (view.transferAfterSecondCents ?: 0) < 0,
                            modifier = Modifier.weight(1f),
                        )
                        val p = ui.projection
                        StatCard(
                            label = "Safe to send today",
                            value = if (p == null || p.balanceMissing) "—" else Money.format(maxOf(p.safeToPayCents, 0L)),
                            detail = p?.takeIf { !it.balanceMissing }?.let { "Low ${Money.format(it.lowPoint.balanceCents)} on ${Dates.short(it.lowPoint.date) ?: ""}" },
                            hero = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pending.isNotEmpty()) {
                        Text("Free-money figures update once changes are sent.", fontSize = 12.sp, color = c.muted)
                    }
                }
            }

            sections.forEach { section ->
                item(key = "h-${section.key}") {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            section.title.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (section.danger) c.danger else c.neutral,
                            modifier = Modifier.weight(1f),
                        )
                        Text(section.summary, fontSize = 12.sp, color = c.muted)
                    }
                }
                if (section.rows.isEmpty()) {
                    item(key = "e-${section.key}") { Text("Nothing here.", fontSize = 14.sp, color = c.muted) }
                }
                items(section.rows, key = { "${section.key}-${it.lineItemId}-${it.year}-${it.month}" }) { entry ->
                    EntryRow(entry, onClick = { selected = entry })
                }
            }
        } else if (!ui.loading) {
            item { Text("Nothing loaded yet.", color = c.muted) }
        }
    }

    selected?.let { entry ->
        ConfirmSheet(
            entry = entry,
            defaultPaidOn = vm.defaultPaidOn(entry),
            onDismiss = { selected = null },
            onConfirm = { cents, day ->
                vm.record(entry, cents, day)
                selected = null
            },
            onClear = {
                vm.clear(entry)
                selected = null
            },
        )
    }

    if (editingBalance && view != null) {
        BalanceDialog(
            current = view.billsBalanceCents,
            accountName = view.billsAccountName,
            onDismiss = { editingBalance = false },
            onSave = {
                vm.setBalance(it)
                editingBalance = false
            },
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    hero: Boolean = false,
) {
    val c = Budget.colors
    Column(
        modifier
            .background(if (hero) c.hero else c.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(label, fontSize = 12.sp, color = if (hero) c.heroMuted else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                hero -> c.heroText
                danger -> c.danger
                else -> c.ink
            },
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
        )
        if (detail != null) {
            Text(detail, fontSize = 11.sp, color = if (hero) c.heroMuted else c.muted, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private data class Status(val label: String, val fg: Color, val bg: Color)

@Composable
private fun statusOf(entry: MonthEntry): Status {
    val c = Budget.colors
    val today = LocalDate.now()
    val due = Dates.dueDate(entry.year, entry.month, entry.dueDay)
    val days = due?.let { ChronoUnit.DAYS.between(today, it) }
    return when {
        entry.isIncome && entry.settled -> Status("Received", c.ok, c.okSoft)
        entry.isIncome -> Status("Expected", c.info, c.infoSoft)
        entry.settled -> Status(Dates.short(entry.paidOn)?.let { "Paid $it" } ?: "Paid", c.ok, c.okSoft)
        entry.carriedFrom != null -> Status("Unpaid from ${YearMonth.of(entry.year, entry.month).month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}", c.danger, c.dangerSoft)
        days != null && days < 0 -> Status("Overdue", c.danger, c.dangerSoft)
        days != null && days == 0L -> Status("Due today", c.warn, c.warnSoft)
        days != null && days <= 7 -> Status("Due in $days days", c.warn, c.warnSoft)
        else -> Status("Upcoming", c.neutral, c.neutralSoft)
    }
}

@Composable
private fun EntryRow(entry: MonthEntry, onClick: () -> Unit) {
    val c = Budget.colors
    val status = statusOf(entry)
    val meta = listOfNotNull(
        Dates.dueDate(entry.year, entry.month, entry.dueDay)?.let { Dates.short(it.toString())?.let { d -> "${if (entry.isIncome) "Arrives" else "Due"} $d" } },
        entry.categoryName,
    ).joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (entry.settled) c.accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.settled) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else Box(Modifier.size(18.dp).background(c.neutralSoft, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Pill(status.label, status.fg, status.bg)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                entry.actualCents?.let { Money.format(it) } ?: "Add",
                fontWeight = if (entry.settled) FontWeight.Bold else FontWeight.Normal,
                color = if (entry.settled) c.ink else c.accentText,
            )
            Text(Money.format(entry.budgetedCents), fontSize = 12.sp, color = c.muted)
        }
    }
}

/**
 * The only way a payment is recorded: check the amount and the date, then press
 * Confirm. No tap-to-tick, as on the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(
    entry: MonthEntry,
    defaultPaidOn: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
    onClear: () -> Unit,
) {
    val c = Budget.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember(entry) { mutableStateOf(Money.toDecimal(entry.actualCents ?: entry.budgetedCents)) }
    var day by remember(entry) { mutableStateOf(defaultPaidOn) }
    var confirmClear by remember(entry) { mutableStateOf(false) }
    val cents = Money.parse(amount)
    val dayOk = Dates.isValidDay(day)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(entry.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(
                "${if (entry.isIncome) "Expected" else "Budget"} ${Money.format(entry.budgetedCents)}" +
                    if (entry.carriedFrom != null) " · from ${Dates.monthTitle(entry.year, entry.month)}" else "",
                color = c.muted,
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(if (entry.isIncome) "Amount received" else "Amount paid") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = cents == null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = day,
                onValueChange = { day = it.take(10) },
                label = { Text(if (entry.isIncome) "Received on (YYYY-MM-DD)" else "Paid on (YYYY-MM-DD)") },
                singleLine = true,
                isError = !dayOk,
                modifier = Modifier.fillMaxWidth(),
            )
            if (cents != null) {
                val diff = cents - entry.budgetedCents
                Text(
                    when {
                        diff == 0L -> "Exactly on budget"
                        diff < 0 -> "${Money.format(-diff)} under budget"
                        else -> "${Money.format(diff)} over budget"
                    },
                    color = c.muted,
                    fontSize = 14.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.settled) {
                    if (confirmClear) {
                        TextButton(onClick = onClear) { Text("Yes, mark unpaid", color = c.danger) }
                    } else {
                        TextButton(onClick = { confirmClear = true }) { Text(if (entry.isIncome) "Not received" else "Mark unpaid", color = c.danger) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { if (cents != null && dayOk) onConfirm(cents, day) },
                    enabled = cents != null && dayOk,
                ) { Text(if (cents != null) "Confirm ${Money.format(cents)}" else "Confirm") }
            }
        }
    }
}

@Composable
private fun BalanceDialog(current: Long?, accountName: String?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(current?.let { Money.toDecimal(maxOf(it, 0L)) } ?: "") }
    val cents = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("In ${accountName ?: "bills"} now") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = text.isNotEmpty() && cents == null,
            )
        },
        confirmButton = {
            Button(onClick = { cents?.let(onSave) }, enabled = cents != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
