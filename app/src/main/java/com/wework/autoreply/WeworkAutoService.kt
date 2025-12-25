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

    // 客户数据类(包含名称和部门信息)
    data class Customer(
        val name: String,
        val department: String? = null  // 可选的部门信息,用于精准匹配
    )

    // 批量邀请功能的客户列表
    private val inviteCustomers = mutableListOf<Customer>()

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

    // 处理好友申请时的滚动检测变量
    private var lastFirstVisibleCustomer = ""  // 记录滚动前的第一个可见客户

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
                "com.wework.autoreply.TEST_SCROLL" -> {
                    android.util.Log.e("WEWORK_DEBUG", "📡 收到测试滚动指令")
                    sendLog("📡 收到测试滚动指令")
                    // 延迟2秒后开始测试滚动
                    handler.postDelayed({
                        testScrollPage()
                    }, 2000)
                }
                "com.wework.autoreply.TEST_SEARCH" -> {
                    android.util.Log.e("WEWORK_DEBUG", "📡 收到测试搜索指令")
                    sendLog("📡 收到测试搜索指令")
                    // 延迟2秒后开始测试搜索
                    handler.postDelayed({
                        testSearchCustomer()
                    }, 2000)
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
        val filter = IntentFilter()
        filter.addAction("com.wework.autoreply.START_BATCH_PROCESS")
        filter.addAction("com.wework.autoreply.TEST_SCROLL")
        filter.addAction("com.wework.autoreply.TEST_SEARCH")
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

        // 检查是否测试搜索模式
        val testSearchMode = prefs.getBoolean("test_search_mode", false)
        if (testSearchMode) {
            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            android.util.Log.e("WEWORK_DEBUG", "🔍 检测到测试搜索模式")

            if (timeDiff < 60000) {
                android.util.Log.e("WEWORK_DEBUG", "🚀 开始测试搜索!")

                Toast.makeText(this, "🔍 测试搜索功能", Toast.LENGTH_LONG).show()

                // 清除标志
                prefs.edit().apply {
                    putBoolean("test_search_mode", false)
                    apply()
                }

                // 延迟2秒后开始测试搜索
                handler.postDelayed({
                    testSearchCustomer()
                }, 2000)

                return
            }
        }

        // 检查是否测试滚动模式
        val testScrollMode = prefs.getBoolean("test_scroll_mode", false)
        if (testScrollMode) {
            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            android.util.Log.e("WEWORK_DEBUG", "🔄 检测到测试滚动模式")

            if (timeDiff < 60000) {
                android.util.Log.e("WEWORK_DEBUG", "🚀 开始测试滚动!")

                // 清除标志
                prefs.edit().apply {
                    putBoolean("test_scroll_mode", false)
                    apply()
                }

                // 延迟2秒后开始测试滚动
                handler.postDelayed({
                    testScrollPage()
                }, 2000)

                return
            }
        }

        // 检查是否测试邀请模式
        // 检查是否是测试点击放大镜模式
        val testSearchButtonMode = prefs.getBoolean("test_search_button_mode", false)
        if (testSearchButtonMode) {
            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            android.util.Log.e("WEWORK_DEBUG", "🔍 检测到测试点击放大镜模式")

            if (timeDiff < 60000) {
                android.util.Log.e("WEWORK_DEBUG", "🚀 开始测试点击放大镜!")

                Toast.makeText(this, "🔍 测试点击放大镜", Toast.LENGTH_LONG).show()

                // 清除标志
                prefs.edit().putBoolean("test_search_button_mode", false).apply()

                // 开始测试流程
                handler.postDelayed({
                    testClickSearchButton()
                }, 1500)
            }
            return
        }

        val inviteCustomersMode = prefs.getBoolean("invite_customers_mode", false)
        if (inviteCustomersMode) {
            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            android.util.Log.e("WEWORK_DEBUG", "👥 检测到批量邀请模式")

            if (timeDiff < 60000) {
                android.util.Log.e("WEWORK_DEBUG", "🚀 开始批量邀请好友进群!")

                Toast.makeText(this, "👥 批量邀请好友进群", Toast.LENGTH_LONG).show()

                // 清除标志
                prefs.edit().putBoolean("invite_customers_mode", false).apply()

                // 从SharedPreferences读取客户列表
                val customerListText = prefs.getString("customer_list", "") ?: ""
                inviteCustomers.clear()

                // 解析客户列表（每行一个，支持 "名称|部门" 格式）
                customerListText.split("\n").forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        val parts = trimmed.split("|")
                        if (parts.size == 2) {
                            // 格式: 名称|部门
                            inviteCustomers.add(Customer(parts[0].trim(), parts[1].trim()))
                        } else {
                            // 格式: 名称
                            inviteCustomers.add(Customer(trimmed))
                        }
                    }
                }

                android.util.Log.e("WEWORK_DEBUG", "📝 需要邀请的好友: ${inviteCustomers.map { it.name }.joinToString(", ")}")

                // 开始批量邀请
                handler.postDelayed({
                    inviteCustomersToGroup()
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
        lastFirstVisibleCustomer = ""  // 🔥 重置滚动检测变量
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
            // 没找到"查看"按钮,尝试滚动查找更多
            android.util.Log.e("WEWORK_DEBUG", "🔍 当前页面没有'查看'按钮,尝试滚动查找更多...")
            sendLog("🔄 滚动查找更多好友...")

            // 查找ListView并滚动
            val listView = findNodeByResourceId(rootNode, "com.tencent.wework:id/f_1")
            if (listView != null) {
                android.util.Log.e("WEWORK_DEBUG", "✅ 找到ListView,准备滚动")

                // 🔥 获取滚动前的第一个可见客户
                val currentFirstCustomer = getFirstVisibleCustomerName(rootNode)
                android.util.Log.e("WEWORK_DEBUG", "📍 滚动前第一个客户: $currentFirstCustomer")
                android.util.Log.e("WEWORK_DEBUG", "📍 上次记录的第一个客户: $lastFirstVisibleCustomer")

                // 🔥 检查是否已经到底（滚动前后第一个客户没有变化）
                if (currentFirstCustomer.isNotEmpty() && currentFirstCustomer == lastFirstVisibleCustomer) {
                    // 滚动前后第一个客户相同,说明已经到底了
                    android.util.Log.e("WEWORK_DEBUG", "📊 检测到滚动前后第一个客户相同,确认已到底")
                    sendLog("📊 已滚动到底,没有更多好友申请")
                } else {
                    // 记录当前第一个客户
                    lastFirstVisibleCustomer = currentFirstCustomer

                    // 执行滚动
                    android.util.Log.e("WEWORK_DEBUG", "🔄 执行滚动操作...")
                    listView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

                    // 等待后再次查找
                    sendLog("✅ 滚动成功,继续查找...")
                    handler.postDelayed({
                        processNextCustomer()
                    }, 1000)
                    return
                }
            } else {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到ListView,无法滚动")
                sendLog("⚠️ 未找到ListView")
            }

            // 确实没有更多好友申请了,进入下一步
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "✅ 所有好友申请已通过！")
            android.util.Log.e("WEWORK_DEBUG", "📊 统计: 通过${approvedCount}个, 失败${failedCount}个")
            android.util.Log.e("WEWORK_DEBUG", "📊 已通过客户列表: $approvedCustomers")
            android.util.Log.e("WEWORK_DEBUG", "========================================")

            sendLog("✅ 所有好友申请已通过！")
            sendLog("📊 统计: 通过${approvedCount}个, 失败${failedCount}个")

            // 🔥 将approvedCustomers转换为inviteCustomers格式
            inviteCustomers.clear()
            approvedCustomers.forEach { name ->
                inviteCustomers.add(Customer(name))
            }
            android.util.Log.e("WEWORK_DEBUG", "📝 已将${approvedCustomers.size}个已通过客户转换为邀请列表")

            // 🔥 智能返回到消息页面，然后调用批量邀请流程
            handler.postDelayed({
                android.util.Log.e("WEWORK_DEBUG", "🔄 调用 navigateToMessagesForInvite() 返回消息页面")
                navigateToMessagesForInvite()
            }, 1500)
            return
        }

        // 🔥 始终点击第一个"查看"按钮（因为处理完的按钮会消失）
        val firstViewButton = viewButtons[0]
        android.util.Log.e("WEWORK_DEBUG", "👆 点击第一个'查看'按钮 (当前已处理:$currentCustomerIndex 个)")
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
     * 获取ListView中第一个可见客户的名称（用于判断滚动是否成功）
     */
    private fun getFirstVisibleCustomerName(rootNode: AccessibilityNodeInfo): String {
        try {
            val listView = findNodeByResourceId(rootNode, "com.tencent.wework:id/f_1")
            if (listView != null && listView.childCount > 0) {
                // 遍历ListView的子节点，找到第一个包含客户信息的item
                for (i in 0 until listView.childCount) {
                    val item = listView.getChild(i)
                    if (item != null) {
                        // 查找客户名称节点（通常在o8e这个ViewGroup中）
                        val nameViewGroup = findNodeByResourceId(item, "com.tencent.wework:id/o8e")
                        if (nameViewGroup != null && nameViewGroup.childCount > 0) {
                            val nameNode = nameViewGroup.getChild(0)
                            if (nameNode != null && nameNode.text != null) {
                                val customerName = nameNode.text.toString()
                                android.util.Log.e("WEWORK_DEBUG", "📍 第一个可见客户: $customerName")
                                return customerName
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 获取第一个可见客户失败: ${e.message}")
        }
        return ""
    }

    /**
     * 导航到消息页面（用于批量邀请）
     * 智能识别当前页面，自动返回到消息页面，然后调用inviteCustomersToGroup()
     */
    private fun navigateToMessagesForInvite() {
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "🔧 navigateToMessagesForInvite() 被调用")
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
            handler.postDelayed({ navigateToMessagesForInvite() }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始检查当前页面")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印当前页面的所有文本：")
        printAllTexts(rootNode)

        // 🔥 智能识别页面
        val recyclerView = findNodeByResourceId(rootNode, "com.tencent.wework:id/czy")
        val hasMessageTab = findNodeByText(rootNode, "消息") != null
        val hasContactTab = findNodeByText(rootNode, "通讯录") != null

        android.util.Log.e("WEWORK_DEBUG", "📋 页面检查:")
        android.util.Log.e("WEWORK_DEBUG", "   - recyclerView=${recyclerView != null}")
        android.util.Log.e("WEWORK_DEBUG", "   - hasMessageTab=$hasMessageTab")
        android.util.Log.e("WEWORK_DEBUG", "   - hasContactTab=$hasContactTab")

        if (recyclerView != null) {
            // 已经在消息列表页面，直接调用批量邀请
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            android.util.Log.e("WEWORK_DEBUG", "✅ 已经在消息列表页面，准备开始批量邀请")
            android.util.Log.e("WEWORK_DEBUG", "========================================")
            sendLog("✅ 已在消息页面")
            navigateRetryCount = 0

            handler.postDelayed({
                android.util.Log.e("WEWORK_DEBUG", "🔄 调用 inviteCustomersToGroup() 开始批量邀请")
                inviteCustomersToGroup()
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
                    android.util.Log.e("WEWORK_DEBUG", "🔄 调用 inviteCustomersToGroup() 开始批量邀请")
                    inviteCustomersToGroup()
                }, 1500)
            } else {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到'消息'按钮，1秒后重试")
                navigateRetryCount++
                handler.postDelayed({ navigateToMessagesForInvite() }, 1000)
            }
        } else {
            // 不在主页面也不在消息列表，按返回键
            android.util.Log.e("WEWORK_DEBUG", "⬅️ 不在目标页面，按返回键")
            sendLog("⬅️ 返回主页面...")
            performGlobalAction(GLOBAL_ACTION_BACK)
            navigateRetryCount++
            android.util.Log.e("WEWORK_DEBUG", "⬅️ 已按返回键，1秒后重新检查页面")
            handler.postDelayed({ navigateToMessagesForInvite() }, 1000)
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
                // 查找+号按钮: 遍历所有LinearLayout子节点,找到只有ImageView没有TextView的
                var plusButton: AccessibilityNodeInfo? = null

                for (i in 0 until childCount) {
                    val child = recyclerView.getChild(i) ?: continue

                    // 检查这个子节点是否是LinearLayout
                    if (child.className == "android.widget.LinearLayout") {
                        // 检查是否包含TextView
                        val hasTextView = hasTextViewChild(child)

                        if (!hasTextView) {
                            // 没有TextView,说明是+号或-号
                            // 查找这个LinearLayout中的可点击ImageView
                            val imageView = findClickableImageViewInNode(child)
                            if (imageView != null) {
                                // 找到第一个没有TextView的LinearLayout中的ImageView,应该是+号
                                plusButton = imageView
                                android.util.Log.e("WEWORK_DEBUG", "✅ 找到+号按钮(LinearLayout[$i]中的ImageView,没有TextView)")
                                break
                            }
                        }
                    }
                }

                if (plusButton != null) {
                    val clicked = plusButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    if (clicked) {
                        sendLog("✅ 已点击+号")
                        android.util.Log.e("WEWORK_DEBUG", "✅ +号点击成功")
                        handler.postDelayed({
                            currentState = ProcessState.SELECTING_CUSTOMERS
                            // 使用currentCustomerIndex来添加客户
                            android.util.Log.e("WEWORK_DEBUG", "📝 准备添加客户(index=$currentCustomerIndex)")
                            searchAndAddSingleCustomer(currentCustomerIndex)
                        }, 1500)
                        return
                    } else {
                        android.util.Log.e("WEWORK_DEBUG", "❌ +号点击失败")
                    }
                } else {
                    android.util.Log.e("WEWORK_DEBUG", "❌ 未在RecyclerView[$index]中找到+号")
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
     * 检查节点是否包含TextView子节点
     */
    private fun hasTextViewChild(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.className == "android.widget.TextView") {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (hasTextViewChild(child)) {
                return true
            }
        }
        return false
    }

    /**
     * 在节点中查找第一个可点击的ImageView
     */
    private fun findClickableImageViewInNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className == "android.widget.ImageView" && node.isClickable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findClickableImageViewInNode(child)
            if (result != null) {
                return result
            }
        }
        return null
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
                    currentState = ProcessState.SELECTING_CUSTOMERS
                    // 开始搜索并添加第一个客户
                    searchAndAddSingleCustomer(0)
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
                            currentState = ProcessState.SELECTING_CUSTOMERS
                            // 开始搜索并添加第一个客户
                            searchAndAddSingleCustomer(0)
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
     * 搜索并添加单个客户(每次只添加一个,添加完成后点击确定,然后继续下一个)
     */
    private fun searchAndAddSingleCustomer(index: Int) {
        // 检查是否所有客户都已添加完成
        if (index >= inviteCustomers.size) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 所有客户已添加完成!")
            sendLog("✅ 所有客户已添加完成!")
            Toast.makeText(this, "✅ 所有客户已添加完成!", Toast.LENGTH_LONG).show()

            currentState = ProcessState.COMPLETED
            stopProcessing()
            return
        }

        val customer = inviteCustomers[index]
        android.util.Log.e("WEWORK_DEBUG", "🔍 [${index + 1}/${inviteCustomers.size}] 开始添加客户: ${customer.name} (部门: ${customer.department ?: "无"})")
        sendLog("🔍 [${index + 1}/${inviteCustomers.size}] 添加: ${customer.name}")

        // 1. 先点击"我的客户"
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            sendLog("❌ 无法获取rootNode,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        // 查找"我的客户"文本节点
        val myCustomersTextNode = findNodeByTextExact(rootNode, "我的客户")
        if (myCustomersTextNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到'我的客户'文本节点")
            sendLog("❌ 未找到'我的客户'选项,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到'我的客户'文本节点,开始查找头像...")

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

        if (cmdNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到cmd父节点")
            sendLog("❌ 页面结构异常,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        // 在cmd节点下查找头像节点
        val avatarNode = findNodeByResourceId(cmdNode, "com.tencent.wework:id/lmb")
        if (avatarNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到'我的客户'头像节点")
            sendLog("❌ 未找到头像,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到'我的客户'头像,准备点击...")
        val clickSuccess = clickNode(avatarNode)
        if (!clickSuccess) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 点击'我的客户'头像失败")
            sendLog("❌ 点击失败,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 点击'我的客户'头像成功,等待页面加载...")

        // 2. 等待500ms后点击放大镜按钮
        handler.postDelayed({
            clickSearchButtonForSingleCustomer(customer, index)
        }, 500)
    }

    /**
     * 点击放大镜按钮(通过遍历节点查找并点击)
     */
    private fun clickSearchButtonForSingleCustomer(customer: Customer, index: Int) {
        android.util.Log.e("WEWORK_DEBUG", "🔍 准备点击放大镜按钮...")

        // 获取屏幕宽度
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // 使用相对坐标计算搜索按钮位置
        // 测试结果: 720px屏幕上,搜索按钮在x=590的位置
        // 相对位置: screenWidth - 130
        val searchButtonX = screenWidth - 130  // 搜索按钮的X坐标(相对)
        val searchButtonY = 124  // 标题栏中心Y坐标

        android.util.Log.e("WEWORK_DEBUG", "📍 屏幕宽度: $screenWidth, 放大镜按钮坐标: ($searchButtonX, $searchButtonY)")

        // 尝试在运行时查找坐标附近的可点击节点
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            android.util.Log.e("WEWORK_DEBUG", "🔍 开始遍历节点,查找坐标($searchButtonX, $searchButtonY)附近的可点击节点...")
            val targetNode = findNodeByCoordinates(rootNode, searchButtonX, searchButtonY, 50)

            if (targetNode != null) {
                val rect = android.graphics.Rect()
                targetNode.getBoundsInScreen(rect)
                android.util.Log.e("WEWORK_DEBUG", "✅ 找到目标节点: ${targetNode.className}, bounds=[$rect]")
                val clicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    android.util.Log.e("WEWORK_DEBUG", "✅ 成功点击节点!")
                    sendLog("🔍 正在打开搜索...")

                    // 延迟1秒后继续
                    handler.postDelayed({
                        inputSearchKeywordForSingleCustomer(customer, index)
                    }, 1000)
                } else {
                    android.util.Log.e("WEWORK_DEBUG", "❌ 节点点击失败,重试中...")
                    sendLog("❌ 点击放大镜失败,重试中...")
                    handler.postDelayed({
                        searchAndAddSingleCustomer(index)
                    }, 1000)
                }
            } else {
                android.util.Log.e("WEWORK_DEBUG", "❌ 未找到目标节点,重试中...")
                sendLog("❌ 未找到放大镜按钮,重试中...")
                handler.postDelayed({
                    searchAndAddSingleCustomer(index)
                }, 1000)
            }
        } else {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode,重试中...")
            sendLog("❌ 无法获取rootNode,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
        }
    }

    /**
     * 输入搜索关键词(单个客户添加模式)
     */
    private fun inputSearchKeywordForSingleCustomer(customer: Customer, index: Int) {
        android.util.Log.e("WEWORK_DEBUG", "⌨️ 输入搜索关键词: ${customer.name}")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            sendLog("❌ 无法获取rootNode,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        // 查找搜索框
        val searchBox = findNodeByClassName(rootNode, "android.widget.EditText")
        if (searchBox == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索框")
            sendLog("❌ 未找到搜索框,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到搜索框,准备输入...")

        // 输入搜索关键词
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, customer.name)
        val inputSuccess = searchBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (!inputSuccess) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 输入搜索关键词失败")
            sendLog("❌ 输入失败,重试中...")
            handler.postDelayed({
                searchAndAddSingleCustomer(index)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 输入搜索关键词成功,等待搜索结果...")

        // 3. 等待1秒后点击搜索结果
        handler.postDelayed({
            clickSearchResultForSingleCustomer(customer, index)
        }, 1000)
    }

    /**
     * 点击搜索结果并确定(单个客户添加模式)
     * 精准匹配名称+部门,如果有多个同名的,全部尝试点击
     */
    private fun clickSearchResultForSingleCustomer(customer: Customer, index: Int) {
        android.util.Log.e("WEWORK_DEBUG", "👆 点击搜索结果: ${customer.name} (部门: ${customer.department ?: "无"})")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            sendLog("❌ 无法获取rootNode,跳过此客户")

            // 跳过此客户,继续下一个
            handler.postDelayed({
                // 需要先回到群聊页面,再点击+号
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        // 只在RecyclerView或ListView中查找客户节点,避免查找到第一级页面的节点
        android.util.Log.e("WEWORK_DEBUG", "🔍 开始查找RecyclerView和ListView...")
        val listContainers = mutableListOf<AccessibilityNodeInfo>()
        findAllListContainers(rootNode, listContainers)
        android.util.Log.e("WEWORK_DEBUG", "📋 找到 ${listContainers.size} 个列表容器(RecyclerView/ListView)")

        if (listContainers.isEmpty()) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到列表容器,跳过此客户")
            sendLog("❌ 未找到客户列表,跳过")

            // 按返回键返回,然后继续下一个客户
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        // 在所有列表容器中查找包含关键词的搜索结果
        val allResults = mutableListOf<AccessibilityNodeInfo>()
        for (container in listContainers) {
            val containerType = container.className?.toString()?.substringAfterLast('.') ?: "Unknown"
            val results = findAllNodesByText(container, customer.name)
            allResults.addAll(results)
            android.util.Log.e("WEWORK_DEBUG", "📋 $containerType 中找到 ${results.size} 个包含关键词的结果")
        }

        if (allResults.isEmpty()) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索结果: ${customer.name}")
            sendLog("❌ 未找到: ${customer.name},跳过")

            // 按返回键返回,然后继续下一个客户
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到 ${allResults.size} 个包含关键词的结果,开始精准匹配...")

        // 过滤出名称精确匹配的结果
        val exactMatches = allResults.filter { node ->
            val nodeText = node.text?.toString() ?: ""
            // 精准匹配:名称完全相同(不包含括号内容)
            val isNameMatch = nodeText == customer.name

            if (!isNameMatch) {
                return@filter false
            }

            // 如果指定了部门,还需要匹配部门
            if (customer.department != null) {
                // 查找部门信息节点
                val departmentNode = findDepartmentForCustomer(node)
                val departmentText = departmentNode?.text?.toString() ?: ""
                android.util.Log.e("WEWORK_DEBUG", "🔍 检查部门: $departmentText vs ${customer.department}")
                return@filter departmentText.contains(customer.department)
            }

            true
        }

        if (exactMatches.isEmpty()) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 没有精准匹配的结果: ${customer.name} (部门: ${customer.department ?: "无"})")
            sendLog("❌ 未找到精准匹配: ${customer.name},跳过")

            // 从搜索结果页面返回到群详情页面需要按4次返回键
            // 1. 搜索结果页面 → 搜索页面
            // 2. 搜索页面 → 客户列表页面
            // 3. 客户列表页面 → 添加群成员页面
            // 4. 添加群成员页面 → 群详情页面
            android.util.Log.e("WEWORK_DEBUG", "🔙 开始返回到群详情页面(需要按4次返回键)...")
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(300)
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(300)
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(300)
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到 ${exactMatches.size} 个精准匹配的结果,开始尝试点击...")
        android.util.Log.e("WEWORK_DEBUG", "========================================")

        // 只点击第一个可用的客户,避免多次点击导致误选其他客户
        var clickedCount = 0
        for ((idx, resultNode) in exactMatches.withIndex()) {
            val nodeText = resultNode.text?.toString() ?: ""
            android.util.Log.e("WEWORK_DEBUG", "")
            android.util.Log.e("WEWORK_DEBUG", "🔍 检查第 ${idx + 1}/${exactMatches.size} 个精准匹配结果: $nodeText")

            // 记录节点的详细信息
            val rect = android.graphics.Rect()
            resultNode.getBoundsInScreen(rect)
            android.util.Log.e("WEWORK_DEBUG", "📊 节点信息: class=${resultNode.className}, bounds=$rect")
            android.util.Log.e("WEWORK_DEBUG", "📊 节点状态: clickable=${resultNode.isClickable}, enabled=${resultNode.isEnabled}, selected=${resultNode.isSelected}")

            // 查找对应的头像
            android.util.Log.e("WEWORK_DEBUG", "🔍 开始查找头像节点...")
            val avatarNode = findAvatarForCustomer(resultNode)
            if (avatarNode == null) {
                android.util.Log.e("WEWORK_DEBUG", "❌ 第 ${idx + 1} 个结果没有找到头像节点,跳过")
                android.util.Log.e("WEWORK_DEBUG", "📊 父节点信息: parent=${resultNode.parent?.className}")
                continue
            }

            // 记录头像节点的详细信息
            val avatarRect = android.graphics.Rect()
            avatarNode.getBoundsInScreen(avatarRect)
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到头像节点!")
            android.util.Log.e("WEWORK_DEBUG", "📊 头像信息: class=${avatarNode.className}, bounds=$avatarRect")
            android.util.Log.e("WEWORK_DEBUG", "📊 头像状态: clickable=${avatarNode.isClickable}, enabled=${avatarNode.isEnabled}, selected=${avatarNode.isSelected}")

            // 检查头像是否可点击
            if (!avatarNode.isEnabled) {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 第 ${idx + 1} 个结果的头像不可点击(isEnabled=false,可能已在群里),跳过")
                continue
            }

            android.util.Log.e("WEWORK_DEBUG", "✅ 第 ${idx + 1} 个结果的头像可点击,准备点击...")

            val clickSuccess = clickNode(avatarNode)
            if (clickSuccess) {
                clickedCount++
                android.util.Log.e("WEWORK_DEBUG", "✅ 第 ${idx + 1} 个结果点击成功!")
                sendLog("✅ 已选择: ${customer.name}")

                // 只点击第一个可用的,立即跳出循环
                android.util.Log.e("WEWORK_DEBUG", "✅ 已成功点击第一个可用客户,停止遍历")
                break
            } else {
                android.util.Log.e("WEWORK_DEBUG", "❌ 第 ${idx + 1} 个结果点击失败,尝试下一个")
            }
        }

        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")

        if (clickedCount == 0) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 所有精准匹配的结果都无法点击(都已在群里),跳过此客户")
            sendLog("⚠️ ${customer.name} 已在群里,跳过")

            // 从搜索结果页面返回到群详情页面需要按4次返回键
            // 1. 搜索结果页面 → 搜索页面
            // 2. 搜索页面 → 客户列表页面
            // 3. 客户列表页面 → 添加群成员页面
            // 4. 添加群成员页面 → 群详情页面
            android.util.Log.e("WEWORK_DEBUG", "🔙 开始返回到群详情页面(需要按4次返回键)...")
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(300)
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(300)
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(300)
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 成功点击了 $clickedCount 个客户")
        sendLog("✅ 已选择 $clickedCount 个: ${customer.name}")

        // 等待1500ms后点击确定按钮(增加延迟,避免误点击)
        android.util.Log.e("WEWORK_DEBUG", "⏱️ 等待1.5秒后点击确定按钮...")
        handler.postDelayed({
            clickConfirmButtonForSingleCustomer(customer, index)
        }, 1500)
    }

    /**
     * 点击确定按钮(单个客户添加模式)
     */
    private fun clickConfirmButtonForSingleCustomer(customer: Customer, index: Int) {
        android.util.Log.e("WEWORK_DEBUG", "👆 点击确定按钮")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            sendLog("❌ 无法获取rootNode,跳过此客户")

            // 按返回键返回,然后继续下一个客户
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        // 查找确定按钮
        val confirmButton = findNodeByText(rootNode, "确定")
        if (confirmButton == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到确定按钮")
            sendLog("❌ 未找到确定按钮,跳过")

            // 按返回键返回,然后继续下一个客户
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到确定按钮,准备点击...")

        val clickSuccess = clickNode(confirmButton)
        if (!clickSuccess) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 点击确定按钮失败")
            sendLog("❌ 点击确定失败,跳过")

            // 按返回键返回,然后继续下一个客户
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 点击确定按钮成功,客户已添加: ${customer.name}")
        sendLog("✅ 已添加: ${customer.name}")

        // 5. 等待1.5秒后检查是否有邀请弹窗
        handler.postDelayed({
            checkAndClickInviteButtonForSingleCustomer(index)
        }, 1500)
    }

    /**
     * 检查并点击邀请按钮(智能识别是否有邀请弹窗)
     */
    private fun checkAndClickInviteButtonForSingleCustomer(index: Int) {
        android.util.Log.e("WEWORK_DEBUG", "🔍 检查是否有邀请弹窗...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null,直接继续下一个客户")
            // 继续下一个客户
            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 500)
            return
        }

        // 查找"邀请"按钮（精确匹配）
        val inviteButton = findNodeByTextExact(rootNode, "邀请")

        if (inviteButton != null && inviteButton.isClickable) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 发现邀请弹窗,点击邀请按钮")
            sendLog("📨 点击邀请按钮...")

            val clicked = clickNode(inviteButton)
            if (clicked) {
                android.util.Log.e("WEWORK_DEBUG", "✅ 邀请按钮点击成功")
                sendLog("✅ 已确认邀请")
            } else {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 邀请按钮点击失败")
            }

            // 等待1秒后继续下一个客户
            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 1000)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "ℹ️ 没有邀请弹窗,直接继续下一个客户")

            // 没有邀请弹窗,直接继续下一个客户
            handler.postDelayed({
                continueNextCustomer(index + 1)
            }, 500)
        }
    }

    /**
     * 继续下一个客户
     */
    private fun continueNextCustomer(nextIndex: Int) {
        if (nextIndex >= inviteCustomers.size) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 所有客户已添加完成!")
            sendLog("✅ 所有客户已添加完成!")
            Toast.makeText(this, "✅ 所有客户已添加完成!", Toast.LENGTH_LONG).show()

            currentState = ProcessState.COMPLETED
            stopProcessing()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "🔄 准备添加下一个客户(index=$nextIndex)...")

        // 保存当前要添加的客户索引
        currentCustomerIndex = nextIndex

        // 点击确定后回到群详情页面,需要再次点击+号
        currentState = ProcessState.CLICKING_ADD_BUTTON
        clickPlusButtonInGroupDetail()
    }

    /**
     * 测试搜索客户
     */
    private fun testSearchCustomer() {
        android.util.Log.e("WEWORK_DEBUG", "🔍 testSearchCustomer() 被调用")
        sendLog("🔍 开始测试搜索功能...")

        // 1. 点击放大镜按钮
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            sendLog("❌ 无法获取rootNode")
            return
        }

        // 查找放大镜按钮 (resource-id: com.tencent.wework:id/nhn)
        val searchButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/nhn")
        if (searchButton == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到放大镜按钮")
            sendLog("❌ 未找到放大镜按钮")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到放大镜按钮,准备点击...")
        sendLog("✅ 找到放大镜按钮,准备点击...")

        val clickSuccess = clickNode(searchButton)
        if (!clickSuccess) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 点击放大镜按钮失败")
            sendLog("❌ 点击放大镜按钮失败")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 点击放大镜按钮成功,等待搜索框出现...")
        sendLog("✅ 点击放大镜按钮成功,等待搜索框出现...")

        // 2. 等待1秒后输入搜索关键词
        handler.postDelayed({
            performSearch("创视空间")
        }, 1000)
    }

    /**
     * 执行搜索
     */
    private fun performSearch(keyword: String) {
        android.util.Log.e("WEWORK_DEBUG", "🔍 performSearch() 被调用,关键词: $keyword")
        sendLog("🔍 搜索关键词: $keyword")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            sendLog("❌ 无法获取rootNode")
            return
        }

        // 查找搜索框 (通常是EditText)
        val searchBox = findNodeByClassName(rootNode, "android.widget.EditText")
        if (searchBox == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索框")
            sendLog("❌ 未找到搜索框")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到搜索框,准备输入...")
        sendLog("✅ 找到搜索框,准备输入...")

        // 输入搜索关键词
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
        val inputSuccess = searchBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (!inputSuccess) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 输入搜索关键词失败")
            sendLog("❌ 输入搜索关键词失败")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 输入搜索关键词成功,等待搜索结果...")
        sendLog("✅ 输入搜索关键词成功,等待搜索结果...")

        // 3. 等待1秒后点击搜索结果
        handler.postDelayed({
            clickSearchResult(keyword)
        }, 1000)
    }

    /**
     * 点击搜索结果
     */
    private fun clickSearchResult(keyword: String) {
        android.util.Log.e("WEWORK_DEBUG", "🔍 clickSearchResult() 被调用")
        sendLog("🔍 查找搜索结果...")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            sendLog("❌ 无法获取rootNode")
            return
        }

        // 查找搜索结果中的客户名称
        val resultNode = findNodeByText(rootNode, keyword)
        if (resultNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索结果: $keyword")
            sendLog("❌ 未找到搜索结果: $keyword")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到搜索结果,查找头像...")
        sendLog("✅ 找到搜索结果,查找头像...")

        // 查找对应的头像并点击
        val avatarNode = findAvatarForCustomer(resultNode)
        if (avatarNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到头像")
            sendLog("❌ 未找到头像")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到头像,准备点击...")
        sendLog("✅ 找到头像,准备点击...")

        val clickSuccess = clickNode(avatarNode)
        if (clickSuccess) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 点击头像成功!")
            sendLog("✅ 点击头像成功!")
        } else {
            android.util.Log.e("WEWORK_DEBUG", "❌ 点击头像失败")
            sendLog("❌ 点击头像失败")
        }
    }

    /**
     * 测试滚动页面
     */
    private fun testScrollPage() {
        android.util.Log.e("WEWORK_DEBUG", "🔄 testScrollPage() 被调用")
        sendLog("🔄 开始测试滚动页面...")

        // 执行5次滚动
        performTestScroll(0, 5)
    }

    /**
     * 执行测试滚动
     */
    private fun performTestScroll(count: Int, maxCount: Int) {
        if (count >= maxCount) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 测试滚动完成,共滚动${count}次")
            sendLog("✅ 测试滚动完成,共滚动${count}次")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "🔄 执行第${count + 1}次滚动...")
        sendLog("🔄 执行第${count + 1}次滚动...")

        performScrollDownGesture {
            // 滚动完成后,等待1秒再继续下一次滚动
            handler.postDelayed({
                performTestScroll(count + 1, maxCount)
            }, 1000)
        }
    }

    /**
     * 执行向下滚动手势
     */
    private fun performScrollDownGesture(onComplete: () -> Unit) {
        try {
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val screenWidth = displayMetrics.widthPixels

            val startX = screenWidth / 2f
            val startY = screenHeight * 0.7f
            val endY = screenHeight * 0.3f

            android.util.Log.e("WEWORK_DEBUG", "📜 执行向下滚动手势: ($startX, $startY) → ($startX, $endY)")

            val path = android.graphics.Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)

            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            gestureBuilder.addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 500)
            )

            val result = dispatchGesture(
                gestureBuilder.build(),
                object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        android.util.Log.e("WEWORK_DEBUG", "✅ 滚动手势完成")
                        handler.post {
                            onComplete()
                        }
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        android.util.Log.e("WEWORK_DEBUG", "❌ 滚动手势被取消")
                        handler.post {
                            onComplete()
                        }
                    }
                },
                handler
            )

            android.util.Log.e("WEWORK_DEBUG", "📋 dispatchGesture 返回: $result")

            // 如果dispatchGesture返回false,说明手势分发失败,直接调用回调
            if (!result) {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ dispatchGesture返回false,手势分发失败")
                handler.postDelayed({
                    onComplete()
                }, 500)
            }
        } catch (e: Exception) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 滚动异常: ${e.message}")
            handler.post {
                onComplete()
            }
        }
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
     * 为客户节点查找对应的部门信息
     */
    private fun findDepartmentForCustomer(customerNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 向上查找到包含部门信息的父节点
        var parent = customerNode.parent
        while (parent != null) {
            // 查找resource-id为dsr的部门信息节点
            val department = findNodeByResourceId(parent, "com.tencent.wework:id/dsr")
            if (department != null) {
                return department
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
     * 查找所有列表容器(RecyclerView或ListView)
     */
    private fun findAllListContainers(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        val className = node.className?.toString() ?: ""
        if (className == "androidx.recyclerview.widget.RecyclerView" ||
            className == "android.widget.ListView") {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            findAllListContainers(node.getChild(i), result)
        }
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
     * 测试滚动当前页面 - 只测试滚动功能,不做其他操作
     */
    private fun testScrollCurrentPage() {
        android.util.Log.e("WEWORK_DEBUG", "🧪 开始测试滚动当前页面")
        sendLog("🧪 开始测试滚动当前页面")

        val rootNode = rootInActiveWindow ?: run {
            sendLog("❌ 无法获取窗口信息")
            Toast.makeText(this, "❌ 无法获取窗口信息", Toast.LENGTH_LONG).show()
            return
        }

        try {
            // 查找ListView (resource-id="com.tencent.wework:id/f_1")
            val listView = findNodeByResourceId(rootNode, "com.tencent.wework:id/f_1")

            if (listView == null) {
                sendLog("❌ 未找到ListView")
                Toast.makeText(this, "❌ 未找到ListView", Toast.LENGTH_LONG).show()
                return
            }

            sendLog("✅ 找到ListView")
            sendLog("📋 ListView信息:")
            sendLog("  - scrollable: ${listView.isScrollable}")
            sendLog("  - childCount: ${listView.childCount}")
            sendLog("  - bounds: ${listView.getBoundsInScreen(android.graphics.Rect())}")

            // 记录滚动前的状态
            val beforeScrollText = StringBuilder()
            beforeScrollText.append("📝 滚动前的内容:\n")
            for (i in 0 until listView.childCount) {
                val child = listView.getChild(i)
                if (child != null) {
                    val text = child.text?.toString() ?: child.contentDescription?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        beforeScrollText.append("  - $text\n")
                    }
                    child.recycle()
                }
            }
            sendLog(beforeScrollText.toString())

            // 尝试滚动
            sendLog("🔄 尝试滚动...")
            val scrollSuccess = listView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            sendLog("📊 滚动结果: ${if (scrollSuccess) "成功" else "失败"}")

            // 等待一下,然后检查滚动后的状态
            handler.postDelayed({
                checkAfterScroll()
            }, 1500)

        } catch (e: Exception) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 测试滚动异常: ${e.message}")
            sendLog("❌ 测试滚动异常: ${e.message}")
            Toast.makeText(this, "❌ 测试失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 检查滚动后的状态
     */
    private fun checkAfterScroll() {
        android.util.Log.e("WEWORK_DEBUG", "🔍 检查滚动后的状态")
        sendLog("🔍 检查滚动后的状态")

        val rootNode = rootInActiveWindow ?: run {
            sendLog("❌ 无法获取窗口信息")
            return
        }

        try {
            val listView = findNodeByResourceId(rootNode, "com.tencent.wework:id/f_1")

            if (listView == null) {
                sendLog("❌ 未找到ListView")
                return
            }

            // 记录滚动后的状态
            val afterScrollText = StringBuilder()
            afterScrollText.append("📝 滚动后的内容:\n")
            for (i in 0 until listView.childCount) {
                val child = listView.getChild(i)
                if (child != null) {
                    val text = child.text?.toString() ?: child.contentDescription?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        afterScrollText.append("  - $text\n")
                    }
                    child.recycle()
                }
            }
            sendLog(afterScrollText.toString())

            sendLog("✅ 测试完成!")
            Toast.makeText(this, "✅ 测试完成,请查看日志", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 检查状态异常: ${e.message}")
            sendLog("❌ 检查状态异常: ${e.message}")
        }
    }

    /**
     * 测试点击放大镜按钮
     * 假设用户已经手动进入"我的客户"页面,直接点击放大镜
     */
    private fun testClickSearchButton() {
        android.util.Log.e("WEWORK_DEBUG", "🔍 testClickSearchButton() 被调用")
        sendLog("🔍 开始测试点击放大镜...")
        sendLog("📝 假设您已在'我的客户'页面")

        // 延迟1秒后点击放大镜
        handler.postDelayed({
            testClickSearchButtonFinal()
        }, 1000)
    }

    /**
     * 测试搜索群聊(用于测试点击放大镜)
     */
    private fun testSearchGroupChatForSearchButton(groupName: String) {
        android.util.Log.e("WEWORK_DEBUG", "🔍 开始搜索群聊: $groupName")
        sendLog("🔍 搜索群聊: $groupName")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            handler.postDelayed({ testSearchGroupChatForSearchButton(groupName) }, 1000)
            return
        }

        // 查找搜索按钮
        val searchButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/ik9")
        if (searchButton == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索按钮")
            handler.postDelayed({ testSearchGroupChatForSearchButton(groupName) }, 1000)
            return
        }

        clickNode(searchButton)
        android.util.Log.e("WEWORK_DEBUG", "✅ 已点击搜索按钮")

        // 等待搜索框出现
        handler.postDelayed({
            testInputGroupNameForSearchButton(groupName)
        }, 1000)
    }

    /**
     * 测试输入群聊名称(用于测试点击放大镜)
     */
    private fun testInputGroupNameForSearchButton(groupName: String) {
        android.util.Log.e("WEWORK_DEBUG", "⌨️ 输入群聊名称: $groupName")
        sendLog("⌨️ 输入群聊名称")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            handler.postDelayed({ testInputGroupNameForSearchButton(groupName) }, 1000)
            return
        }

        // 查找搜索输入框
        val searchInput = findNodeByResourceId(rootNode, "com.tencent.wework:id/jvh")
        if (searchInput == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索输入框")
            handler.postDelayed({ testInputGroupNameForSearchButton(groupName) }, 1000)
            return
        }

        // 输入群聊名称
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, groupName)
        searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        android.util.Log.e("WEWORK_DEBUG", "✅ 已输入群聊名称")

        // 等待搜索结果
        handler.postDelayed({
            testClickGroupChatForSearchButton(groupName)
        }, 1500)
    }

    /**
     * 测试点击群聊(用于测试点击放大镜)
     */
    private fun testClickGroupChatForSearchButton(groupName: String) {
        android.util.Log.e("WEWORK_DEBUG", "👆 点击群聊: $groupName")
        sendLog("👆 点击群聊")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            handler.postDelayed({ testClickGroupChatForSearchButton(groupName) }, 1000)
            return
        }

        // 查找群聊
        val groupNode = findNodeByTextExact(rootNode, groupName)
        if (groupNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到群聊: $groupName")
            handler.postDelayed({ testClickGroupChatForSearchButton(groupName) }, 1000)
            return
        }

        clickNode(groupNode)
        android.util.Log.e("WEWORK_DEBUG", "✅ 已点击群聊")
        sendLog("✅ 已进入群聊")

        // 等待进入群聊
        handler.postDelayed({
            testClickPlusButtonForSearchButton()
        }, 1500)
    }

    /**
     * 测试点击+号(用于测试点击放大镜)
     */
    private fun testClickPlusButtonForSearchButton() {
        android.util.Log.e("WEWORK_DEBUG", "➕ 点击+号")
        sendLog("➕ 点击+号")

        clickPlusButtonInGroupDetailForSearchButton()
    }

    /**
     * 点击群详情页面的+号按钮(用于测试点击放大镜)
     */
    private fun clickPlusButtonInGroupDetailForSearchButton() {
        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ clickPlusButtonInGroupDetailForSearchButton() }, 1000)
            return
        }

        // 查找群成员RecyclerView
        val memberRecyclerViews = mutableListOf<AccessibilityNodeInfo>()
        findRecyclerViews(rootNode, memberRecyclerViews)

        for ((index, recyclerView) in memberRecyclerViews.withIndex()) {
            val childCount = recyclerView.childCount

            if (childCount >= 2) {
                var plusButton: AccessibilityNodeInfo? = null

                for (i in 0 until childCount) {
                    val child = recyclerView.getChild(i) ?: continue

                    if (child.className == "android.widget.LinearLayout") {
                        val hasTextView = hasTextViewChild(child)

                        if (!hasTextView) {
                            val imageView = findClickableImageViewInNode(child)
                            if (imageView != null) {
                                plusButton = imageView
                                android.util.Log.e("WEWORK_DEBUG", "✅ 找到+号按钮")
                                break
                            }
                        }
                    }
                }

                if (plusButton != null) {
                    val clicked = plusButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    if (clicked) {
                        sendLog("✅ 已点击+号")
                        android.util.Log.e("WEWORK_DEBUG", "✅ +号点击成功")
                        handler.postDelayed({
                            testClickMyCustomersForSearchButton()
                        }, 1500)
                        return
                    }
                }
            }
        }

        android.util.Log.e("WEWORK_DEBUG", "❌ 未找到+号按钮，重试")
        handler.postDelayed({ clickPlusButtonInGroupDetailForSearchButton() }, 1000)
    }

    /**
     * 测试点击我的客户(用于测试点击放大镜)
     */
    private fun testClickMyCustomersForSearchButton() {
        android.util.Log.e("WEWORK_DEBUG", "👥 点击我的客户")
        sendLog("👥 点击我的客户")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            handler.postDelayed({ testClickMyCustomersForSearchButton() }, 1000)
            return
        }

        // 查找"我的客户"文本节点
        val myCustomersTextNode = findNodeByTextExact(rootNode, "我的客户")
        if (myCustomersTextNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到'我的客户'文本节点")
            handler.postDelayed({ testClickMyCustomersForSearchButton() }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到'我的客户'文本节点,开始查找头像...")

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

        if (cmdNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到cmd父节点")
            handler.postDelayed({ testClickMyCustomersForSearchButton() }, 1000)
            return
        }

        // 在cmd节点下查找头像节点
        val avatarNode = findNodeByResourceId(cmdNode, "com.tencent.wework:id:lmb")
        if (avatarNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到'我的客户'头像节点")
            handler.postDelayed({ testClickMyCustomersForSearchButton() }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到'我的客户'头像,准备点击...")
        val clickSuccess = clickNode(avatarNode)
        if (!clickSuccess) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 点击'我的客户'头像失败")
            handler.postDelayed({ testClickMyCustomersForSearchButton() }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 点击'我的客户'头像成功,等待页面加载...")
        sendLog("✅ 已点击我的客户")

        // 等待页面加载后点击放大镜
        handler.postDelayed({
            testClickSearchButtonFinal()
        }, 1500)
    }

    /**
     * 测试点击放大镜按钮(最终步骤)
     */
    private fun testClickSearchButtonFinal() {
        android.util.Log.e("WEWORK_DEBUG", "🔍 准备点击放大镜按钮...")
        sendLog("🔍 点击放大镜...")

        // 获取屏幕宽度
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // 使用相对坐标计算搜索按钮位置
        // 测试结果: 720px屏幕上,搜索按钮在x=590的位置
        // 相对位置: screenWidth - 130
        val searchButtonX = screenWidth - 130  // 搜索按钮的X坐标(相对)
        val searchButtonY = 124  // 标题栏中心Y坐标

        android.util.Log.e("WEWORK_DEBUG", "📍 屏幕宽度: $screenWidth, 放大镜按钮坐标: ($searchButtonX, $searchButtonY)")

        // 尝试在运行时查找坐标附近的可点击节点
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            android.util.Log.e("WEWORK_DEBUG", "🔍 开始遍历节点,查找坐标($searchButtonX, $searchButtonY)附近的可点击节点...")
            val targetNode = findNodeByCoordinates(rootNode, searchButtonX, searchButtonY, 50)

            if (targetNode != null) {
                val rect = android.graphics.Rect()
                targetNode.getBoundsInScreen(rect)
                android.util.Log.e("WEWORK_DEBUG", "✅ 找到目标节点: ${targetNode.className}, bounds=[$rect]")
                val clicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    android.util.Log.e("WEWORK_DEBUG", "✅ 成功点击节点!")
                    sendLog("✅ 已点击放大镜")
                    Toast.makeText(this, "✅ 测试完成!", Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.e("WEWORK_DEBUG", "❌ 节点点击失败")
                    sendLog("❌ 节点点击失败")
                    Toast.makeText(this, "❌ 点击失败", Toast.LENGTH_LONG).show()
                }
            } else {
                android.util.Log.e("WEWORK_DEBUG", "❌ 未找到目标节点,尝试使用GestureDescription...")

                // 如果找不到节点,回退到GestureDescription
                val path = android.graphics.Path()
                path.moveTo(searchButtonX.toFloat(), searchButtonY.toFloat())

                val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
                gestureBuilder.addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 200)
                )

                val gesture = gestureBuilder.build()
                dispatchGesture(gesture, null, null)

                android.util.Log.e("WEWORK_DEBUG", "✅ 已发送点击手势(200ms)")
                sendLog("✅ 已发送点击手势")
                Toast.makeText(this, "✅ 已发送点击", Toast.LENGTH_LONG).show()
            }
        } else {
            android.util.Log.e("WEWORK_DEBUG", "❌ 无法获取rootNode")
            Toast.makeText(this, "❌ 无法获取rootNode", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 根据坐标查找节点
     * @param node 根节点
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @param tolerance 容差范围(像素)
     * @return 找到的节点,如果没找到返回null
     */
    private fun findNodeByCoordinates(
        node: AccessibilityNodeInfo,
        targetX: Int,
        targetY: Int,
        tolerance: Int
    ): AccessibilityNodeInfo? {
        // 获取节点的屏幕坐标
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        // 计算节点中心点
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2

        // 检查是否在目标坐标附近
        if (Math.abs(centerX - targetX) <= tolerance && Math.abs(centerY - targetY) <= tolerance) {
            // 检查节点是否可点击
            if (node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                android.util.Log.e("WEWORK_DEBUG", "🎯 找到匹配节点: class=${node.className}, bounds=[$rect], clickable=${node.isClickable}")
                return node
            }
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findNodeByCoordinates(child, targetX, targetY, tolerance)
                if (result != null) {
                    return result
                }
            }
        }

        return null
    }

    /**
     * 批量邀请好友进群
     * 从消息页面开始的完整流程
     */
    private fun inviteCustomersToGroup() {
        android.util.Log.e("WEWORK_DEBUG", "👥 inviteCustomersToGroup() 被调用")
        sendLog("👥 开始批量邀请好友...")
        sendLog("📝 步骤1: 搜索群聊")

        // 读取群聊名称
        val prefs = getSharedPreferences("wework_auto", Context.MODE_PRIVATE)
        val groupName = prefs.getString("target_group_name", "") ?: ""

        if (groupName.isEmpty()) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 群聊名称为空")
            sendLog("❌ 群聊名称为空,请先在主界面输入群聊名称")
            Toast.makeText(this, "❌ 群聊名称为空", Toast.LENGTH_LONG).show()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "📝 目标群聊: $groupName")
        sendLog("📝 目标群聊: $groupName")

        // 开始搜索群聊
        searchGroupChatForInvite(groupName)
    }

    /**
     * 搜索群聊（用于批量邀请）
     */
    private fun searchGroupChatForInvite(groupName: String) {
        android.util.Log.e("WEWORK_DEBUG", "🔍 searchGroupChatForInvite() 被调用")
        sendLog("🔍 正在搜索群聊...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            Toast.makeText(this, "❌ 无法获取窗口信息", Toast.LENGTH_LONG).show()
            return
        }

        // 查找搜索按钮(放大镜)
        val searchButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/nht")
        if (searchButton == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索按钮")
            sendLog("❌ 未找到搜索按钮")
            Toast.makeText(this, "❌ 未找到搜索按钮", Toast.LENGTH_LONG).show()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到搜索按钮,准备点击")
        clickNode(searchButton)

        // 等待搜索页面打开,然后输入群聊名称
        handler.postDelayed({
            testInputSearchText(groupName)
        }, 1500)
    }

    /**
     * 测试输入搜索文本
     */
    private fun testInputSearchText(groupName: String) {
        android.util.Log.e("WEWORK_DEBUG", "⌨️ testInputSearchText() 被调用")
        sendLog("⌨️ 正在输入群聊名称...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            return
        }

        // 查找搜索输入框
        val editText = findEditText(rootNode)
        if (editText == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索输入框")
            sendLog("❌ 未找到搜索输入框")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到搜索输入框,准备输入: $groupName")

        // 输入群聊名称
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, groupName)
        editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        sendLog("✅ 已输入群聊名称: $groupName")

        // 等待搜索结果,然后点击
        handler.postDelayed({
            testClickSearchResult(groupName)
        }, 1500)
    }

    /**
     * 测试点击搜索结果
     */
    private fun testClickSearchResult(groupName: String) {
        android.util.Log.e("WEWORK_DEBUG", "👆 testClickSearchResult() 被调用")
        sendLog("👆 正在点击搜索结果...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            return
        }

        // 查找搜索结果RecyclerView
        val recyclerView = findNodeByResourceId(rootNode, "com.tencent.wework:id/ks8")
        if (recyclerView == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到搜索结果RecyclerView")
            sendLog("❌ 未找到搜索结果")
            return
        }

        // 查找所有可点击的ViewGroup
        val clickableViewGroups = mutableListOf<AccessibilityNodeInfo>()
        findClickableViewGroups(recyclerView, clickableViewGroups)
        android.util.Log.e("WEWORK_DEBUG", "📋 找到 ${clickableViewGroups.size} 个可点击的ViewGroup")

        // 查找包含群聊名称的ViewGroup
        for (viewGroup in clickableViewGroups) {
            if (containsText(viewGroup, groupName)) {
                android.util.Log.e("WEWORK_DEBUG", "✅ 找到包含'$groupName'的ViewGroup,准备点击")
                clickNode(viewGroup)
                sendLog("✅ 已点击群聊: $groupName")

                // 等待进入群聊,然后点击三个点
                handler.postDelayed({
                    sendLog("📝 步骤2: 点击三个点")
                    testClickThreeDots()
                }, 1500)
                return
            }
        }

        android.util.Log.e("WEWORK_DEBUG", "❌ 未找到包含'$groupName'的搜索结果")
        sendLog("❌ 未找到群聊: $groupName")
    }

    /**
     * 测试点击三个点
     */
    private fun testClickThreeDots() {
        android.util.Log.e("WEWORK_DEBUG", "👆 testClickThreeDots() 被调用")
        sendLog("👆 正在点击三个点...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ testClickThreeDots() }, 1000)
            return
        }

        // 查找三个点按钮
        val threeDotsButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/nhi")
        if (threeDotsButton == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到三个点按钮,重试")
            handler.postDelayed({ testClickThreeDots() }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到三个点按钮,准备点击")
        clickNode(threeDotsButton)
        sendLog("✅ 已点击三个点")

        // 等待进入群详情页面,然后点击+号
        handler.postDelayed({
            sendLog("📝 步骤3: 点击+号")
            testClickPlusButton()
        }, 1500)
    }

    /**
     * 测试点击+号
     */
    private fun testClickPlusButton() {
        android.util.Log.e("WEWORK_DEBUG", "👆 testClickPlusButton() 被调用")
        sendLog("👆 正在点击+号...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ testClickPlusButton() }, 1000)
            return
        }

        // 查找群成员RecyclerView
        val memberRecyclerViews = mutableListOf<AccessibilityNodeInfo>()
        findRecyclerViews(rootNode, memberRecyclerViews)

        android.util.Log.e("WEWORK_DEBUG", "📋 找到 ${memberRecyclerViews.size} 个RecyclerView")

        // 遍历所有RecyclerView,找到包含成员头像的那个
        for ((index, recyclerView) in memberRecyclerViews.withIndex()) {
            val childCount = recyclerView.childCount
            android.util.Log.e("WEWORK_DEBUG", "   RecyclerView[$index]: childCount=$childCount")

            if (childCount >= 2) {
                // 查找+号按钮
                var plusButton: AccessibilityNodeInfo? = null

                for (i in 0 until childCount) {
                    val child = recyclerView.getChild(i) ?: continue

                    if (child.className == "android.widget.LinearLayout") {
                        val hasTextView = hasTextViewChild(child)

                        if (!hasTextView) {
                            val imageView = findClickableImageViewInNode(child)
                            if (imageView != null) {
                                plusButton = imageView
                                android.util.Log.e("WEWORK_DEBUG", "✅ 找到+号按钮")
                                break
                            }
                        }
                    }
                }

                if (plusButton != null) {
                    clickNode(plusButton)
                    sendLog("✅ 已点击+号")

                    // 等待进入添加成员页面,然后开始搜索并添加第一个客户
                    handler.postDelayed({
                        sendLog("📝 步骤4: 开始搜索并添加客户")
                        // 初始化currentCustomerIndex为0
                        currentCustomerIndex = 0
                        searchAndAddSingleCustomer(0)
                    }, 1500)
                    return
                }
            }
        }

        android.util.Log.e("WEWORK_DEBUG", "❌ 未找到+号按钮,重试")
        handler.postDelayed({ testClickPlusButton() }, 1000)
    }

    /**
     * 测试选择"我的客户"
     */
    private fun testSelectMyCustomers() {
        android.util.Log.e("WEWORK_DEBUG", "👆 testSelectMyCustomers() 被调用")
        sendLog("👆 正在选择我的客户...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            handler.postDelayed({ testSelectMyCustomers() }, 1000)
            return
        }

        // 使用和selectMyCustomers()相同的逻辑
        // 查找"我的客户"文本节点
        val myCustomersTextNode = findNodeByTextExact(rootNode, "我的客户")
        android.util.Log.e("WEWORK_DEBUG", "🔍 查找'我的客户'文本节点: ${if (myCustomersTextNode != null) "找到" else "未找到"}")

        if (myCustomersTextNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到'我的客户'文本,重试")
            handler.postDelayed({ testSelectMyCustomers() }, 1000)
            return
        }

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

        if (cmdNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到cmd父节点,重试")
            handler.postDelayed({ testSelectMyCustomers() }, 1000)
            return
        }

        // 在cmd节点下查找头像节点
        val avatarNode = findNodeByResourceId(cmdNode, "com.tencent.wework:id/lmb")

        if (avatarNode == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到我的客户头像,重试")
            handler.postDelayed({ testSelectMyCustomers() }, 1000)
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到我的客户头像,准备点击")
        sendLog("👆 点击我的客户...")
        clickNode(avatarNode)
        sendLog("✅ 已选择我的客户")

        // 等待进入"我的客户"页面,然后开始勾选好友
        handler.postDelayed({
            sendLog("📝 步骤5: 勾选13个好友")
            testSelectCustomersInList()
        }, 1500)
    }

    /**
     * 测试在"我的客户"页面勾选好友
     */
    private fun testSelectCustomersInList() {
        android.util.Log.e("WEWORK_DEBUG", "🧪 testSelectCustomersInList() 被调用")
        sendLog("🧪 开始勾选好友...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            Toast.makeText(this, "❌ 无法获取窗口信息", Toast.LENGTH_LONG).show()
            return
        }

        // 打印当前页面的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印当前页面的所有文本：")
        printAllTexts(rootNode)

        // 查找ListView (添加成员页面的客户列表)
        val listView = findNodeByResourceId(rootNode, "com.tencent.wework:id/ctt")
        if (listView == null) {
            android.util.Log.e("WEWORK_DEBUG", "❌ 未找到客户列表ListView")
            sendLog("❌ 未找到客户列表")
            Toast.makeText(this, "❌ 未找到客户列表", Toast.LENGTH_LONG).show()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 找到客户列表ListView")
        sendLog("✅ 找到客户列表,开始查找并勾选好友...")

        // 需要邀请的好友列表
        val customersToInvite = approvedCustomers.toMutableList()
        val selectedCustomers = mutableListOf<String>()
        var scrollCount = 0
        val maxScrolls = 20  // 最多滚动20次

        // 开始查找并勾选好友
        selectCustomersWithScroll(customersToInvite, selectedCustomers, scrollCount, maxScrolls)
    }

    /**
     * 滚动查找并勾选客户
     */
    private fun selectCustomersWithScroll(
        customersToInvite: MutableList<String>,
        selectedCustomers: MutableList<String>,
        scrollCount: Int,
        maxScrolls: Int
    ) {
        android.util.Log.e("WEWORK_DEBUG", "🔍 selectCustomersWithScroll() 被调用")
        android.util.Log.e("WEWORK_DEBUG", "📊 还需邀请: ${customersToInvite.size} 个, 已选择: ${selectedCustomers.size} 个, 滚动次数: $scrollCount")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            return
        }

        // 在当前页面查找并勾选客户
        val iterator = customersToInvite.iterator()
        while (iterator.hasNext()) {
            val customerName = iterator.next()
            android.util.Log.e("WEWORK_DEBUG", "🔍 查找客户: $customerName")

            // 查找所有包含客户名称的节点（可能有多个同名客户）
            val customerNodes = findAllNodesByText(rootNode, customerName)
            if (customerNodes.isEmpty()) {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 当前页面未找到客户: $customerName")
                continue
            }

            android.util.Log.e("WEWORK_DEBUG", "📝 找到 ${customerNodes.size} 个名为 '$customerName' 的客户")

            // 尝试点击所有同名客户中可用的那个
            var clicked = false
            for ((index, customerNode) in customerNodes.withIndex()) {
                // 查找客户节点的头像
                val avatarNode = findAvatarForCustomer(customerNode)
                if (avatarNode == null) {
                    android.util.Log.e("WEWORK_DEBUG", "⚠️ 第${index + 1}个客户未找到头像: $customerName")
                    continue
                }

                // 详细日志:头像节点的状态
                android.util.Log.e("WEWORK_DEBUG", "📊 第${index + 1}个客户头像状态: isEnabled=${avatarNode.isEnabled}, isClickable=${avatarNode.isClickable}, isSelected=${avatarNode.isSelected}")

                if (!avatarNode.isEnabled) {
                    android.util.Log.e("WEWORK_DEBUG", "⚠️ 第${index + 1}个客户不可选择（可能已在群中）: $customerName")
                    continue
                }

                // 找到可用的客户,点击它
                android.util.Log.e("WEWORK_DEBUG", "👆 点击选择第${index + 1}个客户: $customerName")

                val rect = android.graphics.Rect()
                avatarNode.getBoundsInScreen(rect)
                android.util.Log.e("WEWORK_DEBUG", "📊 头像节点bounds: $rect")

                val clickSuccess = clickNode(avatarNode)
                android.util.Log.e("WEWORK_DEBUG", "📊 点击结果: ${if (clickSuccess) "成功" else "失败"}")

                if (clickSuccess) {
                    selectedCustomers.add(customerName)
                    clicked = true

                    // 每次点击后稍微延迟
                    Thread.sleep(300)
                    break  // 只点击一个可用的
                } else {
                    android.util.Log.e("WEWORK_DEBUG", "⚠️ 第${index + 1}个客户点击失败: $customerName")
                }
            }

            if (clicked) {
                iterator.remove()  // 从待邀请列表中移除
            } else {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 所有名为 '$customerName' 的客户都不可选择")
            }
        }

        sendLog("📝 已选择 ${selectedCustomers.size} 个客户")
        android.util.Log.e("WEWORK_DEBUG", "📝 已选择客户: ${selectedCustomers.joinToString(", ")}")

        // 如果还有客户需要邀请,且没有超过最大滚动次数,继续滚动
        if (customersToInvite.isNotEmpty() && scrollCount < maxScrolls) {
            android.util.Log.e("WEWORK_DEBUG", "🔄 还有 ${customersToInvite.size} 个客户需要查找,尝试滚动...")
            sendLog("🔄 滚动查找更多客户...")

            // 使用手势滑动来滚动页面
            performScrollDownGesture {
                // 滚动完成后继续查找
                handler.postDelayed({
                    selectCustomersWithScroll(customersToInvite, selectedCustomers, scrollCount + 1, maxScrolls)
                }, 1000)
            }
            return
        }

        // 完成选择
        android.util.Log.e("WEWORK_DEBUG", "")
        android.util.Log.e("WEWORK_DEBUG", "========================================")
        android.util.Log.e("WEWORK_DEBUG", "✅ 客户选择完成!")
        android.util.Log.e("WEWORK_DEBUG", "📊 已选择: ${selectedCustomers.size} 个")
        android.util.Log.e("WEWORK_DEBUG", "📊 未找到: ${customersToInvite.size} 个")
        android.util.Log.e("WEWORK_DEBUG", "📝 已选择客户: ${selectedCustomers.joinToString(", ")}")
        if (customersToInvite.isNotEmpty()) {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到客户: ${customersToInvite.joinToString(", ")}")
        }
        android.util.Log.e("WEWORK_DEBUG", "========================================")

        sendLog("✅ 客户选择完成! 已选择 ${selectedCustomers.size} 个")
        if (customersToInvite.isNotEmpty()) {
            sendLog("⚠️ 未找到 ${customersToInvite.size} 个客户")
        }

        Toast.makeText(this, "✅ 已选择 ${selectedCustomers.size} 个客户", Toast.LENGTH_LONG).show()

        // 现在可以点击确定按钮了
        sendLog("📝 请手动点击确定按钮完成邀请")
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

