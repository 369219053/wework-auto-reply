package com.wework.autoreply

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理器 - 管理应用配置信息
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "wework_batch_process_config",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_GROUP_NAME = "group_name"
        private const val DEFAULT_GROUP_NAME = "智界Aigc客户群（18）"
    }

    /**
     * 获取群聊名称
     */
    fun getGroupName(): String {
        return prefs.getString(KEY_GROUP_NAME, DEFAULT_GROUP_NAME) ?: DEFAULT_GROUP_NAME
    }

    /**
     * 设置群聊名称
     */
    fun setGroupName(name: String) {
        prefs.edit().putString(KEY_GROUP_NAME, name).apply()
    }
}

