package com.memorycurator.app.data.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File


class MediaRepositoryImpl(
    private val context: Context
) : MediaRepository {

    private val mediaDao = DatabaseProvider.getDatabase(context).mediaDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun getPagedPhotos(bestTakesOnly: Boolean): Flow<PagingData<MediaPhoto>> {
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                initialLoadSize = 120,
                prefetchDistance = 30
            ),
            pagingSourceFactory = {
                if (bestTakesOnly) mediaDao.getPagedBestTakes() else mediaDao.getPagedMedia()
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toMediaPhoto() }
        }
    }

    override fun getAllPhotos(): Flow<List<MediaPhoto>> {
        return mediaDao.getAllMedia().map { entities ->
            entities.map { it.toMediaPhoto() }
        }
    }

    override suspend fun getMediaEntities(ids: List<Long>): List<MediaEntity> {
        return mediaDao.getMediaByIds(ids)
    }

    override suspend fun saveAiResults(entities: List<MediaEntity>) {
        mediaDao.insertAll(entities)
        scope.launch {
            entities.forEach { entity ->
                if (entity.mimeType?.startsWith("video") != true) {
                    ExifMetadataManager.writeExifMetadata(
                        context,
                        Uri.parse(entity.uri),
                        CurationExifData(
                            aiScore = entity.aiScore,
                            isBestTake = entity.isBestTake,
                            rejectionReason = entity.rejectionReason,
                            clusterId = entity.clusterId,
                            originalFolder = entity.originalFolderName ?: entity.folderName
                        )
                    )
                }
            }
        }
    }

    override suspend fun updateBestTakeStatus(id: Long, isBest: Boolean) {
        val entity = mediaDao.getMediaById(id) ?: return
        
        // If not analyzed, set to 1.0 (Keep) and add a reason
        val newScore = if (entity.aiScore == -1f) 1.0f else entity.aiScore
        val newReason = if (!isBest || entity.aiScore == -1f) {
            com.memorycurator.app.core.ai.RejectionReason.MANUAL.name
        } else {
            entity.rejectionReason
        }

        // 1. Update Database with new score and reason
        mediaDao.updateMetadata(id, isBest, newScore, newReason)
        
        scope.launch {
            if (entity.mimeType?.startsWith("video") != true) {
                // 2. Write to physical file metadata
                ExifMetadataManager.writeExifMetadata(
                    context,
                    Uri.parse(entity.uri),
                    CurationExifData(
                        aiScore = newScore,
                        isBestTake = isBest,
                        rejectionReason = newReason,
                        clusterId = entity.clusterId,
                        originalFolder = entity.originalFolderName ?: entity.folderName
                    )
                )
            }
        }
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

    override fun getArchivedPhotos(): Flow<List<MediaPhoto>> {
        return mediaDao.getArchivedMedia().map { entities ->
            entities.map { it.toMediaPhoto() }
        }
    }

    override suspend fun deleteMediaFromDb(ids: List<Long>) {
        mediaDao.deleteByIds(ids)
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
