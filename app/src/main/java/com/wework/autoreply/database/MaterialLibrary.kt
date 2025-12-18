package com.wework.autoreply.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 素材库配置表
 * 存储用户设置的素材库聊天信息
 */
@Entity(tableName = "material_library")
data class MaterialLibrary(
    @PrimaryKey
    val id: Int = 1,
    val chatName: String,          // 素材库聊天名称,如"张三"
    val lastSyncTime: Long,        // 最后同步时间戳
    val autoSync: Boolean = false, // 是否启用自动同步
    val syncInterval: String = "daily" // 同步频率: hourly/daily/manual
)

