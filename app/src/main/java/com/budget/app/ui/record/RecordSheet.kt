package com.budget.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.AmountInput
import com.budget.app.data.Dates
import com.budget.app.data.EntryStatuses
import com.budget.app.data.Money
import com.budget.app.data.Tone
import com.budget.app.data.remote.MonthEntry
import com.budget.app.ui.common.BillTile
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class Step { ENTER, CONFIRM }

private fun keyOf(e: MonthEntry) = "${e.lineItemId}:${e.year}:${e.month}"

/**
 * Recording a payment: pick the bill (due ones are offered first), check or
 * type the amount, pick the date, then Review and Confirm. Nothing is recorded
 * by a single tap, as on the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    candidates: List<MonthEntry>,
    everything: List<MonthEntry>,
    initial: MonthEntry?,
    defaultPaidOn: (MonthEntry) -> String,
    onDismiss: () -> Unit,
    onConfirm: (MonthEntry, Long, String) -> Unit,
    onClear: (MonthEntry) -> Unit,
) {
    val c = Budget.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selected by remember { mutableStateOf(initial ?: candidates.firstOrNull()) }
    val chips = remember(candidates, initial) {
        (listOfNotNull(initial) + candidates.take(8)).distinctBy(::keyOf)
    }
    var browsing by remember { mutableStateOf(selected == null) }
    var step by remember { mutableStateOf(Step.ENTER) }
    var pickingDate by remember { mutableStateOf(false) }

    val current = selected
    var text by remember(current) { mutableStateOf(current?.let { Money.toDecimal(it.actualCents ?: it.budgetedCents) } ?: "") }
    var fresh by remember(current) { mutableStateOf(true) }
    var day by remember(current) { mutableStateOf(current?.let(defaultPaidOn) ?: LocalDate.now().toString()) }
    var confirmClear by remember(current) { mutableStateOf(false) }
    val cents = AmountInput.cents(text)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step == Step.CONFIRM && current != null && cents != null) {
                ConfirmStep(
                    entry = current,
                    cents = cents,
                    day = day,
                    onChange = { step = Step.ENTER },
                    onConfirm = { onConfirm(current, cents, day) },
                )
            } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (current?.isIncome == true) "Record income" else "Record a payment",
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { PhIcon(Ph.X, "Close", c.muted) }
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { e ->
                    val on = current != null && keyOf(e) == keyOf(current) && !browsing
                    Chip(
                        label = e.name,
                        on = on,
                        onClick = { selected = e; browsing = false },
                    ) { BillTileIcon(e, if (on) c.accent else c.muted) }
                }
                Chip(label = "All items", on = browsing, dashed = true, onClick = { browsing = !browsing }) {
                    PhIcon(Ph.SEARCH, null, if (browsing) c.accent else c.muted, size = 18.dp)
                }
            }

            if (browsing || current == null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (everything.isEmpty()) Text("Nothing to record this month.", color = c.muted, modifier = Modifier.padding(8.dp))
                    everything.forEach { e ->
                        val status = EntryStatuses.of(e)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = e; browsing = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BillTile(e, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, color = c.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(status.text, color = toneColor(status.tone), fontSize = 13.sp)
                            }
                            Text(Money.format(e.actualCents ?: e.budgetedCents), color = c.ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                EnterStep(
                    entry = current,
                    text = text,
                    day = day,
                    confirmClear = confirmClear,
                    cents = cents,
                    onPlanned = {
                        text = Money.toDecimal(current.budgetedCents)
                        fresh = true
                    },
                    onPickDate = { pickingDate = true },
                    onKey = { key ->
                        text = AmountInput.press(text, key, fresh)
                        fresh = false
                    },
                    onClear = {
                        if (confirmClear) onClear(current)
                        confirmClear = true
                    },
                    onReview = { step = Step.CONFIRM },
                )
            }
            }
        }
    }

    if (pickingDate) {
        val initialMillis = runCatching { LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun EnterStep(
    entry: MonthEntry,
    text: String,
    day: String,
    confirmClear: Boolean,
    cents: Long?,
    onPlanned: () -> Unit,
    onPickDate: () -> Unit,
    onKey: (Char) -> Unit,
    onClear: () -> Unit,
    onReview: () -> Unit,
) {
    val c = Budget.colors
    val current = entry
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val status = EntryStatuses.of(current)
            Text(
                listOfNotNull(status.text, current.accountName?.let { "from $it" }).joinToString(" · "),
                color = toneColor(status.tone), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "$" + AmountInput.display(text),
                fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, letterSpacing = (-1).sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Amount ${AmountInput.display(text)} dollars" },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                SmallChip(Ph.RESET, "${if (current.isIncome) "Expected" else "Planned"} ${Money.format(current.budgetedCents)}", onPlanned)
                SmallChip(Ph.CALENDAR, "${if (current.isIncome) "Received" else "Paid"} ${Dates.dayLabel(day)}", onPickDate)
            }

            Keypad(onKey)

            if (current.settled) {
                TextButton(onClick = onClear) {
                    Text(
                        when {
                            confirmClear -> "Tap again to mark unpaid"
                            current.isIncome -> "Not received after all"
                            else -> "Mark unpaid"
                        },
                        color = c.danger, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Button(
                onClick = onReview,
                enabled = cents != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) {
                Text("Review", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                PhIcon(Ph.ARROW_RIGHT, null, c.onAccent, size = 20.dp)
            }
    }
}

@Composable
private fun ConfirmStep(entry: MonthEntry, cents: Long, day: String, onChange: () -> Unit, onConfirm: () -> Unit) {
    val c = Budget.colors
    val diff = cents - entry.budgetedCents
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BillTile(entry, size = 56.dp, tint = c.accent, background = c.accentSoft)
        Text(
            "Record ${entry.name} as ${if (entry.isIncome) "received" else "paid"}?",
            color = c.muted, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Text(Money.format(cents), color = c.ink, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.raised, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Detail(if (entry.isIncome) "Received on" else "Paid on", Dates.dayLabel(day), divider = true)
        Detail(if (entry.isIncome) "Expected" else "Planned", Money.format(entry.budgetedCents), divider = diff != 0L || entry.accountName != null)
        if (diff != 0L) {
            Detail(
                "Difference",
                (if (diff > 0) "${Money.format(diff)} more" else "${Money.format(-diff)} less"),
                divider = entry.accountName != null,
            )
        }
        entry.accountName?.let { Detail("From", it, divider = false) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhIcon(Ph.CLOUD_UP, null, c.muted, size = 18.dp)
        Text("No signal? It waits on the phone and sends later.", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onChange,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Change", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(2f).height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) {
            PhIcon(Ph.CHECK, null, c.onAccent, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text("Confirm", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Detail(label: String, value: String, divider: Boolean) {
    val c = Budget.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(label, color = c.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = c.ink, fontWeight = FontWeight.Bold)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit, dashed: Boolean = false, icon: @Composable () -> Unit) {
    val c = Budget.colors
    Row(
        Modifier
            .height(40.dp)
            .background(if (on) c.accentSoft else c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(label, color = if (on) c.accent else if (dashed) c.muted else c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BillTileIcon(e: MonthEntry, tint: androidx.compose.ui.graphics.Color) {
    val key = com.budget.app.data.BillIcons.forItem(e.name, e.categoryName, e.isIncome)
    PhIcon(com.budget.app.ui.theme.iconFor(key), null, tint, size = 18.dp)
}

@Composable
private fun SmallChip(icon: Ph, label: String, onClick: () -> Unit) {
    val c = Budget.colors
    Row(
        Modifier
            .height(36.dp)
            .background(c.raised, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PhIcon(icon, null, c.ink, size = 18.dp)
        Text(label, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit) {
    val rows = listOf("123", "456", "789", ".0" + AmountInput.DELETE)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> Key(k, onKey) }
            }
        }
    }
}

@Composable
private fun RowScope.Key(k: Char, onKey: (Char) -> Unit) {
    val c = Budget.colors
    val label = when (k) {
        AmountInput.DELETE -> "Delete"
        '.' -> "Decimal point"
        else -> k.toString()
    }
    Box(
        Modifier
            .weight(1f)
            .height(60.dp)
            .background(c.raised, RoundedCornerShape(16.dp))
            .clickable { onKey(k) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (k == AmountInput.DELETE) PhIcon(Ph.BACKSPACE, null, c.ink, size = 26.dp)
        else Text(k.toString(), color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun toneColor(tone: Tone) = when (tone) {
    Tone.MUTED -> Budget.colors.muted
    Tone.WARN -> Budget.colors.warn
    Tone.DANGER -> Budget.colors.danger
}
