package com.wework.autoreply.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 大群组配置表
 * 存储用户创建的大群组信息
 */
@Entity(tableName = "group_configs")
data class GroupConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,              // 大群组名称,如"重要客户群组"
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 群聊表
 * 存储大群组包含的群聊列表
 */
@Entity(tableName = "group_chats")
data class GroupChat(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupConfigId: Long,       // 关联的大群组ID
    val chatName: String           // 群聊名称,如"智界Aigc客户群"
)

