package com.wework.autoreply.database

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 群组配置的DAO接口
 */
@Dao
interface GroupConfigDao {
    
    // ========== 大群组操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupConfig(config: GroupConfig): Long
    
    @Query("SELECT * FROM group_configs ORDER BY createdAt DESC")
    fun getAllGroupConfigs(): LiveData<List<GroupConfig>>
    
    @Query("SELECT * FROM group_configs WHERE id = :id")
    suspend fun getGroupConfigById(id: Long): GroupConfig?
    
    @Update
    suspend fun updateGroupConfig(config: GroupConfig)
    
    @Delete
    suspend fun deleteGroupConfig(config: GroupConfig)
    
    // ========== 群聊操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupChat(chat: GroupChat)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupChats(chats: List<GroupChat>)
    
    @Query("SELECT * FROM group_chats WHERE groupConfigId = :groupConfigId")
    fun getGroupChatsByConfigId(groupConfigId: Long): LiveData<List<GroupChat>>
    
    @Query("SELECT * FROM group_chats WHERE groupConfigId = :groupConfigId")
    suspend fun getGroupChatsByConfigIdSync(groupConfigId: Long): List<GroupChat>
    
    @Query("DELETE FROM group_chats WHERE groupConfigId = :groupConfigId")
    suspend fun deleteGroupChatsByConfigId(groupConfigId: Long)
    
    @Delete
    suspend fun deleteGroupChat(chat: GroupChat)
}

