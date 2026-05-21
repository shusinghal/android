package com.memorycurator.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun insertAll(
        media: List<MediaEntity>
    )

    @Query(
        """
        SELECT * FROM media
        ORDER BY dateTaken DESC
        """
    )
    fun getAllMedia():
            Flow<List<MediaEntity>>

    @Query(
        """
        SELECT 
            folderName,
            uri AS thumbnailUri,
            COUNT(*) AS photoCount
        FROM media
        GROUP BY folderName
        ORDER BY photoCount DESC
        """
    )
    fun getAlbums():
            Flow<List<AlbumProjection>>
}