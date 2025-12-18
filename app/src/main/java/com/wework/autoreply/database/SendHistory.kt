package com.wework.autoreply.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 发送历史表
 * 记录批量发送的历史记录
 */
@Entity(tableName = "send_history")
data class SendHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupConfigId: Long,       // 关联的大群组ID
    val messageTemplateId: Long,   // 关联的消息模板ID
    val status: String,            // pending/running/completed/failed
    val totalChats: Int,           // 总群聊数
    val sentChats: Int = 0,        // 已发送数
    val failedChats: String?,      // 失败的群聊列表(JSON格式)
    val createdAt: Long,
    val completedAt: Long? = null
)

