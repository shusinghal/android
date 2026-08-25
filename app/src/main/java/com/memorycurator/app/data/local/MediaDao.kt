package com.memorycurator.app.data.local

import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewMedia(media: List<MediaEntity>)

    @Query("SELECT * FROM media WHERE isArchived = 0 ORDER BY dateTaken DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isArchived = 0 ORDER BY dateTaken DESC")
    fun getPagedMedia(): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media WHERE isBestTake = 1 AND isArchived = 0 ORDER BY dateTaken DESC")
    fun getPagedBestTakes(): PagingSource<Int, MediaEntity>

    @Query("""
        SELECT 
            folderName,
            uri AS thumbnailUri,
            COUNT(*) AS photoCount,
            MAX(dateModified) AS lastModified
        FROM media
        WHERE isArchived = 0
        GROUP BY folderName
        ORDER BY lastModified DESC
    """)
    fun getAlbums(): Flow<List<AlbumProjection>>

    @Query("SELECT * FROM media WHERE folderName = :folderName ORDER BY dateTaken DESC")
    fun getPhotosInAlbum(folderName: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE folderName = :folderName ORDER BY dateTaken DESC")
    suspend fun getPhotosInAlbumSync(folderName: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    fun getMediaWithLocation(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    suspend fun getMediaWithLocationSync(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE latitude IS NULL AND longitude IS NULL AND isArchived = 0")
    suspend fun getMediaMissingLocation(): List<MediaEntity>

    @Query("UPDATE media SET latitude = :lat, longitude = :lon, locationName = :name WHERE id = :id")
    suspend fun updateLocation(id: Long, lat: Double?, lon: Double?, name: String?)

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<Long>): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    fun getMediaByIdsFlow(ids: List<Long>): Flow<List<MediaEntity>>

    @Query("UPDATE media SET isBestTake = :isBest, aiScore = :score, rejectionReason = :reason, isManuallyModified = 1, dateModified = :dateModified WHERE id = :id")
    suspend fun updateMetadata(id: Long, isBest: Boolean, score: Float, reason: String?, dateModified: Long)

    @Query("UPDATE media SET isBestTake = :isBest, aiScore = :score, rejectionReason = :reason, clusterId = :clusterId, originalFolderName = :originalFolder, isManuallyModified = 1 WHERE id = :id")
    suspend fun updateFullMetadata(id: Long, isBest: Boolean, score: Float, reason: String?, clusterId: String?, originalFolder: String?)

    @Query("UPDATE media SET aiScore = -1, isBestTake = 0, rejectionReason = NULL, clusterId = NULL, isManuallyModified = 0 WHERE id IN (:ids)")
    suspend fun resetAiMetadata(ids: List<Long>)

    @Query("UPDATE media SET folderName = :folderName, bucketId = :bucketId WHERE id = :id")
    suspend fun updateFolder(id: Long, folderName: String?, bucketId: String?)

    @Query("UPDATE media SET uri = :newUri WHERE id = :id")
    suspend fun updateMediaUri(id: Long, newUri: String)

    @Delete
    suspend fun delete(entity: MediaEntity)

    @Transaction
    suspend fun transferMetadata(oldId: Long, newId: Long, newUri: String, newFolder: String, originalFolder: String?) {
        val oldEntity = getMediaById(oldId)
        if (oldEntity != null) {
            delete(oldEntity)
            insertAll(listOf(oldEntity.copy(
                id = newId, 
                uri = newUri, 
                folderName = newFolder, 
                originalFolderName = originalFolder
            )))
        }
    }

    @Query("SELECT * FROM media WHERE isArchived = 1 ORDER BY dateTaken DESC")
    fun getArchivedMedia(): Flow<List<MediaEntity>>

    @Query("UPDATE media SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateArchiveStatus(id: Long, isArchived: Boolean)

    @Query("UPDATE media SET isArchived = 1 WHERE id IN (:ids)")
    suspend fun archiveMedia(ids: List<Long>)

    @Query("UPDATE media SET isArchived = 0 WHERE id IN (:ids)")
    suspend fun restoreMedia(ids: List<Long>)

    @Query("SELECT * FROM media ORDER BY dateTaken DESC")
    suspend fun getAllMediaSync(): List<MediaEntity>

    @Query("SELECT MAX(dateModified) FROM media")
    suspend fun getLastModifiedTimestamp(): Long?

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media WHERE id NOT IN (:scannedIds)")
    suspend fun deleteMissingIds(scannedIds: Set<Long>)

    @Query("DELETE FROM media WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT id FROM media")
    suspend fun getAllIds(): List<Long>

    @Transaction
    suspend fun insertOrIgnorePreservingAI(media: List<MediaEntity>) {
        media.forEach { item ->
            val existing = getMediaById(item.id)
            if (existing == null) {
                insertNewMedia(listOf(item))
            } else {
                updateSystemMetadata(
                    item.id, item.uri, item.bucketId, item.folderName,
                    item.dateTaken, item.dateModified, item.mimeType,
                    item.width, item.height, item.size
                )
            }
        }
    }

    @Transaction
    suspend fun insertOrUpdateSingle(entity: MediaEntity) {
        val existing = getMediaById(entity.id)
        if (existing == null) {
            insertAll(listOf(entity))
        } else {
            updateSystemMetadata(
                entity.id, entity.uri, entity.bucketId, entity.folderName,
                entity.dateTaken, entity.dateModified, entity.mimeType,
                entity.width, entity.height, entity.size
            )
        }
    }

    @Query("""
        UPDATE media SET 
            uri = :uri, bucketId = :bucketId, folderName = :folderName, 
            dateTaken = :dateTaken, dateModified = :dateModified, 
            mimeType = :mimeType, width = :width, height = :height, size = :size 
        WHERE id = :id
    """)
    suspend fun updateSystemMetadata(
        id: Long, uri: String, bucketId: String?, folderName: String?,
        dateTaken: Long, dateModified: Long, mimeType: String?,
        width: Int, height: Int, size: Long
    )
}
