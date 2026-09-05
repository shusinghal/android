package com.memorycurator.app.data.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState

class MediaPagingSource(
    private val context: Context
) : PagingSource<Int, MediaPhoto>() {

    override suspend fun load(
        params: LoadParams<Int>
    ): LoadResult<Int, MediaPhoto> {

        return try {

            val page = params.key ?: 0

            val pageSize = params.loadSize

            val photos = mutableListOf<MediaPhoto>()

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
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

                val idColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns._ID
                    )
                val dateColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns.DATE_TAKEN
                    )
                val modifiedColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns.DATE_MODIFIED
                    )
                val typeColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns.MEDIA_TYPE
                    )

                val startIndex =
                    page * pageSize

                if (cursor.moveToPosition(startIndex)) {

                    do {

                        val id =
                            cursor.getLong(idColumn)
                        val dateTaken =
                            cursor.getLong(dateColumn)
                        val dateModified =
                            cursor.getLong(modifiedColumn)
                        val mediaType =
                            cursor.getInt(typeColumn)
                        
                        val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        
                        val contentUri =
                            ContentUris.withAppendedId(
                                if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                        photos.add(
                            MediaPhoto(
                                id = id,
                                contentUri = contentUri,
                                dateTaken = dateTaken,
                                dateModified = dateModified,
                                isVideo = isVideo,
                                aiScore = -1f,
                                isBestTake = false,
                                rejectionReason = null
                            )
                        )

                    } while (
                        cursor.moveToNext() &&
                        photos.size < pageSize
                    )
                }
            }

            LoadResult.Page(

                data = photos,

                prevKey =
                    if (page == 0) null
                    else page - 1,

                nextKey =
                    if (photos.isEmpty()) null
                    else page + 1
            )

        } catch (e: Exception) {

            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(
        state: PagingState<Int, MediaPhoto>
    ): Int? {

        return state.anchorPosition
    }
}