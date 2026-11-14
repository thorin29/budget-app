package com.budget.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.budget.app.data.remote.MonthView
import com.budget.app.data.remote.Projection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * What the phone keeps between launches: the queue of unsent changes, the list
 * of refused ones, and the last copy of each month and of the projection so the
 * app opens offline. All of it can be fetched again; none of it is secret
 * beyond what the server shows the signed-in person, and it is cleared on
 * sign-out.
 */
class LocalStore(private val store: DataStore<Preferences>, private val json: Json) {
    private val queueKey = stringPreferencesKey("write_queue")
    private val problemsKey = stringPreferencesKey("sync_problems")
    private val projectionKey = stringPreferencesKey("cache_projection")
    private val queueSerializer = ListSerializer(PendingWrite.serializer())
    private val problemSerializer = ListSerializer(SyncProblem.serializer())

    private fun monthKey(year: Int, month: Int) = stringPreferencesKey("cache_month_${year}_$month")

    private fun decodeQueue(raw: String?): List<PendingWrite> =
        raw?.let { runCatching { json.decodeFromString(queueSerializer, it) }.getOrNull() } ?: emptyList()

    private fun decodeProblems(raw: String?): List<SyncProblem> =
        raw?.let { runCatching { json.decodeFromString(problemSerializer, it) }.getOrNull() } ?: emptyList()

    // ------------------------------------------------------------ queue

    val queue: Flow<List<PendingWrite>> = store.data.map { decodeQueue(it[queueKey]) }
    val problems: Flow<List<SyncProblem>> = store.data.map { decodeProblems(it[problemsKey]) }

    suspend fun queueSnapshot(): List<PendingWrite> = queue.first()

    suspend fun enqueue(write: PendingWrite) {
        store.edit { prefs ->
            // Bounded, so a long outage can't grow it forever; newest kept.
            val next = (decodeQueue(prefs[queueKey]) + write).takeLast(MAX_QUEUE)
            prefs[queueKey] = json.encodeToString(queueSerializer, next)
        }
    }

    suspend fun dequeue(id: String) {
        store.edit { prefs ->
            prefs[queueKey] = json.encodeToString(queueSerializer, decodeQueue(prefs[queueKey]).filterNot { it.id == id })
        }
    }

    suspend fun addProblem(problem: SyncProblem) {
        store.edit { prefs ->
            val next = (decodeProblems(prefs[problemsKey]) + problem).takeLast(50)
            prefs[problemsKey] = json.encodeToString(problemSerializer, next)
        }
    }

    suspend fun clearProblems() {
        store.edit { it.remove(problemsKey) }
    }

    // ------------------------------------------------------------ cache

    suspend fun saveMonth(view: MonthView) {
        store.edit { it[monthKey(view.year, view.month)] = json.encodeToString(MonthView.serializer(), view) }
    }

    suspend fun loadMonth(year: Int, month: Int): MonthView? =
        store.data.first()[monthKey(year, month)]?.let {
            runCatching { json.decodeFromString(MonthView.serializer(), it) }.getOrNull()
        }

    suspend fun saveProjection(p: Projection) {
        store.edit { it[projectionKey] = json.encodeToString(Projection.serializer(), p) }
    }

    suspend fun loadProjection(): Projection? =
        store.data.first()[projectionKey]?.let {
            runCatching { json.decodeFromString(Projection.serializer(), it) }.getOrNull()
        }

    /** Sign-out or a different server: nothing from one sign-in survives into the next. */
    suspend fun clearAll() {
        store.edit { prefs ->
            val names = prefs.asMap().keys.map { it.name }.filter {
                it == queueKey.name || it == problemsKey.name || it.startsWith("cache_")
            }
            // All of these are string keys, so rebuild them typed.
            names.forEach { prefs.remove(stringPreferencesKey(it)) }
        }
    }

    private companion object {
        const val MAX_QUEUE = 200
    }
}
