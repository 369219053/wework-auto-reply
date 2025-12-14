package com.wework.autoreply

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 主Activity - 配置界面
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var configManager: ConfigManager
    private lateinit var etGroupLink: EditText
    private lateinit var etWelcomeMessage: EditText
    private lateinit var switchEnabled: Switch
    private lateinit var btnSave: Button
    private lateinit var btnCheckPermissions: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        configManager = ConfigManager(this)
        
        initViews()
        loadConfig()
        setupListeners()
        
        // 首次启动时显示权限引导
        if (!hasAllPermissions()) {
            showPermissionGuide()
        }
    }
    
    private fun initViews() {
        etGroupLink = findViewById(R.id.et_group_link)
        etWelcomeMessage = findViewById(R.id.et_welcome_message)
        switchEnabled = findViewById(R.id.switch_enabled)
        btnSave = findViewById(R.id.btn_save)
        btnCheckPermissions = findViewById(R.id.btn_check_permissions)
    }
    
    private fun loadConfig() {
        etGroupLink.setText(configManager.getGroupChatLink())
        etWelcomeMessage.setText(configManager.getWelcomeMessage())
        switchEnabled.isChecked = configManager.isEnabled()
    }
    
    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveConfig()
        }
        
        btnCheckPermissions.setOnClickListener {
            checkAndRequestPermissions()
        }
    }
    
    private fun saveConfig() {
        val groupLink = etGroupLink.text.toString().trim()
        val welcomeMessage = etWelcomeMessage.text.toString().trim()
        val enabled = switchEnabled.isChecked
        
        if (welcomeMessage.isEmpty()) {
            Toast.makeText(this, "欢迎语不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        configManager.setGroupChatLink(groupLink)
        configManager.setWelcomeMessage(welcomeMessage)
        configManager.setEnabled(enabled)
        
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
    }
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = mutableListOf<String>()
        
        if (!isNotificationListenerEnabled()) {
            missingPermissions.add("通知访问权限")
        }
        
        if (!isAccessibilityServiceEnabled()) {
            missingPermissions.add("无障碍服务权限")
        }
        
        if (missingPermissions.isEmpty()) {
            Toast.makeText(this, "所有权限已授予!", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionDialog(missingPermissions)
        }
    }
    
    private fun showPermissionDialog(missingPermissions: List<String>) {
        val message = "缺少以下权限:\n${missingPermissions.joinToString("\n")}\n\n请点击确定前往设置"
        
        AlertDialog.Builder(this)
            .setTitle("权限检查")
            .setMessage(message)
            .setPositiveButton("前往设置") { _, _ ->
                openPermissionSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showPermissionGuide() {
        AlertDialog.Builder(this)
            .setTitle("欢迎使用企微自动回复")
            .setMessage("本应用需要以下权限才能正常工作:\n\n1. 通知访问权限 - 监听企业微信通知\n2. 无障碍服务权限 - 自动发送消息\n\n请点击确定前往设置")
            .setPositiveButton("前往设置") { _, _ ->
                openPermissionSettings()
            }
            .setNegativeButton("稍后设置", null)
            .show()
    }
    
    private fun openPermissionSettings() {
        try {
            // 打开通知监听设置
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            
            // 提示用户还需要开启无障碍服务
            Toast.makeText(this, "请同时开启通知访问和无障碍服务", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return isNotificationListenerEnabled() && isAccessibilityServiceEnabled()
    }
    
    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(packageName) == true
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(packageName) == true
    }
}

