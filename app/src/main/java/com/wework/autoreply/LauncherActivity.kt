package com.wework.autoreply

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * 启动器Activity
 * 让用户选择进入功能一(批量通过)或功能二(批量发送)
 */
class LauncherActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        
        val btnFeature1: Button = findViewById(R.id.btn_feature_1)
        val btnFeature2: Button = findViewById(R.id.btn_feature_2)
        
        // 功能一:批量通过好友申请
        btnFeature1.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        
        // 功能二:批量发送消息
        btnFeature2.setOnClickListener {
            startActivity(Intent(this, BatchSendActivity::class.java))
        }
    }
}

