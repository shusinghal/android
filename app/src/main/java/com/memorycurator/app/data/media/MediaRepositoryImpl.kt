package com.memorycurator.app.data.media

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class MediaRepositoryImpl(
    private val context: Context
) : MediaRepository {

    override fun getPagedPhotos():
            Flow<PagingData<MediaPhoto>> {

        return Pager(

            config = PagingConfig(

                pageSize = 60,

                initialLoadSize = 120,

                prefetchDistance = 30
            ),

            pagingSourceFactory = {

                MediaPagingSource(context)
            }

        ).flow
    }
}