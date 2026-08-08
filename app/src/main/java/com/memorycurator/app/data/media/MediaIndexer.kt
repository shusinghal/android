package com.memorycurator.app.data.media

import android.content.Context
import android.net.Uri
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

            val projection = arrayOf(

                MediaStore.Files.FileColumns._ID,

                MediaStore.Files.FileColumns.BUCKET_ID,

                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,

                MediaStore.Files.FileColumns.DATE_TAKEN,

                MediaStore.Files.FileColumns.MIME_TYPE,

                MediaStore.Files.FileColumns.WIDTH,

                MediaStore.Files.FileColumns.HEIGHT,

                MediaStore.Files.FileColumns.SIZE,

                MediaStore.Files.FileColumns.MEDIA_TYPE,
                
                MediaStore.Files.FileColumns.DATA
            )

            val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )

            val sortOrder =
                "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"

            context.contentResolver.query(

                MediaStore.Files.getContentUri("external"),

                projection,

                selection,

                selectionArgs,

                sortOrder

            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns._ID
                )

                val bucketIdColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.BUCKET_ID
                )

                val folderColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
                )

                val dateColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.DATE_TAKEN
                )

                val mimeColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.MIME_TYPE
                )

                val widthColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.WIDTH
                )

                val heightColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.HEIGHT
                )

                val sizeColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.SIZE
                )

                val typeColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.MEDIA_TYPE
                )

                // Note: latitude/longitude are often not available in MediaStore.Files
                // We'll try to get them, but handle their absence gracefully
                val latColumn = cursor.getColumnIndex("latitude")
                val lonColumn = cursor.getColumnIndex("longitude")

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)
                    val mediaType = cursor.getInt(typeColumn)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                    val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                 else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                    val contentUriString = "$baseUri/$id"
                    val contentUri = Uri.parse(contentUriString)

                    var latitude = if (latColumn != -1 && !cursor.isNull(latColumn)) cursor.getDouble(latColumn) else null
                    var longitude = if (lonColumn != -1 && !cursor.isNull(lonColumn)) cursor.getDouble(lonColumn) else null
                    
                    val bucketId = cursor.getString(bucketIdColumn)
                    val folderName = cursor.getString(folderColumn)

                    // If MediaStore has no location, try Exif
                    if (latitude == null || longitude == null) {
                        try {
                            context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                                val exif = ExifInterface(inputStream)
                                val latLong = exif.latLong
                                if (latLong != null) {
                                    latitude = latLong[0]
                                    longitude = latLong[1]
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val isArchived = folderName?.contains("Archive", ignoreCase = true) == true
                    
                    val entity = MediaEntity(
                        id = id,
                        uri = contentUriString,
                        bucketId = bucketId,
                        folderName = folderName,
                        dateTaken = cursor.getLong(dateColumn),
                        mimeType = cursor.getString(mimeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        size = cursor.getLong(sizeColumn),
                        latitude = latitude,
                        longitude = longitude,
                        isArchived = isArchived
                    )
                    
                    mediaList.add(entity)
                    
                    // Also check if we need to update the folder name and archive status for existing entries
                    mediaDao.updateArchiveStatus(id, folderName, bucketId, isArchived)
                }
            }

            mediaDao.insertNewMedia(mediaList)

            // Cleanup: remove items from DB that are no longer present on device
            val scannedIds = mediaList.map { it.id }.toSet()
            val allDbEntities = mediaDao.getAllMediaSync()
            val idsToDelete = allDbEntities.filter { it.id !in scannedIds }.map { it.id }
            if (idsToDelete.isNotEmpty()) {
                mediaDao.deleteByIds(idsToDelete)
            }
        }
    }
}