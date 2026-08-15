package com.memorycurator.app.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.memorycurator.app.data.local.MediaDao
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class MediaIndexer(
    private val context: Context,
    private val mediaDao: MediaDao
) {

    suspend fun indexMedia() {
        withContext(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaEntity>()
            val projection = mutableListOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.BUCKET_ID,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                "latitude",
                "longitude"
            )

            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection.toTypedArray(),
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
                val latCol = cursor.getColumnIndex("latitude")
                val lonCol = cursor.getColumnIndex("longitude")

                val allDbEntities = mediaDao.getAllMediaSync().associateBy { it.id }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val mediaType = cursor.getInt(typeCol)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI 
                                 else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val contentUriString = "$baseUri/$id"
                    
                    val folderName = cursor.getString(folderCol)
                    val isArchived = folderName?.contains("Archive", ignoreCase = true) == true

                    val existing = allDbEntities[id]
                    var aiScore = existing?.aiScore ?: -1f
                    var isBestTake = existing?.isBestTake ?: false
                    var rejectionReason = existing?.rejectionReason
                    var clusterId = existing?.clusterId
                    val isManuallyModified = existing?.isManuallyModified ?: false

                    if (aiScore == -1f && !isVideo) {
                        ExifMetadataManager.readExifMetadata(context, Uri.parse(contentUriString))?.let { exif ->
                            aiScore = exif.aiScore
                            isBestTake = exif.isBestTake
                            rejectionReason = exif.rejectionReason
                            clusterId = exif.clusterId
                        }
                    }

                    mediaList.add(MediaEntity(
                        id = id,
                        uri = contentUriString,
                        bucketId = cursor.getString(bucketIdCol),
                        folderName = folderName,
                        dateTaken = cursor.getLong(dateCol),
                        dateModified = cursor.getLong(modCol),
                        mimeType = cursor.getString(mimeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        size = cursor.getLong(sizeCol),
                        latitude = if (latCol != -1) cursor.getDouble(latCol).takeIf { it != 0.0 } else null,
                        longitude = if (lonCol != -1) cursor.getDouble(lonCol).takeIf { it != 0.0 } else null,
                        isArchived = isArchived,
                        aiScore = aiScore,
                        isBestTake = isBestTake,
                        rejectionReason = rejectionReason,
                        clusterId = clusterId,
                        isManuallyModified = isManuallyModified
                    ))
                }
            }

            mediaDao.insertAll(mediaList)
            
            // Clean up missing files
            val scannedIds = mediaList.map { it.id }.toSet()
            val allDbEntitiesAfterScan = mediaDao.getAllMediaSync()
            val idsToDelete = allDbEntitiesAfterScan.filter { it.id !in scannedIds }.map { it.id }
            if (idsToDelete.isNotEmpty()) {
                mediaDao.deleteByIds(idsToDelete)
            }
        }
    }

    suspend fun indexUri(uri: Uri) {
        // Ensure Uri is a valid item Uri (e.g. content://media/external/images/media/123), not a broad table Uri
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return

        withContext(Dispatchers.IO) {
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
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                "latitude",
                "longitude"
            )

            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val folderName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME))
                        val isArchived = folderName?.contains("Archive", ignoreCase = true) == true
                        val mediaType = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                        val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val latCol = cursor.getColumnIndex("latitude")
                        val lonCol = cursor.getColumnIndex("longitude")

                        val entity = MediaEntity(
                            id = id,
                            uri = uri.toString(),
                            bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)),
                            folderName = folderName,
                            dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)),
                            dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)),
                            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)),
                            width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)),
                            height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)),
                            size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                            latitude = if (latCol != -1) cursor.getDouble(latCol).takeIf { it != 0.0 } else null,
                            longitude = if (lonCol != -1) cursor.getDouble(lonCol).takeIf { it != 0.0 } else null,
                            isArchived = isArchived
                        )
                        
                        // Preserve AI results when updating metadata
                        val existing = mediaDao.getMediaById(id)
                        var aiScore = existing?.aiScore ?: -1f
                        var isBestTake = existing?.isBestTake ?: false
                        var rejectionReason = existing?.rejectionReason
                        var clusterId = existing?.clusterId
                        val isManuallyModified = existing?.isManuallyModified ?: false

                        if (aiScore == -1f && !isVideo) {
                            ExifMetadataManager.readExifMetadata(context, uri)?.let { exif ->
                                aiScore = exif.aiScore
                                isBestTake = exif.isBestTake
                                rejectionReason = exif.rejectionReason
                                clusterId = exif.clusterId
                            }
                        }

                        mediaDao.insertAll(listOf(entity.copy(
                            aiScore = aiScore,
                            isBestTake = isBestTake,
                            rejectionReason = rejectionReason,
                            clusterId = clusterId,
                            isManuallyModified = isManuallyModified
                        )))
                    } else {
                        mediaDao.deleteByUri(uri.toString())
                    }
                } ?: mediaDao.deleteByUri(uri.toString())
            } catch (e: Exception) {
                // Ignore or log error gracefully
            }
        }
    }
}
