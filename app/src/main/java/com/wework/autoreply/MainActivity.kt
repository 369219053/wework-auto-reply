package com.wework.autoreply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 主Activity - 批量处理界面
 */
class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var etGroupName: EditText
    private lateinit var btnStartBatch: Button
    private lateinit var btnCheckPermissions: Button
    private lateinit var tvApprovedCount: TextView
    private lateinit var tvInvitedCount: TextView
    private lateinit var tvFailedCount: TextView
    private lateinit var tvLog: TextView

    // 日志广播接收器
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: return
            addLog(message)
        }
    }

    // 统计数据广播接收器
    private val statsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val approved = intent?.getIntExtra("approved", 0) ?: 0
            val invited = intent?.getIntExtra("invited", 0) ?: 0
            val failed = intent?.getIntExtra("failed", 0) ?: 0
            updateStats(approved, invited, failed)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configManager = ConfigManager(this)

        initViews()
        loadConfig()
        setupListeners()
        registerReceivers()

        // 首次启动时显示权限引导
        if (!isAccessibilityServiceEnabled()) {
            showPermissionGuide()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceivers()
    }

    private fun registerReceivers() {
        // 注册日志接收器
        val logFilter = IntentFilter("com.wework.autoreply.LOG_UPDATE")
        registerReceiver(logReceiver, logFilter, RECEIVER_NOT_EXPORTED)

        // 注册统计数据接收器
        val statsFilter = IntentFilter("com.wework.autoreply.STATS_UPDATE")
        registerReceiver(statsReceiver, statsFilter, RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterReceivers() {
        try {
            unregisterReceiver(logReceiver)
            unregisterReceiver(statsReceiver)
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    private fun initViews() {
        etGroupName = findViewById(R.id.et_group_name)
        btnStartBatch = findViewById(R.id.btn_start_batch)
        btnCheckPermissions = findViewById(R.id.btn_check_permissions)
        tvApprovedCount = findViewById(R.id.tv_approved_count)
        tvInvitedCount = findViewById(R.id.tv_invited_count)
        tvFailedCount = findViewById(R.id.tv_failed_count)
        tvLog = findViewById(R.id.tv_log)
    }

    private fun loadConfig() {
        val groupName = configManager.getGroupName()
        if (groupName.isNotEmpty()) {
            etGroupName.setText(groupName)
        }
    }

    private fun setupListeners() {
        btnStartBatch.setOnClickListener {
            startBatchProcess()
        }

        btnCheckPermissions.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun startBatchProcess() {
        val groupName = etGroupName.text.toString().trim()

        if (groupName.isEmpty()) {
            Toast.makeText(this, "请输入群聊名称", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务权限", Toast.LENGTH_LONG).show()
            showPermissionGuide()
            return
        }

        // 保存群聊名称
        configManager.setGroupName(groupName)

        // 发送广播给无障碍服务,开始批量处理
        val intent = Intent("com.wework.autoreply.START_BATCH_PROCESS")
        intent.putExtra("groupName", groupName)
        sendBroadcast(intent)

        addLog("📱 已发送批量处理指令")
        addLog("⏳ 请确保企业微信已打开并在「新的客户」列表页面")

        Toast.makeText(this, "批量处理已启动,请查看日志", Toast.LENGTH_LONG).show()
    }

    fun addLog(message: String) {
        runOnUiThread {
            val currentLog = tvLog.text.toString()
            val newLog = if (currentLog == "等待开始...") {
                message
            } else {
                "$currentLog\n$message"
            }
            tvLog.text = newLog

            // 自动滚动到底部
            val scrollView = tvLog.parent as? ScrollView
            scrollView?.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    fun updateStats(approved: Int, invited: Int, failed: Int) {
        runOnUiThread {
            tvApprovedCount.text = "✅ 通过验证: $approved 个"
            tvInvitedCount.text = "👥 邀请成功: $invited 个"
            tvFailedCount.text = "❌ 邀请失败: $failed 个"
        }
    }
    
    private fun checkAndRequestPermissions() {
        if (!isAccessibilityServiceEnabled()) {
            showPermissionDialog()
        } else {
            Toast.makeText(this, "✅ 无障碍服务权限已授予!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("权限检查")
            .setMessage("缺少无障碍服务权限\n\n请点击确定前往设置")
            .setPositiveButton("前往设置") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPermissionGuide() {
        AlertDialog.Builder(this)
            .setTitle("欢迎使用企微批量处理助手")
            .setMessage("本应用需要无障碍服务权限才能正常工作\n\n功能:\n1. 批量通过好友申请\n2. 批量邀请到群聊\n\n请点击确定前往设置")
            .setPositiveButton("前往设置") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("稍后设置", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请找到「企微批量处理助手」并开启", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(packageName) == true
    }
}

