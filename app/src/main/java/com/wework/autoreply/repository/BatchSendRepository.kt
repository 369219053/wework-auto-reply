package com.wework.autoreply.repository

import androidx.lifecycle.LiveData
import com.wework.autoreply.database.*

/**
 * 批量发送功能的Repository
 * 统一管理数据访问
 */
class BatchSendRepository(private val database: AppDatabase) {

    private val materialDao = database.materialDao()
    private val groupConfigDao = database.groupConfigDao()
    private val messageGroupDao = database.messageGroupDao()
    private val sendHistoryDao = database.sendHistoryDao()
    private val appSettingsDao = database.appSettingsDao()
    
    // ========== 素材库操作 ==========
    
    fun getMaterialLibrary(): LiveData<MaterialLibrary?> = materialDao.getMaterialLibrary()
    
    suspend fun getMaterialLibrarySync(): MaterialLibrary? = materialDao.getMaterialLibrarySync()
    
    suspend fun saveMaterialLibrary(library: MaterialLibrary) {
        materialDao.insertMaterialLibrary(library)
    }
    
    suspend fun updateMaterialLibrary(library: MaterialLibrary) {
        materialDao.updateMaterialLibrary(library)
    }
    
    // ========== 素材操作 ==========
    
    fun getAllMaterials(): LiveData<List<Material>> = materialDao.getAllMaterials()
    
    suspend fun getMaterialById(id: Long): Material? = materialDao.getMaterialById(id)
    
    suspend fun saveMaterial(material: Material): Long = materialDao.insertMaterial(material)
    
    suspend fun saveMaterials(materials: List<Material>) {
        materialDao.insertMaterials(materials)
    }
    
    suspend fun deleteMaterial(material: Material) {
        materialDao.deleteMaterial(material)
    }
    
    suspend fun deleteAllMaterials() {
        materialDao.deleteAllMaterials()
    }
    
    fun getMaterialCount(): LiveData<Int> = materialDao.getMaterialCount()

    fun getMaterialsByType(type: String): LiveData<List<Material>> = materialDao.getMaterialsByType(type)

    suspend fun getMaterialsByTypeSync(type: String): List<Material> {
        return materialDao.getMaterialsByType(type).value ?: emptyList()
    }

    // ========== 群组配置操作 ==========
    
    fun getAllGroupConfigs(): LiveData<List<GroupConfig>> = groupConfigDao.getAllGroupConfigs()
    
    suspend fun getGroupConfigById(id: Long): GroupConfig? = groupConfigDao.getGroupConfigById(id)
    
    suspend fun saveGroupConfig(config: GroupConfig): Long = groupConfigDao.insertGroupConfig(config)
    
    suspend fun updateGroupConfig(config: GroupConfig) {
        groupConfigDao.updateGroupConfig(config)
    }
    
    suspend fun deleteGroupConfig(config: GroupConfig) {
        groupConfigDao.deleteGroupConfig(config)
        groupConfigDao.deleteGroupChatsByConfigId(config.id)
    }
    
    // ========== 群聊操作 ==========
    
    fun getGroupChatsByConfigId(groupConfigId: Long): LiveData<List<GroupChat>> =
        groupConfigDao.getGroupChatsByConfigId(groupConfigId)
    
    suspend fun getGroupChatsByConfigIdSync(groupConfigId: Long): List<GroupChat> =
        groupConfigDao.getGroupChatsByConfigIdSync(groupConfigId)
    
    suspend fun saveGroupChats(chats: List<GroupChat>) {
        groupConfigDao.insertGroupChats(chats)
    }
    
    suspend fun deleteGroupChatsByConfigId(groupConfigId: Long) {
        groupConfigDao.deleteGroupChatsByConfigId(groupConfigId)
    }
    
    // ========== 发送历史操作 ==========
    
    fun getRecentHistory(): LiveData<List<SendHistory>> = sendHistoryDao.getRecentHistory()
    
    suspend fun getHistoryById(id: Long): SendHistory? = sendHistoryDao.getHistoryById(id)
    
    suspend fun saveHistory(history: SendHistory): Long = sendHistoryDao.insertHistory(history)
    
    suspend fun updateHistory(history: SendHistory) {
        sendHistoryDao.updateHistory(history)
    }
    
    suspend fun deleteHistory(history: SendHistory) {
        sendHistoryDao.deleteHistory(history)
    }
    
    // ========== 应用设置操作 ==========

    fun getSettings(): LiveData<AppSettings?> = appSettingsDao.getSettings()

    suspend fun getSettingsSync(): AppSettings? = appSettingsDao.getSettingsSync()

    suspend fun saveSettings(settings: AppSettings) {
        appSettingsDao.insertSettings(settings)
    }

    suspend fun updateSettings(settings: AppSettings) {
        appSettingsDao.updateSettings(settings)
    }

    // ========== 消息组操作 ==========

    fun getAllMessageGroups(): LiveData<List<MessageGroup>> = messageGroupDao.getAllGroups()

    suspend fun getAllMessageGroupsSync(): List<MessageGroup> = messageGroupDao.getAllGroupsSync()

    suspend fun getMessageGroupById(id: Long): MessageGroup? = messageGroupDao.getGroupById(id)

    suspend fun saveMessageGroup(group: MessageGroup): Long = messageGroupDao.insertGroup(group)

    suspend fun updateMessageGroup(group: MessageGroup) {
        messageGroupDao.updateGroup(group)
    }

    suspend fun deleteMessageGroup(group: MessageGroup) {
        messageGroupDao.deleteGroup(group)
    }

    fun getMessageGroupCount(): LiveData<Int> = messageGroupDao.getGroupCount()
}

