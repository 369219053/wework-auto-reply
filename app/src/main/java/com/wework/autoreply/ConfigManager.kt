package com.wework.autoreply

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理器 - 管理应用配置信息
 */
class ConfigManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "wework_auto_reply_config", 
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_GROUP_CHAT_LINK = "group_chat_link"
        private const val KEY_WELCOME_MESSAGE = "welcome_message"
        private const val KEY_ENABLED = "enabled"
        private const val DEFAULT_WELCOME_MESSAGE = "您好!欢迎添加,请点击链接加入我们的群聊:"
    }
    
    /**
     * 获取群聊链接
     */
    fun getGroupChatLink(): String {
        return prefs.getString(KEY_GROUP_CHAT_LINK, "") ?: ""
    }
    
    /**
     * 设置群聊链接
     */
    fun setGroupChatLink(link: String) {
        prefs.edit().putString(KEY_GROUP_CHAT_LINK, link).apply()
    }
    
    /**
     * 获取欢迎语
     */
    fun getWelcomeMessage(): String {
        return prefs.getString(KEY_WELCOME_MESSAGE, DEFAULT_WELCOME_MESSAGE) ?: DEFAULT_WELCOME_MESSAGE
    }
    
    /**
     * 设置欢迎语
     */
    fun setWelcomeMessage(message: String) {
        prefs.edit().putString(KEY_WELCOME_MESSAGE, message).apply()
    }
    
    /**
     * 获取完整的回复消息(欢迎语 + 群聊链接)
     */
    fun getFullReplyMessage(): String {
        val message = getWelcomeMessage()
        val link = getGroupChatLink()
        return if (link.isNotEmpty()) {
            "$message $link"
        } else {
            message
        }
    }
    
    /**
     * 是否启用自动回复
     */
    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, true)
    }
    
    /**
     * 设置是否启用自动回复
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

