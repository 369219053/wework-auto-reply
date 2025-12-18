package com.wework.autoreply.database

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 素材库和素材的DAO接口
 */
@Dao
interface MaterialDao {
    
    // ========== 素材库操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialLibrary(library: MaterialLibrary)
    
    @Query("SELECT * FROM material_library WHERE id = 1")
    fun getMaterialLibrary(): LiveData<MaterialLibrary?>
    
    @Query("SELECT * FROM material_library WHERE id = 1")
    suspend fun getMaterialLibrarySync(): MaterialLibrary?
    
    @Update
    suspend fun updateMaterialLibrary(library: MaterialLibrary)
    
    // ========== 素材操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: Material): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<Material>)
    
    @Query("SELECT * FROM materials ORDER BY syncedAt DESC")
    fun getAllMaterials(): LiveData<List<Material>>
    
    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: Long): Material?
    
    @Query("SELECT * FROM materials WHERE type = :type ORDER BY syncedAt DESC")
    fun getMaterialsByType(type: String): LiveData<List<Material>>
    
    @Delete
    suspend fun deleteMaterial(material: Material)
    
    @Query("DELETE FROM materials")
    suspend fun deleteAllMaterials()
    
    @Query("SELECT COUNT(*) FROM materials")
    fun getMaterialCount(): LiveData<Int>
}

