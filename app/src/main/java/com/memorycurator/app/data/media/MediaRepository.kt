package com.memorycurator.app.data.media

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    fun getPagedPhotos():
            Flow<PagingData<MediaPhoto>>
}