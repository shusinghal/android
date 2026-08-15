package com.memorycurator.app.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.local.MediaEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
        scope.launch {
            entities.forEach { entity ->
                if (entity.mimeType?.startsWith("video") != true) {
                    val path = getFilePathFromUri(Uri.parse(entity.uri)) ?: return@forEach
                    ExifMetadataManager.writeExifMetadata(
                        path,
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
        mediaDao.updateBestTakeStatus(id, isBest)
        scope.launch {
            mediaDao.getMediaById(id)?.let { entity ->
                if (entity.mimeType?.startsWith("video") != true) {
                    val path = getFilePathFromUri(Uri.parse(entity.uri)) ?: return@launch
                    ExifMetadataManager.writeExifMetadata(
                        path,
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

    override suspend fun resetAiMetadata(ids: List<Long>) {
        mediaDao.resetAiMetadata(ids)
    }

    override suspend fun archiveMedia(ids: List<Long>) {
        val entities = mediaDao.getMediaByIds(ids)
        entities.forEach { entity ->
            val sourcePath = getFilePathFromUri(Uri.parse(entity.uri)) ?: return@forEach
            
            // 1. Update EXIF safely using File path before moving
            if (entity.mimeType?.startsWith("video") != true) {
                ExifMetadataManager.writeExifMetadata(
                    sourcePath,
                    CurationExifData(
                        aiScore = entity.aiScore,
                        isBestTake = entity.isBestTake,
                        rejectionReason = entity.rejectionReason,
                        clusterId = entity.clusterId,
                        originalFolder = entity.folderName
                    )
                )
            }

            // 2. Physical move
            val newUri = moveFilePhysically(entity.uri, "Pictures/Archive")
            if (newUri != null) {
                val newId = ContentUris.parseId(newUri)
                mediaDao.transferMetadata(entity.id, newId, newUri.toString(), "Archive", true, entity.folderName)
            }
        }
    }

    override suspend fun restoreMedia(ids: List<Long>) {
        val entities = mediaDao.getMediaByIds(ids)
        entities.forEach { entity ->
            val path = getFilePathFromUri(Uri.parse(entity.uri)) ?: return@forEach
            
            // 1. Read target folder from EXIF (Stateless)
            val exif = ExifMetadataManager.readExifMetadata(path)
            val targetFolder = exif?.originalFolder ?: entity.originalFolderName ?: "MemoryCurator"
            val targetPath = "Pictures/$targetFolder"
            
            // 2. Physical move
            val newUri = moveFilePhysically(entity.uri, targetPath)
            if (newUri != null) {
                val newId = ContentUris.parseId(newUri)
                mediaDao.transferMetadata(entity.id, newId, newUri.toString(), targetFolder, false, null)
            }
        }
    }

    private suspend fun moveFilePhysically(uriString: String, targetRelativePath: String): Uri? {
        val sourceUri = Uri.parse(uriString)
        val sourcePath = getFilePathFromUri(sourceUri) ?: return null
        val sourceFile = File(sourcePath)
        
        if (!sourceFile.exists()) return null
        
        val externalDir = Environment.getExternalStorageDirectory()
        val targetDir = File(externalDir, targetRelativePath)
        if (!targetDir.exists()) targetDir.mkdirs()
        
        val targetFile = File(targetDir, sourceFile.name)
        
        // Attempt atomic rename first
        val movedSuccessfully = sourceFile.renameTo(targetFile) || try {
            sourceFile.copyTo(targetFile, overwrite = true)
            sourceFile.delete()
            true
        } catch (e: Exception) {
            false
        }

        return if (movedSuccessfully) {
            // Remove old entry from MediaStore
            try { context.contentResolver.delete(sourceUri, null, null) } catch (e: Exception) {}
            
            // Scan new file to add to MediaStore and wait for result
            suspendCancellableCoroutine<Uri?> { continuation ->
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null) { _, uri ->
                    continuation.resume(uri)
                }
            }
        } else {
            null
        }
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
