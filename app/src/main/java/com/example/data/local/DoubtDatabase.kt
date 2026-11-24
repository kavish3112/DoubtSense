package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem

@Database(
    entities = [DoubtItem::class, ConversationSession::class],
    version = 6,
    exportSchema = false
)
abstract class DoubtDatabase : RoomDatabase() {
    abstract fun doubtDao(): DoubtDao
    abstract fun conversationSessionDao(): ConversationSessionDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversation_sessions ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE conversation_sessions ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE conversation_sessions SET cloudId = 'session_' || id || '_' || lower(hex(randomblob(6))) WHERE cloudId = ''"
                )
                db.execSQL(
                    "UPDATE conversation_sessions SET updatedAtMillis = CASE WHEN endTime > 0 THEN endTime ELSE 0 END WHERE updatedAtMillis = 0"
                )

                db.execSQL(
                    "ALTER TABLE doubts ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE doubts ADD COLUMN sessionCloudId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE doubts ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE doubts SET cloudId = 'doubt_' || id || '_' || lower(hex(randomblob(6))) WHERE cloudId = ''"
                )
                db.execSQL(
                    "UPDATE doubts SET updatedAtMillis = CASE WHEN timestamp > 0 THEN timestamp ELSE 0 END WHERE updatedAtMillis = 0"
                )
                db.execSQL(
                    "UPDATE doubts SET sessionCloudId = COALESCE((SELECT cloudId FROM conversation_sessions WHERE conversation_sessions.id = doubts.sessionId), '') WHERE sessionId > 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: DoubtDatabase? = null

        fun getInstance(context: Context): DoubtDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DoubtDatabase::class.java,
                    "doubtsense_database.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
