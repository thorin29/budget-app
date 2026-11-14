package com.budget.app.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The phone's database: the queue of unsent changes, the refused ones, and the
 * last copy of each screen.
 *
 * Unlike the Kairos cache, this is NOT disposable: [PendingWriteEntity] holds
 * changes entered offline that exist nowhere else. So there is no destructive
 * fallback. Changing an entity means bumping [version] and adding a migration
 * (an AutoMigration where Room can work it out), using the schema JSON that
 * the build writes to app/schemas. A missing migration fails loudly in testing
 * instead of quietly deleting someone's unsent payments.
 */
@Database(
    entities = [PendingWriteEntity::class, SyncProblemEntity::class, CachedViewEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BudgetDatabase : RoomDatabase() {

    abstract fun localDao(): LocalDao

    companion object {
        private const val DB_NAME = "budget.db"

        fun build(context: Context): BudgetDatabase {
            val appContext = context.applicationContext
            return Room.databaseBuilder<BudgetDatabase>(
                context = appContext,
                name = appContext.getDatabasePath(DB_NAME).absolutePath,
            )
                // One SQLite build on every phone rather than the OS copy.
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}
