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

    @Query("SELECT * FROM media WHERE isArchived = 0 AND isBestTake = 1 ORDER BY dateTaken DESC")
    fun getPagedBestTakes(): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media WHERE isArchived = 1 ORDER BY dateTaken DESC")
    fun getArchivedMedia(): Flow<List<MediaEntity>>

    @Query("""
        SELECT 
            folderName,
            uri AS thumbnailUri,
            COUNT(*) AS photoCount
        FROM media
        GROUP BY folderName
        ORDER BY photoCount DESC
    """)
    fun getAlbums(): Flow<List<AlbumProjection>>

    @Query("SELECT * FROM media WHERE folderName = :folderName ORDER BY dateTaken DESC")
    fun getPhotosInAlbum(folderName: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE folderName = :folderName ORDER BY dateTaken DESC")
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

    @Query("UPDATE media SET folderName = :folderName, bucketId = :bucketId, isArchived = :isArchived WHERE id = :id")
    suspend fun updateArchiveStatus(id: Long, folderName: String?, bucketId: String?, isArchived: Boolean)

    @Query("UPDATE media SET folderName = :folderName, bucketId = :bucketId WHERE id = :id")
    suspend fun updateFolder(id: Long, folderName: String?, bucketId: String?)

    @Query("UPDATE media SET isArchived = 1, folderName = 'Archive' WHERE id IN (:ids)")
    suspend fun archiveMedia(ids: List<Long>)

    @Query("UPDATE media SET isArchived = 0, folderName = 'MemoryCurator' WHERE id IN (:ids)")
    suspend fun restoreMedia(ids: List<Long>)

    @Query("UPDATE media SET uri = :newUri WHERE id = :id")
    suspend fun updateMediaUri(id: Long, newUri: String)

    @Delete
    suspend fun delete(entity: MediaEntity)

    @Transaction
    suspend fun transferMetadata(oldId: Long, newId: Long, newUri: String, newFolder: String, isArchived: Boolean) {
        val oldEntity = getMediaById(oldId)
        if (oldEntity != null) {
            delete(oldEntity)
            insertAll(listOf(oldEntity.copy(id = newId, uri = newUri, folderName = newFolder, isArchived = isArchived)))
        }
    }

    @Query("SELECT * FROM media")
    suspend fun getAllMediaSync(): List<MediaEntity>

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
