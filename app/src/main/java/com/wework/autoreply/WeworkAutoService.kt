package com.wework.autoreply

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 企业微信自动化服务
 * 实现批量通过好友申请并邀请进群
 */
class WeworkAutoService : AccessibilityService() {

    companion object {
        private const val TAG = "WeworkAutoService"
        private const val WEWORK_PACKAGE = "com.tencent.wework"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isProcessing = false
    private var targetGroupName = ""
    private var hasClickedWeworkDialog = false  // 标记是否已点击过双企微弹窗

    // 统计数据
    private var approvedCount = 0
    private var invitedCount = 0
    private var failedCount = 0
    private val approvedCustomers = mutableListOf<String>()  // 存储已通过的客户名称

    // 当前处理状态
    private enum class ProcessState {
        IDLE,                    // 空闲
        OPENING_WEWORK,          // 打开企业微信
        NAVIGATING_TO_CONTACTS,  // 导航到通讯录
        OPENING_NEW_CUSTOMERS,   // 打开新的客户
        PROCESSING_CUSTOMER,     // 处理客户
        APPROVING,               // 通过验证
        RETURNING_TO_LIST,       // 返回列表
        NAVIGATING_TO_MESSAGES,  // 导航到消息页面
        OPENING_GROUP_CHAT,      // 打开群聊
        OPENING_GROUP_MEMBERS,   // 打开群成员列表
        CLICKING_ADD_BUTTON,     // 点击添加按钮
        SELECTING_MY_CUSTOMERS,  // 选择我的客户
        SELECTING_CUSTOMERS,     // 选择客户
        CONFIRMING_INVITE,       // 确认邀请
        COMPLETED                // 完成
    }

    private var currentState = ProcessState.IDLE
    private var currentCustomerIndex = 0

    // 滚动查找群聊的重试计数
    private var scrollRetryCount = 0
    private val MAX_SCROLL_RETRY = 10  // 最多滚动10次

    // 导航到消息页面的重试计数
    private var navigateRetryCount = 0
    private val MAX_NAVIGATE_RETRY = 10  // 最多重试10次

    // 测试滚动模式相关变量
    private var testScrollCount = 0
    private var previousViewButtonCount = 0

    // 广播接收器 - 接收开始批量处理的指令
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "📡 收到广播: ${intent?.action}")
            sendLog("📡 收到广播: ${intent?.action}")
            when (intent?.action) {
                "com.wework.autoreply.START_BATCH_PROCESS" -> {
                    targetGroupName = intent.getStringExtra("groupName") ?: ""
                    Log.d(TAG, "🎯 目标群聊: $targetGroupName")
                    sendLog("🎯 目标群聊: $targetGroupName")
                    startBatchProcess()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ 服务已创建 onCreate()")
        Toast.makeText(this, "✅ WeworkAutoService已创建", Toast.LENGTH_LONG).show()
        sendLog("✅ WeworkAutoService已创建")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ 服务已连接 onServiceConnected()")
        Toast.makeText(this, "✅ WeworkAutoService已连接", Toast.LENGTH_LONG).show()
        sendLog("✅ WeworkAutoService已连接并准备就绪")

        // 注册广播接收器
        val filter = IntentFilter("com.wework.autoreply.START_BATCH_PROCESS")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
                Log.d(TAG, "✅ 广播接收器已注册 (EXPORTED)")
            } else {
                registerReceiver(commandReceiver, filter)
                Log.d(TAG, "✅ 广播接收器已注册")
            }
            Toast.makeText(this, "✅ 广播接收器已注册", Toast.LENGTH_SHORT).show()
            sendLog("✅ 广播接收器已注册，等待指令...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注册广播接收器失败", e)
            Toast.makeText(this, "❌ 注册失败: ${e.message}", Toast.LENGTH_LONG).show()
            sendLog("❌ 注册广播接收器失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ 服务已销毁")
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "取消注册接收器失败", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 强制输出日志到logcat（不依赖TAG）
        android.util.Log.e("WEWORK_DEBUG", "📱 事件: pkg=${event.packageName}, type=${event.eventType}, isProcessing=$isProcessing")

        // 🔥 优先处理双企微选择弹窗（同时检查功能一和功能二的SharedPreferences）
        if (event.packageName == "com.vivo.doubleinstance") {
            // 检查功能一是否应该启动
            val prefsAuto = getSharedPreferences("wework_auto", android.content.Context.MODE_PRIVATE)
            val shouldStartAuto = prefsAuto.getBoolean("should_start", false)

            // 检查功能二是否应该启动
            val prefsBatch = getSharedPreferences("batch_send", android.content.Context.MODE_PRIVATE)
            val shouldStartBatch = prefsBatch.getBoolean("should_start", false)

            android.util.Log.e(TAG, "🔍 检测到双企微弹窗! hasClickedWeworkDialog=$hasClickedWeworkDialog, shouldStartAuto=$shouldStartAuto, shouldStartBatch=$shouldStartBatch")

            // 只有其中一个为true时才处理弹窗
            if (!shouldStartAuto && !shouldStartBatch) {
                android.util.Log.e(TAG, "⚠️ 两个功能都不需要启动,跳过处理弹窗")
                return
            }

            // 🔥 检查是否是新任务启动,如果是,重置hasClickedWeworkDialog标志
            // 这样可以确保每次新任务启动时都能正确处理弹窗
            if (shouldStartAuto) {
                val startTime = prefsAuto.getLong("start_time", 0)
                val timeDiff = System.currentTimeMillis() - startTime
                if (timeDiff < 3000) {  // 3秒内认为是新任务启动
                    android.util.Log.e(TAG, "🔄 检测到功能一新任务启动(timeDiff=${timeDiff}ms),重置hasClickedWeworkDialog")
                    hasClickedWeworkDialog = false
                }
            }
            if (shouldStartBatch) {
                val startTime = prefsBatch.getLong("start_time", 0)
                val timeDiff = System.currentTimeMillis() - startTime
                if (timeDiff < 3000) {  // 3秒内认为是新任务启动
                    android.util.Log.e(TAG, "🔄 检测到功能二新任务启动(timeDiff=${timeDiff}ms),重置hasClickedWeworkDialog")
                    hasClickedWeworkDialog = false
                }
            }

            // 🔥 只点击一次,避免重复处理
            if (hasClickedWeworkDialog) {
                android.util.Log.e(TAG, "⚠️ 已经点击过弹窗,跳过")
                return
            }

            android.util.Log.e(TAG, "🔍 检测到双企微选择弹窗!")
            sendLog("🔍 检测到双企微选择弹窗!")

            // 获取目标企微
            val weworkTarget = getString(R.string.wework_target)
            android.util.Log.e(TAG, "🎯 目标企微: $weworkTarget")

            android.util.Log.e(TAG, "🎯 准备调用clickWeworkByCoordinate()")
            // 🎯 立即点击,不延迟!
            clickWeworkByCoordinate(weworkTarget)
            android.util.Log.e(TAG, "✅ clickWeworkByCoordinate()调用完成")

            // 标记已点击
            hasClickedWeworkDialog = true
            android.util.Log.e(TAG, "✅ hasClickedWeworkDialog已设置为true")
            return
        }

        // 检查是否需要启动批量处理(只处理功能一,不处理功能二)
        if (!isProcessing && event.packageName == "com.tencent.wework") {
            // 🔥 检查是否是功能二启动的
            val prefsBatch = getSharedPreferences("batch_send", android.content.Context.MODE_PRIVATE)
            val shouldStartBatch = prefsBatch.getBoolean("should_start", false)

            // 如果是功能二,不要处理,让BatchSendService处理
            if (shouldStartBatch) {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 功能二启动,WeworkAutoService不处理企微事件")
                return
            }

            android.util.Log.e("WEWORK_DEBUG", "🔍 检测到企业微信，检查是否需要启动批量处理...")
            checkAndStartBatchProcess()
        }

        // 如果正在处理，根据当前状态处理事件
        if (isProcessing) {
            android.util.Log.e("WEWORK_DEBUG", "⚙️ 正在处理，当前状态: $currentState")
            when (currentState) {
                ProcessState.OPENING_WEWORK -> handleWeworkOpened(event)
                ProcessState.NAVIGATING_TO_CONTACTS -> handleContactsPage(event)
                ProcessState.OPENING_NEW_CUSTOMERS -> handleNewCustomersPage(event)
                ProcessState.PROCESSING_CUSTOMER -> handleCustomerDetail(event)
                ProcessState.APPROVING -> handleApprovalPage(event)
                ProcessState.RETURNING_TO_LIST -> handleReturnToList(event)
                else -> {}
            }
        }
    }

    /**
     * 检查SharedPreferences，如果需要则启动批量处理
     */
    private fun checkAndStartBatchProcess() {
        android.util.Log.e("WEWORK_DEBUG", "🔍 checkAndStartBatchProcess() 被调用")

        val prefs = getSharedPreferences("wework_auto", Context.MODE_PRIVATE)

        // 检查是否测试滚动模式
        val testScrollMode = prefs.getBoolean("test_scroll_mode", false)
        if (testScrollMode) {
            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            android.util.Log.e("WEWORK_DEBUG", "🧪 检测到测试滚动模式")

            if (timeDiff < 60000) {
                android.util.Log.e("WEWORK_DEBUG", "🚀 开始测试滚动查找好友!")

                Toast.makeText(this, "🧪 测试滚动查找好友", Toast.LENGTH_LONG).show()

                // 清除标志
                prefs.edit().putBoolean("test_scroll_mode", false).apply()

                // 重置测试变量
                testScrollCount = 0
                previousViewButtonCount = 0

                // 开始测试流程
                isProcessing = true
                currentState = ProcessState.NAVIGATING_TO_CONTACTS
                handler.postDelayed({
                    navigateToContacts()
                }, 1500)
            }
            return
        }

        val shouldStart = prefs.getBoolean("should_start", false)

        android.util.Log.e("WEWORK_DEBUG", "📋 shouldStart = $shouldStart")

        if (shouldStart) {
            val groupName = prefs.getString("target_group_name", "") ?: ""
            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            android.util.Log.e("WEWORK_DEBUG", "📋 groupName = $groupName, timeDiff = $timeDiff ms")

            // 检查是否在60秒内（避免重复触发）
            if (timeDiff < 60000 && groupName.isNotEmpty()) {
                android.util.Log.e("WEWORK_DEBUG", "🚀 开始批量处理！群聊名称: $groupName")

                // 显示Toast
                Toast.makeText(this, "🚀 开始批量处理: $groupName", Toast.LENGTH_LONG).show()

                // 清除标志
                prefs.edit().putBoolean("should_start", false).apply()  // 🔥 修复: 使用正确的键名

                // 保存群聊名称
                targetGroupName = groupName

                // 开始批量处理
                startBatchProcess()
            } else {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 不满足启动条件: timeDiff=$timeDiff, groupName=$groupName")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "服务被中断")
    }

    /**
     * 开始批量处理流程
     */
    private fun startBatchProcess() {
        android.util.Log.e("WEWORK_DEBUG", "📍 startBatchProcess() 开始执行")
        android.util.Log.e("WEWORK_DEBUG", "📍 当前 isProcessing = $isProcessing")

        // 🔥 修复: 如果已有任务在进行中，先停止旧任务
        if (isProcessing) {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 已有任务在进行中，停止旧任务")
            sendLog("⚠️ 停止旧任务，启动新任务")
            stopProcessing()
            // 等待旧任务停止后再启动新任务
            handler.postDelayed({
                startBatchProcess()
            }, 500)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "📍 设置 isProcessing = true")
        isProcessing = true
        hasClickedWeworkDialog = false  // 🔥 重置弹窗点击标志
        currentState = ProcessState.OPENING_WEWORK
        currentCustomerIndex = 0
        approvedCount = 0
        invitedCount = 0
        failedCount = 0
        approvedCustomers.clear()  // 清空已通过客户列表
        scrollRetryCount = 0  // 🔥 重置滚动重试计数
        navigateRetryCount = 0  // 🔥 重置导航重试计数

        android.util.Log.e("WEWORK_DEBUG", "📍 isProcessing 已设置为: $isProcessing")
        android.util.Log.e("WEWORK_DEBUG", "📍 currentState = $currentState")

        sendLog("🚀 开始批量处理流程")
        sendLog("📱 目标群聊: $targetGroupName")

        // 🔥 修复: 调用openWework()打开企业微信
        android.util.Log.e("WEWORK_DEBUG", "📍 准备打开企业微信...")
        openWework()
    }

    /**
     * 打开企业微信应用
     */
    private fun openWework() {
        try {
            android.util.Log.e(TAG, "🚀 openWework() 被调用")
            sendLog("🚀 openWework() 被调用")

            // 获取当前应用的目标企微(从资源文件)
            val weworkTarget = getString(R.string.wework_target)
            android.util.Log.e(TAG, "🎯 目标企微: $weworkTarget")
            sendLog("🎯 目标企微: $weworkTarget")

            val intent = packageManager.getLaunchIntentForPackage(WEWORK_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                android.util.Log.e(TAG, "✅ 已调用startActivity打开企业微信")
                sendLog("✅ 正在打开企业微信...")

                // 🔥 弹窗会在onAccessibilityEvent中自动处理
                // 点击成功后会自动调用navigateToContacts
            } else {
                android.util.Log.e(TAG, "❌ 未找到企业微信应用")
                sendLog("❌ 未找到企业微信应用")
                stopProcessing()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ 打开企业微信失败", e)
            sendLog("❌ 打开企业微信失败: ${e.message}")
            stopProcessing()
        }
    }

    /**
     * 🎯 通过resource-id查找并点击企微选项
     * 适配所有机型,不使用硬编码坐标
     */
    private fun clickWeworkByCoordinate(targetWework: String) {
        try {
            android.util.Log.e(TAG, "🎯 开始查找并点击企微选项,目标: $targetWework")
            sendLog("🎯 开始查找并点击: $targetWework")

            val rootNode = rootInActiveWindow ?: run {
                android.util.Log.e(TAG, "❌ 无法获取窗口信息")
                return
            }

            // 🔍 查找目标resource-id
            val targetResourceId = if (targetWework == "企业微信") {
                "com.vivo.doubleinstance:id/main"
            } else {
                "com.vivo.doubleinstance:id/clone"
            }

            android.util.Log.e(TAG, "🔍 查找resource-id: $targetResourceId")

            // 递归查找目标节点
            val targetNode = findNodeByResourceIdRecursive(rootNode, targetResourceId)

            if (targetNode == null) {
                android.util.Log.e(TAG, "❌ 未找到目标节点: $targetResourceId")
                sendLog("❌ 未找到目标企微选项")
                return
            }

            android.util.Log.e(TAG, "✅ 找到目标节点: $targetResourceId")

            // 获取节点坐标
            val rect = android.graphics.Rect()
            targetNode.getBoundsInScreen(rect)
            val centerX = (rect.left + rect.right) / 2
            val centerY = (rect.top + rect.bottom) / 2

            android.util.Log.e(TAG, "📍 节点坐标: ($centerX, $centerY), bounds=$rect")

            // 🔥 方案1: 使用performAction点击
            val clicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            android.util.Log.e(TAG, "🔥 performAction点击结果: $clicked")

            if (clicked) {
                android.util.Log.e(TAG, "✅ 点击成功!")
                sendLog("✅ 已自动选择: $targetWework")

                // 🔥 检查是功能一还是功能二
                val prefsAuto = getSharedPreferences("wework_auto", android.content.Context.MODE_PRIVATE)
                val shouldStartAuto = prefsAuto.getBoolean("should_start", false)

                // 只有功能一才导航到通讯录,功能二让BatchSendService接管
                if (shouldStartAuto) {
                    android.util.Log.e(TAG, "⏰ 功能一启动,3秒后导航到通讯录")
                    handler.postDelayed({
                        android.util.Log.e(TAG, "⏰ 3秒延迟结束,开始导航到通讯录")
                        currentState = ProcessState.NAVIGATING_TO_CONTACTS
                        navigateToContacts()
                    }, 3000)
                } else {
                    android.util.Log.e(TAG, "⏰ 功能二启动,不导航到通讯录,让BatchSendService接管")
                }
            } else {
                // 🔥 方案2: 使用GestureDescription点击坐标
                android.util.Log.e(TAG, "⚠️ performAction失败,尝试坐标点击")

                val path = android.graphics.Path()
                path.moveTo(centerX.toFloat(), centerY.toFloat())

                val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
                val strokeDescription = android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, 100
                )
                gestureBuilder.addStroke(strokeDescription)

                val gesture = gestureBuilder.build()
                val result = dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        android.util.Log.e(TAG, "✅ 坐标点击成功!")
                        sendLog("✅ 已自动选择: $targetWework")

                        // 🔥 检查是功能一还是功能二
                        val prefsAuto = getSharedPreferences("wework_auto", android.content.Context.MODE_PRIVATE)
                        val shouldStartAuto = prefsAuto.getBoolean("should_start", false)

                        // 只有功能一才导航到通讯录,功能二让BatchSendService接管
                        if (shouldStartAuto) {
                            android.util.Log.e(TAG, "⏰ 功能一启动,3秒后导航到通讯录")
                            handler.postDelayed({
                                android.util.Log.e(TAG, "⏰ 3秒延迟结束,开始导航到通讯录")
                                currentState = ProcessState.NAVIGATING_TO_CONTACTS
                                navigateToContacts()
                            }, 3000)
                        } else {
                            android.util.Log.e(TAG, "⏰ 功能二启动,不导航到通讯录,让BatchSendService接管")
                        }
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        android.util.Log.e(TAG, "❌ 坐标点击被取消")
                    }
                }, null)

                if (!result) {
                    android.util.Log.e(TAG, "❌ dispatchGesture返回false")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ 点击失败", e)
            sendLog("❌ 点击失败: ${e.message}")
        }
    }

    /**
     * 根据resource-id查找所有匹配的节点
     */
    /**
     * 递归查找指定resource-id的节点
     */
    private fun findNodeByResourceIdRecursive(node: AccessibilityNodeInfo?, resourceId: String): AccessibilityNodeInfo? {
        if (node == null) return null

        // 检查当前节点
        if (node.viewIdResourceName == resourceId) {
            return node
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findNodeByResourceIdRecursive(child, resourceId)
            if (found != null) {
                return found
            }
        }

        return null
    }

    /**
     * 处理双企微选择弹窗 - 已废弃,使用clickWeworkByCoordinate代替
     */
    private fun handleWeworkSelectionDialog(targetWework: String) {
        // 此方法已废弃
    }

    /**
     * 导航到通讯录页面
     */
    private fun navigateToContacts() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 navigateToContacts() 被调用")
        sendLog("📋 正在导航到通讯录...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找通讯录按钮")

        // 打印界面上的所有文本（仅第一次）
        if (currentCustomerIndex == 0) {
            printAllTexts(rootNode)
        }

        // 查找"通讯录"按钮
        val contactsButton = findNodeByText(rootNode, "通讯录")
        if (contactsButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到通讯录按钮，准备点击")
            clickNode(contactsButton)
            sendLog("✅ 已点击通讯录")

            handler.postDelayed({
                currentState = ProcessState.OPENING_NEW_CUSTOMERS
                openNewCustomers()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到通讯录按钮，1秒后重试")
            sendLog("⚠️ 未找到通讯录按钮，重试中...")
            handler.postDelayed({ navigateToContacts() }, 1000)
        }
    }

    /**
     * 打印节点树中的所有文本（用于调试）
     */
    private fun printAllTexts(node: AccessibilityNodeInfo, depth: Int = 0) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""

        if (text.isNotEmpty() || contentDesc.isNotEmpty() || resourceId.isNotEmpty()) {
            val indent = "  ".repeat(depth)
            android.util.Log.e("WEWORK_DEBUG", "${indent}📝 text='$text', desc='$contentDesc', id='$resourceId', class=${node.className}")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { printAllTexts(it, depth + 1) }
        }
    }

    /**
     * 打开"新的客户"页面
     */
    private fun openNewCustomers() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 openNewCustomers() 被调用")
        sendLog("👥 正在打开添加客户...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找添加客户按钮")

        // 打印界面上的所有文本（仅第一次）
        if (currentCustomerIndex == 0) {
            android.util.Log.e("WEWORK_DEBUG", "📋 打印通讯录页面的所有文本：")
            printAllTexts(rootNode)
        }

        // 查找"添加客户"按钮（通讯录页面上的按钮）
        val addCustomerButton = findNodeByText(rootNode, "添加客户")
        if (addCustomerButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到添加客户按钮，准备点击")
            clickNode(addCustomerButton)
            sendLog("✅ 已点击添加客户")

            // 等待进入添加客户页面，然后点击"新的客户"标签
            handler.postDelayed({
                clickNewCustomersTab()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到添加客户按钮，1秒后重试")
            sendLog("⚠️ 未找到添加客户按钮，重试中...")
            handler.postDelayed({ openNewCustomers() }, 1000)
        }
    }

    /**
     * 点击"新的客户"标签
     */
    private fun clickNewCustomersTab() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickNewCustomersTab() 被调用")
        sendLog("👥 正在点击新的客户标签...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找新的客户标签")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印添加客户页面的所有文本：")
        printAllTexts(rootNode)

        // 查找"新的客户"标签
        val newCustomersTab = findNodeByText(rootNode, "新的客户")
        if (newCustomersTab != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到新的客户标签，准备点击")
            clickNode(newCustomersTab)
            sendLog("✅ 已点击新的客户")

            // 检查是否是测试滚动模式
            val prefs = getSharedPreferences("wework_auto", Context.MODE_PRIVATE)
            val isTestMode = prefs.getBoolean("test_scroll_mode", false)

            if (isTestMode) {
                // 测试模式: 等待进入新的客户列表，然后开始测试滚动
                android.util.Log.e("WEWORK_DEBUG", "🧪 测试模式: 准备开始测试滚动")
                handler.postDelayed({
                    testScrollFindViewButtons()
                }, 1500)
            } else {
                // 正常模式: 等待进入新的客户列表，然后开始处理客户
                handler.postDelayed({
                    currentState = ProcessState.PROCESSING_CUSTOMER
                    processNextCustomer()
                }, 1500)
            }
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到新的客户标签，1秒后重试")
            sendLog("⚠️ 未找到新的客户标签，重试中...")
            handler.postDelayed({ clickNewCustomersTab() }, 1000)
        }
    }

    /**
     * 处理下一个客户
     */
    private fun processNextCustomer() {
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "🔧 processNextCustomer() 被调用")
        android.util.Log.e("WEWORK_DEBUG", "📊 当前状态: currentState=$currentState")
        android.util.Log.e("WEWORK_DEBUG", "📊 当前客户索引: currentCustomerIndex=$currentCustomerIndex")
        android.util.Log.e("WEWORK_DEBUG", "📊 已通过客户数: approvedCount=$approvedCount")
        android.util.Log.e("WEWORK_DEBUG", "📊 已通过客户列表: approvedCustomers=$approvedCustomers")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        sendLog("🔄 正在查找待处理客户...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找'查看'按钮")

        // 打印界面上的所有文本（仅第一次）
        if (currentCustomerIndex == 0) {
            android.util.Log.e("WEWORK_DEBUG", "📋 打印添加客户页面的所有文本：")
            printAllTexts(rootNode)
        }

        // 查找所有"查看"按钮
        val viewButtons = findAllNodesByText(rootNode, "查看")
        android.util.Log.e("WEWORK_DEBUG", "📋 找到 ${viewButtons.size} 个'查看'按钮")

        if (viewButtons.isEmpty()) {
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "✅ 所有好友申请已通过！")
            android.util.Log.e("WEWORK_DEBUG", "📊 统计: 通过${approvedCount}个, 失败${failedCount}个")
            android.util.Log.e("WEWORK_DEBUG", "📊 已通过客户列表: $approvedCustomers")
            android.util.Log.e("WEWORK_DEBUG", "🔄 准备进入邀请到群聊的流程")
            android.util.Log.e("WEWORK_DEBUG", "🔄 1.5秒后将状态改为 NAVIGATING_TO_MESSAGES")
            android.util.Log.e("WEWORK_DEBUG", "========================================")

            sendLog("✅ 所有好友申请已通过！")
            sendLog("📊 统计: 通过${approvedCount}个, 失败${failedCount}个")

            // 进入邀请到群聊的流程
            handler.postDelayed({
                android.util.Log.e("WEWORK_DEBUG", "🔄 状态已改为 NAVIGATING_TO_MESSAGES，调用 navigateToMessages()")
                currentState = ProcessState.NAVIGATING_TO_MESSAGES
                navigateToMessages()
            }, 1500)
            return
        }

        // 点击第一个"查看"按钮
        val firstViewButton = viewButtons[0]
        android.util.Log.e("WEWORK_DEBUG", "👆 点击第一个'查看'按钮")
        sendLog("👤 正在处理第 ${currentCustomerIndex + 1} 个客户...")

        clickNode(firstViewButton)

        handler.postDelayed({
            currentState = ProcessState.APPROVING
            approveCustomer()
        }, 1500)
    }

    /**
     * 通过客户验证
     */
    private fun approveCustomer() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 approveCustomer() 被调用")
        sendLog("✅ 正在通过验证...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        // 获取客户名称（通过resource-id查找）
        val nameNode = findNodeByResourceId(rootNode, "com.tencent.wework:id/moj")
        val customerName = nameNode?.text?.toString() ?: "未知客户"
        android.util.Log.e("WEWORK_DEBUG", "📝 客户名称: $customerName")
        sendLog("👤 客户: $customerName")

        // 查找"通过验证"按钮
        val approveButton = findNodeByText(rootNode, "通过验证")
        if (approveButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到通过验证按钮，准备点击")
            clickNode(approveButton)
            approvedCount++
            approvedCustomers.add(customerName)  // 保存已通过的客户名称
            android.util.Log.e("WEWORK_DEBUG", "📝 已保存客户名称: $customerName，当前列表: ${approvedCustomers.joinToString(", ")}")
            sendStats()
            sendLog("✅ 已通过验证")

            // 点击"完成"按钮
            handler.postDelayed({
                clickCompleteButton()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到通过验证按钮")
            sendLog("⚠️ 未找到通过验证按钮，跳过此客户")
            failedCount++
            sendStats()
            returnToCustomerList()
        }
    }

    /**
     * 点击"完成"按钮
     */
    private fun clickCompleteButton() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickCompleteButton() 被调用")
        sendLog("👆 点击完成...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        // 查找"完成"按钮
        val completeButton = findNodeByText(rootNode, "完成")
        if (completeButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到完成按钮，准备点击")
            clickNode(completeButton)
            sendLog("✅ 已点击完成，等待加载...")

            // 智能等待加载完成
            waitForLoadingComplete(0)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到完成按钮")
            sendLog("⚠️ 未找到完成按钮")
            failedCount++
            sendStats()
            returnToCustomerList()
        }
    }

    /**
     * 智能等待加载完成
     * @param retryCount 重试次数
     */
    private fun waitForLoadingComplete(retryCount: Int) {
        android.util.Log.e("WEWORK_DEBUG", "🔧 waitForLoadingComplete() 被调用, retryCount=$retryCount")

        if (retryCount >= 10) {
            // 超过10次重试（10秒），认为加载失败
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 等待加载超时，强制继续")
            sendLog("⚠️ 等待加载超时")
            returnToCustomerList()
            return
        }

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ rootInActiveWindow 为 null，1秒后重试")
            handler.postDelayed({
                waitForLoadingComplete(retryCount + 1)
            }, 1000)
            return
        }

        // 检查是否还在加载中（查找加载相关的文本或元素）
        val isLoading = checkIfLoading(rootNode)

        if (isLoading) {
            android.util.Log.e("WEWORK_DEBUG", "⏳ 页面仍在加载中，1秒后重试...")
            sendLog("⏳ 加载中...")
            handler.postDelayed({
                waitForLoadingComplete(retryCount + 1)
            }, 1000)
        } else {
            // 检查是否已经到达客户列表或客户详情页
            val hasViewButton = findNodeByText(rootNode, "查看") != null
            val hasCustomerDetail = findNodeByText(rootNode, "备注") != null ||
                                   findNodeByText(rootNode, "发消息") != null

            if (hasViewButton) {
                // 已经在客户列表页面
                android.util.Log.e("WEWORK_DEBUG", "✅ 加载完成，已在客户列表页面")
                sendLog("✅ 加载完成")
                returnToCustomerList()
            } else if (hasCustomerDetail) {
                // 在客户详情页面，需要返回
                android.util.Log.e("WEWORK_DEBUG", "✅ 加载完成，当前在客户详情页面")
                sendLog("✅ 加载完成")
                returnToCustomerList()
            } else {
                // 页面状态不明确，再等待一次
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 页面状态不明确，1秒后重试...")
                handler.postDelayed({
                    waitForLoadingComplete(retryCount + 1)
                }, 1000)
            }
        }
    }

    /**
     * 检查页面是否正在加载
     */
    private fun checkIfLoading(rootNode: AccessibilityNodeInfo): Boolean {
        // 查找常见的加载指示器
        val loadingTexts = listOf("加载中", "请稍候", "Loading", "正在加载")

        for (text in loadingTexts) {
            if (findNodeByText(rootNode, text) != null) {
                android.util.Log.e("WEWORK_DEBUG", "🔍 检测到加载文本: $text")
                return true
            }
        }

        // 查找ProgressBar（通过className）
        if (findNodeByClassName(rootNode, "android.widget.ProgressBar") != null) {
            android.util.Log.e("WEWORK_DEBUG", "🔍 检测到ProgressBar")
            return true
        }

        return false
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
     * 根据resource-id查找节点
     */
    private fun findNodeByResourceId(node: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == resourceId) {
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
     * 返回客户列表
     */
    private fun returnToCustomerList() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 returnToCustomerList() 被调用")
        sendLog("🔙 返回客户列表...")
        currentState = ProcessState.RETURNING_TO_LIST

        // 智能检测当前页面，决定返回次数
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val hasViewButton = findNodeByText(rootNode, "查看") != null

            if (hasViewButton) {
                // 已经在客户列表页面，直接处理下一个
                android.util.Log.e("WEWORK_DEBUG", "✅ 已在客户列表页面")
                handler.postDelayed({
                    currentState = ProcessState.PROCESSING_CUSTOMER
                    currentCustomerIndex++
                    processNextCustomer()
                }, 1000)
            } else {
                // 需要返回到客户列表
                android.util.Log.e("WEWORK_DEBUG", "⬅️ 按返回键返回客户列表")
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({
                    // 再次检查是否到达客户列表
                    val checkNode = rootInActiveWindow
                    val hasView = checkNode?.let { findNodeByText(it, "查看") != null } ?: false

                    if (!hasView) {
                        // 还没到，再按一次返回
                        android.util.Log.e("WEWORK_DEBUG", "⬅️ 再次按返回键")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }

                    handler.postDelayed({
                        currentState = ProcessState.PROCESSING_CUSTOMER
                        currentCustomerIndex++
                        processNextCustomer()
                    }, 1000)
                }, 1000)
            }
        } else {
            // 无法获取界面信息，按固定次数返回
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 无法获取界面信息，按固定次数返回")
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({
                currentState = ProcessState.PROCESSING_CUSTOMER
                currentCustomerIndex++
                processNextCustomer()
            }, 1500)
        }
    }

    /**
     * 停止处理
     */
    private fun stopProcessing() {
        isProcessing = false
        currentState = ProcessState.IDLE
        hasClickedWeworkDialog = false  // 重置弹窗点击标志
        sendLog("⏹️ 批量处理已停止")
    }

    /**
     * 导航到消息页面
     */
    private fun navigateToMessages() {
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "🔧 navigateToMessages() 被调用")
        android.util.Log.e("WEWORK_DEBUG", "📊 当前状态: currentState=$currentState")
        android.util.Log.e("WEWORK_DEBUG", "📊 重试次数: $navigateRetryCount/$MAX_NAVIGATE_RETRY")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        sendLog("📱 正在导航到消息页面...")

        // 🔥 检查重试次数
        if (navigateRetryCount >= MAX_NAVIGATE_RETRY) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 导航到消息页面失败，已达到最大重试次数")
            sendLog("❌ 导航到消息页面失败，请检查企业微信状态")
            stopProcessing()
            return
        }

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            navigateRetryCount++
            handler.postDelayed({ navigateToMessages() }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始检查当前页面")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印当前页面的所有文本：")
        printAllTexts(rootNode)

        // 🔥 简化逻辑: 只检查两种情况
        // 1. 是否已经在消息列表页面（有RecyclerView）
        // 2. 是否在企业微信主页面（有底部导航栏）

        val recyclerView = findNodeByResourceId(rootNode, "com.tencent.wework:id/czy")
        val hasMessageTab = findNodeByText(rootNode, "消息") != null
        val hasContactTab = findNodeByText(rootNode, "通讯录") != null

        android.util.Log.e("WEWORK_DEBUG", "📋 页面检查:")
        android.util.Log.e("WEWORK_DEBUG", "   - recyclerView=${recyclerView != null}")
        android.util.Log.e("WEWORK_DEBUG", "   - hasMessageTab=$hasMessageTab")
        android.util.Log.e("WEWORK_DEBUG", "   - hasContactTab=$hasContactTab")

        if (recyclerView != null) {
            // 已经在消息列表页面，直接打开群聊
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "✅ 已经在消息列表页面，准备查找群聊")
            android.util.Log.e("WEWORK_DEBUG", "🔄 1.5秒后将状态改为 OPENING_GROUP_CHAT")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            sendLog("✅ 已在消息页面")

            // 🔥 重置重试计数
            navigateRetryCount = 0

            handler.postDelayed({
                android.util.Log.e("WEWORK_DEBUG", "🔄 状态已改为 OPENING_GROUP_CHAT，调用 openGroupChat()")
                currentState = ProcessState.OPENING_GROUP_CHAT
                openGroupChat()
            }, 1500)
        } else if (hasMessageTab && hasContactTab) {
            // 在企业微信主页面，点击"消息"按钮
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "✅ 检测到在企业微信主页面，点击'消息'按钮")
            android.util.Log.e("WEWORK_DEBUG", "========================================")

            val messagesButton = findNodeByText(rootNode, "消息")
            if (messagesButton != null) {
                clickNode(messagesButton)
                sendLog("✅ 已点击消息")
                navigateRetryCount = 0

                handler.postDelayed({
                    android.util.Log.e("WEWORK_DEBUG", "🔄 状态已改为 OPENING_GROUP_CHAT，调用 openGroupChat()")
                    currentState = ProcessState.OPENING_GROUP_CHAT
                    openGroupChat()
                }, 1500)
            } else {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到'消息'按钮，1秒后重试")
                navigateRetryCount++
                handler.postDelayed({ navigateToMessages() }, 1000)
            }
        } else {
            // 不在主页面也不在消息列表，按返回键
            android.util.Log.e("WEWORK_DEBUG", "⬅️ 不在目标页面，按返回键")
            sendLog("⬅️ 返回主页面...")
            performGlobalAction(GLOBAL_ACTION_BACK)
            navigateRetryCount++
            android.util.Log.e("WEWORK_DEBUG", "⬅️ 已按返回键，1秒后重新检查页面")
            handler.postDelayed({ navigateToMessages() }, 1000)
        }
    }

    /**
     * 打开群聊 - 使用搜索功能
     */
    private fun openGroupChat() {
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "🔧 openGroupChat() 被调用")
        android.util.Log.e("WEWORK_DEBUG", "📊 当前状态: currentState=$currentState")
        android.util.Log.e("WEWORK_DEBUG", "📊 目标群聊名称: '$targetGroupName'")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        sendLog("🔍 正在搜索群聊: $targetGroupName")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        // 查找放大镜按钮 (resource-id="com.tencent.wework:id/nht")
        val searchButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/nht")
        if (searchButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到放大镜按钮")
            sendLog("✅ 找到搜索按钮")
            clickNode(searchButton)

            // 等待搜索页面打开
            handler.postDelayed({
                inputSearchText()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到放大镜按钮")
            sendLog("❌ 未找到搜索按钮")
            Toast.makeText(this, "❌ 未找到搜索按钮", Toast.LENGTH_LONG).show()
            stopProcessing()
        }
    }

    /**
     * 输入搜索文本
     */
    private fun inputSearchText() {
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "⌨️ inputSearchText() 被调用")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        sendLog("⌨️ 输入搜索文本: $targetGroupName")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ inputSearchText() }, 1000)
            return
        }

        // 查找搜索输入框 (通常是EditText)
        val searchInput = findEditText(rootNode)
        if (searchInput != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到搜索输入框")
            sendLog("✅ 找到搜索输入框")

            // 输入搜索文本
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetGroupName)
            searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            android.util.Log.e("WEWORK_DEBUG", "✅ 已输入搜索文本: $targetGroupName")
            sendLog("✅ 已输入搜索文本")

            // 等待搜索结果
            handler.postDelayed({
                clickSearchResult()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索输入框")
            sendLog("❌ 未找到搜索输入框")
            Toast.makeText(this, "❌ 未找到搜索输入框", Toast.LENGTH_LONG).show()
            stopProcessing()
        }
    }

    /**
     * 点击搜索结果
     */
    private fun clickSearchResult() {
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "🎯 clickSearchResult() 被调用")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        sendLog("🎯 查找搜索结果")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ clickSearchResult() }, 1000)
            return
        }

        // 查找RecyclerView中所有可点击的ViewGroup
        val recyclerView = findNodeByResourceId(rootNode, "com.tencent.wework:id/ks8")
        if (recyclerView != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到RecyclerView")

            // 查找所有可点击的ViewGroup
            val clickableGroups = mutableListOf<AccessibilityNodeInfo>()
            findClickableViewGroups(recyclerView, clickableGroups)

            android.util.Log.e("WEWORK_DEBUG", "📊 找到 ${clickableGroups.size} 个可点击的ViewGroup")

            // 遍历所有可点击的ViewGroup,查找包含目标群聊名称的
            for ((index, group) in clickableGroups.withIndex()) {
                val hasTargetText = containsText(group, targetGroupName)
                android.util.Log.e("WEWORK_DEBUG", "🔍 ViewGroup[$index] 包含目标文本: $hasTargetText")

                if (hasTargetText) {
                    android.util.Log.e("WEWORK_DEBUG", "✅ 找到包含 '$targetGroupName' 的ViewGroup，准备点击")
                    sendLog("✅ 找到搜索结果")
                    clickNode(group)
                    sendLog("✅ 已打开群聊")

                    // 点击群聊后，点击右上角三个点进入群详情
                    handler.postDelayed({
                        currentState = ProcessState.OPENING_GROUP_MEMBERS
                        clickThreeDotsInChat()
                    }, 1500)
                    return
                }
            }

            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到包含目标文本的ViewGroup")
            sendLog("❌ 未找到搜索结果")
            Toast.makeText(this, "❌ 未找到群聊: $targetGroupName", Toast.LENGTH_LONG).show()
            stopProcessing()
        } else {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到RecyclerView")
            sendLog("❌ 未找到搜索结果列表")
            Toast.makeText(this, "❌ 未找到搜索结果列表", Toast.LENGTH_LONG).show()
            stopProcessing()
        }
    }

    // 🔥 新增: 递归查找所有可点击的聊天列表项
    private fun findClickableChatItems(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        // 检查当前节点是否是聊天列表项（RelativeLayout且clickable=true）
        if (node.className == "android.widget.RelativeLayout" && node.isClickable) {
            // 检查是否包含聊天名称节点（resource-id为hwl）
            val hasNameNode = findNodeByResourceId(node, "com.tencent.wework:id/hwl") != null
            if (hasNameNode) {
                result.add(node)
            }
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableChatItems(child, result)
        }
    }

    /**
     * 查找包含指定文本的节点
     */
    private fun findNodeContainingText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        if (nodeText.contains(text)) {
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
     * 打印所有节点的isScrollable属性
     */
    private fun printScrollableNodes(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return

        val indent = "  ".repeat(depth)
        if (node.isScrollable) {
            android.util.Log.e("WEWORK_DEBUG", "$indent✅ SCROLLABLE: ${node.className}, id=${node.viewIdResourceName}")
        }

        for (i in 0 until node.childCount) {
            printScrollableNodes(node.getChild(i), depth + 1)
        }
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
     * 点击群聊页面右上角三个点，进入群详情
     */
    private fun clickThreeDotsInChat() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickThreeDotsInChat() 被调用")
        sendLog("📱 正在点击右上角三个点...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找三个点按钮")

        // 查找右上角三个点按钮 (resource-id="com.tencent.wework:id/nhi")
        val threeDotsButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/nhi")

        if (threeDotsButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到三个点按钮，准备点击")
            val clicked = threeDotsButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            if (clicked) {
                sendLog("✅ 已点击三个点")
                android.util.Log.e("WEWORK_DEBUG", "✅ 三个点点击成功，等待进入群详情页面")
                // 点击三个点后，等待进入群详情页面，然后查找+号
                handler.postDelayed({
                    currentState = ProcessState.CLICKING_ADD_BUTTON
                    clickPlusButtonInGroupDetail()
                }, 1500)
            } else {
                android.util.Log.e("WEWORK_DEBUG", "❌ 三个点点击失败，重试")
                handler.postDelayed({ clickThreeDotsInChat() }, 1000)
            }
        } else {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到三个点按钮，重试")
            handler.postDelayed({ clickThreeDotsInChat() }, 1000)
        }
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
     * 点击群详情页面的+号按钮
     */
    private fun clickPlusButtonInGroupDetail() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickPlusButtonInGroupDetail() 被调用")
        sendLog("➕ 正在查找+号按钮...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ clickPlusButtonInGroupDetail() }, 1000)
            return
        }

        // 查找群成员RecyclerView (包含成员头像和+号、-号)
        val memberRecyclerViews = mutableListOf<AccessibilityNodeInfo>()
        findRecyclerViews(rootNode, memberRecyclerViews)

        android.util.Log.e("WEWORK_DEBUG", "📋 找到 ${memberRecyclerViews.size} 个RecyclerView")

        // 遍历所有RecyclerView,找到包含成员头像的那个
        for ((index, recyclerView) in memberRecyclerViews.withIndex()) {
            val childCount = recyclerView.childCount
            android.util.Log.e("WEWORK_DEBUG", "   RecyclerView[$index]: childCount=$childCount")

            // 群成员RecyclerView应该有多个子节点(成员头像 + +号 + -号)
            if (childCount >= 2) {
                // 查找这个RecyclerView中所有可点击的ImageView
                val imageViews = mutableListOf<AccessibilityNodeInfo>()
                findClickableImageViewsInNode(recyclerView, imageViews)

                android.util.Log.e("WEWORK_DEBUG", "   RecyclerView[$index]中找到 ${imageViews.size} 个可点击ImageView")

                // +号应该是倒数第二个ImageView,-号是最后一个
                if (imageViews.size >= 2) {
                    val plusButton = imageViews[imageViews.size - 2]
                    android.util.Log.e("WEWORK_DEBUG", "✅ 找到+号按钮(倒数第二个ImageView)，准备点击")

                    val clicked = plusButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    if (clicked) {
                        sendLog("✅ 已点击+号")
                        android.util.Log.e("WEWORK_DEBUG", "✅ +号点击成功")
                        handler.postDelayed({
                            currentState = ProcessState.SELECTING_MY_CUSTOMERS
                            selectMyCustomers()
                        }, 1500)
                        return
                    }
                }
            }
        }

        android.util.Log.e("WEWORK_DEBUG", "❌ 未找到+号按钮，重试")
        handler.postDelayed({ clickPlusButtonInGroupDetail() }, 1000)
    }

    /**
     * 查找所有RecyclerView
     */
    private fun findRecyclerViews(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.className == "androidx.recyclerview.widget.RecyclerView") {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            findRecyclerViews(node.getChild(i), result)
        }
    }

    /**
     * 在指定节点中查找所有可点击的ImageView
     */
    private fun findClickableImageViewsInNode(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.className == "android.widget.ImageView" && node.isClickable) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            findClickableImageViewsInNode(node.getChild(i), result)
        }
    }

    /**
     * 查找所有可点击的ImageView
     */
    private fun findClickableImageViews(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.className == "android.widget.ImageView" && node.isClickable) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            findClickableImageViews(node.getChild(i), result)
        }
    }

    /**
     * 点击弹窗中的"添加成员"
     */
    private fun clickAddMemberInMenu() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickAddMemberInMenu() 被调用")
        sendLog("➕ 正在点击添加成员...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找添加成员按钮")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印弹窗的所有文本：")
        printAllTexts(rootNode)

        // 查找"添加成员"按钮
        val addMemberTextNode = findNodeByText(rootNode, "添加成员")
        if (addMemberTextNode != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到添加成员文本节点")
            android.util.Log.e("WEWORK_DEBUG", "   clickable=${addMemberTextNode.isClickable}, enabled=${addMemberTextNode.isEnabled}")

            // 方案1: 直接对TextView执行点击,即使它标记为不可点击
            android.util.Log.e("WEWORK_DEBUG", "🖱️ 方案1: 直接点击TextView节点")
            val clicked = addMemberTextNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            android.util.Log.e("WEWORK_DEBUG", "   performAction返回: $clicked")

            if (clicked) {
                sendLog("✅ 已点击添加成员")
                handler.postDelayed({
                    currentState = ProcessState.SELECTING_MY_CUSTOMERS
                    selectMyCustomers()
                }, 1500)
            } else {
                // 方案2: 查找所有父节点并尝试点击
                android.util.Log.e("WEWORK_DEBUG", "🖱️ 方案2: 尝试点击所有父节点")
                var parent = addMemberTextNode.parent
                var level = 1
                var success = false

                while (parent != null && level <= 5) {
                    android.util.Log.e("WEWORK_DEBUG", "   尝试点击第${level}层父节点, clickable=${parent.isClickable}")
                    val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (parentClicked) {
                        android.util.Log.e("WEWORK_DEBUG", "   ✅ 第${level}层父节点点击成功!")
                        sendLog("✅ 已点击添加成员")
                        success = true

                        handler.postDelayed({
                            currentState = ProcessState.SELECTING_MY_CUSTOMERS
                            selectMyCustomers()
                        }, 1500)
                        break
                    }
                    parent = parent.parent
                    level++
                }

                if (!success) {
                    android.util.Log.e("WEWORK_DEBUG", "❌ 所有方案都失败，1秒后重试")
                    handler.postDelayed({ clickAddMemberInMenu() }, 1000)
                }
            }

        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到添加成员按钮，1秒后重试")
            sendLog("⚠️ 未找到添加成员按钮，重试中...")
            handler.postDelayed({ clickAddMemberInMenu() }, 1000)
        }
    }

    /**
     * 选择"我的客户"
     */
    private fun selectMyCustomers() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 selectMyCustomers() 被调用")
        sendLog("👥 正在选择我的客户...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始智能判断页面状态")

        // 智能判断1：先检查是否已经在"我的客户"页面
        val filterNode = findNodeByText(rootNode, "根据标签筛选")

        if (filterNode != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 已在我的客户页面，直接选择客户")
            sendLog("✅ 已在我的客户页面")

            // 已经在"我的客户"页面，直接选择客户
            currentState = ProcessState.SELECTING_CUSTOMERS
            selectAllCustomers()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "📋 不在我的客户页面，需要点击切换")

        // 智能判断2：检查是否能找到"我的客户"分类标签
        val myCustomersTextNode = findNodeByTextExact(rootNode, "我的客户")
        android.util.Log.e("WEWORK_DEBUG", "🔍 查找'我的客户'文本节点: ${if (myCustomersTextNode != null) "找到" else "未找到"}")

        if (myCustomersTextNode != null) {
            // 找到了"我的客户"文本，现在找它的头像
            // 向上遍历找到包含它的cmd父节点
            var parent = myCustomersTextNode.parent
            var cmdNode: AccessibilityNodeInfo? = null
            var depth = 0

            while (parent != null && depth < 10) {
                if (parent.viewIdResourceName == "com.tencent.wework:id/cmd") {
                    cmdNode = parent
                    android.util.Log.e("WEWORK_DEBUG", "✅ 找到cmd父节点 (深度: $depth)")
                    break
                }
                parent = parent.parent
                depth++
            }

            if (cmdNode != null) {
                // 在cmd节点下查找头像节点
                val avatarNode = findNodeByResourceId(cmdNode, "com.tencent.wework:id/lmb")

                if (avatarNode != null) {
                    android.util.Log.e("WEWORK_DEBUG", "✅ 找到我的客户头像，准备点击")
                    sendLog("👆 点击我的客户...")

                    clickNode(avatarNode)

                    // 点击后等待3秒，检查页面是否切换
                    handler.postDelayed({
                        checkIfMyCustomersPageLoaded()
                    }, 3000)
                } else {
                    android.util.Log.e("WEWORK_DEBUG", "❌ 未找到我的客户头像")
                    sendLog("⚠️ 未找到我的客户头像，重试中...")
                    handler.postDelayed({ selectMyCustomers() }, 1000)
                }
            } else {
                android.util.Log.e("WEWORK_DEBUG", "❌ 未找到cmd父节点")
                sendLog("⚠️ 页面结构异常，重试中...")
                handler.postDelayed({ selectMyCustomers() }, 1000)
            }
        } else {
            // 没找到"我的客户"分类标签，说明页面还没加载好
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 页面还没加载好，1秒后重试")
            sendLog("⚠️ 页面加载中，等待...")
            handler.postDelayed({ selectMyCustomers() }, 1000)
        }
    }

    /**
     * 检查是否已经切换到"我的客户"页面
     */
    private fun checkIfMyCustomersPageLoaded() {
        android.util.Log.e("WEWORK_DEBUG", "🔍 检查是否已切换到我的客户页面")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("⚠️ 页面检查失败，重试中...")
            handler.postDelayed({ selectMyCustomers() }, 1000)
            return
        }

        // 查找"根据标签筛选"文本，这是"我的客户"视图的特征
        val filterNode = findNodeByText(rootNode, "根据标签筛选")

        if (filterNode != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 已切换到我的客户页面")
            sendLog("✅ 已打开我的客户")

            // 页面已切换，开始选择客户
            currentState = ProcessState.SELECTING_CUSTOMERS
            selectAllCustomers()
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 页面未切换，重试点击我的客户")
            sendLog("⚠️ 页面未切换，重试中...")
            handler.postDelayed({ selectMyCustomers() }, 1000)
        }
    }

    /**
     * 选择所有客户
     */
    private fun selectAllCustomers() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 selectAllCustomers() 被调用")
        sendLog("✅ 正在选择所有新通过的客户...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始选择客户")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印客户列表页面的所有文本：")
        printAllTexts(rootNode)

        // 根据已通过的客户名称列表来选择客户
        if (approvedCustomers.isEmpty()) {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 没有已通过的客户需要邀请")
            sendLog("⚠️ 没有客户需要邀请，批量处理完成")
            // 🔥 修复: 如果没有客户需要邀请，直接完成，不要继续执行
            handler.postDelayed({
                currentState = ProcessState.COMPLETED
                stopProcessing()
            }, 1500)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "📝 需要邀请的客户: ${approvedCustomers.joinToString(", ")}")

        // 逐个查找并点击客户
        var selectedCount = 0
        for (customerName in approvedCustomers) {
            android.util.Log.e("WEWORK_DEBUG", "🔍 查找客户: $customerName")

            // 查找包含客户名称的节点
            val customerNode = findNodeByText(rootNode, customerName)
            if (customerNode == null) {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到客户: $customerName")
                continue
            }

            // 查找客户节点的头像（向上查找父节点，然后找头像）
            val avatarNode = findAvatarForCustomer(customerNode)
            if (avatarNode == null) {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到客户头像: $customerName")
                continue
            }

            if (!avatarNode.isEnabled) {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 客户不可选择（可能已在群中）: $customerName")
                continue
            }

            android.util.Log.e("WEWORK_DEBUG", "👆 点击选择客户: $customerName")
            clickNode(avatarNode)
            selectedCount++

            // 每次点击后稍微延迟，避免操作过快
            Thread.sleep(300)
        }

        sendLog("📝 已选择 $selectedCount 个客户")

        handler.postDelayed({
            currentState = ProcessState.CONFIRMING_INVITE
            confirmInvite()
        }, 1500)
    }

    /**
     * 为客户节点查找对应的头像
     */
    private fun findAvatarForCustomer(customerNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 向上查找到包含头像的父节点
        var parent = customerNode.parent
        while (parent != null) {
            // 查找resource-id为lmb的头像节点
            val avatar = findNodeByResourceId(parent, "com.tencent.wework:id/lmb")
            if (avatar != null) {
                return avatar
            }
            parent = parent.parent
        }
        return null
    }

    /**
     * 从客户头像节点中提取客户名称
     */
    private fun extractCustomerNameFromAvatar(avatarNode: AccessibilityNodeInfo): String {
        // 头像节点的父节点的父节点包含客户名称
        var parent = avatarNode.parent ?: return "未知客户"
        parent = parent.parent ?: return "未知客户"

        // 在父节点中查找包含客户名称的TextView
        return findCustomerNameInNode(parent)
    }

    /**
     * 在节点树中查找客户名称
     */
    private fun findCustomerNameInNode(node: AccessibilityNodeInfo): String {
        // 查找resource-id为hw3的ViewGroup，它包含客户名称
        if (node.viewIdResourceName == "com.tencent.wework:id/hw3") {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (child.className == "android.widget.TextView" && !child.text.isNullOrEmpty()) {
                    return child.text.toString()
                }
            }
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val name = findCustomerNameInNode(child)
            if (name != "未知客户") {
                return name
            }
        }

        return "未知客户"
    }

    /**
     * 查找所有指定resource-id的节点
     */
    private fun findAllNodesByResourceId(
        node: AccessibilityNodeInfo?,
        resourceId: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return

        if (node.viewIdResourceName == resourceId) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            findAllNodesByResourceId(node.getChild(i), resourceId, result)
        }
    }

    /**
     * 确认邀请
     */
    private fun confirmInvite() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 confirmInvite() 被调用")
        sendLog("✅ 正在确认邀请...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找确定按钮")

        // 查找"确定"按钮
        val confirmButton = findNodeByText(rootNode, "确定")
        if (confirmButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到确定按钮，准备点击")
            clickNode(confirmButton)
            sendLog("✅ 已点击确定")

            // 等待邀请弹窗出现，然后点击"邀请"按钮
            handler.postDelayed({
                clickInviteButton()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到确定按钮，1秒后重试")
            sendLog("⚠️ 未找到确定按钮，重试中...")
            handler.postDelayed({ confirmInvite() }, 1000)
        }
    }


    /**
     * 点击"邀请"按钮（处理邀请弹窗）
     */
    private fun clickInviteButton() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickInviteButton() 被调用")
        sendLog("📨 正在点击邀请按钮...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找邀请按钮")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印邀请弹窗的所有文本：")
        printAllTexts(rootNode)

        // 查找"邀请"按钮（精确匹配，避免匹配到提示文本）
        val inviteButton = findNodeByTextExact(rootNode, "邀请")
        if (inviteButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到邀请按钮，准备点击")
            clickNode(inviteButton)
            sendLog("✅ 已确认邀请")

            invitedCount = approvedCount

            handler.postDelayed({
                currentState = ProcessState.COMPLETED
                sendLog("🎉 批量处理完成！")
                sendLog("📊 最终统计: 通过${approvedCount}个, 邀请${invitedCount}个, 失败${failedCount}个")
                stopProcessing()
            }, 2000)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到邀请按钮，1秒后重试")
            sendLog("⚠️ 未找到邀请按钮，重试中...")
            handler.postDelayed({ clickInviteButton() }, 1000)
        }
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

    // ==================== 辅助方法 ====================

    /**
     * 处理企业微信打开事件
     */
    private fun handleWeworkOpened(event: AccessibilityEvent) {
        if (event.packageName == WEWORK_PACKAGE) {
            Log.d(TAG, "企业微信已打开")
        }
    }

    /**
     * 处理通讯录页面
     */
    private fun handleContactsPage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    /**
     * 处理新客户页面
     */
    private fun handleNewCustomersPage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    /**
     * 处理客户详情页面
     */
    private fun handleCustomerDetail(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    /**
     * 处理审批页面
     */
    private fun handleApprovalPage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    /**
     * 处理邀请页面
     */
    private fun handleInvitePage(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    /**
     * 处理群聊选择
     */
    private fun handleGroupSelection(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    /**
     * 处理确认邀请
     */
    private fun handleConfirmInvite(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    // 🔥 持续监控页面变化
    private var lastChatList = listOf<String>()
    private fun monitorPageChanges() {
        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            handler.postDelayed({ monitorPageChanges() }, 1000)
            return
        }

        // 查找RecyclerView
        val recyclerView = findNodeByResourceId(rootNode, "com.tencent.wework:id/czy")
        if (recyclerView == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到RecyclerView")
            handler.postDelayed({ monitorPageChanges() }, 1000)
            return
        }

        // 查找所有聊天项
        val chatItems = mutableListOf<AccessibilityNodeInfo>()
        findClickableChatItems(recyclerView, chatItems)

        // 提取聊天名称
        val currentChatList = mutableListOf<String>()
        for (chatItem in chatItems) {
            val textNode = findNodeByResourceId(chatItem, "com.tencent.wework:id/hwl")
            if (textNode != null && textNode.text != null) {
                currentChatList.add(textNode.text.toString())
            }
        }

        // 检查列表是否变化
        if (currentChatList != lastChatList) {
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "🔄 页面内容发生变化！")
            android.util.Log.e("WEWORK_DEBUG", "📋 当前聊天列表 (${currentChatList.size}个):")
            currentChatList.forEachIndexed { index, name ->
                android.util.Log.e("WEWORK_DEBUG", "   [$index] '$name'")
            }
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "")

            lastChatList = currentChatList
        }

        // 继续监控
        handler.postDelayed({ monitorPageChanges() }, 500)
    }

    /**
     * 处理返回列表
     */
    private fun handleReturnToList(event: AccessibilityEvent) {
        // 页面变化时的处理
    }

    /**
     * 根据文本查找节点（精确匹配）
     */
    private fun findNodeByTextExact(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        if (nodeText == text) {
            android.util.Log.e("WEWORK_DEBUG", "🎯 找到精确匹配文本: '$nodeText' == '$text'")
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextExact(child, text)
            if (result != null) return result
        }

        return null
    }

    /**
     * 根据文本查找节点（包含匹配）
     */
    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        if (nodeText.contains(text)) {
            android.util.Log.e("WEWORK_DEBUG", "🎯 找到匹配文本: '$nodeText' 包含 '$text'")
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
     * 根据资源ID查找所有节点
     */
    private fun findAllNodesByResourceId(node: AccessibilityNodeInfo, resourceId: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        if (node.viewIdResourceName == resourceId) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            results.addAll(findAllNodesByResourceId(child, resourceId))
        }

        return results
    }

    /**
     * 根据文本查找所有节点
     */
    private fun findAllNodesByText(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        val nodeText = node.text?.toString() ?: ""
        if (nodeText.contains(text)) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            results.addAll(findAllNodesByText(child, text))
        }

        return results
    }

    /**
     * 获取节点文本
     */
    private fun getNodeText(node: AccessibilityNodeInfo): String? {
        return node.text?.toString()
    }

    /**
     * 点击节点
     */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        android.util.Log.e("WEWORK_DEBUG", "🖱️ 尝试点击节点: text='${node.text}', clickable=${node.isClickable}")

        // 尝试直接点击
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 直接点击成功")
            return true
        }

        android.util.Log.e("WEWORK_DEBUG", "⚠️ 直接点击失败，尝试点击父节点...")

        // 如果节点不可点击，尝试找到可点击的父节点
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            android.util.Log.e("WEWORK_DEBUG", "🔍 检查父节点 depth=$depth, clickable=${parent.isClickable}")
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                android.util.Log.e("WEWORK_DEBUG", "✅ 点击父节点成功 (depth=$depth)")
                return true
            }
            parent = parent.parent
            depth++
        }

        android.util.Log.e("WEWORK_DEBUG", "❌ 所有点击尝试都失败了")

        // 最后尝试：使用全局坐标点击
        try {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            android.util.Log.e("WEWORK_DEBUG", "📍 节点屏幕坐标: $rect")

            if (rect.width() > 0 && rect.height() > 0) {
                val x = rect.centerX()
                val y = rect.centerY()
                android.util.Log.e("WEWORK_DEBUG", "🎯 尝试使用全局坐标点击: ($x, $y)")

                // 使用GestureDescription进行点击
                val path = android.graphics.Path()
                path.moveTo(x.toFloat(), y.toFloat())

                val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
                gestureBuilder.addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)
                )

                val result = dispatchGesture(
                    gestureBuilder.build(),
                    object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                            android.util.Log.e("WEWORK_DEBUG", "✅ 全局坐标点击成功")
                        }

                        override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                            android.util.Log.e("WEWORK_DEBUG", "❌ 全局坐标点击被取消")
                        }
                    },
                    null
                )

                android.util.Log.e("WEWORK_DEBUG", "📋 dispatchGesture 返回: $result")
            }
        } catch (e: Exception) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 全局坐标点击异常: ${e.message}")
        }

        return false
    }

    /**
     * 查找所有可点击的ViewGroup
     */
    private fun findClickableViewGroups(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        if (node.className == "android.view.ViewGroup" && node.isClickable) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            findClickableViewGroups(node.getChild(i), result)
        }
    }

    /**
     * 检查节点或其子节点是否包含指定文本
     */
    private fun containsText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false

        // 检查当前节点的文本
        if (node.text?.toString()?.contains(text) == true) {
            return true
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            if (containsText(node.getChild(i), text)) {
                return true
            }
        }

        return false
    }

    /**
     * 查找EditText节点
     */
    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.className == "android.widget.EditText") {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findEditText(node.getChild(i))
            if (result != null) return result
        }

        return null
    }

    /**
     * 测试模式: 滚动查找所有"查看"按钮
     */
    private fun testScrollFindViewButtons() {
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "🧪 testScrollFindViewButtons() 被调用")
        android.util.Log.e("WEWORK_DEBUG", "📊 滚动次数: $testScrollCount")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        sendLog("🧪 测试滚动查找好友 (第${testScrollCount + 1}次)")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ testScrollFindViewButtons() }, 1000)
            return
        }

        // 查找所有"查看"按钮
        val viewButtons = findAllNodesByText(rootNode, "查看")
        android.util.Log.e("WEWORK_DEBUG", "📋 找到 ${viewButtons.size} 个'查看'按钮")
        sendLog("📋 找到 ${viewButtons.size} 个'查看'按钮")

        // 检查是否有新的"查看"按钮
        if (viewButtons.size > previousViewButtonCount) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 发现新的'查看'按钮! 之前${previousViewButtonCount}个, 现在${viewButtons.size}个")
            sendLog("✅ 发现新的'查看'按钮! +${viewButtons.size - previousViewButtonCount}个")
            previousViewButtonCount = viewButtons.size
        } else if (testScrollCount > 0) {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 滚动后没有发现新的'查看'按钮")
            sendLog("⚠️ 滚动后没有发现新的'查看'按钮")
        }

        // 如果滚动次数超过10次,停止测试
        if (testScrollCount >= 10) {
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "✅ 测试完成!")
            android.util.Log.e("WEWORK_DEBUG", "📊 总共找到 ${viewButtons.size} 个'查看'按钮")
            android.util.Log.e("WEWORK_DEBUG", "📊 滚动次数: $testScrollCount")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            sendLog("✅ 测试完成! 总共找到 ${viewButtons.size} 个好友申请")
            Toast.makeText(this, "✅ 测试完成!\n总共找到 ${viewButtons.size} 个好友申请", Toast.LENGTH_LONG).show()
            stopProcessing()
            return
        }

        // 尝试滚动页面
        android.util.Log.e("WEWORK_DEBUG", "📜 准备滚动页面...")
        sendLog("📜 滚动页面...")

        // 方法1: 使用坐标滑动
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        val startX = screenWidth / 2f
        val startY = screenHeight * 0.8f
        val endY = screenHeight * 0.3f

        android.util.Log.e("WEWORK_DEBUG", "📱 屏幕尺寸: ${screenWidth}x${screenHeight}")
        android.util.Log.e("WEWORK_DEBUG", "📜 滑动坐标: ($startX, $startY) → ($startX, $endY)")

        try {
            val path = android.graphics.Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)

            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            gestureBuilder.addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)
            )

            val result = dispatchGesture(
                gestureBuilder.build(),
                object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        android.util.Log.e("WEWORK_DEBUG", "✅ 滚动手势完成")
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        android.util.Log.e("WEWORK_DEBUG", "❌ 滚动手势被取消")
                    }
                },
                null
            )

            android.util.Log.e("WEWORK_DEBUG", "📋 dispatchGesture 返回: $result")

            testScrollCount++

            // 等待滚动完成后再次查找
            handler.postDelayed({
                testScrollFindViewButtons()
            }, 1500)

        } catch (e: Exception) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 滚动异常: ${e.message}")
            sendLog("❌ 滚动失败: ${e.message}")
            stopProcessing()
        }
    }

    /**
     * 发送日志到MainActivity
     */
    private fun sendLog(message: String) {
        Log.d(TAG, message)
        val intent = Intent("com.wework.autoreply.LOG_UPDATE")
        intent.putExtra("message", message)
        sendBroadcast(intent)
    }

    /**
     * 发送统计数据到MainActivity
     */
    private fun sendStats() {
        val intent = Intent("com.wework.autoreply.STATS_UPDATE")
        intent.putExtra("approved", approvedCount)
        intent.putExtra("invited", invitedCount)
        intent.putExtra("failed", failedCount)
        sendBroadcast(intent)
    }
}

