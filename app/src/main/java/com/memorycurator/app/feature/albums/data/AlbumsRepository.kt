package com.memorycurator.app.feature.albums.data

import com.memorycurator.app.data.local.MediaDao
import com.memorycurator.app.data.local.MediaEntity
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.toMediaPhoto
import com.memorycurator.app.feature.albums.model.Album
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class AlbumsRepository(
    private val mediaDao: MediaDao
) {

    fun getAlbums(): Flow<List<Album>> {
        return mediaDao.getAlbums().map { albums ->
            albums.map {
                Album(
                    folderName = it.folderName,
                    thumbnailUri = it.thumbnailUri,
                    photoCount = it.photoCount
                )
            }
        }
    }

    fun getLocationAlbums(): Flow<List<Album>> {
        return mediaDao.getMediaWithLocation().map { mediaList ->
            mediaList.groupBy { 
                // Group by rounded coordinates (~11km precision)
                val lat = ((it.latitude ?: 0.0) * 10).roundToInt() / 10.0
                val lon = ((it.longitude ?: 0.0) * 10).roundToInt() / 10.0
                "$lat, $lon"
            }.map { (location, photos) ->
                Album(
                    folderName = "Location $location", 
                    thumbnailUri = photos.first().uri,
                    photoCount = photos.size
                )
            }.sortedByDescending { it.photoCount }
        }
    }

    suspend fun getPhotosInAlbum(folderName: String): List<MediaPhoto> {
        return mediaDao.getPhotosInAlbumSync(folderName).map { it.toMediaPhoto() }
    }

    suspend fun getPhotosAtLocation(lat: Double, lon: Double): List<MediaPhoto> {
        return mediaDao.getMediaWithLocationSync().filter {
            val photoLat = ((it.latitude ?: 0.0) * 10).roundToInt() / 10.0
            val photoLon = ((it.longitude ?: 0.0) * 10).roundToInt() / 10.0
            photoLat == lat && photoLon == lon
        }.map { it.toMediaPhoto() }
    }
}
