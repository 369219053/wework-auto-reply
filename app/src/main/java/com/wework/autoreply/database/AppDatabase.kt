package com.wework.autoreply.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 应用数据库
 * 功能一和功能二使用的Room数据库
 */
@Database(
    entities = [
        MaterialLibrary::class,
        Material::class,
        GroupConfig::class,
        GroupChat::class,
        MessageGroup::class,
        SendHistory::class,
        AppSettings::class
    ],
    version = 3,  // 版本升级到3,删除MessageTemplate表,修改MessageGroup和AppSettings表
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun materialDao(): MaterialDao
    abstract fun groupConfigDao(): GroupConfigDao
    abstract fun messageGroupDao(): MessageGroupDao
    abstract fun sendHistoryDao(): SendHistoryDao
    abstract fun appSettingsDao(): AppSettingsDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wework_batch_send_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

