package com.memorycurator.app.data.media

import android.content.Context
import android.provider.MediaStore
import com.memorycurator.app.data.local.MediaDao
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

                MediaStore.Files.FileColumns.MEDIA_TYPE
            )

            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
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

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)
                    val mediaType = cursor.getInt(typeColumn)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                    val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                 else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                    val contentUri = "$baseUri/$id"

                    mediaList.add(

                        MediaEntity(

                            id = id,

                            uri = contentUri,

                            bucketId = cursor.getString(bucketIdColumn),

                            folderName = cursor.getString(folderColumn),

                            dateTaken = cursor.getLong(dateColumn),

                            mimeType = cursor.getString(mimeColumn),

                            width = cursor.getInt(widthColumn),

                            height = cursor.getInt(heightColumn),

                            size = cursor.getLong(sizeColumn)
                        )
                    )
                }
            }

            mediaDao.insertNewMedia(mediaList)
        }
    }
}