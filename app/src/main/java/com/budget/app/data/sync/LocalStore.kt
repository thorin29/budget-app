package com.budget.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.budget.app.data.local.CachedViewEntity
import com.budget.app.data.local.LocalDao
import com.budget.app.data.local.PendingWriteEntity
import com.budget.app.data.local.SyncProblemEntity
import com.budget.app.data.remote.MonthView
import com.budget.app.data.remote.Projection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * What the phone keeps between launches, in the Room database: the queue of
 * unsent changes, the list of refused ones, and the last copy of each month
 * and of the projection so the app opens offline. Cleared on sign-out.
 *
 * 0.1.x kept these in DataStore. The first time anything here is used, a
 * waiting queue and refused list are moved across and the old keys removed;
 * the old cached screens are just dropped, since they are fetched again.
 */
class LocalStore(
    private val dao: LocalDao,
    private val legacy: DataStore<Preferences>,
    private val json: Json,
) {
    // ------------------------------------------------------------ queue

    val queue: Flow<List<PendingWrite>> =
        dao.observeQueue().map { rows -> rows.map { it.toModel() } }.onStart { ready() }

    val problems: Flow<List<SyncProblem>> =
        dao.observeProblems().map { rows -> rows.map { it.toModel() } }.onStart { ready() }

    suspend fun queueSnapshot(): List<PendingWrite> {
        ready()
        return dao.queue().map { it.toModel() }
    }

    suspend fun enqueue(write: PendingWrite) {
        ready()
        if (dao.queuedCount(write.id) == 0) dao.insertWrite(write.toEntity())
        dao.trimQueue(MAX_QUEUE)
    }

    suspend fun dequeue(id: String) {
        ready()
        dao.deleteWrite(id)
    }

    suspend fun addProblem(problem: SyncProblem) {
        ready()
        dao.insertProblem(problem.toEntity())
        dao.trimProblems(MAX_PROBLEMS)
    }

    suspend fun clearProblems() {
        ready()
        dao.clearProblems()
    }

    // ------------------------------------------------------------ cache

    private fun monthKey(year: Int, month: Int) = "month:$year-${month.toString().padStart(2, '0')}"

    suspend fun saveMonth(view: MonthView) {
        dao.putView(CachedViewEntity(monthKey(view.year, view.month), json.encodeToString(MonthView.serializer(), view), System.currentTimeMillis()))
        dao.trimMonths(MAX_MONTHS)
    }

    suspend fun loadMonth(year: Int, month: Int): MonthView? =
        dao.view(monthKey(year, month))?.let {
            runCatching { json.decodeFromString(MonthView.serializer(), it.json) }.getOrNull()
        }

    suspend fun saveProjection(p: Projection) {
        dao.putView(CachedViewEntity(PROJECTION_KEY, json.encodeToString(Projection.serializer(), p), System.currentTimeMillis()))
    }

    suspend fun loadProjection(): Projection? =
        dao.view(PROJECTION_KEY)?.let {
            runCatching { json.decodeFromString(Projection.serializer(), it.json) }.getOrNull()
        }

    /** Sign-out or a different server: nothing from one sign-in survives into the next. */
    suspend fun clearAll() {
        ready()
        dao.clearQueue()
        dao.clearProblems()
        dao.clearViews()
    }

    // ------------------------------------------------------------ 0.1.x import

    private val importLock = Mutex()

    @Volatile
    private var imported = false

    private suspend fun ready() {
        if (imported) return
        importLock.withLock {
            if (imported) return
            // Never let a bad old copy stop the app; the worst case is the old
            // queue isn't carried over.
            runCatching { importLegacy() }
            imported = true
        }
    }

    private suspend fun importLegacy() {
        legacy.edit { prefs ->
            val queueRaw = prefs[LEGACY_QUEUE]
            val problemsRaw = prefs[LEGACY_PROBLEMS]
            val old = prefs.asMap().keys.map { it.name }.filter {
                it == LEGACY_QUEUE.name || it == LEGACY_PROBLEMS.name || it.startsWith("cache_")
            }
            if (old.isEmpty()) return@edit
            // Rows go in first; the keys are removed only if that worked.
            queueRaw?.let { raw ->
                json.decodeFromString(ListSerializer(PendingWrite.serializer()), raw).forEach { w ->
                    if (dao.queuedCount(w.id) == 0) dao.insertWrite(w.toEntity())
                }
            }
            problemsRaw?.let { raw ->
                json.decodeFromString(ListSerializer(SyncProblem.serializer()), raw).forEach { dao.insertProblem(it.toEntity()) }
            }
            old.forEach { prefs.remove(stringPreferencesKey(it)) }
        }
    }

    private companion object {
        const val MAX_QUEUE = 200
        const val MAX_PROBLEMS = 50
        const val MAX_MONTHS = 24
        const val PROJECTION_KEY = "projection"
        val LEGACY_QUEUE = stringPreferencesKey("write_queue")
        val LEGACY_PROBLEMS = stringPreferencesKey("sync_problems")
    }
}

private fun PendingWriteEntity.toModel() = PendingWrite(
    id = id, type = type, year = year, month = month, lineItemId = lineItemId,
    amountCents = amountCents, paidOn = paidOn, enteredAt = enteredAt, label = label,
)

private fun PendingWrite.toEntity() = PendingWriteEntity(
    id = id, type = type, year = year, month = month, lineItemId = lineItemId,
    amountCents = amountCents, paidOn = paidOn, enteredAt = enteredAt, label = label,
)

private fun SyncProblemEntity.toModel() = SyncProblem(id = id, label = label, message = message, at = at)

private fun SyncProblem.toEntity() = SyncProblemEntity(id = id, label = label, message = message, at = at)
