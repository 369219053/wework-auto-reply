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

        // 检查是否需要启动批量处理
        if (!isProcessing && event.packageName == "com.tencent.wework") {
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
        val shouldStart = prefs.getBoolean("should_start_batch", false)

        android.util.Log.e("WEWORK_DEBUG", "📋 shouldStart = $shouldStart")

        if (shouldStart) {
            val groupName = prefs.getString("target_group_name", "") ?: ""
            val startTime = prefs.getLong("start_time", 0)
            val timeDiff = System.currentTimeMillis() - startTime

            android.util.Log.e("WEWORK_DEBUG", "📋 groupName = $groupName, timeDiff = $timeDiff ms")

            // 检查是否在10秒内（避免重复触发）
            if (timeDiff < 10000 && groupName.isNotEmpty()) {
                android.util.Log.e("WEWORK_DEBUG", "🚀 开始批量处理！群聊名称: $groupName")

                // 显示Toast
                Toast.makeText(this, "🚀 开始批量处理: $groupName", Toast.LENGTH_LONG).show()

                // 清除标志
                prefs.edit().putBoolean("should_start_batch", false).apply()

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

        if (isProcessing) {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 已有任务在进行中，退出")
            sendLog("⚠️ 已有任务在进行中")
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "📍 设置 isProcessing = true")
        isProcessing = true
        currentState = ProcessState.OPENING_WEWORK
        currentCustomerIndex = 0
        approvedCount = 0
        invitedCount = 0
        failedCount = 0
        approvedCustomers.clear()  // 清空已通过客户列表

        android.util.Log.e("WEWORK_DEBUG", "📍 isProcessing 已设置为: $isProcessing")
        android.util.Log.e("WEWORK_DEBUG", "📍 currentState = $currentState")

        sendLog("🚀 开始批量处理流程")
        sendLog("📱 目标群聊: $targetGroupName")

        // 企业微信已经打开了，直接开始导航
        android.util.Log.e("WEWORK_DEBUG", "📍 企业微信已打开，开始导航到通讯录...")
        handler.postDelayed({
            navigateToContacts()
        }, 2000)
    }

    /**
     * 打开企业微信应用
     */
    private fun openWework() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(WEWORK_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                sendLog("✅ 正在打开企业微信...")

                // 等待应用打开
                handler.postDelayed({
                    currentState = ProcessState.NAVIGATING_TO_CONTACTS
                    navigateToContacts()
                }, 2000)
            } else {
                sendLog("❌ 未找到企业微信应用")
                stopProcessing()
            }
        } catch (e: Exception) {
            sendLog("❌ 打开企业微信失败: ${e.message}")
            stopProcessing()
        }
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

            // 等待进入新的客户列表，然后开始处理客户
            handler.postDelayed({
                currentState = ProcessState.PROCESSING_CUSTOMER
                processNextCustomer()
            }, 1500)
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
        android.util.Log.e("WEWORK_DEBUG", "🔧 processNextCustomer() 被调用")
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
        android.util.Log.e("WEWORK_DEBUG", "找到 ${viewButtons.size} 个'查看'按钮")

        if (viewButtons.isEmpty()) {
            sendLog("✅ 所有好友申请已通过！")
            sendLog("📊 统计: 通过${approvedCount}个, 失败${failedCount}个")
            android.util.Log.e("WEWORK_DEBUG", "✅ 好友申请处理完成，开始邀请到群聊")

            // 进入邀请到群聊的流程
            handler.postDelayed({
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
        sendLog("⏹️ 批量处理已停止")
    }

    /**
     * 导航到消息页面
     */
    private fun navigateToMessages() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 navigateToMessages() 被调用")
        sendLog("📱 正在导航到消息页面...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找消息按钮")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印当前页面的所有文本：")
        printAllTexts(rootNode)

        // 检查是否已经在消息页面
        val hasMessageTab = findNodeByText(rootNode, "消息") != null
        val hasContactTab = findNodeByText(rootNode, "通讯录") != null

        if (hasMessageTab && hasContactTab) {
            // 在主页面，点击"消息"按钮
            val messagesButton = findNodeByText(rootNode, "消息")
            if (messagesButton != null) {
                android.util.Log.e("WEWORK_DEBUG", "✅ 找到消息按钮，准备点击")
                clickNode(messagesButton)
                sendLog("✅ 已点击消息")

                handler.postDelayed({
                    currentState = ProcessState.OPENING_GROUP_CHAT
                    openGroupChat()
                }, 1500)
            } else {
                android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到消息按钮，1秒后重试")
                sendLog("⚠️ 未找到消息按钮，重试中...")
                handler.postDelayed({ navigateToMessages() }, 1000)
            }
        } else {
            // 不在主页面，先按返回键返回
            android.util.Log.e("WEWORK_DEBUG", "⬅️ 不在主页面，按返回键")
            sendLog("⬅️ 返回主页面...")
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ navigateToMessages() }, 1000)
        }
    }

    /**
     * 打开群聊
     */
    private fun openGroupChat() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 openGroupChat() 被调用")
        sendLog("👥 正在打开群聊: $targetGroupName")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找群聊")

        // 打印所有文本，方便调试
        android.util.Log.e("WEWORK_DEBUG", "📋 打印消息页面的所有文本：")
        printAllTexts(rootNode)

        // 查找包含群聊名称和人数的节点（例如："智界Aigc客户群（18）"）
        val groupChatNode = findNodeContainingText(rootNode, targetGroupName)
        if (groupChatNode != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到群聊节点: text='${groupChatNode.text}'，准备点击")
            clickNode(groupChatNode)
            sendLog("✅ 已打开群聊")

            handler.postDelayed({
                currentState = ProcessState.OPENING_GROUP_MEMBERS
                openGroupMembers()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到群聊: $targetGroupName，1秒后重试")
            sendLog("⚠️ 未找到群聊，重试中...")
            handler.postDelayed({ openGroupChat() }, 1000)
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
     * 打开群成员列表
     */
    private fun openGroupMembers() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 openGroupMembers() 被调用")
        sendLog("👥 正在打开群成员列表...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找...按钮")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印群聊页面的所有文本：")
        printAllTexts(rootNode)

        // 直接通过resource-id查找右上角的"..."按钮
        // 根据UI dump，右上角有两个按钮：nhn和nhi，nhi是"..."按钮
        var menuButton: AccessibilityNodeInfo? = findNodeByResourceId(rootNode, "com.tencent.wework:id/nhi")

        // 如果没找到，尝试nhn
        if (menuButton == null) {
            menuButton = findNodeByResourceId(rootNode, "com.tencent.wework:id/nhn")
        }

        // 如果还是没找到，尝试其他方式
        if (menuButton == null) {
            menuButton = findNodeByText(rootNode, "...")
                ?: findNodeByContentDescription(rootNode, "更多")
                ?: findNodeByContentDescription(rootNode, "聊天详情")
                ?: findNodeByContentDescription(rootNode, "更多功能")
        }

        if (menuButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到菜单按钮 (id=${menuButton.viewIdResourceName})，准备点击")
            clickNode(menuButton)
            sendLog("✅ 已点击菜单")

            handler.postDelayed({
                clickViewAllMembers()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到菜单按钮，1秒后重试")
            sendLog("⚠️ 未找到菜单按钮，重试中...")
            handler.postDelayed({ openGroupMembers() }, 1000)
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
     * 点击"查看全部群成员"
     */
    private fun clickViewAllMembers() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickViewAllMembers() 被调用")
        sendLog("👥 正在查看全部群成员...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找查看全部群成员按钮")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印菜单页面的所有文本：")
        printAllTexts(rootNode)

        // 查找"查看全部群成员"按钮
        val viewMembersButton = findNodeByText(rootNode, "查看全部群成员")
        if (viewMembersButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到查看全部群成员按钮，准备点击")
            clickNode(viewMembersButton)
            sendLog("✅ 已打开群成员列表")

            handler.postDelayed({
                currentState = ProcessState.CLICKING_ADD_BUTTON
                clickAddButton()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到查看全部群成员按钮，1秒后重试")
            sendLog("⚠️ 未找到按钮，重试中...")
            handler.postDelayed({ clickViewAllMembers() }, 1000)
        }
    }

    /**
     * 点击"添加"按钮
     */
    private fun clickAddButton() {
        android.util.Log.e("WEWORK_DEBUG", "🔧 clickAddButton() 被调用")
        sendLog("➕ 正在点击添加按钮...")

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.e("WEWORK_DEBUG", "❌ rootInActiveWindow 为 null")
            sendLog("❌ 无法获取窗口信息")
            retryOrStop()
            return
        }

        android.util.Log.e("WEWORK_DEBUG", "✅ 获取到 rootNode，开始查找添加按钮")

        // 打印界面上的所有文本
        android.util.Log.e("WEWORK_DEBUG", "📋 打印群成员页面的所有文本：")
        printAllTexts(rootNode)

        // 查找"添加"按钮
        val addButton = findNodeByText(rootNode, "添加")
        if (addButton != null) {
            android.util.Log.e("WEWORK_DEBUG", "✅ 找到添加按钮，准备点击")
            clickNode(addButton)
            sendLog("✅ 已点击添加")

            handler.postDelayed({
                currentState = ProcessState.SELECTING_MY_CUSTOMERS
                selectMyCustomers()
            }, 1500)
        } else {
            android.util.Log.e("WEWORK_DEBUG", "⚠️ 未找到添加按钮，1秒后重试")
            sendLog("⚠️ 未找到添加按钮，重试中...")
            handler.postDelayed({ clickAddButton() }, 1000)
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
            sendLog("⚠️ 没有客户需要邀请")
            handler.postDelayed({
                currentState = ProcessState.CONFIRMING_INVITE
                confirmInvite()
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

            android.util.Log.e("WEWORK_DEBUG", "📤 dispatchGesture 返回: $result")
            return result
        }

        return false
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

