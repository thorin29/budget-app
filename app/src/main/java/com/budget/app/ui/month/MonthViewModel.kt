package com.budget.app.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budget.app.AppContainer
import com.budget.app.data.Money
import com.budget.app.data.remote.ApiException
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView
import com.budget.app.data.remote.Projection
import com.budget.app.data.sync.PendingWrite
import com.budget.app.data.sync.SyncProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class MonthUi(
    val year: Int,
    val month: Int,
    /** As the server last sent it; queued changes are laid over it on screen. */
    val view: MonthView? = null,
    val projection: Projection? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
)

class MonthViewModel(private val c: AppContainer) : ViewModel() {
    private val now = YearMonth.now()
    private val _ui = MutableStateFlow(MonthUi(now.year, now.monthValue))
    val ui: StateFlow<MonthUi> = _ui

    val pending: StateFlow<List<PendingWrite>> =
        c.local.queue.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val problems: StateFlow<List<SyncProblem>> =
        c.local.problems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val syncing: StateFlow<Boolean> = c.sync.syncing

    // Loaded when its tab is shown (MainScreen), so switching tabs refreshes it.

    fun shift(by: Int) {
        val next = YearMonth.of(_ui.value.year, _ui.value.month).plusMonths(by.toLong())
        _ui.update { it.copy(year = next.year, month = next.monthValue, view = null, error = null) }
        load()
    }

    fun today() {
        _ui.update { it.copy(year = now.year, month = now.monthValue, view = null, error = null) }
        load()
    }

    fun load() {
        val year = _ui.value.year
        val month = _ui.value.month
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            // Show the last copy at once, then replace it with fresh data.
            c.local.loadMonth(year, month)?.let { cached ->
                if (_ui.value.view == null && _ui.value.year == year && _ui.value.month == month) {
                    _ui.update { it.copy(view = cached) }
                }
            }
            if (_ui.value.projection == null) {
                c.local.loadProjection()?.let { p -> _ui.update { it.copy(projection = p) } }
            }

            c.sync.flush()

            try {
                val fresh = c.api.month(year, month)
                c.local.saveMonth(fresh)
                c.network.confirmOnline()
                val projection = try {
                    c.api.projection().also { c.local.saveProjection(it) }
                } catch (_: Exception) {
                    null
                }
                if (_ui.value.year == year && _ui.value.month == month) {
                    _ui.update {
                        it.copy(view = fresh, projection = projection ?: it.projection, loading = false, offline = false)
                    }
                }
            } catch (e: ApiException) {
                if (e.status == 401) c.session.onUnauthorized()
                _ui.update { it.copy(loading = false, error = e.message) }
            } catch (_: IOException) {
                _ui.update { it.copy(loading = false, offline = true) }
            }
        }
    }

    // ------------------------------------------------------------ changes

    fun record(entry: MonthEntry, amountCents: Long, paidOn: String) {
        enqueue(
            PendingWrite(
                id = UUID.randomUUID().toString(),
                type = PendingWrite.RECORD,
                year = entry.year,
                month = entry.month,
                lineItemId = entry.lineItemId,
                amountCents = amountCents,
                paidOn = paidOn,
                enteredAt = Instant.now().toString(),
                label = "${entry.name} · ${Money.format(amountCents)}",
            ),
        )
    }

    fun clear(entry: MonthEntry) {
        enqueue(
            PendingWrite(
                id = UUID.randomUUID().toString(),
                type = PendingWrite.CLEAR,
                year = entry.year,
                month = entry.month,
                lineItemId = entry.lineItemId,
                enteredAt = Instant.now().toString(),
                label = "${entry.name} · marked unpaid",
            ),
        )
    }

    fun setBalance(amountCents: Long) {
        enqueue(
            PendingWrite(
                id = UUID.randomUUID().toString(),
                type = PendingWrite.BALANCE,
                year = _ui.value.year,
                month = _ui.value.month,
                amountCents = amountCents,
                enteredAt = Instant.now().toString(),
                label = "Bills balance · ${Money.format(amountCents)}",
            ),
        )
    }

    fun dismissProblems() {
        viewModelScope.launch { c.local.clearProblems() }
    }

    /** Every change goes through the queue; online, it leaves at once. */
    private fun enqueue(write: PendingWrite) {
        viewModelScope.launch {
            c.local.enqueue(write)
            if (c.sync.flush()) load()
        }
    }

    /** The date the confirm sheet starts on: when it was paid, else today in this month, else the due date. */
    fun defaultPaidOn(entry: MonthEntry): String {
        entry.paidOn?.let { return it.take(10) }
        val today = LocalDate.now()
        val ym = YearMonth.of(entry.year, entry.month)
        if (entry.carriedFrom != null || YearMonth.from(today) == ym) return today.toString()
        val due = entry.dueDay?.let { ym.atDay(it.coerceIn(1, ym.lengthOfMonth())) }
        return (due ?: ym.atDay(1)).toString()
    }
}
