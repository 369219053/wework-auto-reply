package com.wework.autoreply

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.wework.autoreply.database.AppDatabase
import com.wework.autoreply.database.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 批量发送功能主Activity
 * 功能二的主界面,使用底部导航栏+Fragment架构
 *
 * 注意:不修改原有的MainActivity(功能一)
 */
class BatchSendActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_send)

        database = AppDatabase.getDatabase(this)

        // 设置底部导航栏
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_batch_send, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etMaterialSourceChat = dialogView.findViewById<EditText>(R.id.et_material_source_chat)

        // 加载当前设置
        lifecycleScope.launch {
            val settings = withContext(Dispatchers.IO) {
                database.appSettingsDao().getSettingsSync() ?: AppSettings()
            }

            etMaterialSourceChat.setText(settings.materialSourceChat)

            // 显示对话框
            AlertDialog.Builder(this@BatchSendActivity)
                .setTitle("应用设置")
                .setView(dialogView)
                .setPositiveButton("保存") { _, _ ->
                    val materialSourceChat = etMaterialSourceChat.text.toString().trim()
                    saveSettings(materialSourceChat)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun saveSettings(materialSourceChat: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val settings = database.appSettingsDao().getSettingsSync() ?: AppSettings()
                val updatedSettings = settings.copy(materialSourceChat = materialSourceChat)
                database.appSettingsDao().insertSettings(updatedSettings)
            }

            Toast.makeText(this@BatchSendActivity, "设置已保存", Toast.LENGTH_SHORT).show()
        }
    }
}

