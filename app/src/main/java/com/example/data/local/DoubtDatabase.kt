package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.DoubtItem

@Database(entities = [DoubtItem::class], version = 2, exportSchema = false)
abstract class DoubtDatabase : RoomDatabase() {
    abstract fun doubtDao(): DoubtDao

    companion object {
        @Volatile
        private var INSTANCE: DoubtDatabase? = null

        fun getInstance(context: Context): DoubtDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DoubtDatabase::class.java,
                    "doubtsense_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
