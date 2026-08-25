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
                    photoCount = it.photoCount,
                    lastModified = it.lastModified
                )
            }
        }
    }

    fun getLocationAlbums(): Flow<List<Album>> {
        return mediaDao.getMediaWithLocation().map { mediaList ->
            mediaList
                .filter { (it.latitude ?: 0.0) != 0.0 && (it.longitude ?: 0.0) != 0.0 }
                .groupBy { 
                    it.locationName ?: run {
                        // Fallback to rounded coordinates (~11km precision)
                        val lat = ((it.latitude ?: 0.0) * 10).roundToInt() / 10.0
                        val lon = ((it.longitude ?: 0.0) * 10).roundToInt() / 10.0
                        "$lat, $lon"
                    }
                }.map { (location, photos) ->
                    Album(
                        folderName = if (location.contains(",")) "Location $location" else location, 
                        thumbnailUri = photos.first().uri,
                        photoCount = photos.size,
                        lastModified = photos.maxOfOrNull { it.dateModified } ?: 0L
                    )
                }.sortedByDescending { it.lastModified }
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

    suspend fun getPhotosByName(name: String): List<MediaPhoto> {
        return mediaDao.getMediaWithLocationSync().filter {
            it.locationName == name
        }.map { it.toMediaPhoto() }
    }
}
