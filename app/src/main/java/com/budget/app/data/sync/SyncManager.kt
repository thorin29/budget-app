package com.budget.app.data.sync

import com.budget.app.data.Session
import com.budget.app.data.remote.ApiClient
import com.budget.app.data.remote.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant

/**
 * Sends queued changes, oldest first, one at a time. Stops at the first one
 * that can't be sent now (offline, server trouble) so order is kept; drops and
 * reports one the server refused (changed elsewhere, invalid).
 */
class SyncManager(
    private val api: ApiClient,
    private val local: LocalStore,
    private val session: Session,
) {
    private val mutex = Mutex()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** True if the queue is empty afterwards. */
    suspend fun flush(): Boolean = mutex.withLock {
        _syncing.value = true
        try {
            for (write in local.queueSnapshot()) {
                val (outcome, message) = try {
                    send(write)
                    SyncRules.classify(200) to null
                } catch (e: ApiException) {
                    SyncRules.classify(e.status) to e.message
                } catch (_: IOException) {
                    SyncRules.classify(null) to null
                }
                when (outcome) {
                    SendOutcome.DONE -> local.dequeue(write.id)
                    SendOutcome.REFUSED -> {
                        local.dequeue(write.id)
                        local.addProblem(
                            SyncProblem(
                                id = write.id,
                                label = write.label,
                                message = message ?: "The server didn't accept this change.",
                                at = Instant.now().toString(),
                            ),
                        )
                    }
                    SendOutcome.SIGNED_OUT -> {
                        session.onUnauthorized()
                        return@withLock false
                    }
                    SendOutcome.RETRY_LATER -> return@withLock false
                }
            }
            true
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun send(w: PendingWrite) {
        when (w.type) {
            PendingWrite.RECORD -> api.recordActual(
                year = w.year,
                month = w.month,
                lineItemId = requireNotNull(w.lineItemId),
                amountCents = requireNotNull(w.amountCents),
                paidOn = w.paidOn,
                enteredAt = w.enteredAt,
                idempotencyKey = w.id,
            )
            PendingWrite.CLEAR -> api.clearActual(w.year, w.month, requireNotNull(w.lineItemId), w.enteredAt, w.id)
            PendingWrite.BALANCE -> api.setBalance(w.year, w.month, requireNotNull(w.amountCents), w.enteredAt, w.id)
        }
    }
}
