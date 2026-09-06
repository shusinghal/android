package com.memorycurator.app.data.media

import android.net.Uri
import androidx.paging.PagingData
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    fun getPagedPhotos(bestTakesOnly: Boolean = false): Flow<PagingData<MediaPhoto>>
    
    fun getAllPhotos(): Flow<List<MediaPhoto>>
    
    suspend fun getMediaEntities(ids: List<Long>): List<MediaEntity>

    fun getMediaEntitiesFlow(ids: List<Long>): Flow<List<MediaEntity>>
    
    suspend fun saveAiResults(entities: List<MediaEntity>): List<Throwable>
    
    suspend fun updateBestTakeStatus(id: Long, isBest: Boolean): Throwable?
    
    data class SyncResult(val successCount: Int, val failedUris: List<Uri>, val securityExceptions: List<Throwable>)
    suspend fun syncToExif(ids: List<Long>): SyncResult
    
    suspend fun resetAiMetadata(ids: List<Long>)

    suspend fun archiveMedia(ids: List<Long>)
    
    suspend fun restoreMedia(ids: List<Long>)

    fun getArchivedPhotos(): Flow<List<MediaPhoto>>

    suspend fun deleteMediaFromDb(ids: List<Long>)
}
