package com.budget.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budget.app.AppContainer
import com.budget.app.data.remote.ApiException
import com.budget.app.data.remote.AutoRecordedDto
import com.budget.app.data.remote.CardsResponse
import com.budget.app.data.remote.CategoryDetailResponse
import com.budget.app.data.remote.EnvelopesResponse
import com.budget.app.data.remote.InsightsResponse
import com.budget.app.data.remote.LooksPaidDto
import com.budget.app.data.remote.RepeatDto
import com.budget.app.data.remote.ReviewResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

data class ExtrasUi(
    val cards: CardsResponse? = null,
    val review: ReviewResponse? = null,
    val insights: InsightsResponse? = null,
    /** Null on a server older than 0.40. */
    val envelopes: EnvelopesResponse? = null,
    /** Insights shows the envelopes for the calendar month instead of the pay period. */
    val envelopesByMonth: Boolean = false,
    /** The month Insights shows, YYYY-MM; null for the month in progress. */
    val insightsMonth: String? = null,
    val category: CategoryDetailResponse? = null,
    val busy: Boolean = false,
    /** Reading the bank now, from Today's balance. */
    val bankSyncing: Boolean = false,
    /** What the last "read the bank now" found. */
    val bankNote: String? = null,
    /** A short note after an action ("Recorded.") or why it failed. */
    val message: String? = null,
)

/**
 * The bank-feed parts of Today: cards, the Review list and the spending
 * summary. Online only; on an older server or offline they simply don't show,
 * and the last copy stays on screen.
 */
class ExtrasViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ExtrasUi())
    val ui: StateFlow<ExtrasUi> = _ui

    fun load() {
        viewModelScope.launch {
            val cards = async { runCatching { c.api.cards() }.getOrNull() }
            val review = async { runCatching { c.api.review() }.getOrNull() }
            val insights = async { runCatching { c.api.insights(_ui.value.insightsMonth) }.getOrNull() }
            val envelopes = async { runCatching { c.api.envelopes(_ui.value.envelopesByMonth) }.getOrNull() }
            val (cr, rr, ir) = Triple(cards.await(), review.await(), insights.await())
            val er = envelopes.await()
            _ui.update { it.copy(cards = cr ?: it.cards, review = rr ?: it.review, insights = ir ?: it.insights, envelopes = er ?: it.envelopes) }
        }
    }

    /** The envelopes by pay period, or by calendar month. */
    fun showEnvelopesByMonth(month: Boolean) {
        _ui.update { it.copy(envelopesByMonth = month) }
        viewModelScope.launch {
            runCatching { c.api.envelopes(month) }.getOrNull()?.let { r -> _ui.update { it.copy(envelopes = r) } }
        }
    }

    /** Insights for another month (YYYY-MM), or the month in progress for null. */
    fun showInsightsMonth(month: String?) {
        _ui.update { it.copy(insightsMonth = month) }
        viewModelScope.launch {
            runCatching { c.api.insights(month) }.getOrNull()?.let { r -> _ui.update { it.copy(insights = r) } }
        }
    }

    fun openCategory(id: String?) {
        _ui.update { it.copy(category = null) }
        if (id == null) return
        viewModelScope.launch {
            runCatching { c.api.categoryDetail(id, _ui.value.insightsMonth) }.getOrNull()?.let { r -> _ui.update { it.copy(category = r) } }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    /** The confirm step: records the payment with the amount and date accepted. */
    fun confirm(item: LooksPaidDto, amountCents: Long, paidOn: String, onDone: () -> Unit = {}) =
        act("Recorded.", onDone) { c.api.confirmReview(item, amountCents, paidOn, UUID.randomUUID().toString()) }

    fun dismiss(item: LooksPaidDto) = act("Won't suggest that charge for ${item.billName} again.") { c.api.dismissReview(item) }

    fun addRepeat(item: RepeatDto) = act("Added ${item.merchant} as a monthly bill. Edit it on Plan if anything's off.") { c.api.repeat(item.key, "add") }

    fun dismissRepeat(item: RepeatDto) = act("Won't suggest ${item.merchant} as a bill again.") { c.api.repeat(item.key, "dismiss") }

    /** Reads the bank now; [onDone] reloads the month so the new balance shows. */
    fun syncBank(onDone: () -> Unit) {
        if (_ui.value.bankSyncing) return
        viewModelScope.launch {
            _ui.update { it.copy(bankSyncing = true, bankNote = null) }
            val note = try {
                val r = c.api.syncBank()
                when {
                    r.lastError != null -> r.lastError
                    !r.ran -> "The bank was read a few minutes ago. Nothing newer yet."
                    r.added > 0 -> "${r.added} new from the bank."
                    else -> "Up to date. Nothing new from the bank yet."
                }
            } catch (e: ApiException) {
                e.message ?: "The server refused that."
            } catch (e: IOException) {
                "You're offline. Try again when you're back."
            }
            _ui.update { it.copy(bankSyncing = false, bankNote = note) }
            onDone()
            load()
        }
    }

    fun undo(item: AutoRecordedDto) = act("Removed. That charge won't be used for ${item.name} again.") { c.api.undoAuto(item) }

    private fun act(success: String, onDone: () -> Unit = {}, call: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            val note = try {
                call()
                onDone()
                success
            } catch (e: ApiException) {
                e.message ?: "The server refused that."
            } catch (e: IOException) {
                "You're offline. Try again when you're back."
            }
            _ui.update { it.copy(busy = false, message = note) }
            load()
        }
    }
}
