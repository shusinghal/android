package com.memorycurator.app.feature.albums.data

import com.memorycurator.app.data.local.MediaDao
import com.memorycurator.app.feature.albums.model.Album
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlbumsRepository(
    private val mediaDao: MediaDao
) {

    fun getAlbums(): Flow<List<Album>> {

        return mediaDao
            .getAlbums()
            .map { albums ->

                albums.map {

                    Album(

                        folderName =
                            it.folderName,

                        thumbnailUri =
                            it.thumbnailUri,

                        photoCount =
                            it.photoCount
                    )
                }
            }
    }
}