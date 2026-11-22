package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "active_run")
data class ActiveRunEntity(
  @PrimaryKey val id: Int = 1,
  val seedLabel: String,
  val heroClassName: String,
  val act: Int,
  val hp: Int,
  val maxHp: Int,
  val flasks: Int,
  val payloadJson: String,
  val updatedAtMillis: Long,
)

@Entity(tableName = "shade_deck")
data class ShadeDeckEntity(
  @PrimaryKey val id: Int = 1,
  val heroClassName: String,
  val seedLabel: String,
  val cardIdsCsv: String,
  val recordedAtMillis: Long,
)

@Entity(tableName = "run_history")
data class RunHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val seedLabel: String,
  val heroClassName: String,
  val actReached: Int,
  val victory: Boolean,
  val dreadEndingAchieved: Boolean = false,
  val terminationReason: String,
  val enemiesSlain: Int,
  val turnsTaken: Int,
  val highestSpectrumHalfSteps: Int,
  val finishedAtMillis: Long,
)

@Dao
interface EmberfallDao {
  @Query("SELECT * FROM active_run WHERE id = 1")
  fun observeActiveRun(): Flow<ActiveRunEntity?>

  @Query("SELECT * FROM active_run WHERE id = 1")
  suspend fun getActiveRun(): ActiveRunEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveActiveRun(entity: ActiveRunEntity)

  @Query("DELETE FROM active_run")
  suspend fun clearActiveRun()

  @Query("SELECT * FROM shade_deck WHERE id = 1")
  suspend fun getShadeDeck(): ShadeDeckEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveShadeDeck(entity: ShadeDeckEntity)

  @Query("SELECT * FROM run_history ORDER BY finishedAtMillis DESC LIMIT 50")
  fun observeRunHistory(): Flow<List<RunHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRunHistory(entry: RunHistoryEntity)

  @Query(
    """
    SELECT COUNT(*) FROM run_history
    WHERE seedLabel = :seedLabel
      AND heroClassName = :heroClassName
      AND actReached = :actReached
      AND turnsTaken = :turnsTaken
      AND enemiesSlain = :enemiesSlain
      AND terminationReason = :reason
    """
  )
  suspend fun countMatchingHistory(
    seedLabel: String,
    heroClassName: String,
    actReached: Int,
    turnsTaken: Int,
    enemiesSlain: Int,
    reason: String,
  ): Int

  @Transaction
  suspend fun finishRunAtomic(history: RunHistoryEntity, shadeDeck: ShadeDeckEntity?) {
    val hasActive = getActiveRun() != null
    val alreadyRecorded = countMatchingHistory(
      seedLabel = history.seedLabel,
      heroClassName = history.heroClassName,
      actReached = history.actReached,
      turnsTaken = history.turnsTaken,
      enemiesSlain = history.enemiesSlain,
      reason = history.terminationReason,
    ) > 0
    if (hasActive || !alreadyRecorded) {
      insertRunHistory(history)
    }
    if (shadeDeck != null) {
      saveShadeDeck(shadeDeck)
    }
    clearActiveRun()
  }
}

@Database(
  entities = [ActiveRunEntity::class, ShadeDeckEntity::class, RunHistoryEntity::class],
  version = 4,
  exportSchema = false,
)
abstract class EmberfallDatabase : RoomDatabase() {
  abstract fun emberfallDao(): EmberfallDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS shade_deck (
            id INTEGER NOT NULL PRIMARY KEY,
            heroClassName TEXT NOT NULL,
            seedLabel TEXT NOT NULL,
            cardIdsCsv TEXT NOT NULL,
            recordedAtMillis INTEGER NOT NULL
          )
          """.trimIndent()
        )
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Snapshot JSON bumped to v2; Room schema columns unchanged
      }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE run_history ADD COLUMN dreadEndingAchieved INTEGER NOT NULL DEFAULT 0"
        )
      }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    @Volatile
    private var INSTANCE: EmberfallDatabase? = null

    fun getInstance(context: Context): EmberfallDatabase =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          EmberfallDatabase::class.java,
          "emberfall.db",
        )
          .addMigrations(*ALL_MIGRATIONS)
          .build()
          .also { INSTANCE = it }
      }
  }
}
