package com.budget.app.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A change waiting to reach the server. This table is the only copy of what
 * was entered offline, so it is never dropped by a schema change: a new
 * version needs a real migration (see BudgetDatabase).
 *
 * [seq] keeps the order changes were made in; [id] is the Idempotency-Key.
 */
@Entity(tableName = "pending_write", indices = [Index(value = ["id"], unique = true)])
data class PendingWriteEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val id: String,
    val type: String,
    val year: Int,
    val month: Int,
    val lineItemId: String?,
    val amountCents: Long?,
    val paidOn: String?,
    val enteredAt: String,
    val label: String,
)

/** A queued change the server refused, shown under "Not saved" until dismissed. */
@Entity(tableName = "sync_problem")
data class SyncProblemEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val id: String,
    val label: String,
    val message: String,
    val at: String,
)

/**
 * The last copy of a screen's data, so the app opens offline. Kept as the
 * server's JSON: it is re-fetched on every refresh, and the month's totals
 * follow the server's planning rules, which the phone doesn't repeat.
 *
 * [key] is "month:YYYY-MM" or "projection".
 */
@Entity(tableName = "cached_view")
data class CachedViewEntity(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
)
