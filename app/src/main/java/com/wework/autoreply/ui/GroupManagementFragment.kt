package com.wework.autoreply.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wework.autoreply.R
import com.wework.autoreply.database.GroupConfig
import com.wework.autoreply.ui.adapter.GroupConfigAdapter
import com.wework.autoreply.viewmodel.GroupManagementViewModel

/**
 * 群组管理Fragment
 * 显示和管理大群组列表
 */
class GroupManagementFragment : Fragment() {

    private lateinit var viewModel: GroupManagementViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: GroupConfigAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_group_management, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[GroupManagementViewModel::class.java]

        // 初始化视图
        recyclerView = view.findViewById(R.id.recycler_view_groups)
        fabAdd = view.findViewById(R.id.fab_add_group)

        // 设置RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = GroupConfigAdapter(
            onItemClick = { group -> showGroupDetails(group) },
            onEditClick = { group -> showEditGroupDialog(group) },
            onDeleteClick = { group -> showDeleteConfirmDialog(group) }
        )
        recyclerView.adapter = adapter

        // 观察数据变化
        viewModel.allGroupConfigs.observe(viewLifecycleOwner) { groups ->
            adapter.submitList(groups)
        }

        // 添加按钮点击事件
        fabAdd.setOnClickListener {
            showAddGroupDialog()
        }
    }

    private fun showGroupDetails(group: GroupConfig) {
        Toast.makeText(requireContext(), "查看群组: ${group.name}", Toast.LENGTH_SHORT).show()
    }

    private fun showAddGroupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_group, null)
        val etGroupName = dialogView.findViewById<android.widget.EditText>(R.id.et_group_name)
        val etChatList = dialogView.findViewById<android.widget.EditText>(R.id.et_chat_list)

        AlertDialog.Builder(requireContext())
            .setTitle("添加群组")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val groupName = etGroupName.text.toString().trim()
                val chatListText = etChatList.text.toString().trim()

                if (groupName.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入群组名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (chatListText.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入至少一个群聊名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val chatNames = chatListText.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (chatNames.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入至少一个群聊名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.addGroupConfig(groupName, chatNames)
                Toast.makeText(requireContext(), "已添加群组: $groupName (${chatNames.size}个群聊)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditGroupDialog(group: GroupConfig) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_group, null)
        val etGroupName = dialogView.findViewById<android.widget.EditText>(R.id.et_group_name)
        val etChatList = dialogView.findViewById<android.widget.EditText>(R.id.et_chat_list)

        // 加载现有数据
        etGroupName.setText(group.name)
        viewModel.getGroupChats(group.id).observe(viewLifecycleOwner) { chats ->
            val chatListText = chats.joinToString("\n") { it.chatName }
            etChatList.setText(chatListText)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("编辑群组")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val groupName = etGroupName.text.toString().trim()
                val chatListText = etChatList.text.toString().trim()

                if (groupName.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入群组名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (chatListText.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入至少一个群聊名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val chatNames = chatListText.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (chatNames.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入至少一个群聊名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.updateGroupConfig(group.id, groupName, chatNames)
                Toast.makeText(requireContext(), "已更新群组: $groupName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirmDialog(group: GroupConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除群组")
            .setMessage("确定要删除群组「${group.name}」吗?")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteGroupConfig(group)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

