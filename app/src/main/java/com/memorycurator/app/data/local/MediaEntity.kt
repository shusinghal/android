package com.memorycurator.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(

    @PrimaryKey
    val id: Long,

    val uri: String,

    val bucketId: String?,

    val folderName: String?,

    val dateTaken: Long,

    val mimeType: String?,

    val width: Int,

    val height: Int,

    val size: Long,

    val latitude: Double? = null,
    val longitude: Double? = null,

    // AI Metadata
    val aiScore: Float = -1f,
    val isBestTake: Boolean = false,
    val rejectionReason: String? = null,
    val clusterId: String? = null,
    val isManuallyModified: Boolean = false,
    val isArchived: Boolean = false
)
