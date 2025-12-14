package com.wework.autoreply

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务 - 自动化UI操作
 */
class AutoReplyAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AutoReplyAccessibility"
        private const val WEWORK_PACKAGE = "com.tencent.wework"
    }
    
    private lateinit var configManager: ConfigManager
    private val handler = Handler(Looper.getMainLooper())
    private var pendingReplyMessage: String? = null
    
    private val autoReplyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_AUTO_REPLY") {
                pendingReplyMessage = intent.getStringExtra("message")
                Log.d(TAG, "收到自动回复请求: $pendingReplyMessage")
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager(this)
        
        // 注册广播接收器
        val filter = IntentFilter("ACTION_AUTO_REPLY")
        registerReceiver(autoReplyReceiver, filter, RECEIVER_NOT_EXPORTED)
        
        Log.d(TAG, "无障碍服务已启动")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 只处理企业微信的事件
        if (event.packageName != WEWORK_PACKAGE) return
        
        // 如果有待发送的消息,尝试发送
        if (pendingReplyMessage != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.postDelayed({
                sendMessage(pendingReplyMessage!!)
                pendingReplyMessage = null
            }, 2000) // 等待2秒让界面加载完成
        }
    }
    
    /**
     * 发送消息
     */
    private fun sendMessage(message: String) {
        try {
            val rootNode = rootInActiveWindow ?: run {
                Log.e(TAG, "无法获取根节点")
                return
            }
            
            // 查找输入框
            val inputNode = findInputField(rootNode)
            if (inputNode == null) {
                Log.e(TAG, "未找到输入框")
                rootNode.recycle()
                return
            }
            
            // 输入文本
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            Log.d(TAG, "已输入文本: $message")
            
            // 等待一下再发送
            handler.postDelayed({
                // 查找发送按钮
                val sendButton = findSendButton(rootNode)
                if (sendButton != null) {
                    sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "已点击发送按钮")
                } else {
                    Log.e(TAG, "未找到发送按钮")
                }
                
                // 返回主界面
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Log.d(TAG, "已返回主界面")
                }, 500)
                
                rootNode.recycle()
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败", e)
        }
    }
    
    /**
     * 查找输入框
     */
    private fun findInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 尝试通过className查找
        val editTextNodes = node.findAccessibilityNodeInfosByViewId("com.tencent.wework:id/input")
        if (editTextNodes.isNotEmpty()) {
            return editTextNodes[0]
        }
        
        // 递归查找EditText
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.className == "android.widget.EditText") {
                return child
            }
            val result = findInputField(child)
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * 查找发送按钮
     */
    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 尝试通过文本查找
        val sendNodes = node.findAccessibilityNodeInfosByText("发送")
        if (sendNodes.isNotEmpty()) {
            return sendNodes[0]
        }
        
        return null
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(autoReplyReceiver)
        Log.d(TAG, "无障碍服务已停止")
    }
}

