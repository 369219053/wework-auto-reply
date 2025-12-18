package com.wework.autoreply.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.wework.autoreply.database.AppDatabase
import com.wework.autoreply.database.MessageGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 消息组ViewModel
 */
class MessageGroupViewModel(application: Application) : AndroidViewModel(application) {
    
    private val messageGroupDao = AppDatabase.getDatabase(application).messageGroupDao()
    
    val allGroups: LiveData<List<MessageGroup>> = messageGroupDao.getAllGroups()
    
    /**
     * 根据ID获取消息组
     */
    suspend fun getGroupById(id: Long): MessageGroup? {
        return withContext(Dispatchers.IO) {
            messageGroupDao.getGroupById(id)
        }
    }
    
    /**
     * 插入或更新消息组
     */
    fun insertGroup(group: MessageGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            messageGroupDao.insertGroup(group)
        }
    }

    /**
     * 删除消息组
     */
    fun deleteGroup(group: MessageGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            messageGroupDao.deleteGroup(group)
        }
    }
}

