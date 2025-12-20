package com.wework.autoreply

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.wework.autoreply.database.MessageGroup
import com.wework.autoreply.viewmodel.MessageGroupViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 消息组详情页面(简化版)
 * 用于配置从素材库聊天转发的消息数量和延迟设置
 */
class MessageGroupDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: MessageGroupViewModel
    private lateinit var etGroupName: EditText
    private lateinit var etGroupDescription: EditText
    private lateinit var etMessageCount: EditText
    private lateinit var cbEnableDelay: CheckBox
    private lateinit var etDelayMin: EditText
    private lateinit var etDelayMax: EditText
    private lateinit var btnSave: Button

    private var groupId: Long = -1
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_group_detail_new)

        // 获取传递的消息组ID
        groupId = intent.getLongExtra("GROUP_ID", -1)
        isEditMode = groupId != -1L

        // 设置标题
        supportActionBar?.title = if (isEditMode) "编辑消息组" else "创建消息组"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[MessageGroupViewModel::class.java]

        // 初始化视图
        initViews()

        // 如果是编辑模式,加载数据
        if (isEditMode) {
            loadGroupData()
        }

        // 设置保存按钮点击事件
        btnSave.setOnClickListener {
            saveGroup()
        }

        // 设置延迟复选框监听
        cbEnableDelay.setOnCheckedChangeListener { _, isChecked ->
            etDelayMin.isEnabled = isChecked
            etDelayMax.isEnabled = isChecked
        }
    }

    private fun initViews() {
        etGroupName = findViewById(R.id.et_group_name)
        etGroupDescription = findViewById(R.id.et_group_description)
        etMessageCount = findViewById(R.id.et_message_count)
        cbEnableDelay = findViewById(R.id.cb_enable_delay)
        etDelayMin = findViewById(R.id.et_delay_min)
        etDelayMax = findViewById(R.id.et_delay_max)
        btnSave = findViewById(R.id.btn_save)

        // 默认禁用延迟输入框
        etDelayMin.isEnabled = false
        etDelayMax.isEnabled = false
    }

    private fun loadGroupData() {
        CoroutineScope(Dispatchers.Main).launch {
            val group = withContext(Dispatchers.IO) {
                viewModel.getGroupById(groupId)
            }

            group?.let {
                etGroupName.setText(it.name)
                etGroupDescription.setText(it.description)
                etMessageCount.setText(it.messageCount.toString())

                // 如果有延迟设置,显示延迟
                if (it.delayMin > 0 || it.delayMax > 0) {
                    cbEnableDelay.isChecked = true
                    etDelayMin.setText(it.delayMin.toString())
                    etDelayMax.setText(it.delayMax.toString())
                }
            }
        }
    }

    private fun saveGroup() {
        val name = etGroupName.text.toString().trim()
        val description = etGroupDescription.text.toString().trim()
        val messageCountStr = etMessageCount.text.toString().trim()

        // 验证输入
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入消息组名称", Toast.LENGTH_SHORT).show()
            return
        }

        val messageCount = messageCountStr.toIntOrNull() ?: 1
        if (messageCount < 1) {
            Toast.makeText(this, "消息数量必须大于0", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取延迟设置
        var delayMin = 0
        var delayMax = 0
        if (cbEnableDelay.isChecked) {
            delayMin = etDelayMin.text.toString().toIntOrNull() ?: 0
            delayMax = etDelayMax.text.toString().toIntOrNull() ?: 0

            if (delayMin < 0 || delayMax < 0) {
                Toast.makeText(this, "延迟时间不能为负数", Toast.LENGTH_SHORT).show()
                return
            }

            if (delayMin > delayMax) {
                Toast.makeText(this, "最小延迟不能大于最大延迟", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 创建或更新消息组
        CoroutineScope(Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            val group = MessageGroup(
                id = if (isEditMode) groupId else 0,
                name = name,
                description = description,
                messageCount = messageCount,
                delayMin = delayMin,
                delayMax = delayMax,
                createdAt = now,
                updatedAt = now
            )

            viewModel.insertGroup(group)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MessageGroupDetailActivity, "保存成功", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

