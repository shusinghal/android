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
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN
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

                val idColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media._ID
                    )
                val dateColumn =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DATE_TAKEN
                    )

                val startIndex =
                    page * pageSize

                if (cursor.moveToPosition(startIndex)) {

                    do {

                        val id =
                            cursor.getLong(idColumn)
                        val dateTaken =
                            cursor.getLong(dateColumn)
                        val contentUri =
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                        photos.add(
                            MediaPhoto(
                                id = id,
                                contentUri = contentUri,
                                dateTaken
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