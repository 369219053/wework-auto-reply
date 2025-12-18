package com.wework.autoreply.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 素材表
 * 存储从素材库聊天中同步的图片/卡片消息
 */
@Entity(tableName = "materials")
data class Material(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,              // image/card
    val position: Int,             // 消息在聊天中的位置(从最新往前数)
    val timestamp: Long,           // 消息时间戳
    val description: String?,      // 消息描述(卡片标题/图片说明)
    val thumbnailPath: String?,    // 缩略图路径(可选,后期优化)
    val syncedAt: Long             // 同步时间
)

