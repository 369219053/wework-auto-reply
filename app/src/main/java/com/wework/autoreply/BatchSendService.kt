package com.wework.autoreply

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 批量发送服务
 * 实现批量发送消息到多个群聊
 */
class BatchSendService : AccessibilityService() {

    companion object {
        private const val TAG = "BatchSendService"
        private const val WEWORK_PACKAGE = "com.tencent.wework"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isProcessing = false

    // 发送任务参数 - 新的转发模式
    private var materialSourceChat = ""  // 素材库聊天名称
    private var messageCount = 0  // 要转发的消息数量
    private var delayMin = 0  // 随机延迟最小值(毫秒)
    private var delayMax = 0  // 随机延迟最大值(毫秒)
    private var groupChats = listOf<String>()  // 目标群聊列表
    private var currentChatIndex = 0  // 当前处理的群聊索引
    private var sendHistoryId = 0L    // 发送历史ID

    // 统计数据
    private var sentCount = 0
    private var failedCount = 0
    private val failedChats = mutableListOf<String>()

    // 当前处理状态
    private enum class ProcessState {
        IDLE,                        // 空闲
        NAVIGATING_TO_MESSAGES,      // 导航到消息页面
        OPENING_MATERIAL_CHAT,       // 打开素材库聊天
        SCROLLING_TO_BOTTOM,         // 滚动到底部
        SELECTING_MESSAGES,          // 选择消息
        FORWARDING_MESSAGES,         // 转发消息
        SELECTING_TARGET_CHAT,       // 选择目标群聊
        CONFIRMING_FORWARD,          // 确认转发
        WAITING_DELAY,               // 等待延迟
        RETURNING_TO_LIST,           // 返回消息列表
        COMPLETED                    // 完成
    }

    private var currentState = ProcessState.IDLE
    private var selectedMessageCount = 0  // 已选择的消息数量

    // 广播接收器 - 接收开始批量发送的指令(暂时不使用,保留以备后用)
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.e(TAG, "📡 收到广播: ${intent?.action}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ BatchSendService已创建")
        Toast.makeText(this, "✅ BatchSendService已创建", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ BatchSendService已连接")
        Toast.makeText(this, "✅ BatchSendService已连接", Toast.LENGTH_SHORT).show()

        // 注册广播接收器
        val filter = IntentFilter("com.wework.autoreply.START_BATCH_SEND")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(commandReceiver, filter)
            }
            Log.d(TAG, "✅ 广播接收器已注册")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注册广播接收器失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ BatchSendService已销毁")
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "取消注册接收器失败", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 检查是否需要启动批量发送(和功能一一样的方式)
        if (!isProcessing && event.packageName == WEWORK_PACKAGE) {
            checkAndStartBatchSend()
        }

        // 如果正在处理，根据当前状态处理事件
        if (isProcessing && event.packageName == WEWORK_PACKAGE) {
            when (currentState) {
                ProcessState.NAVIGATING_TO_MESSAGES -> handleMessagesPage(event)
                ProcessState.OPENING_MATERIAL_CHAT -> handleMaterialChatPage(event)
                ProcessState.SELECTING_MESSAGES -> handleSelectingMessages(event)
                ProcessState.FORWARDING_MESSAGES -> handleForwardingMessages(event)
                ProcessState.SELECTING_TARGET_CHAT -> handleSelectingTargetChat(event)
                ProcessState.RETURNING_TO_LIST -> handleReturnToList(event)
                else -> {}
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "服务被中断")
    }

    /**
     * 检查SharedPreferences，如果需要则启动批量发送(和功能一一样的方式)
     */
    private fun checkAndStartBatchSend() {
        val prefs = getSharedPreferences("batch_send", Context.MODE_PRIVATE)
        val shouldStart = prefs.getBoolean("should_start", false)

        if (shouldStart) {
            // 立即清除标志,防止重复启动
            prefs.edit().putBoolean("should_start", false).apply()

            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            // 检查是否在10秒内
            if (timeDiff < 10000) {
                Log.e(TAG, "🚀 开始批量发送(转发模式)")
                Toast.makeText(this, "🚀 开始批量发送(转发模式)", Toast.LENGTH_SHORT).show()

                // 读取参数
                sendHistoryId = prefs.getLong("history_id", 0L)
                materialSourceChat = prefs.getString("material_source_chat", "") ?: ""
                messageCount = prefs.getInt("message_count", 1)
                delayMin = prefs.getInt("delay_min", 0)
                delayMax = prefs.getInt("delay_max", 0)

                // 读取群聊列表
                val groupChatsJson = prefs.getString("group_chats", "[]") ?: "[]"
                val gson = com.google.gson.Gson()
                groupChats = gson.fromJson(groupChatsJson, Array<String>::class.java).toList()

                Log.e(TAG, "📋 素材库聊天: $materialSourceChat")
                Log.e(TAG, "📋 转发消息数量: $messageCount")
                Log.e(TAG, "📋 延迟范围: $delayMin-$delayMax 毫秒")
                Log.e(TAG, "📋 目标群聊数量: ${groupChats.size}")
                Log.e(TAG, "📋 目标群聊列表: $groupChats")

                // 开始批量发送
                startBatchSend()
            }
        }
    }

    /**
     * 开始批量发送流程
     */
    private fun startBatchSend() {
        if (isProcessing) {
            Log.e(TAG, "⚠️ 已有任务在进行中")
            return
        }

        isProcessing = true
        currentState = ProcessState.NAVIGATING_TO_MESSAGES
        currentChatIndex = 0
        sentCount = 0
        failedCount = 0
        failedChats.clear()

        Log.e(TAG, "🚀 开始批量发送(转发模式)")
        Log.e(TAG, "📋 素材库聊天: $materialSourceChat")
        Log.e(TAG, "📋 转发消息数量: $messageCount")
        Log.e(TAG, "📋 延迟范围: $delayMin-$delayMax 毫秒")
        Log.e(TAG, "📋 目标群聊数量: ${groupChats.size}")
        Log.e(TAG, "📋 目标群聊列表: $groupChats")

        sendLog("🚀 开始批量发送(转发模式)")
        sendLog("📊 素材库聊天: $materialSourceChat")
        sendLog("📊 转发消息数量: $messageCount 条")
        sendLog("📊 目标群聊数: ${groupChats.size}")
        updateProgress()

        // 导航到消息页面
        Log.e(TAG, "⏰ 准备在1秒后导航到消息页面")
        handler.postDelayed({
            Log.e(TAG, "⏰ 1秒延迟结束,开始导航")
            navigateToMessages()
        }, 1000)
    }

    /**
     * 导航到消息页面
     */
    private fun navigateToMessages() {
        Log.e(TAG, "📱 导航到消息页面")
        sendLog("📱 导航到消息页面...")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        Log.e(TAG, "✅ rootNode获取成功")

        // 检查是否已经在消息页面
        val hasMessageTab = findNodeByText(rootNode, "消息") != null
        val hasContactTab = findNodeByText(rootNode, "通讯录") != null

        Log.e(TAG, "🔍 hasMessageTab=$hasMessageTab, hasContactTab=$hasContactTab")

        if (hasMessageTab && hasContactTab) {
            // 在主页面，点击"消息"按钮
            Log.e(TAG, "✅ 在主页面,准备点击消息按钮")
            val messagesButton = findNodeByText(rootNode, "消息")
            if (messagesButton != null) {
                Log.e(TAG, "✅ 找到消息按钮,点击")
                clickNode(messagesButton)
                sendLog("✅ 已点击消息")

                Log.e(TAG, "⏰ 准备在1.5秒后打开素材库聊天")
                handler.postDelayed({
                    Log.e(TAG, "⏰ 1.5秒延迟结束,开始打开素材库聊天")
                    currentState = ProcessState.OPENING_MATERIAL_CHAT
                    openMaterialChat()
                }, 1500)
            } else {
                Log.e(TAG, "❌ 未找到消息按钮,重试")
                handler.postDelayed({ navigateToMessages() }, 1000)
            }
        } else {
            // 不在主页面，先按返回键或直接打开素材库聊天
            Log.e(TAG, "⚠️ 不在主页面,尝试直接打开素材库聊天")

            // 尝试直接查找素材库聊天
            val materialChatNode = findNodeContainingText(rootNode, materialSourceChat)
            if (materialChatNode != null) {
                Log.e(TAG, "✅ 找到素材库聊天,直接打开")
                clickNode(materialChatNode)
                Log.e(TAG, "⏰ 准备在1.5秒后开始选择消息")
                handler.postDelayed({
                    Log.e(TAG, "⏰ 1.5秒延迟结束,开始选择消息")
                    currentState = ProcessState.SELECTING_MESSAGES
                    selectMessages()
                }, 1500)
            } else {
                Log.e(TAG, "❌ 未找到素材库聊天,按返回键")
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ navigateToMessages() }, 1000)
            }
        }
    }

    /**
     * 打开素材库聊天
     */
    private fun openMaterialChat() {
        Log.e(TAG, "📚 打开素材库聊天: $materialSourceChat")
        sendLog("📚 打开素材库聊天: $materialSourceChat")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        Log.e(TAG, "✅ rootNode获取成功,查找素材库聊天")

        // 查找素材库聊天
        val materialChatNode = findNodeContainingText(rootNode, materialSourceChat)
        if (materialChatNode != null) {
            Log.e(TAG, "✅ 找到素材库聊天,点击打开")
            clickNode(materialChatNode)
            sendLog("✅ 已打开素材库聊天")

            Log.e(TAG, "⏰ 准备在1.5秒后开始选择消息")
            handler.postDelayed({
                Log.e(TAG, "⏰ 1.5秒延迟结束,调用selectMessages()")
                currentState = ProcessState.SELECTING_MESSAGES
                selectMessages()
            }, 1500)
        } else {
            Log.e(TAG, "❌ 未找到素材库聊天: $materialSourceChat")
            sendLog("❌ 未找到素材库聊天: $materialSourceChat")
            Toast.makeText(this, "❌ 未找到素材库聊天,请检查设置", Toast.LENGTH_LONG).show()
            stopBatchSend()
        }
    }

    /**
     * 选择消息
     */
    private fun selectMessages() {
        Log.e(TAG, "📋 开始选择消息,数量: $messageCount")
        sendLog("📋 开始选择消息,数量: $messageCount")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            Toast.makeText(this, "❌ 无法获取窗口信息", Toast.LENGTH_SHORT).show()
            stopBatchSend()
            return
        }

        Log.e(TAG, "✅ rootNode获取成功")

        // 步骤1: 滚动到底部(最新消息)
        Log.e(TAG, "📜 滚动到底部")
        sendLog("📜 滚动到底部")

        // 查找RecyclerView或ListView
        val scrollableNode = findScrollableNode(rootNode)
        if (scrollableNode != null) {
            Log.e(TAG, "✅ 找到可滚动节点,执行多次滚动到底部")
            // 执行多次滚动到底部的操作,确保加载所有消息
            scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            handler.postDelayed({
                scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }, 200)
            handler.postDelayed({
                scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }, 400)
            handler.postDelayed({
                scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }, 600)
            handler.postDelayed({
                scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }, 800)
        } else {
            Log.e(TAG, "⚠️ 未找到可滚动节点")
        }

        // 等待滚动完成后,直接长按最后一条消息(不要先滚动!)
        Log.e(TAG, "⏰ 准备在1.5秒后长按最后一条消息")
        handler.postDelayed({
            Log.e(TAG, "⏰ 1.5秒延迟结束,调用longPressLastMessage()")
            longPressLastMessage()
        }, 1500)
    }

    /**
     * 查找可滚动的节点
     */
    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isScrollable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findScrollableNode(node.getChild(i))
            if (result != null) return result
        }

        return null
    }

    /**
     * 长按最后一条消息
     */
    private fun longPressLastMessage() {
        Log.e(TAG, "👆 长按最后一条消息")
        sendLog("👆 长按最后一条消息")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            Toast.makeText(this, "❌ 无法获取窗口信息", Toast.LENGTH_SHORT).show()
            stopBatchSend()
            return
        }

        Log.e(TAG, "✅ rootNode获取成功,查找消息节点")

        // 查找所有消息节点(通常是RelativeLayout或LinearLayout)
        // 企业微信的消息节点通常包含文本或图片
        val messageNodes = findMessageNodes(rootNode)

        Log.e(TAG, "🔍 找到消息节点数量: ${messageNodes.size}")

        if (messageNodes.isEmpty()) {
            Log.e(TAG, "❌ 未找到消息节点")
            Toast.makeText(this, "❌ 未找到消息节点", Toast.LENGTH_SHORT).show()
            stopBatchSend()
            return
        }

        // 按Y坐标排序,确保最后一条是Y坐标最大的(最下面的消息)
        val sortedMessages = messageNodes.sortedBy { node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            rect.top
        }

        // 打印所有消息的位置信息
        sortedMessages.forEachIndexed { index, node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            Log.e(TAG, "📋 消息 #${index + 1}: resourceId=${node.viewIdResourceName}, bounds=$rect")
        }

        // 最后一条消息是Y坐标最大的
        val lastMessage = sortedMessages.last()
        val lastRect = android.graphics.Rect()
        lastMessage.getBoundsInScreen(lastRect)
        Log.e(TAG, "✅ 找到最后一条消息,准备长按")
        Log.e(TAG, "🔍 消息节点信息: resourceId=${lastMessage.viewIdResourceName}, className=${lastMessage.className}, bounds=$lastRect")

        // 执行长按操作
        val longPressed = lastMessage.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        Log.e(TAG, "👆 长按结果: $longPressed")

        if (longPressed) {
            Log.e(TAG, "✅ 长按成功,等待菜单出现")
            // 等待菜单出现后点击"多选"按钮
            handler.postDelayed({
                clickMultiSelectButton()
            }, 800)
        } else {
            Log.e(TAG, "❌ 长按失败")
            Toast.makeText(this, "❌ 长按失败", Toast.LENGTH_SHORT).show()
            stopBatchSend()
        }
    }

    /**
     * 点击"多选"按钮
     */
    private fun clickMultiSelectButton() {
        Log.e(TAG, "🔍 查找并点击'多选'按钮")
        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            stopBatchSend()
            return
        }

        // 查找"多选"按钮
        val multiSelectButton = findNodeByText(rootNode, "多选")
        if (multiSelectButton != null) {
            Log.e(TAG, "✅ 找到'多选'按钮,点击")
            clickNode(multiSelectButton)

            // 等待进入多选模式后,开始勾选消息
            handler.postDelayed({
                Log.e(TAG, "⏰ 进入多选模式,开始勾选消息")
                selectedMessageCount = 1  // 已选择1条(长按的那条)
                selectMoreMessages()
            }, 1000)
        } else {
            Log.e(TAG, "❌ 未找到'多选'按钮")
            Toast.makeText(this, "❌ 未找到'多选'按钮", Toast.LENGTH_SHORT).show()
            stopBatchSend()
        }
    }



    /**
     * 查找消息节点
     * 企业微信的消息节点特征:
     * - 在ListView中(resource-id: com.tencent.wework:id/iop)
     * - 每条消息是RelativeLayout(resource-id: com.tencent.wework:id/cmn)
     * - 消息内容在LinearLayout中,支持long-clickable:
     *   1. 文字消息: resource-id: com.tencent.wework:id/hxd
     *   2. 卡片消息(群聊邀请等): resource-id: com.tencent.wework:id/ih3
     */
    private fun findMessageNodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node == null) return result

        // 查找所有可长按的消息节点
        // 包括: ih3, hxd (消息内容), k2j (卡片消息中的图片)
        val resourceId = node.viewIdResourceName
        if (node.isLongClickable &&
            (resourceId == "com.tencent.wework:id/hxd" ||   // 文字消息
             resourceId == "com.tencent.wework:id/ih3" ||   // 卡片消息(LinearLayout)
             resourceId == "com.tencent.wework:id/k2j")) {  // 卡片消息中的图片(ImageView)
            result.add(node)
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            result.addAll(findMessageNodes(node.getChild(i)))
        }

        return result
    }

    /**
     * 继续勾选更多消息
     * 新策略: 一次性勾选当前屏幕上的所有未勾选消息,不要每勾选一条就滚动
     */
    private fun selectMoreMessages() {
        if (selectedMessageCount >= messageCount) {
            // 已选择足够的消息,点击转发按钮
            Log.e(TAG, "✅ 已选择 $selectedMessageCount 条消息,准备点击转发")
            sendLog("✅ 已选择 $selectedMessageCount 条消息")
            clickForwardButton()
            return
        }

        Log.e(TAG, "📋 继续勾选消息: $selectedMessageCount/$messageCount")
        sendLog("📋 继续勾选消息: $selectedMessageCount/$messageCount")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            stopBatchSend()
            return
        }

        // 查找所有CheckBox节点(多选模式下每条消息左侧的复选框)
        val checkBoxes = findCheckBoxNodes(rootNode)
        Log.e(TAG, "🔍 找到CheckBox数量: ${checkBoxes.size}")

        if (checkBoxes.isEmpty()) {
            Log.e(TAG, "❌ 未找到CheckBox,停止勾选")
            clickForwardButton()
            return
        }

        // 按Y坐标排序,确保顺序正确(从上到下)
        val sortedCheckBoxes = checkBoxes.sortedBy { node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            rect.top
        }

        // 找到所有未勾选的CheckBox(从后往前,即从最新到最旧)
        val uncheckedBoxes = mutableListOf<AccessibilityNodeInfo>()
        for (i in sortedCheckBoxes.size - 1 downTo 0) {
            val checkBox = sortedCheckBoxes[i]
            if (!checkBox.isChecked) {
                uncheckedBoxes.add(checkBox)
            }
        }

        Log.e(TAG, "🔍 找到未勾选的CheckBox数量: ${uncheckedBoxes.size}")

        if (uncheckedBoxes.isEmpty()) {
            // 当前屏幕没有未勾选的消息了,直接点击转发
            Log.e(TAG, "⚠️ 当前屏幕没有未勾选的消息,直接转发")
            clickForwardButton()
            return
        }

        // 计算还需要勾选多少条
        val needCount = messageCount - selectedMessageCount
        Log.e(TAG, "📋 还需要勾选 $needCount 条消息")

        // 一次性勾选当前屏幕上的所有未勾选消息(最多勾选needCount条)
        val toSelectCount = minOf(needCount, uncheckedBoxes.size)
        Log.e(TAG, "📋 准备一次性勾选 $toSelectCount 条消息")

        var successCount = 0  // 成功勾选的数量

        for (i in 0 until toSelectCount) {
            val checkBox = uncheckedBoxes[i]
            val messageRow = findMessageRowForCheckBox(checkBox)
            if (messageRow != null) {
                // 延迟点击,避免点击过快
                handler.postDelayed({
                    val clicked = clickNode(messageRow)
                    if (clicked) {
                        selectedMessageCount++
                        Log.e(TAG, "✅ 已勾选第 $selectedMessageCount 条消息")
                    }
                }, (i * 200).toLong())
                successCount++
            }
        }

        // 等待所有勾选操作完成后,检查是否需要继续滚动
        handler.postDelayed({
            if (selectedMessageCount >= messageCount) {
                // 已经勾选够了,点击转发
                Log.e(TAG, "✅ 已勾选够 $selectedMessageCount 条消息,点击转发")
                clickForwardButton()
            } else {
                // 还没勾选够,向上滚动一次,继续勾选
                Log.e(TAG, "⚠️ 还需要勾选 ${messageCount - selectedMessageCount} 条消息,向上滚动")
                scrollUpAndContinue()
            }
        }, (toSelectCount * 200 + 500).toLong())
    }

    /**
     * 向上滚动并继续勾选消息
     * 使用手势滑动,精确控制滚动距离
     */
    private var scrollCount = 0  // 滚动次数计数
    private val maxScrollCount = 20  // 最大滚动次数,避免无限循环

    private fun scrollUpAndContinue() {
        scrollCount++
        Log.e(TAG, "📜 向上滚动,加载更多消息(第 $scrollCount 次)")

        // 如果滚动次数过多,停止勾选
        if (scrollCount > maxScrollCount) {
            Log.e(TAG, "⚠️ 滚动次数过多($scrollCount),停止勾选,转发已选择的消息")
            Toast.makeText(this, "⚠️ 已达到最大滚动次数,将转发已选择的消息", Toast.LENGTH_SHORT).show()
            scrollCount = 0
            clickForwardButton()
            return
        }

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            clickForwardButton()
            return
        }

        // 在多选模式下,使用ACTION_SCROLL_BACKWARD滚动
        val scrollableNode = findScrollableNode(rootNode)
        if (scrollableNode != null) {
            Log.e(TAG, "✅ 找到可滚动节点,使用ACTION_SCROLL_BACKWARD滚动")
            val scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            Log.e(TAG, "📜 滚动结果: $scrolled")

            // 等待滚动完成后继续勾选
            handler.postDelayed({
                Log.e(TAG, "✅ 滚动完成,继续勾选消息")
                selectMoreMessages()
            }, 800)
        } else {
            Log.e(TAG, "❌ 未找到可滚动节点,停止勾选")
            clickForwardButton()
        }
    }

    /**
     * 查找CheckBox节点(多选模式下的复选框)
     */
    private fun findCheckBoxNodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node == null) return result

        // 查找resource-id为com.tencent.wework:id/c4g的CheckBox
        val resourceId = node.viewIdResourceName
        if (resourceId == "com.tencent.wework:id/c4g" && node.className == "android.widget.CheckBox") {
            result.add(node)
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            result.addAll(findCheckBoxNodes(node.getChild(i)))
        }

        return result
    }

    /**
     * 查找包含CheckBox的消息行
     * CheckBox的层级结构: CheckBox -> RelativeLayout(ih2) -> RelativeLayout(imf/ihi) -> RelativeLayout(cmn)
     */
    private fun findMessageRowForCheckBox(checkBox: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = checkBox

        // 向上查找,直到找到resource-id为com.tencent.wework:id/cmn的节点
        for (i in 0 until 10) {  // 最多向上查找10层
            current = current?.parent
            if (current == null) break

            val resourceId = current.viewIdResourceName
            Log.e(TAG, "🔍 向上查找第${i+1}层: resourceId=$resourceId, className=${current.className}")

            if (resourceId == "com.tencent.wework:id/cmn") {
                Log.e(TAG, "✅ 找到消息行节点: $resourceId")
                return current
            }
        }

        Log.e(TAG, "❌ 未找到消息行节点")
        return null
    }

    /**
     * 点击转发按钮
     */
    private fun clickForwardButton() {
        Log.d(TAG, "📤 点击转发按钮")
        sendLog("📤 点击转发按钮")

        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "❌ 无法获取窗口信息")
            stopBatchSend()
            return
        }

        // 查找转发按钮(通常包含"转发"文本)
        val forwardButton = findNodeContainingText(rootNode, "转发")

        if (forwardButton != null) {
            Log.d(TAG, "✅ 找到转发按钮")
            clickNode(forwardButton)

            // 等待转发方式选择对话框出现
            handler.postDelayed({
                clickOneByOneForward()
            }, 800)
        } else {
            Log.d(TAG, "❌ 未找到转发按钮")
            Toast.makeText(this, "❌ 未找到转发按钮", Toast.LENGTH_SHORT).show()
            stopBatchSend()
        }
    }

    /**
     * 点击"逐条转发"按钮
     */
    private fun clickOneByOneForward() {
        Log.e(TAG, "🔍 查找并点击'逐条转发'按钮")
        sendLog("🔍 点击逐条转发")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            stopBatchSend()
            return
        }

        // 查找包含"逐条转发"文本的节点
        val oneByOneButton = findNodeContainingText(rootNode, "逐条转发")

        if (oneByOneButton != null) {
            Log.e(TAG, "✅ 找到'逐条转发'按钮")

            // 获取按钮的坐标
            val rect = android.graphics.Rect()
            oneByOneButton.getBoundsInScreen(rect)
            Log.e(TAG, "🔍 '逐条转发'按钮位置: $rect")

            // 计算中心点坐标
            val centerX = (rect.left + rect.right) / 2
            val centerY = (rect.top + rect.bottom) / 2

            // 使用坐标点击
            val path = android.graphics.Path()
            path.moveTo(centerX.toFloat(), centerY.toFloat())

            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            val strokeDescription = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)
            gestureBuilder.addStroke(strokeDescription)

            val clicked = dispatchGesture(gestureBuilder.build(), object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    Log.e(TAG, "✅ 点击'逐条转发'成功")
                    // 等待进入选择联系人界面
                    handler.postDelayed({
                        currentState = ProcessState.SELECTING_TARGET_CHAT
                        selectTargetChat()
                    }, 1000)
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    Log.e(TAG, "❌ 点击'逐条转发'被取消")
                    stopBatchSend()
                }
            }, null)

            Log.e(TAG, "👆 发起点击手势: $clicked, 坐标=($centerX, $centerY)")
        } else {
            Log.e(TAG, "❌ 未找到'逐条转发'按钮")
            Toast.makeText(this, "❌ 未找到'逐条转发'按钮", Toast.LENGTH_SHORT).show()
            stopBatchSend()
        }
    }

    /**
     * 选择目标群聊(带滚动查找)
     */
    private fun selectTargetChat(scrollAttempts: Int = 0) {
        if (currentChatIndex >= groupChats.size) {
            // 所有群聊都已处理完成
            Log.e(TAG, "✅ 所有群聊都已处理完成")
            completeBatchSend()
            return
        }

        val targetChat = groupChats[currentChatIndex]
        if (scrollAttempts == 0) {
            Log.e(TAG, "🎯 选择目标群聊: $targetChat (${currentChatIndex + 1}/${groupChats.size})")
            sendLog("🎯 选择目标群聊: $targetChat (${currentChatIndex + 1}/${groupChats.size})")
        } else {
            Log.e(TAG, "🔍 第${scrollAttempts}次滚动后继续查找: $targetChat")
        }

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            Toast.makeText(this, "❌ 无法获取窗口信息", Toast.LENGTH_SHORT).show()
            failedCount++
            failedChats.add(targetChat)
            moveToNextTargetChat()
            return
        }

        // 直接查找包含目标群聊名称的节点
        val chatNode = findNodeContainingText(rootNode, targetChat)

        if (chatNode != null) {
            Log.e(TAG, "✅ 找到目标群聊节点: $targetChat")

            // 查找包含这个文本节点的可点击父节点(resource-id: com.tencent.wework:id/hbv)
            val clickableParent = findClickableParent(chatNode, "com.tencent.wework:id/hbv")

            if (clickableParent != null) {
                Log.e(TAG, "✅ 找到父节点,准备通过坐标点击")

                // 获取节点的坐标
                val rect = android.graphics.Rect()
                clickableParent.getBoundsInScreen(rect)
                Log.e(TAG, "🔍 群聊节点位置: $rect")

                // 计算中心点坐标
                val centerX = (rect.left + rect.right) / 2
                val centerY = (rect.top + rect.bottom) / 2

                // 使用手势点击
                val path = android.graphics.Path()
                path.moveTo(centerX.toFloat(), centerY.toFloat())

                val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
                val strokeDescription = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)
                gestureBuilder.addStroke(strokeDescription)

                val gestureDispatched = dispatchGesture(gestureBuilder.build(), object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        Log.e(TAG, "✅ 点击群聊节点成功,开始智能等待发送按钮")
                        currentState = ProcessState.CONFIRMING_FORWARD
                        // 使用智能等待,不硬编码等待时间
                        waitForSendButton(targetChat)
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        Log.e(TAG, "❌ 点击群聊节点被取消")
                        failedCount++
                        failedChats.add(targetChat)
                        moveToNextTargetChat()
                    }
                }, null)

                Log.e(TAG, "👆 发起点击手势: $gestureDispatched, 坐标=($centerX, $centerY)")
            } else {
                Log.e(TAG, "❌ 未找到可点击的父节点")
                failedCount++
                failedChats.add(targetChat)
                moveToNextTargetChat()
            }
        } else {
            // 未找到目标群聊,尝试向下滚动
            Log.e(TAG, "⚠️ 未找到目标群聊,尝试向下滚动查找")

            // 查找可滚动节点
            val scrollableNode = findScrollableNode(rootNode)
            if (scrollableNode != null) {
                Log.e(TAG, "✅ 找到可滚动节点,向下滚动")
                val scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

                if (scrolled) {
                    Log.e(TAG, "📜 滚动成功,等待后继续查找")
                    // 等待滚动完成后继续查找
                    handler.postDelayed({
                        selectTargetChat(scrollAttempts + 1)
                    }, 500)
                } else {
                    // 无法继续滚动,说明已经到底部了
                    Log.e(TAG, "❌ 已滚动到底部,仍未找到目标群聊: $targetChat")
                    Toast.makeText(this, "❌ 未找到目标群聊: $targetChat", Toast.LENGTH_SHORT).show()
                    failedCount++
                    failedChats.add(targetChat)
                    moveToNextTargetChat()
                }
            } else {
                // 找不到可滚动节点,说明列表不可滚动或已经到底部
                Log.e(TAG, "❌ 未找到可滚动节点,无法继续查找: $targetChat")
                Toast.makeText(this, "❌ 未找到目标群聊: $targetChat", Toast.LENGTH_SHORT).show()
                failedCount++
                failedChats.add(targetChat)
                moveToNextTargetChat()
            }
        }
    }

    /**
     * 点击搜索结果
     */
    private fun clickSearchResult(targetChat: String) {
        Log.d(TAG, "🔍 点击搜索结果: $targetChat")

        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "❌ 无法获取窗口信息")
            failedCount++
            failedChats.add(targetChat)
            moveToNextTargetChat()
            return
        }

        // 查找包含目标群聊名称的节点
        val resultNode = findNodeContainingText(rootNode, targetChat)

        if (resultNode != null) {
            Log.d(TAG, "✅ 找到搜索结果,点击")
            clickNode(resultNode)

            // 等待确认对话框出现
            handler.postDelayed({
                confirmForward(targetChat)
            }, 1000)
        } else {
            Log.d(TAG, "❌ 未找到搜索结果: $targetChat")
            Toast.makeText(this, "❌ 未找到群聊: $targetChat", Toast.LENGTH_SHORT).show()
            failedCount++
            failedChats.add(targetChat)
            moveToNextTargetChat()
        }
    }

    /**
     * 通过坐标点击发送按钮
     */
    private fun clickSendButtonByCoordinate(confirmButton: AccessibilityNodeInfo, targetChat: String) {
        // 获取按钮位置
        val rect = android.graphics.Rect()
        confirmButton.getBoundsInScreen(rect)
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2

        Log.e(TAG, "🔍 '发送'按钮位置: $rect")

        // 使用坐标点击
        val path = android.graphics.Path()
        path.moveTo(centerX.toFloat(), centerY.toFloat())

        val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
        gestureBuilder.addStroke(
            android.accessibilityservice.GestureDescription.StrokeDescription(
                path,
                0,
                100
            )
        )

        val gestureDispatched = dispatchGesture(gestureBuilder.build(), object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Log.e(TAG, "✅ 坐标点击'发送'按钮成功")

                // 转发成功
                sentCount++
                Log.e(TAG, "🎉 转发成功: $targetChat")
                sendLog("🎉 转发成功: $targetChat")

                // 等待随机延迟后处理下一个群聊
                val delay = getRandomDelay()
                if (delay > 0) {
                    Log.e(TAG, "⏱️ 等待随机延迟: ${delay}ms")
                    sendLog("⏱️ 等待 ${delay / 1000} 秒")
                }

                handler.postDelayed({
                    moveToNextTargetChat()
                }, delay)
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Log.e(TAG, "❌ 坐标点击'发送'按钮被取消")
                failedCount++
                failedChats.add(targetChat)
                moveToNextTargetChat()
            }
        }, null)

        Log.e(TAG, "👆 发起坐标点击手势: $gestureDispatched, 坐标=($centerX, $centerY)")
    }

    /**
     * 智能等待发送按钮出现
     * 每300ms检测一次,最多检测10次(3秒)
     */
    private fun waitForSendButton(targetChat: String, attempts: Int = 0, maxAttempts: Int = 10) {
        if (attempts >= maxAttempts) {
            Log.e(TAG, "❌ 等待发送按钮超时(${maxAttempts * 300}ms)")
            Toast.makeText(this, "❌ 等待发送按钮超时", Toast.LENGTH_SHORT).show()
            failedCount++
            failedChats.add(targetChat)
            moveToNextTargetChat()
            return
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "❌ 无法获取窗口信息,继续等待...")
            handler.postDelayed({
                waitForSendButton(targetChat, attempts + 1, maxAttempts)
            }, 300)
            return
        }

        // 查找"发送"按钮
        val sendButton = findNodeContainingText(rootNode, "发送")

        if (sendButton != null) {
            // 找到了,立即点击
            Log.e(TAG, "✅ 找到发送按钮(第${attempts + 1}次检测,耗时${(attempts + 1) * 300}ms)")
            confirmForward(targetChat)
        } else {
            // 还没找到,继续等待
            Log.d(TAG, "⏳ 第${attempts + 1}次检测未找到发送按钮,继续等待...")
            handler.postDelayed({
                waitForSendButton(targetChat, attempts + 1, maxAttempts)
            }, 300)
        }
    }

    /**
     * 确认转发
     */
    private fun confirmForward(targetChat: String) {
        Log.e(TAG, "✅ 确认转发到: $targetChat")
        sendLog("✅ 转发到: $targetChat")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "❌ 无法获取窗口信息")
            failedCount++
            failedChats.add(targetChat)
            moveToNextTargetChat()
            return
        }

        // 查找"发送"按钮 - 使用resource-id精确查找
        val confirmButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/dbo")
            ?: findNodeContainingText(rootNode, "确定")

        if (confirmButton != null) {
            Log.e(TAG, "✅ 找到确认按钮,准备点击")

            // 方法1: 先尝试performAction(ACTION_CLICK)
            Log.e(TAG, "🔍 方法1: 尝试performAction(ACTION_CLICK)")
            val actionClicked = confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.e(TAG, "👆 performAction结果: $actionClicked")

            if (actionClicked) {
                // performAction成功,等待一下看是否真的点击成功
                handler.postDelayed({
                    // 检查弹窗是否关闭
                    val rootNode = rootInActiveWindow
                    val stillHasDialog = rootNode?.let {
                        findNodeByResourceId(it, "com.tencent.wework:id/dbo") != null
                    } ?: false

                    if (stillHasDialog) {
                        Log.e(TAG, "⚠️ performAction点击后弹窗仍存在,尝试方法2: 坐标点击")
                        clickSendButtonByCoordinate(confirmButton, targetChat)
                    } else {
                        Log.e(TAG, "✅ performAction点击成功,弹窗已关闭")
                        // 转发成功
                        sentCount++
                        Log.e(TAG, "🎉 转发成功: $targetChat")
                        sendLog("🎉 转发成功: $targetChat")

                        // 等待随机延迟后处理下一个群聊
                        val delay = getRandomDelay()
                        if (delay > 0) {
                            Log.e(TAG, "⏱️ 等待随机延迟: ${delay}ms")
                            sendLog("⏱️ 等待 ${delay / 1000} 秒")
                        }

                        handler.postDelayed({
                            moveToNextTargetChat()
                        }, delay)
                    }
                }, 500)  // 等待500ms检查
            } else {
                // performAction失败,直接使用坐标点击
                Log.e(TAG, "⚠️ performAction失败,使用方法2: 坐标点击")
                clickSendButtonByCoordinate(confirmButton, targetChat)
            }
        } else {
            // 没有找到"发送"按钮,可能已经进入聊天界面,消息已自动发送
            Log.e(TAG, "⚠️ 未找到确认按钮,检查是否已进入聊天界面")

            // 检查是否有输入框(说明已进入聊天界面)
            val inputBox = findNodeByResourceId(rootNode, "com.tencent.wework:id/kah")
            if (inputBox != null) {
                Log.e(TAG, "✅ 已进入聊天界面,消息已自动发送")
                sentCount++
                sendLog("🎉 转发成功: $targetChat")

                // 等待随机延迟后处理下一个群聊
                val delay = getRandomDelay()
                if (delay > 0) {
                    Log.e(TAG, "⏱️ 等待随机延迟: ${delay}ms")
                    sendLog("⏱️ 等待 ${delay / 1000} 秒")
                }
                handler.postDelayed({
                    moveToNextTargetChat()
                }, delay)
            } else {
                Log.e(TAG, "❌ 未找到确认按钮,也未进入聊天界面")
                Toast.makeText(this, "❌ 转发失败", Toast.LENGTH_SHORT).show()
                failedCount++
                failedChats.add(targetChat)
                moveToNextTargetChat()
            }
        }
    }

    /**
     * 获取随机延迟时间
     */
    private fun getRandomDelay(): Long {
        if (delayMin == 0 && delayMax == 0) return 0
        if (delayMin >= delayMax) return delayMin.toLong()

        val random = kotlin.random.Random.Default
        return (random.nextInt(delayMax - delayMin) + delayMin).toLong()
    }

    /**
     * 移动到下一个目标群聊
     */
    private fun moveToNextTargetChat() {
        Log.e(TAG, "🔄 移动到下一个目标群聊")

        currentChatIndex++

        if (currentChatIndex >= groupChats.size) {
            // 所有群聊都已处理完成
            Log.e(TAG, "✅ 所有目标群聊都已处理完成")
            completeBatchSend()
        } else {
            // 点击"发送"后,企业微信会自动返回到素材库聊天页面
            // 需要重新执行整个流程:滚动到底部 → 长按 → 多选 → 勾选消息 → 转发 → 逐条转发 → 选择下一个目标聊天
            Log.e(TAG, "📋 准备发送到下一个目标群聊: ${currentChatIndex + 1}/${groupChats.size}")
            Log.e(TAG, "🔄 重新开始选择消息流程")

            // 重置状态
            selectedMessageCount = 0
            scrollCount = 0

            // 等待一下,确保页面已经返回到素材库聊天
            handler.postDelayed({
                // 重新开始整个流程:滚动到底部 → 长按 → 多选 → 勾选消息
                currentState = ProcessState.SELECTING_MESSAGES
                selectMessages()
            }, 1000)
        }
    }

    /**
     * 移动到下一个群聊(转发模式下暂不使用)
     */
    private fun moveToNextChat() {
        Log.d(TAG, "🔄 移动到下一个群聊 - 当前索引: $currentChatIndex")

        // 返回消息列表
        performGlobalAction(GLOBAL_ACTION_BACK)

        handler.postDelayed({
            currentChatIndex++
            Log.d(TAG, "📈 索引已增加 - 新索引: $currentChatIndex, 总数: ${groupChats.size}")
            // TODO: 转发模式下的逻辑
            sendLog("⚠️ 转发模式开发中...")
        }, 1500)
    }

    /**
     * 完成批量发送
     */
    private fun completeBatchSend() {
        Log.d(TAG, "🎉 批量发送完成")
        sendLog("🎉 批量发送完成！")
        sendLog("📊 成功: $sentCount, 失败: $failedCount")

        if (failedChats.isNotEmpty()) {
            sendLog("❌ 失败的群聊: ${failedChats.joinToString(", ")}")
        }

        currentState = ProcessState.COMPLETED
        isProcessing = false

        // 更新数据库状态
        updateFinalStatus()
    }

    /**
     * 停止批量发送
     */
    private fun stopBatchSend() {
        Log.d(TAG, "⏹️ 停止批量发送")
        sendLog("⏹️ 批量发送已停止")

        currentState = ProcessState.IDLE
        isProcessing = false

        // 更新数据库状态
        updateFinalStatus()
    }

    /**
     * 停止处理
     */
    private fun stopProcessing() {
        isProcessing = false
        currentState = ProcessState.IDLE
        sendLog("⏹️ 批量发送已停止")
    }

    /**
     * 重试或停止
     */
    private fun retryOrStop() {
        handler.postDelayed({
            if (isProcessing) {
                sendLog("🔄 重试中...")
            } else {
                stopProcessing()
            }
        }, 2000)
    }

    // ==================== 事件处理方法 ====================

    private fun handleMessagesPage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    private fun handleGroupChatPage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    private fun handleInputPage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    private fun handleSendingPage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    private fun handleReturnToList(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    private fun handleMaterialChatPage(event: AccessibilityEvent) {
        // 素材库聊天页面变化时的处理
    }

    private fun handleSelectingMessages(event: AccessibilityEvent) {
        // 选择消息时的处理
    }

    private fun handleForwardingMessages(event: AccessibilityEvent) {
        // 转发消息时的处理
    }

    private fun handleSelectingTargetChat(event: AccessibilityEvent) {
        // 选择目标群聊时的处理
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据文本查找节点（包含匹配）
     */
    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        if (nodeText.contains(text)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) return result
        }

        return null
    }

    /**
     * 标准化文本:将全角字符转换为半角字符,用于模糊匹配
     */
    private fun normalizeText(text: String): String {
        return text.map { char ->
            when (char) {
                // 全角波浪号 → 半角波浪号
                '～' -> '~'
                // 全角空格 → 半角空格
                '　' -> ' '
                // 全角数字 → 半角数字
                in '０'..'９' -> (char.code - '０'.code + '0'.code).toChar()
                // 全角字母 → 半角字母
                in 'Ａ'..'Ｚ' -> (char.code - 'Ａ'.code + 'A'.code).toChar()
                in 'ａ'..'ｚ' -> (char.code - 'ａ'.code + 'a'.code).toChar()
                // 其他字符保持不变
                else -> char
            }
        }.joinToString("")
    }

    /**
     * 查找包含指定文本的节点(支持全角/半角模糊匹配)
     */
    private fun findNodeContainingText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""

        // 标准化后进行比较
        val normalizedNodeText = normalizeText(nodeText)
        val normalizedSearchText = normalizeText(text)

        if (normalizedNodeText.contains(normalizedSearchText)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeContainingText(child, text)
            if (result != null) return result
        }

        return null
    }

    /**
     * 查找可点击的父节点
     */
    private fun findClickableParent(node: AccessibilityNodeInfo?, targetResourceId: String? = null): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node

        // 向上查找,直到找到可点击的节点
        for (i in 0 until 10) {  // 最多向上查找10层
            current = current?.parent
            if (current == null) break

            val resourceId = current.viewIdResourceName
            Log.e(TAG, "🔍 向上查找第${i+1}层: resourceId=$resourceId, clickable=${current.isClickable}")

            // 如果指定了targetResourceId,优先匹配
            if (targetResourceId != null && resourceId == targetResourceId) {
                Log.e(TAG, "✅ 找到目标resource-id的父节点: $resourceId")
                return current
            }

            // 如果没有指定targetResourceId,找到第一个可点击的节点
            if (targetResourceId == null && current.isClickable) {
                Log.e(TAG, "✅ 找到可点击的父节点")
                return current
            }
        }

        Log.e(TAG, "❌ 未找到可点击的父节点")
        return null
    }

    /**
     * 根据className查找节点
     */
    private fun findNodeByClassName(node: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (node.className?.toString() == className) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByClassName(child, className)
            if (result != null) return result
        }

        return null
    }

    /**
     * 根据contentDescription查找节点
     */
    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (nodeDesc.contains(desc)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByContentDescription(child, desc)
            if (result != null) return result
        }

        return null
    }

    /**
     * 根据resource-id查找节点
     */
    private fun findNodeByResourceId(node: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        val nodeResourceId = node.viewIdResourceName ?: ""
        if (nodeResourceId == resourceId) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByResourceId(child, resourceId)
            if (result != null) return result
        }

        return null
    }

    /**
     * 打印所有节点信息（调试用）
     */
    private fun printAllNodesDebug(node: AccessibilityNodeInfo, depth: Int = 0) {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        if (text.isNotEmpty() || desc.isNotEmpty() || resourceId.isNotEmpty()) {
            val indent = "  ".repeat(depth)
            Log.e(TAG, "${indent}📝 text='$text', desc='$desc', id='$resourceId', class=$className")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { printAllNodesDebug(it, depth + 1) }
        }
    }

    /**
     * 使用全局坐标点击
     */
    private fun performGlobalClick(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "❌ 系统版本过低，不支持全局点击")
            return false
        }

        try {
            val path = android.graphics.Path()
            path.moveTo(x, y)

            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            val strokeDescription = android.accessibilityservice.GestureDescription.StrokeDescription(
                path,
                0,
                100
            )
            gestureBuilder.addStroke(strokeDescription)

            val gesture = gestureBuilder.build()

            var success = false
            val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    success = true
                    Log.e(TAG, "✅ 全局点击成功")
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    Log.e(TAG, "❌ 全局点击被取消")
                }
            }

            dispatchGesture(gesture, callback, null)

            // 等待一小段时间让手势完成
            Thread.sleep(200)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 全局点击失败", e)
            return false
        }
    }

    /**
     * 打印节点树中的所有信息（用于调试）
     */
    private fun printAllNodes(node: AccessibilityNodeInfo, depth: Int = 0) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        if (text.isNotEmpty() || contentDesc.isNotEmpty() || resourceId.isNotEmpty()) {
            val indent = "  ".repeat(depth)
            Log.e("BATCH_SEND_DEBUG", "${indent}📝 text='$text', desc='$contentDesc', id='$resourceId', class=$className")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { printAllNodes(it, depth + 1) }
        }
    }

    /**
     * 点击节点
     */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        // 尝试直接点击
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // 尝试点击父节点
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
            depth++
        }

        return false
    }

    /**
     * 发送日志
     */
    private fun sendLog(message: String) {
        Log.d(TAG, message)
        val intent = Intent("com.wework.autoreply.BATCH_SEND_LOG")
        intent.putExtra("message", message)
        sendBroadcast(intent)
    }

    /**
     * 更新进度
     */
    private fun updateProgress() {
        val intent = Intent("com.wework.autoreply.BATCH_SEND_PROGRESS")
        intent.putExtra("historyId", sendHistoryId)
        intent.putExtra("sentCount", sentCount)
        intent.putExtra("failedCount", failedCount)
        intent.putExtra("currentIndex", currentChatIndex)
        intent.putExtra("totalCount", groupChats.size)
        sendBroadcast(intent)
    }

    /**
     * 更新最终状态
     */
    private fun updateFinalStatus() {
        val intent = Intent("com.wework.autoreply.BATCH_SEND_COMPLETE")
        intent.putExtra("historyId", sendHistoryId)
        intent.putExtra("sentCount", sentCount)
        intent.putExtra("failedCount", failedCount)
        intent.putExtra("failedChats", failedChats.joinToString(","))
        sendBroadcast(intent)
    }
}

