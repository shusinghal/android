package com.memorycurator.app.data.media

import androidx.paging.PagingData
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    fun getPagedPhotos(bestTakesOnly: Boolean = false): Flow<PagingData<MediaPhoto>>
    
    fun getAllPhotos(): Flow<List<MediaPhoto>>
    
    fun getArchivedPhotos(): Flow<List<MediaPhoto>>
    
    suspend fun getMediaEntities(ids: List<Long>): List<MediaEntity>
    
    suspend fun saveAiResults(entities: List<MediaEntity>)
    
    suspend fun updateBestTakeStatus(id: Long, isBest: Boolean)
    
    suspend fun resetAiMetadata(ids: List<Long>)

    suspend fun archiveMedia(ids: List<Long>)

    suspend fun restoreMedia(ids: List<Long>)
}
