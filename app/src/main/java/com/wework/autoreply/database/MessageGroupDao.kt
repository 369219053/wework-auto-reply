package com.wework.autoreply.database

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 消息组DAO
 * 提供消息组的数据库操作
 */
@Dao
interface MessageGroupDao {
    
    // ========== 查询操作 ==========
    
    @Query("SELECT * FROM message_groups ORDER BY updatedAt DESC")
    fun getAllGroups(): LiveData<List<MessageGroup>>
    
    @Query("SELECT * FROM message_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): MessageGroup?
    
    @Query("SELECT * FROM message_groups ORDER BY updatedAt DESC")
    suspend fun getAllGroupsSync(): List<MessageGroup>
    
    // ========== 插入操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: MessageGroup): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<MessageGroup>)
    
    // ========== 更新操作 ==========
    
    @Update
    suspend fun updateGroup(group: MessageGroup)
    
    // ========== 删除操作 ==========
    
    @Delete
    suspend fun deleteGroup(group: MessageGroup)
    
    @Query("DELETE FROM message_groups")
    suspend fun deleteAllGroups()
    
    // ========== 统计操作 ==========
    
    @Query("SELECT COUNT(*) FROM message_groups")
    fun getGroupCount(): LiveData<Int>
}

