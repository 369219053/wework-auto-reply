package com.wework.autoreply.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wework.autoreply.R
import com.wework.autoreply.database.SendHistory
import java.text.SimpleDateFormat
import java.util.*

/**
 * 发送历史列表适配器
 */
class SendHistoryAdapter : ListAdapter<SendHistory, SendHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_send_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tv_history_time)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_history_status)
        private val tvProgress: TextView = itemView.findViewById(R.id.tv_history_progress)

        fun bind(item: SendHistory) {
            tvTime.text = formatTime(item.createdAt)
            tvStatus.text = getStatusText(item.status)
            tvProgress.text = "${item.sentChats}/${item.totalChats}"
            
            // 根据状态设置颜色
            val statusColor = when (item.status) {
                "completed" -> 0xFF4CAF50.toInt() // 绿色
                "running" -> 0xFF2196F3.toInt()   // 蓝色
                "failed" -> 0xFFF44336.toInt()    // 红色
                else -> 0xFF999999.toInt()        // 灰色
            }
            tvStatus.setTextColor(statusColor)
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        private fun getStatusText(status: String): String {
            return when (status) {
                "pending" -> "⏳ 等待中"
                "running" -> "🚀 发送中"
                "completed" -> "✅ 已完成"
                "failed" -> "❌ 失败"
                else -> "未知"
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SendHistory>() {
        override fun areItemsTheSame(oldItem: SendHistory, newItem: SendHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SendHistory, newItem: SendHistory): Boolean {
            return oldItem == newItem
        }
    }
}

