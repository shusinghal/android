package com.memorycurator.app.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.memorycurator.app.data.local.MediaDao
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class MediaIndexer(
    private val context: Context,
    private val mediaDao: MediaDao
) {

    suspend fun indexMedia() = withContext(Dispatchers.IO) {
        val scannedIds = HashSet<Long>()
        val batchList = ArrayList<MediaEntity>(CHUNK_SIZE)

        // Optimized projection without problematic raw lat/lon columns
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val queryUri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            while (cursor.moveToNext()) {
                ensureActive() // Cooperatively cancel if coroutine is stopped

                val id = cursor.getLong(idCol)
                scannedIds.add(id)

                val mediaType = cursor.getInt(typeCol)
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI 
                             else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val contentUri = ContentUris.withAppendedId(baseUri, id).toString()

                batchList.add(
                    MediaEntity(
                        id = id,
                        uri = contentUri,
                        bucketId = cursor.getString(bucketIdCol) ?: "",
                        folderName = cursor.getString(folderCol) ?: "",
                        dateTaken = cursor.getLong(dateCol),
                        dateModified = cursor.getLong(modCol),
                        //mimeType = cursor.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        size = cursor.getLong(sizeCol),
                        latitude = null,
                        longitude = null,
                        aiScore = -1f,
                        isBestTake = false,
                        rejectionReason = null,
                        clusterId = null,
                        isManuallyModified = false,
                        isArchived = false
                    )
                )

                // Batch insert every 500 items to keep RAM usage minimal
                if (batchList.size >= CHUNK_SIZE) {
                    mediaDao.insertOrIgnorePreservingAI(batchList)
                    batchList.clear()
                }
            }

            // Flush remaining items
            if (batchList.isNotEmpty()) {
                mediaDao.insertOrIgnorePreservingAI(batchList)
                batchList.clear()
            }
        }

        // Clean up deleted/trashed media
        if (scannedIds.isNotEmpty()) {
            mediaDao.deleteMissingIds(scannedIds)
        }
    }

    suspend fun indexUri(uri: Uri) = withContext(Dispatchers.IO) {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return@withContext

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val entity = MediaEntity(
                        id = id,
                        uri = uri.toString(),
                        bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)) ?: "",
                        folderName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)) ?: "",
                        dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)),
                        dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)),
                        mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)) ?: "",
                        width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)),
                        height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)),
                        size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                        latitude = null,
                        longitude = null
                    )
                    
                    mediaDao.insertOrUpdateSingle(entity)
                } else {
                    mediaDao.deleteById(id)
                }
            } ?: mediaDao.deleteById(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val CHUNK_SIZE = 500
    }
}
