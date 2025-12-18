package com.wework.autoreply.database

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 应用设置的DAO接口
 */
@Dao
interface AppSettingsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)
    
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): LiveData<AppSettings?>
    
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsSync(): AppSettings?
    
    @Update
    suspend fun updateSettings(settings: AppSettings)
}

