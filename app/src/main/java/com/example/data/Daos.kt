package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM saved_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<SavedPhoto>>

    @Query("SELECT * FROM saved_photos WHERE id = :id")
    suspend fun getPhotoById(id: Int): SavedPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: SavedPhoto): Long

    @Delete
    suspend fun deletePhoto(photo: SavedPhoto)

    @Query("DELETE FROM saved_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Int)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM stamp_templates ORDER BY id ASC")
    fun getAllTemplates(): Flow<List<StampTemplate>>

    @Query("SELECT * FROM stamp_templates WHERE id = :id")
    suspend fun getTemplateById(id: Int): StampTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: StampTemplate): Long

    @Delete
    suspend fun deleteTemplate(template: StampTemplate)

    @Query("DELETE FROM stamp_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Int)
    
    @Transaction
    suspend fun insertDefaultTemplatesIfEmpty() {
        // We will call this during DB initialization
    }
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM favorite_locations ORDER BY name ASC")
    fun getAllFavoriteLocations(): Flow<List<FavoriteLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteLocation(location: FavoriteLocation): Long

    @Delete
    suspend fun deleteFavoriteLocation(location: FavoriteLocation)
}
