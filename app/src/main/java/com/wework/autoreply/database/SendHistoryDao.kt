package com.wework.autoreply.database

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 发送历史的DAO接口
 */
@Dao
interface SendHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SendHistory): Long
    
    @Query("SELECT * FROM send_history ORDER BY createdAt DESC LIMIT 50")
    fun getRecentHistory(): LiveData<List<SendHistory>>
    
    @Query("SELECT * FROM send_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): SendHistory?
    
    @Update
    suspend fun updateHistory(history: SendHistory)
    
    @Delete
    suspend fun deleteHistory(history: SendHistory)
    
    @Query("DELETE FROM send_history WHERE createdAt < :timestamp")
    suspend fun deleteOldHistory(timestamp: Long)
}

