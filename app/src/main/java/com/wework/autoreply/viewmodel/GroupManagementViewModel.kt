package com.wework.autoreply.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.wework.autoreply.database.AppDatabase
import com.wework.autoreply.database.GroupConfig
import com.wework.autoreply.database.GroupChat
import com.wework.autoreply.repository.BatchSendRepository
import kotlinx.coroutines.launch

/**
 * 群组管理ViewModel
 */
class GroupManagementViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: BatchSendRepository
    val allGroupConfigs: LiveData<List<GroupConfig>>
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = BatchSendRepository(database)
        allGroupConfigs = repository.getAllGroupConfigs()
    }
    
    /**
     * 添加大群组
     */
    fun addGroupConfig(name: String, chatNames: List<String>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val config = GroupConfig(
                name = name,
                createdAt = now,
                updatedAt = now
            )
            val groupId = repository.saveGroupConfig(config)
            
            // 保存群聊列表
            val chats = chatNames.map { chatName ->
                GroupChat(
                    groupConfigId = groupId,
                    chatName = chatName
                )
            }
            repository.saveGroupChats(chats)
        }
    }
    
    /**
     * 更新大群组
     */
    fun updateGroupConfig(id: Long, name: String, chatNames: List<String>) {
        viewModelScope.launch {
            val config = repository.getGroupConfigById(id)
            if (config != null) {
                val updatedConfig = config.copy(
                    name = name,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateGroupConfig(updatedConfig)
                
                // 删除旧的群聊列表
                repository.deleteGroupChatsByConfigId(id)
                
                // 保存新的群聊列表
                val chats = chatNames.map { chatName ->
                    GroupChat(
                        groupConfigId = id,
                        chatName = chatName
                    )
                }
                repository.saveGroupChats(chats)
            }
        }
    }
    
    /**
     * 删除大群组
     */
    fun deleteGroupConfig(config: GroupConfig) {
        viewModelScope.launch {
            repository.deleteGroupConfig(config)
        }
    }
    
    /**
     * 获取群组的群聊列表
     */
    fun getGroupChats(groupConfigId: Long): LiveData<List<GroupChat>> {
        return repository.getGroupChatsByConfigId(groupConfigId)
    }
}

