package com.wework.autoreply.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wework.autoreply.MessageGroupDetailActivity
import com.wework.autoreply.R
import com.wework.autoreply.ui.adapter.MessageGroupAdapter
import com.wework.autoreply.viewmodel.MessageGroupViewModel

/**
 * 消息组管理Fragment
 * 用于管理消息组列表
 */
class MessageGroupFragment : Fragment() {

    private lateinit var viewModel: MessageGroupViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageGroupAdapter
    private lateinit var emptyView: TextView
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_message_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MessageGroupViewModel::class.java]

        // 初始化视图
        recyclerView = view.findViewById(R.id.recycler_view_message_groups)
        emptyView = view.findViewById(R.id.tv_empty)
        fab = view.findViewById(R.id.fab_add_group)

        // 设置RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = MessageGroupAdapter(
            onItemClick = { group ->
                // 点击查看详情
                val intent = Intent(requireContext(), MessageGroupDetailActivity::class.java)
                intent.putExtra("GROUP_ID", group.id)
                startActivity(intent)
            },
            onEditClick = { group ->
                // 点击编辑
                val intent = Intent(requireContext(), MessageGroupDetailActivity::class.java)
                intent.putExtra("GROUP_ID", group.id)
                startActivity(intent)
            },
            onDeleteClick = { group ->
                // 删除消息组
                viewModel.deleteGroup(group)
            },
            templateCountProvider = { groupId ->
                // 返回消息数量
                0
            }
        )
        recyclerView.adapter = adapter

        // 观察消息组列表
        viewModel.allGroups.observe(viewLifecycleOwner) { groups ->
            adapter.submitList(groups)
            
            // 显示/隐藏空视图
            if (groups.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }
        }

        // FAB点击事件 - 创建新消息组
        fab.setOnClickListener {
            val intent = Intent(requireContext(), MessageGroupDetailActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 刷新列表
        viewModel.allGroups.observe(viewLifecycleOwner) { groups ->
            adapter.submitList(groups)
        }
    }
}

