package com.wework.autoreply.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用设置表
 * 存储应用的全局配置
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val sendInterval: Long = 3000,      // 发送间隔(毫秒),默认3秒
    val materialSourceChat: String = "" // 素材库聊天名称(全局设置)
)

