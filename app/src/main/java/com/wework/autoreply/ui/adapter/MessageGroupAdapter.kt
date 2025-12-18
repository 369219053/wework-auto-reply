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
import com.wework.autoreply.database.MessageGroup
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息组列表适配器
 */
class MessageGroupAdapter(
    private val onItemClick: (MessageGroup) -> Unit,
    private val onEditClick: (MessageGroup) -> Unit,
    private val onDeleteClick: (MessageGroup) -> Unit,
    private val templateCountProvider: (Long) -> Int = { 0 }
) : ListAdapter<MessageGroup, MessageGroupAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_group_name)
        private val tvDescription: TextView = itemView.findViewById(R.id.tv_group_description)
        private val tvTemplateCount: TextView = itemView.findViewById(R.id.tv_template_count)
        private val tvUpdatedAt: TextView = itemView.findViewById(R.id.tv_updated_at)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(group: MessageGroup) {
            tvName.text = group.name
            
            if (group.description.isNotEmpty()) {
                tvDescription.visibility = View.VISIBLE
                tvDescription.text = group.description
            } else {
                tvDescription.visibility = View.GONE
            }
            
            val count = templateCountProvider(group.id)
            tvTemplateCount.text = "包含 $count 条消息"
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            tvUpdatedAt.text = "更新于 ${dateFormat.format(Date(group.updatedAt))}"

            itemView.setOnClickListener { onItemClick(group) }
            btnEdit.setOnClickListener { onEditClick(group) }
            btnDelete.setOnClickListener { onDeleteClick(group) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MessageGroup>() {
        override fun areItemsTheSame(oldItem: MessageGroup, newItem: MessageGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MessageGroup, newItem: MessageGroup): Boolean {
            return oldItem == newItem
        }
    }
}

