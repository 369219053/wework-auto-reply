package com.wework.autoreply.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 消息组表
 * 用于批量发送时从素材库聊天转发消息
 *
 * 使用场景:
 * - 在素材库聊天中准备好消息(文字、图片、视频、卡片等)
 * - 设置要转发的消息数量
 * - 批量转发到多个群聊
 */
@Entity(tableName = "message_groups")
data class MessageGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,              // 消息组名称,如"产品宣传套餐"、"活动通知"
    val description: String = "",  // 消息组描述
    val messageCount: Int = 1,     // 要转发的消息数量(从素材库聊天的最新消息开始计数)
    val delayMin: Int = 0,         // 随机延迟最小值(毫秒),0表示不延迟
    val delayMax: Int = 0,         // 随机延迟最大值(毫秒),0表示不延迟
    val createdAt: Long,
    val updatedAt: Long
)

