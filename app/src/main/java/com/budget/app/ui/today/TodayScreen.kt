package com.budget.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.EntryStatuses
import com.budget.app.data.Money
import com.budget.app.data.MonthLogic
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView
import com.budget.app.data.sync.Overlay
import com.budget.app.ui.common.Banner
import com.budget.app.ui.common.BillTile
import com.budget.app.data.CardLogic
import com.budget.app.data.remote.CardsResponse
import com.budget.app.data.remote.SuggestionDto
import com.budget.app.data.remote.UpcomingDto
import com.budget.app.data.remote.LimitDto
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import com.budget.app.ui.month.MonthViewModel
import com.budget.app.ui.record.toneColor
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import java.time.LocalDate

/** The home tab: what's safe to send, the balance, and what's due next. */
@Composable
fun TodayScreen(
    vm: MonthViewModel,
    onRecord: (MonthEntry?) -> Unit,
    onEditBalance: (MonthView) -> Unit,
    onOpenMonth: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenMore: () -> Unit,
    extras: ExtrasUi = ExtrasUi(),
    onOpenReview: () -> Unit = {},
    onOpenCards: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    onRefreshBank: () -> Unit = {},
) {
    val c = Budget.colors
    val ui by vm.ui.collectAsState()
    val pending by vm.pending.collectAsState()
    val problems by vm.problems.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val view = remember(ui.view, pending) { ui.view?.let { Overlay.apply(it, pending) } }
    val due = remember(view) { view?.let { MonthLogic.dueSoon(it).take(5) } ?: emptyList() }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Dates.weekdayDate(LocalDate.now()), color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(Dates.monthName(ui.year, ui.month), color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    }
                    SyncChip(
                        when {
                            syncing && pending.isNotEmpty() -> SyncState.SENDING
                            ui.offline -> SyncState.OFFLINE
                            pending.isNotEmpty() -> SyncState.WAITING
                            else -> SyncState.SYNCED
                        },
                        pending.size,
                    )
                }
            }

            if (ui.offline) item {
                Banner("You're offline. Anything you record is kept on the phone and sent when you're back.", c.warn, c.warnSoft, icon = Ph.CLOUD_SLASH)
            } else if (pending.isNotEmpty()) item {
                Banner(
                    "${pending.size} change${if (pending.size == 1) "" else "s"} waiting to send. Figures update once they're in.",
                    c.warn, c.warnSoft, icon = Ph.SYNC,
                )
            }
            if (problems.isNotEmpty()) item {
                Banner(
                    "${problems.size} change${if (problems.size == 1) " wasn't" else "s weren't"} saved. Open More to see why.",
                    c.danger, c.dangerSoft, icon = Ph.WARNING,
                    modifier = Modifier.clickable(onClick = onOpenMore),
                )
            }
            ui.error?.let { e -> item { Banner(e, c.danger, c.dangerSoft, icon = Ph.WARNING) } }

            if (view != null) item {
                // As on the web: the bills account's balance, then what's left after each half's bills.
                // A balance the bank keeps current isn't typed over; it can be read again instead.
                val fromBank = view.billsBalanceFromBank
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.accent, RoundedCornerShape(24.dp))
                        .then(if (fromBank) Modifier else Modifier.clickable { onEditBalance(view) })
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${view.billsAccountName ?: "Bills account"} balance",
                            color = c.onAccent.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (fromBank) {
                            Box(
                                Modifier.size(36.dp).clickable(enabled = !extras.bankSyncing, onClick = onRefreshBank),
                                contentAlignment = Alignment.Center,
                            ) { PhIcon(Ph.SYNC, "Read the bank now", c.onAccent.copy(alpha = if (extras.bankSyncing) 0.5f else 1f), size = 20.dp) }
                        } else {
                            PhIcon(Ph.PENCIL, null, c.onAccent, size = 18.dp)
                        }
                    }
                    Text(
                        view.billsBalanceCents?.let { Money.format(it) } ?: "—",
                        color = c.onAccent, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
                    )
                    Text(
                        when {
                            fromBank && extras.bankSyncing -> "Reading the bank…"
                            // The bank's own date first: a sync only picks up what the bridge already has.
                            fromBank -> listOfNotNull(
                                view.billsBankDataAt?.let { Dates.stamp(it) }?.let { "Bank data as of $it" } ?: "From the bank",
                                (view.bankSyncedAt ?: view.billsBalanceAsOf)?.let { Dates.stamp(it) }?.let { "checked $it" },
                            ).joinToString(" · ")
                            view.billsBalanceCents == null -> "Add the balance to see what's left after bills."
                            else -> "Tap to update it if the bank hasn't yet."
                        },
                        color = c.onAccent.copy(alpha = 0.9f), fontSize = 14.sp,
                    )
                    if (fromBank) extras.bankNote?.let { note ->
                        Text(note, color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            }

            // Server 0.40+: the flexible money left this pay period. Opens Insights.
            val envelopes = extras.envelopes
            if (envelopes != null) {
                item { com.budget.app.ui.insights.EnvelopeHero(envelopes, Modifier.clickable(onClick = onOpenInsights)) }
                if (envelopes.message != null) item { com.budget.app.ui.insights.EnvelopeMessage(envelopes) }
            }

            val toReview = extras.review?.let { it.looksPaid.size + it.recorded.size } ?: 0
            if (toReview > 0) item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(c.accentSoft, RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenReview)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).background(c.accent, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Text("$toReview", color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "to review: payments from the bank",
                        color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    PhIcon(Ph.CARET_RIGHT, null, c.accent, size = 20.dp)
                }
            }

            extras.cards?.takeIf { it.cards.isNotEmpty() }?.let { cards ->
                item { CardsRow(cards, onClick = onOpenCards) }
            }

            if (view != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val first = view.transferAfterFirstCents
                        val owedFirst = (view.halves.firstOrNull()?.remainingCents ?: 0L) + view.carriedCents
                        Tile(
                            label = "First half remaining",
                            value = first?.let { Money.format(it) } ?: "—",
                            valueColor = if ((first ?: 0) < 0) c.danger else c.ink,
                            detail = "${Money.format(owedFirst)} still owed before the ${ordinal(view.splitDay)}${if (view.splitPayday != null) " payday" else ""}",
                            modifier = Modifier.weight(1f),
                        )
                        val free = view.transferAfterSecondCents
                        Tile(
                            label = "Second half remaining",
                            value = free?.let { Money.format(it) } ?: "—",
                            valueColor = if ((free ?: 0) < 0) c.danger else c.ink,
                            detail = "${Money.format(view.totalRemainingCents)} still owed this month",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, c.line, RoundedCornerShape(20.dp)),
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Due soon", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                "See month",
                                color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(onClick = onOpenMonth).padding(horizontal = 8.dp, vertical = 12.dp),
                            )
                        }
                        if (due.isEmpty()) {
                            Text("Nothing left to pay this month.", color = c.muted, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
                        }
                        due.forEach { e -> DueRow(e, onClick = { onRecord(e) }) }
                    }
                }

                // The monthly limit, on servers before 0.40; the envelopes near the top replace it.
                if (extras.envelopes == null) {
                    extras.insights?.limit?.takeIf { it.limitCents > 0 }?.let { l ->
                        item { LimitRow(l, onClick = onOpenInsights) }
                    }
                }
                extras.insights?.upcoming?.firstOrNull()?.let { u ->
                    item { ComingUpRow(u, onClick = onOpenInsights) }
                }
                extras.insights?.let { ins ->
                    ins.suggestions.firstOrNull()?.let { top ->
                        item { SuggestionCard(top, more = ins.suggestions.size - 1, onClick = onOpenInsights) }
                    }
                }
            } else if (!ui.loading) {
                item { Text("Nothing loaded yet. Pull it in once you're online.", color = c.muted, modifier = Modifier.padding(4.dp)) }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onRecord(null) },
            containerColor = c.accent,
            contentColor = c.onAccent,
            shape = RoundedCornerShape(18.dp),
            icon = { PhIcon(Ph.PLUS, null, c.onAccent) },
            text = { Text("Record", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private enum class SyncState { SYNCED, WAITING, SENDING, OFFLINE }

@Composable
private fun SyncChip(state: SyncState, count: Int) {
    val c = Budget.colors
    val (icon, label) = when (state) {
        SyncState.SYNCED -> Ph.CLOUD_CHECK to "Synced"
        SyncState.WAITING -> Ph.SYNC to "$count waiting"
        SyncState.SENDING -> Ph.SYNC to "Sending…"
        SyncState.OFFLINE -> Ph.CLOUD_SLASH to "Offline"
    }
    val quiet = state == SyncState.SYNCED
    Row(
        Modifier
            .height(32.dp)
            .background(if (quiet) c.surface else c.warnSoft, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (quiet) c.muted else c.warn
        PhIcon(icon, null, tint, size = 18.dp)
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: Ph? = null,
    valueColor: Color = Budget.colors.ink,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Budget.colors
    Column(
        modifier
            .background(c.surface, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (icon != null) PhIcon(icon, null, c.muted, size = 18.dp)
        }
        Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (detail != null) Text(detail, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** 1 → "1st", 15 → "15th", 22 → "22nd". */
private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

@Composable
private fun DueRow(e: MonthEntry, onClick: () -> Unit) {
    val c = Budget.colors
    val status = EntryStatuses.of(e)
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BillTile(e)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status.text, color = toneColor(status.tone), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(Money.format(e.budgetedCents), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(44.dp).background(c.accentSoft, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) { PhIcon(Ph.CHECK_CIRCLE, "Record ${e.name}", c.accent, size = 22.dp) }
        }
    }
}

/** One line for the cards: what to pay this cycle and when, with each card's color. Opens the Cards tab. */
@Composable
private fun CardsRow(cards: CardsResponse, onClick: () -> Unit) {
    val c = Budget.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shown = cards.cards.take(3)
        Box(Modifier.width((22 + 12 * (shown.size - 1)).dp).height(16.dp)) {
            shown.forEachIndexed { i, card ->
                Box(
                    Modifier
                        .offset(x = (12 * i).dp)
                        .width(22.dp)
                        .height(16.dp)
                        .background(c.surface, RoundedCornerShape(4.dp))
                        .padding(start = if (i > 0) 1.dp else 0.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(CardLogic.argb(card.color.from)), Color(CardLogic.argb(card.color.to)))),
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (cards.payThisCycleCents > 0) {
                    "${Money.whole(cards.payThisCycleCents)} to cards" + (cards.nextDue?.let { " by ${Dates.short(it) ?: it}" } ?: "")
                } else {
                    "Cards are paid for this cycle"
                },
                color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${cards.cards.size} card${if (cards.cards.size == 1) "" else "s"} · balance ${Money.whole(cards.totalOwedCents)}",
                color = c.muted, fontSize = 13.sp,
            )
        }
        PhIcon(Ph.CARET_RIGHT, null, c.muted, size = 20.dp)
    }
}

/** The most worthwhile suggestion, with a way to the rest. Opens the Insights tab. */
@Composable
private fun SuggestionCard(s: SuggestionDto, more: Int, onClick: () -> Unit) {
    val c = Budget.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhIcon(Ph.LIGHTBULB, null, c.warn, size = 18.dp)
            Text("SUGGESTION", color = c.warn, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Text(s.title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(s.detail, color = c.muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            if (more > 0) "See all ${more + 1} in Insights" else "Open Insights",
            color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The next yearly charge, so it doesn't catch anyone off guard. Opens Insights. */
@Composable
private fun ComingUpRow(u: UpcomingDto, onClick: () -> Unit) {
    val c = Budget.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.warnSoft, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhIcon(Ph.CALENDAR, null, c.warn, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Coming up: ${u.merchant}", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Yearly charge around ${Dates.short(u.expectedOn) ?: u.expectedOn}", color = c.muted, fontSize = 13.sp)
        }
        Text(Money.format(u.cents), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** This month's everyday spending against the monthly limit. Opens Insights. */
@Composable
private fun LimitRow(l: LimitDto, onClick: () -> Unit) {
    val c = Budget.colors
    val t = l.thisMonth
    val over = t.leftCents < 0
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spending this month", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${Money.whole(t.spentCents)} of ${Money.whole(l.limitCents)}", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.raised, RoundedCornerShape(4.dp))) {
            Box(
                Modifier
                    .fillMaxWidth((t.spentCents.toFloat() / l.limitCents).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(if (over) c.danger else c.accent, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            if (over) "${Money.whole(-t.leftCents)} over the monthly limit" else "${Money.whole(t.leftCents)} left · about ${Money.whole(t.perWeekLeftCents)} a week",
            color = if (over) c.danger else c.muted, fontSize = 13.sp,
        )
    }
}
