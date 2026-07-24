package com.memorycurator.app.data.media

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepositoryImpl(
    private val context: Context
) : MediaRepository {

    private val mediaDao = DatabaseProvider.getDatabase(context).mediaDao()

    override fun getPagedPhotos(): Flow<PagingData<MediaPhoto>> {
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                initialLoadSize = 120,
                prefetchDistance = 30
            ),
            pagingSourceFactory = {
                MediaPagingSource(context)
            }
        ).flow
    }

    override fun getAllPhotos(): Flow<List<MediaPhoto>> {
        return mediaDao.getAllMedia().map { entities ->
            entities.map { it.toMediaPhoto() }
        }
    }

    override fun getArchivedPhotos(): Flow<List<MediaPhoto>> {
        return mediaDao.getArchivedMedia().map { entities ->
            entities.map { it.toMediaPhoto() }
        }
    }

    override suspend fun getMediaEntities(ids: List<Long>): List<MediaEntity> {
        return mediaDao.getMediaByIds(ids)
    }

    override suspend fun saveAiResults(entities: List<MediaEntity>) {
        mediaDao.insertAll(entities)
    }

    override suspend fun updateBestTakeStatus(id: Long, isBest: Boolean) {
        mediaDao.updateBestTakeStatus(id, isBest)
    }

    override suspend fun resetAiMetadata(ids: List<Long>) {
        mediaDao.resetAiMetadata(ids)
    }

    override suspend fun archiveMedia(ids: List<Long>) {
        mediaDao.archiveMedia(ids)
    }

    override suspend fun restoreMedia(ids: List<Long>) {
        mediaDao.restoreMedia(ids)
    }
}
