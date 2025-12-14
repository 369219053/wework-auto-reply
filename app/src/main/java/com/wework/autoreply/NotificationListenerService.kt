package com.wework.autoreply

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 通知监听服务 - 监听企业微信的通知
 */
class NotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "WeworkNotificationListener"
        private const val WEWORK_PACKAGE = "com.tencent.wework"
        
        // 好友申请关键词
        private val FRIEND_REQUEST_KEYWORDS = listOf(
            "新的朋友",
            "添加了你",
            "通过了你的好友申请",
            "申请添加你为联系人"
        )
    }
    
    private lateinit var configManager: ConfigManager
    
    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager(this)
        Log.d(TAG, "通知监听服务已启动")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        if (sbn == null) return
        
        // 只处理企业微信的通知
        if (sbn.packageName != WEWORK_PACKAGE) return
        
        // 检查是否启用自动回复
        if (!configManager.isEnabled()) {
            Log.d(TAG, "自动回复已禁用")
            return
        }
        
        // 获取通知内容
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        Log.d(TAG, "收到通知 - 标题: $title, 内容: $text")
        
        // 检查是否是好友申请通知
        val isFriendRequest = FRIEND_REQUEST_KEYWORDS.any { keyword ->
            title.contains(keyword) || text.contains(keyword)
        }
        
        if (isFriendRequest) {
            Log.d(TAG, "检测到好友申请通知,准备自动回复")
            handleFriendRequest(sbn)
        }
    }
    
    /**
     * 处理好友申请
     */
    private fun handleFriendRequest(sbn: StatusBarNotification) {
        try {
            // 点击通知打开聊天窗口
            val pendingIntent = sbn.notification.contentIntent
            pendingIntent?.send()
            
            Log.d(TAG, "已点击通知,等待无障碍服务处理...")
            
            // 通知无障碍服务开始自动回复
            val intent = Intent(this, AutoReplyAccessibilityService::class.java)
            intent.action = "ACTION_AUTO_REPLY"
            intent.putExtra("message", configManager.getFullReplyMessage())
            sendBroadcast(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "处理好友申请失败", e)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}

