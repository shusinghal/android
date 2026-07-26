package com.memorycurator.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewMedia(media: List<MediaEntity>)

    @Query("SELECT * FROM media WHERE isArchived = 0 ORDER BY dateTaken DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isArchived = 1 ORDER BY dateTaken DESC")
    fun getArchivedMedia(): Flow<List<MediaEntity>>

    @Query("""
        SELECT 
            folderName,
            uri AS thumbnailUri,
            COUNT(*) AS photoCount
        FROM media
        WHERE isArchived = 0
        GROUP BY folderName
        ORDER BY photoCount DESC
    """)
    fun getAlbums(): Flow<List<AlbumProjection>>

    @Query("SELECT * FROM media WHERE isArchived = 0 AND folderName = :folderName ORDER BY dateTaken DESC")
    fun getPhotosInAlbum(folderName: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isArchived = 0 AND folderName = :folderName ORDER BY dateTaken DESC")
    suspend fun getPhotosInAlbumSync(folderName: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE isArchived = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL")
    fun getMediaWithLocation(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isArchived = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL")
    suspend fun getMediaWithLocationSync(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<Long>): List<MediaEntity>

    @Query("UPDATE media SET isBestTake = :isBest, isManuallyModified = 1 WHERE id = :id")
    suspend fun updateBestTakeStatus(id: Long, isBest: Boolean)

    @Query("UPDATE media SET aiScore = -1, isBestTake = 0, rejectionReason = NULL, clusterId = NULL, isManuallyModified = 0 WHERE id IN (:ids)")
    suspend fun resetAiMetadata(ids: List<Long>)

    @Query("UPDATE media SET isArchived = 1 WHERE id IN (:ids)")
    suspend fun archiveMedia(ids: List<Long>)

    @Query("UPDATE media SET isArchived = 0 WHERE id IN (:ids)")
    suspend fun restoreMedia(ids: List<Long>)
}
