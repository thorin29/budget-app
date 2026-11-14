package com.budget.app.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room 3 wants suspend functions for one-off reads and writes, and Flow for
 * anything observed.
 */
@Dao
interface LocalDao {

    // ------------------------------------------------------------ queue

    @Query("SELECT * FROM pending_write ORDER BY seq")
    fun observeQueue(): Flow<List<PendingWriteEntity>>

    @Query("SELECT * FROM pending_write ORDER BY seq")
    suspend fun queue(): List<PendingWriteEntity>

    @Query("SELECT COUNT(*) FROM pending_write WHERE id = :id")
    suspend fun queuedCount(id: String): Int

    @Insert
    suspend fun insertWrite(row: PendingWriteEntity)

    @Query("DELETE FROM pending_write WHERE id = :id")
    suspend fun deleteWrite(id: String)

    /** Keeps the newest [keep], so a very long outage can't grow it forever. */
    @Query("DELETE FROM pending_write WHERE seq NOT IN (SELECT seq FROM pending_write ORDER BY seq DESC LIMIT :keep)")
    suspend fun trimQueue(keep: Int)

    @Query("DELETE FROM pending_write")
    suspend fun clearQueue()

    // ------------------------------------------------------------ refused

    @Query("SELECT * FROM sync_problem ORDER BY seq")
    fun observeProblems(): Flow<List<SyncProblemEntity>>

    @Insert
    suspend fun insertProblem(row: SyncProblemEntity)

    @Query("DELETE FROM sync_problem WHERE seq NOT IN (SELECT seq FROM sync_problem ORDER BY seq DESC LIMIT :keep)")
    suspend fun trimProblems(keep: Int)

    @Query("DELETE FROM sync_problem")
    suspend fun clearProblems()

    // ------------------------------------------------------------ cache

    @Upsert
    suspend fun putView(row: CachedViewEntity)

    @Query("SELECT * FROM cached_view WHERE `key` = :key LIMIT 1")
    suspend fun view(key: String): CachedViewEntity?

    /** Keeps the [keep] most recently saved months (the projection is one row). */
    @Query(
        "DELETE FROM cached_view WHERE `key` LIKE 'month:%' AND `key` NOT IN (" +
            "SELECT `key` FROM cached_view WHERE `key` LIKE 'month:%' ORDER BY savedAt DESC LIMIT :keep)",
    )
    suspend fun trimMonths(keep: Int)

    @Query("DELETE FROM cached_view")
    suspend fun clearViews()
}
