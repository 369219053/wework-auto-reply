package com.wework.autoreply.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wework.autoreply.R
import com.wework.autoreply.database.GroupConfig
import com.wework.autoreply.ui.adapter.SendHistoryAdapter
import com.wework.autoreply.viewmodel.BatchSendViewModel
import com.wework.autoreply.viewmodel.GroupManagementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 批量发送Fragment
 * 执行批量发送任务
 */
class BatchSendFragment : Fragment() {

    private lateinit var viewModel: BatchSendViewModel
    private lateinit var groupViewModel: GroupManagementViewModel

    private lateinit var spinnerTemplate: Spinner
    private lateinit var spinnerGroup: Spinner
    private lateinit var tvPreview: TextView
    private lateinit var btnStartSend: Button
    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var historyAdapter: SendHistoryAdapter

    private var messageGroups: List<com.wework.autoreply.database.MessageGroup> = emptyList()
    private var groups: List<GroupConfig> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_batch_send, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[BatchSendViewModel::class.java]
        groupViewModel = ViewModelProvider(this)[GroupManagementViewModel::class.java]

        // 初始化视图
        spinnerTemplate = view.findViewById(R.id.spinner_template)
        spinnerGroup = view.findViewById(R.id.spinner_group)
        tvPreview = view.findViewById(R.id.tv_preview)
        btnStartSend = view.findViewById(R.id.btn_start_send)
        recyclerViewHistory = view.findViewById(R.id.recycler_view_history)

        // 设置RecyclerView
        recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = SendHistoryAdapter()
        recyclerViewHistory.adapter = historyAdapter

        // 加载消息组列表
        viewModel.allGroups.observe(viewLifecycleOwner) { groupList ->
            messageGroups = groupList
            val groupNames = groupList.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groupNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTemplate.adapter = adapter

            // 监听选择变化
            spinnerTemplate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // 加载群组列表
        groupViewModel.allGroupConfigs.observe(viewLifecycleOwner) { groupList ->
            groups = groupList
            val groupNames = groupList.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groupNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerGroup.adapter = adapter

            // 监听选择变化
            spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // 开始发送按钮点击事件
        btnStartSend.setOnClickListener {
            startBatchSend()
        }

        // 观察发送历史
        viewModel.recentHistory.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
        }
    }

    private fun updatePreview() {
        val messageGroupPosition = spinnerTemplate.selectedItemPosition
        val groupPosition = spinnerGroup.selectedItemPosition

        if (messageGroupPosition < 0 || messageGroupPosition >= messageGroups.size) {
            tvPreview.text = "请选择消息组"
            return
        }

        if (groupPosition < 0 || groupPosition >= groups.size) {
            tvPreview.text = "请选择群组"
            return
        }

        val messageGroup = messageGroups[messageGroupPosition]
        val group = groups[groupPosition]

        // 显示消息组信息
        val preview = buildString {
            append("📦 消息组: ${messageGroup.name}\n")
            append("📋 群组: ${group.name}\n")
            append("📊 转发消息数量: ${messageGroup.messageCount} 条\n")

            if (messageGroup.delayMin > 0 || messageGroup.delayMax > 0) {
                val minSec = messageGroup.delayMin / 1000
                val maxSec = messageGroup.delayMax / 1000
                append("⏱️ 随机延迟: ${minSec}-${maxSec} 秒\n")
            }

            append("\n提示: 将从素材库聊天转发最新的 ${messageGroup.messageCount} 条消息")
        }
        tvPreview.text = preview
    }

    private fun startBatchSend() {
        val messageGroupPosition = spinnerTemplate.selectedItemPosition
        val groupPosition = spinnerGroup.selectedItemPosition

        if (messageGroupPosition < 0 || messageGroupPosition >= messageGroups.size) {
            Toast.makeText(requireContext(), "请选择消息组", Toast.LENGTH_SHORT).show()
            return
        }

        if (groupPosition < 0 || groupPosition >= groups.size) {
            Toast.makeText(requireContext(), "请选择群组", Toast.LENGTH_SHORT).show()
            return
        }

        val messageGroup = messageGroups[messageGroupPosition]
        val group = groups[groupPosition]

        // 获取群聊列表
        groupViewModel.getGroupChats(group.id).observe(viewLifecycleOwner) { chats ->
            if (chats.isEmpty()) {
                Toast.makeText(requireContext(), "该群组没有群聊", Toast.LENGTH_SHORT).show()
                return@observe
            }

            val chatNames = chats.map { it.chatName }

            // 确认对话框
            AlertDialog.Builder(requireContext())
                .setTitle("确认批量发送")
                .setMessage("将向 ${chatNames.size} 个群聊转发 ${messageGroup.messageCount} 条消息\n\n确定继续吗?")
                .setPositiveButton("开始发送") { _, _ ->
                    executeBatchSend(messageGroup, group, chatNames)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun executeBatchSend(
        messageGroup: com.wework.autoreply.database.MessageGroup,
        group: GroupConfig,
        chatNames: List<String>
    ) {
        android.util.Log.e("BatchSendFragment", "🚀 executeBatchSend() 被调用")

        // 从数据库读取素材库聊天名称
        lifecycleScope.launch {
            android.util.Log.e("BatchSendFragment", "🚀 进入lifecycleScope.launch")
            val database = com.wework.autoreply.database.AppDatabase.getDatabase(requireContext())
            val settings = withContext(kotlinx.coroutines.Dispatchers.IO) {
                database.appSettingsDao().getSettingsSync() ?: com.wework.autoreply.database.AppSettings()
            }

            val materialSourceChat = settings.materialSourceChat
            if (materialSourceChat.isEmpty()) {
                Toast.makeText(requireContext(), "❌ 请先在设置中配置素材库聊天名称", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 创建发送历史记录
            val historyId = viewModel.createSendTask(group.id, messageGroup.id, chatNames.size)

            // 使用SharedPreferences传递参数
            val prefs = requireContext().getSharedPreferences("batch_send", android.content.Context.MODE_PRIVATE)
            val gson = com.google.gson.Gson()

            prefs.edit().apply {
                putBoolean("should_start", true)
                putLong("start_time", System.currentTimeMillis())
                putLong("message_group_id", messageGroup.id)
                putLong("history_id", historyId)
                putString("material_source_chat", materialSourceChat)
                putString("group_chats", gson.toJson(chatNames))
                putInt("message_count", messageGroup.messageCount)
                putInt("delay_min", messageGroup.delayMin)
                putInt("delay_max", messageGroup.delayMax)
                commit()  // 🔥 使用commit()同步写入,确保立即完成
            }

            android.util.Log.e("BatchSendFragment", "✅ SharedPreferences写入完成")

            android.util.Log.e("BatchSendFragment", "🚀 准备启动企业微信")
            Toast.makeText(requireContext(), "正在启动批量发送...", Toast.LENGTH_SHORT).show()

            // 🔥 等待500ms,确保SharedPreferences写入完成
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                android.util.Log.e("BatchSendFragment", "🚀 500ms延迟结束,开始启动企微")

                // 打开企业微信
                try {
                // 🔥 使用显式Intent启动企业微信
                val launchIntent = android.content.Intent().apply {
                    setClassName("com.tencent.wework", "com.tencent.wework.launch.LaunchSplashActivity")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                android.util.Log.e("BatchSendFragment", "🚀 准备调用startActivity")
                startActivity(launchIntent)
                android.util.Log.e("BatchSendFragment", "✅ startActivity调用成功")
                Toast.makeText(requireContext(), "正在启动批量发送...", Toast.LENGTH_SHORT).show()

                // 🔥 延迟500ms后最小化应用,让WeworkAutoService处理弹窗
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    requireActivity().moveTaskToBack(true)
                }, 500)

                } catch (e: Exception) {
                    android.util.Log.e("BatchSendFragment", "❌ 启动失败: ${e.message}", e)
                    Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, 500)
        }
    }
}

