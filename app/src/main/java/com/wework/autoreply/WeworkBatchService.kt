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
 * 企微批量处理无障碍服务
 */
class WeworkBatchService : AccessibilityService() {
    
    companion object {
        private const val TAG = "WeworkBatchService"
        private const val WEWORK_PACKAGE = "com.tencent.wework"
        private var instance: WeworkBatchService? = null
        
        fun addLog(message: String) {
            instance?.sendLogToActivity(message)
        }
        
        fun updateStats(approved: Int, invited: Int, failed: Int) {
            instance?.sendStatsToActivity(approved, invited, failed)
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var groupName: String = ""
    private var isProcessing = false
    
    private val batchProcessReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.wework.autoreply.START_BATCH_PROCESS") {
                groupName = intent.getStringExtra("groupName") ?: ""
                Log.d(TAG, "收到批量处理请求, 群聊: $groupName")
                startBatchProcess()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this

        // 设置AccessibilityHelper的service实例
        AccessibilityHelper.setService(this)

        // 注册广播接收器
        val filter = IntentFilter("com.wework.autoreply.START_BATCH_PROCESS")
        registerReceiver(batchProcessReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        Log.d(TAG, "批量处理服务已启动")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batchProcessReceiver)
        instance = null
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 暂时不需要监听事件
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "服务被中断")
    }
    
    private fun startBatchProcess() {
        if (isProcessing) {
            sendLogToActivity("⚠️ 正在处理中,请稍候...")
            return
        }

        isProcessing = true
        sendLogToActivity("🚀 开始批量处理...")
        sendLogToActivity("📋 目标群聊: $groupName")

        // 在后台线程执行批量处理
        Thread {
            try {
                // 打开企业微信
                sendLogToActivity("📱 正在打开企业微信...")
                openWework()
                Thread.sleep(3000) // 等待企业微信启动

                // 阶段1: 批量通过好友申请
                val approvedCustomers = approveAllCustomers()
                
                if (approvedCustomers.isEmpty()) {
                    sendLogToActivity("⚠️ 没有待处理的好友申请")
                    isProcessing = false
                    return@Thread
                }
                
                sendLogToActivity("✅ 批量通过完成! 共 ${approvedCustomers.size} 个")
                
                // 阶段2: 批量邀请到群聊
                val result = inviteAllToGroup(approvedCustomers, groupName)
                
                sendLogToActivity("🎉 批量处理完成!")
                sendLogToActivity("📊 通过验证: ${approvedCustomers.size} 个")
                sendLogToActivity("📊 邀请成功: ${result.success.size} 个")
                sendLogToActivity("📊 邀请失败: ${result.failed.size} 个")
                
                updateStats(approvedCustomers.size, result.success.size, result.failed.size)
                
            } catch (e: Exception) {
                sendLogToActivity("❌ 处理失败: ${e.message}")
                Log.e(TAG, "批量处理失败", e)
            } finally {
                isProcessing = false
            }
        }.start()
    }
    
    private fun approveAllCustomers(): List<String> {
        sendLogToActivity("📍 阶段1: 批量通过好友申请...")

        val approvedCustomers = mutableListOf<String>()
        var processedCount = 0

        // 循环处理所有好友申请
        while (true) {
            AccessibilityHelper.sleep(1000)

            val rootNode = rootInActiveWindow ?: break

            // 查找所有"查看"按钮
            val viewButtons = AccessibilityHelper.findNodesByText(rootNode, "查看", exact = true)

            if (viewButtons.isEmpty()) {
                sendLogToActivity("✅ 所有好友申请已处理完毕")
                break
            }

            processedCount++
            sendLogToActivity("🎯 处理第 $processedCount 个好友申请 (剩余 ${viewButtons.size} 个)...")

            // 点击第一个"查看"按钮
            val viewButton = viewButtons.first()
            if (!AccessibilityHelper.clickNode(viewButton)) {
                sendLogToActivity("❌ 点击\"查看\"按钮失败")
                break
            }

            AccessibilityHelper.sleep(1500)

            // 获取客户名称
            val detailRoot = rootInActiveWindow
            val nameNode = AccessibilityHelper.findNodeByResourceId(detailRoot, "com.tencent.wework:id/moj")
            val customerName = nameNode?.text?.toString() ?: "未知客户"
            sendLogToActivity("📝 客户名称: $customerName")

            // 点击"通过验证"按钮
            val approveButton = AccessibilityHelper.findNodeByText(detailRoot, "通过验证", exact = true)
            if (approveButton == null) {
                sendLogToActivity("❌ 未找到\"通过验证\"按钮")
                performGlobalAction(GLOBAL_ACTION_BACK)
                continue
            }

            AccessibilityHelper.clickNode(approveButton)
            AccessibilityHelper.sleep(1500)

            // 点击"完成"按钮
            val completeRoot = rootInActiveWindow
            val completeButton = AccessibilityHelper.findNodeByText(completeRoot, "完成", exact = true)
            if (completeButton == null) {
                sendLogToActivity("❌ 未找到\"完成\"按钮")
                performGlobalAction(GLOBAL_ACTION_BACK)
                continue
            }

            AccessibilityHelper.clickNode(completeButton)
            AccessibilityHelper.sleep(2000)

            // 检查是否需要返回到列表页面
            val checkRoot = rootInActiveWindow
            val hasViewButton = AccessibilityHelper.findNodeByText(checkRoot, "查看", exact = true) != null

            if (!hasViewButton) {
                sendLogToActivity("⬅️ 从好友详情页返回到列表...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                AccessibilityHelper.sleep(2000)
            }

            // 记录客户名称
            approvedCustomers.add(customerName)
            sendLogToActivity("✅ 已通过验证: $customerName")

            AccessibilityHelper.sleep(1000)
        }

        sendLogToActivity("📊 批量通过完成! 共通过 ${approvedCustomers.size} 个好友申请")

        return approvedCustomers
    }
    
    private fun inviteAllToGroup(customerNames: List<String>, groupName: String): BatchResult {
        sendLogToActivity("📍 阶段2: 批量邀请到群聊...")
        sendLogToActivity("📋 批量邀请 ${customerNames.size} 个客户到群聊: $groupName")

        val success = mutableListOf<String>()
        val failed = mutableListOf<String>()

        if (customerNames.isEmpty()) {
            sendLogToActivity("⚠️ 没有客户需要邀请")
            return BatchResult(success, failed)
        }

        // 去重客户名称
        val uniqueCustomerNames = customerNames.distinct()
        if (uniqueCustomerNames.size < customerNames.size) {
            sendLogToActivity("⚠️ 检测到重复的客户名称,已去重: ${customerNames.size} → ${uniqueCustomerNames.size}")
        }

        try {
            // Step 1: 返回到"通讯录"页面
            sendLogToActivity("📍 Step 1: 返回到\"通讯录\"页面...")
            performGlobalAction(GLOBAL_ACTION_BACK)
            AccessibilityHelper.sleep(2000)
            sendLogToActivity("✅ 已返回到\"通讯录\"页面")

            // Step 2: 点击底部"消息"标签
            sendLogToActivity("📍 Step 2: 点击底部\"消息\"标签...")
            val messageTab = AccessibilityHelper.findNodeByText(rootInActiveWindow, "消息", exact = true)
            if (messageTab == null) {
                sendLogToActivity("❌ 未找到\"消息\"标签")
                return BatchResult(success, uniqueCustomerNames.toMutableList())
            }
            AccessibilityHelper.clickNode(messageTab)
            AccessibilityHelper.sleep(2000)
            sendLogToActivity("✅ 已进入消息页面")

            // Step 3: 点击群聊
            sendLogToActivity("📍 Step 3: 点击群聊\"$groupName\"...")
            val groupChat = AccessibilityHelper.findNodeByText(rootInActiveWindow, groupName, exact = true)
            if (groupChat == null) {
                sendLogToActivity("❌ 未找到群聊\"$groupName\"")
                return BatchResult(success, uniqueCustomerNames.toMutableList())
            }
            AccessibilityHelper.clickNode(groupChat)
            AccessibilityHelper.sleep(1500)
            sendLogToActivity("✅ 已进入群聊页面")

            // Step 4: 点击右上角三个点 (通过坐标)
            sendLogToActivity("📍 Step 4: 点击右上角三个点...")
            AccessibilityHelper.tap(682, 124, 1500)
            sendLogToActivity("✅ 已进入群详情页面")

            // Step 5: 点击"查看全部群成员"
            sendLogToActivity("📍 Step 5: 点击\"查看全部群成员\"...")
            val viewAllMembers = AccessibilityHelper.findNodeByText(rootInActiveWindow, "查看全部群成员", exact = false)
            if (viewAllMembers == null) {
                sendLogToActivity("❌ 未找到\"查看全部群成员\"")
                return BatchResult(success, uniqueCustomerNames.toMutableList())
            }
            AccessibilityHelper.clickNode(viewAllMembers)
            AccessibilityHelper.sleep(1500)
            sendLogToActivity("✅ 已进入全部群成员页面")

            // Step 6: 点击"添加"按钮 (右上角,通过坐标)
            sendLogToActivity("📍 Step 6: 点击\"添加\"按钮...")
            AccessibilityHelper.tap(654, 124, 1500)
            sendLogToActivity("✅ 已进入添加成员选择页面")

            // Step 7: 点击"我的客户"
            sendLogToActivity("📍 Step 7: 点击\"我的客户\"...")
            val myCustomer = AccessibilityHelper.findNodeByText(rootInActiveWindow, "我的客户", exact = true)
            if (myCustomer == null) {
                sendLogToActivity("❌ 未找到\"我的客户\"")
                return BatchResult(success, uniqueCustomerNames.toMutableList())
            }
            AccessibilityHelper.clickNode(myCustomer)
            AccessibilityHelper.sleep(1500)
            sendLogToActivity("✅ 已显示客户列表")

            // Step 8: 勾选所有客户
            sendLogToActivity("📍 Step 8: 勾选 ${uniqueCustomerNames.size} 个客户...")
            val selectedCustomers = mutableListOf<String>()
            val failedCustomers = mutableListOf<String>()

            for ((index, customerName) in uniqueCustomerNames.withIndex()) {
                sendLogToActivity("  ${index + 1}/${uniqueCustomerNames.size}. 勾选客户: $customerName")

                val customerRoot = rootInActiveWindow

                // 查找"今天"分组
                val todayNode = AccessibilityHelper.findNodeByText(customerRoot, "今天", exact = true)
                val todayBounds = AccessibilityHelper.getNodeBounds(todayNode)
                val todayY2 = todayBounds?.bottom ?: 0

                // 查找下一个分组
                val nextGroupNode = AccessibilityHelper.findNodeByText(customerRoot, "12-15", exact = false)
                val nextGroupBounds = AccessibilityHelper.getNodeBounds(nextGroupNode)
                val nextGroupY1 = nextGroupBounds?.top ?: 9999

                // 查找客户名称节点
                val customerNodes = AccessibilityHelper.findNodesByText(customerRoot, customerName, exact = true)
                val todayCustomers = AccessibilityHelper.filterNodesByYRange(customerNodes, todayY2, nextGroupY1)

                if (todayCustomers.isEmpty()) {
                    sendLogToActivity("  ❌ 在\"今天\"分组下未找到客户: $customerName")
                    failedCustomers.add(customerName)
                    continue
                }

                // 点击客户名称勾选
                val customer = todayCustomers.first()
                val center = AccessibilityHelper.getNodeCenter(customer)
                if (center == null) {
                    sendLogToActivity("  ❌ 无法获取客户坐标: $customerName")
                    failedCustomers.add(customerName)
                    continue
                }

                AccessibilityHelper.tap(center.first, center.second, 500)
                sendLogToActivity("  ✅ 已勾选: $customerName")
                selectedCustomers.add(customerName)
            }

            sendLogToActivity("✅ 已勾选 ${selectedCustomers.size} 个客户")

            if (selectedCustomers.isEmpty()) {
                sendLogToActivity("⚠️ 没有客户被成功勾选,跳过邀请步骤")
                return BatchResult(success, failedCustomers)
            }

            // Step 9: 点击"确定"按钮
            sendLogToActivity("📍 Step 9: 点击\"确定\"按钮...")
            AccessibilityHelper.sleep(1000)

            val confirmButton = AccessibilityHelper.findNodeByText(rootInActiveWindow, "确定", exact = false)
                ?: AccessibilityHelper.findNodeByResourceId(rootInActiveWindow, "com.tencent.wework:id/nhn")

            if (confirmButton == null) {
                sendLogToActivity("❌ 未找到\"确定\"按钮")
                return BatchResult(success, selectedCustomers.toMutableList())
            }

            AccessibilityHelper.clickNode(confirmButton)
            AccessibilityHelper.sleep(2000)
            sendLogToActivity("✅ 已点击\"确定\"按钮")

            // Step 10: 检查是否有"邀请"确认弹窗
            sendLogToActivity("📍 Step 10: 检查是否有\"邀请\"确认弹窗...")
            val inviteButton = AccessibilityHelper.findNodeByText(rootInActiveWindow, "邀请", exact = true)

            if (inviteButton != null) {
                sendLogToActivity("✅ 检测到\"邀请\"确认弹窗,点击\"邀请\"按钮...")
                AccessibilityHelper.clickNode(inviteButton)
                AccessibilityHelper.sleep(1500)
                sendLogToActivity("✅ 已点击\"邀请\"按钮")
            } else {
                sendLogToActivity("ℹ️ 未检测到\"邀请\"确认弹窗,直接完成")
            }

            sendLogToActivity("✅ 成功邀请 ${selectedCustomers.size} 个客户到群聊!")

            success.addAll(selectedCustomers)
            failed.addAll(failedCustomers)

        } catch (e: Exception) {
            sendLogToActivity("❌ 邀请过程出错: ${e.message}")
            Log.e(TAG, "邀请失败", e)
            return BatchResult(success, uniqueCustomerNames.toMutableList())
        }

        return BatchResult(success, failed)
    }
    
    private fun sendLogToActivity(message: String) {
        val intent = Intent("com.wework.autoreply.LOG_UPDATE")
        intent.putExtra("message", message)
        sendBroadcast(intent)
        Log.d(TAG, message)
    }
    
    private fun sendStatsToActivity(approved: Int, invited: Int, failed: Int) {
        val intent = Intent("com.wework.autoreply.STATS_UPDATE")
        intent.putExtra("approved", approved)
        intent.putExtra("invited", invited)
        intent.putExtra("failed", failed)
        sendBroadcast(intent)
    }

    /**
     * 打开企业微信APP
     */
    private fun openWework() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(WEWORK_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d(TAG, "已打开企业微信")
            } else {
                sendLogToActivity("❌ 未找到企业微信应用")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开企业微信失败", e)
            sendLogToActivity("❌ 打开企业微信失败: ${e.message}")
        }
    }
    
    data class BatchResult(val success: List<String>, val failed: List<String>)
}

