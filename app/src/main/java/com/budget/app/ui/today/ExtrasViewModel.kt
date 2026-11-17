package com.budget.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budget.app.AppContainer
import com.budget.app.data.remote.ApiException
import com.budget.app.data.remote.AutoRecordedDto
import com.budget.app.data.remote.CardsResponse
import com.budget.app.data.remote.InsightsResponse
import com.budget.app.data.remote.LooksPaidDto
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
    val busy: Boolean = false,
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
            val insights = async { runCatching { c.api.insights() }.getOrNull() }
            val (cr, rr, ir) = Triple(cards.await(), review.await(), insights.await())
            _ui.update { it.copy(cards = cr ?: it.cards, review = rr ?: it.review, insights = ir ?: it.insights) }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    /** The confirm step: records the payment with the amount and date accepted. */
    fun confirm(item: LooksPaidDto, amountCents: Long, paidOn: String, onDone: () -> Unit = {}) =
        act("Recorded.", onDone) { c.api.confirmReview(item, amountCents, paidOn, UUID.randomUUID().toString()) }

    fun dismiss(item: LooksPaidDto) = act("Won't suggest that charge for ${item.billName} again.") { c.api.dismissReview(item) }

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
