package com.example.data

import kotlinx.coroutines.flow.Flow

class GeoStampRepository(private val db: AppDatabase) {
    private val photoDao = db.photoDao()
    private val templateDao = db.templateDao()
    private val locationDao = db.locationDao()

    val allPhotos: Flow<List<SavedPhoto>> = photoDao.getAllPhotos()
    val allTemplates: Flow<List<StampTemplate>> = templateDao.getAllTemplates()
    val allFavoriteLocations: Flow<List<FavoriteLocation>> = locationDao.getAllFavoriteLocations()

    suspend fun getPhotoById(id: Int): SavedPhoto? = photoDao.getPhotoById(id)

    suspend fun insertPhoto(photo: SavedPhoto): Long = photoDao.insertPhoto(photo)

    suspend fun deletePhoto(photo: SavedPhoto) = photoDao.deletePhoto(photo)
    suspend fun deletePhotoById(id: Int) = photoDao.deletePhotoById(id)

    suspend fun getTemplateById(id: Int): StampTemplate? = templateDao.getTemplateById(id)

    suspend fun insertTemplate(template: StampTemplate): Long = templateDao.insertTemplate(template)

    suspend fun deleteTemplate(template: StampTemplate) = templateDao.deleteTemplate(template)
    suspend fun deleteTemplateById(id: Int) = templateDao.deleteTemplateById(id)

    suspend fun insertFavoriteLocation(location: FavoriteLocation): Long = locationDao.insertFavoriteLocation(location)

    suspend fun deleteFavoriteLocation(location: FavoriteLocation) = locationDao.deleteFavoriteLocation(location)
}
