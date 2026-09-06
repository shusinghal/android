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
import com.memorycurator.app.data.local.UserPreferences
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

    override fun getMediaEntitiesFlow(ids: List<Long>): Flow<List<MediaEntity>> {
        return mediaDao.getMediaByIdsFlow(ids)
    }

    override suspend fun saveAiResults(entities: List<MediaEntity>): List<Throwable> {
        mediaDao.insertAll(entities)
        
        val errors = mutableListOf<Throwable>()
        val userPrefs = UserPreferences(context)
        
        if (userPrefs.isPhysicalStorageEnabled) {
            entities.forEach { entity ->
                if (entity.mimeType?.startsWith("video") != true) {
                    val error = ExifMetadataManager.writeExifMetadata(
                        context,
                        Uri.parse(entity.uri),
                        CurationExifData(
                            aiScore = entity.aiScore,
                            isBestTake = entity.isBestTake,
                            rejectionReason = entity.rejectionReason,
                            clusterId = entity.clusterId,
                            originalFolder = entity.originalFolderName ?: entity.folderName,
                            aiDescription = entity.aiDescription,
                            mainFaceCount = entity.mainFaceCount
                        )
                    )
                    if (error != null) errors.add(error)
                }
            }
        }
        return errors
    }

    override suspend fun updateBestTakeStatus(id: Long, isBest: Boolean): Throwable? {
        val entity = mediaDao.getMediaById(id) ?: return null
        
        // If not analyzed, set to 1.0 (Keep) and add a reason
        val newScore = if (entity.aiScore == -1f) 1.0f else entity.aiScore
        val newReason = if (!isBest || entity.aiScore == -1f) {
            com.memorycurator.app.core.ai.RejectionReason.MANUAL.name
        } else {
            entity.rejectionReason
        }

        // 1. Update Database with new score and reason, and update timestamp for recency
        mediaDao.updateMetadata(id, isBest, newScore, newReason, System.currentTimeMillis())
        
        val userPrefs = UserPreferences(context)
        if (userPrefs.isPhysicalStorageEnabled && entity.mimeType?.startsWith("video") != true) {
            // 2. Write to physical file metadata
            return ExifMetadataManager.writeExifMetadata(
                context,
                Uri.parse(entity.uri),
                CurationExifData(
                    aiScore = newScore,
                    isBestTake = isBest,
                    rejectionReason = newReason,
                    clusterId = entity.clusterId,
                    originalFolder = entity.originalFolderName ?: entity.folderName,
                    aiDescription = entity.aiDescription,
                    mainFaceCount = entity.mainFaceCount
                )
            )
        }
        return null
    }

    override suspend fun syncToExif(ids: List<Long>): MediaRepository.SyncResult {
        val entities = mediaDao.getMediaByIds(ids).filter { it.aiScore != -1f }
        val exceptions = mutableListOf<Throwable>()
        val failedUris = mutableListOf<Uri>()
        var successCount = 0
        
        entities.forEach { entity ->
            if (entity.mimeType?.startsWith("video") != true) {
                val uri = Uri.parse(entity.uri)
                val error = ExifMetadataManager.writeExifMetadata(
                    context,
                    uri,
                    CurationExifData(
                        aiScore = entity.aiScore,
                        isBestTake = entity.isBestTake,
                        rejectionReason = entity.rejectionReason,
                        clusterId = entity.clusterId,
                        originalFolder = entity.originalFolderName ?: entity.folderName,
                        aiDescription = entity.aiDescription,
                        mainFaceCount = entity.mainFaceCount
                    )
                )
                if (error != null) {
                    exceptions.add(error)
                    failedUris.add(uri)
                } else {
                    successCount++
                }
            }
        }
        return MediaRepository.SyncResult(successCount, failedUris, exceptions)
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
