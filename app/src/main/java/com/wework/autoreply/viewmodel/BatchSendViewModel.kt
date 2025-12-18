package com.wework.autoreply.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.wework.autoreply.database.AppDatabase
import com.wework.autoreply.database.MessageGroup
import com.wework.autoreply.database.SendHistory
import com.wework.autoreply.repository.BatchSendRepository
import kotlinx.coroutines.launch

/**
 * 批量发送ViewModel
 */
class BatchSendViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BatchSendRepository
    private val messageGroupDao = AppDatabase.getDatabase(application).messageGroupDao()

    val recentHistory: LiveData<List<SendHistory>>
    val allGroups: LiveData<List<MessageGroup>> = messageGroupDao.getAllGroups()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BatchSendRepository(database)
        recentHistory = repository.getRecentHistory()
    }
    
    /**
     * 创建发送任务
     */
    fun createSendTask(groupConfigId: Long, messageTemplateId: Long, totalChats: Int): Long {
        var taskId: Long = 0
        viewModelScope.launch {
            val history = SendHistory(
                groupConfigId = groupConfigId,
                messageTemplateId = messageTemplateId,
                status = "pending",
                totalChats = totalChats,
                sentChats = 0,
                failedChats = null,
                createdAt = System.currentTimeMillis()
            )
            taskId = repository.saveHistory(history)
        }
        return taskId
    }
    
    /**
     * 更新发送进度
     */
    fun updateProgress(historyId: Long, sentChats: Int, status: String) {
        viewModelScope.launch {
            val history = repository.getHistoryById(historyId)
            if (history != null) {
                val updatedHistory = history.copy(
                    sentChats = sentChats,
                    status = status,
                    completedAt = if (status == "completed" || status == "failed") {
                        System.currentTimeMillis()
                    } else {
                        null
                    }
                )
                repository.updateHistory(updatedHistory)
            }
        }
    }
}

