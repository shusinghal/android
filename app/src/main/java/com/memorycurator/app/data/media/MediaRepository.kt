package com.memorycurator.app.data.media

import androidx.paging.PagingData
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    fun getPagedPhotos(): Flow<PagingData<MediaPhoto>>
    
    suspend fun getMediaEntities(ids: List<Long>): List<MediaEntity>
    
    suspend fun saveAiResults(entities: List<MediaEntity>)
    
    suspend fun updateBestTakeStatus(id: Long, isBest: Boolean)
}
