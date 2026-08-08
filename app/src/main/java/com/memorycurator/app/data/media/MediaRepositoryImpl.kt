package com.memorycurator.app.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.local.MediaEntity
import android.os.Environment
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        val entities = mediaDao.getMediaByIds(ids)
        entities.forEach { entity ->
            val newUri = copyFileToFolder(entity.uri, "Pictures/Archive")
            if (newUri != null) {
                try {
                    // Delete the old one
                    context.contentResolver.delete(Uri.parse(entity.uri), null, null)
                    
                    // Update local DB: delete old ID and insert/update new ID with metadata
                    val newId = android.content.ContentUris.parseId(newUri)
                    mediaDao.transferMetadata(entity.id, newId, newUri.toString(), "Archive", true)
                } catch (e: SecurityException) {
                    // If deletion fails (common on Android 11+ for files not owned by app),
                    // we still update the DB to the new one to complete the "move" in the UI.
                    // The old one will eventually be hidden by the indexer if trashed in UI.
                    val newId = android.content.ContentUris.parseId(newUri)
                    mediaDao.transferMetadata(entity.id, newId, newUri.toString(), "Archive", true)
                }
            }
        }
    }

    override suspend fun restoreMedia(ids: List<Long>) {
        val entities = mediaDao.getMediaByIds(ids)
        entities.forEach { entity ->
            val newUri = copyFileToFolder(entity.uri, "Pictures/MemoryCurator")
            if (newUri != null) {
                try {
                    // Restore: the app owns the archived file, so deletion should work
                    context.contentResolver.delete(Uri.parse(entity.uri), null, null)
                    
                    val newId = android.content.ContentUris.parseId(newUri)
                    mediaDao.transferMetadata(entity.id, newId, newUri.toString(), "MemoryCurator", false)
                } catch (e: SecurityException) {
                    val newId = android.content.ContentUris.parseId(newUri)
                    mediaDao.transferMetadata(entity.id, newId, newUri.toString(), "MemoryCurator", false)
                }
            }
        }
    }

    private fun copyFileToFolder(uriString: String, targetRelativePath: String): Uri? {
        val sourceUri = Uri.parse(uriString)
        val resolver = context.contentResolver
        
        // 1. Get original metadata
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
        
        var displayName: String? = null
        var mimeType: String? = null
        
        resolver.query(sourceUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
            }
        }
        
        if (displayName == null) return null
        
        // 2. Create new entry in MediaStore
        val isVideo = mimeType?.startsWith("video") == true
        val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI 
                      else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                      
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$targetRelativePath/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        
        val targetUri = resolver.insert(baseUri, values) ?: return null
        
        try {
            // 3. Copy bytes
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(targetUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            
            // 4. Publish the file
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(targetUri, values, null, null)
            
            // Trigger scan
            MediaScannerConnection.scanFile(context, arrayOf(targetUri.toString()), null, null)
            
            return targetUri
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(targetUri, null, null)
            return null
        }
    }

    private fun ensureFolderExists(relativePath: String) {
        try {
            val externalDir = Environment.getExternalStorageDirectory()
            val targetDir = File(externalDir, relativePath)
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (created) {
                    // Force the system to recognize the new physical folder immediately
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetDir.absolutePath),
                        null,
                        null
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
