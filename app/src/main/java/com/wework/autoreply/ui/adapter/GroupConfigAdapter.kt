package com.wework.autoreply.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wework.autoreply.R
import com.wework.autoreply.database.GroupConfig
import java.text.SimpleDateFormat
import java.util.*

/**
 * 群组配置列表适配器
 */
class GroupConfigAdapter(
    private val onItemClick: (GroupConfig) -> Unit,
    private val onEditClick: (GroupConfig) -> Unit,
    private val onDeleteClick: (GroupConfig) -> Unit
) : ListAdapter<GroupConfig, GroupConfigAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_group_name)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_group_time)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(item: GroupConfig) {
            tvName.text = item.name
            tvTime.text = formatTime(item.createdAt)

            itemView.setOnClickListener { onItemClick(item) }
            btnEdit.setOnClickListener { onEditClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GroupConfig>() {
        override fun areItemsTheSame(oldItem: GroupConfig, newItem: GroupConfig): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GroupConfig, newItem: GroupConfig): Boolean {
            return oldItem == newItem
        }
    }
}

