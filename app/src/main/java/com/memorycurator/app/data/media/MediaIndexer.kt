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

                MediaStore.Images.Media._ID,

                MediaStore.Images.Media.BUCKET_ID,

                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,

                MediaStore.Images.Media.DATE_TAKEN,

                MediaStore.Images.Media.MIME_TYPE,

                MediaStore.Images.Media.WIDTH,

                MediaStore.Images.Media.HEIGHT,

                MediaStore.Images.Media.SIZE
            )

            val sortOrder =
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            context.contentResolver.query(

                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,

                projection,

                null,

                null,

                sortOrder

            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media._ID
                )

                val bucketIdColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.BUCKET_ID
                )

                val folderColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )

                val dateColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DATE_TAKEN
                )

                val mimeColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.MIME_TYPE
                )

                val widthColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.WIDTH
                )

                val heightColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.HEIGHT
                )

                val sizeColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.SIZE
                )

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)

                    val contentUri =
                        "${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/$id"

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

            mediaDao.insertAll(mediaList)
        }
    }
}