package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanEntity::class,
        HostEntity::class,
        PortEntity::class,
        ScriptResultEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NetDatabase : RoomDatabase() {
    abstract fun netDao(): NetDao

    companion object {
        @Volatile
        private var INSTANCE: NetDatabase? = null

        fun getDatabase(context: Context): NetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetDatabase::class.java,
                    "net_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
