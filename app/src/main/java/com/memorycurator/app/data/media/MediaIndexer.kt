package com.memorycurator.app.data.media

import android.content.ContentUris
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.memorycurator.app.data.local.MediaDao
import com.memorycurator.app.data.local.MediaEntity
import kotlinx.coroutines.*
import java.util.Locale

class MediaIndexer(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    private val TAG = "MediaIndexer"

    suspend fun indexMedia(limit: Int? = null) = withContext(Dispatchers.IO) {
        val isFullScan = limit == null
        Log.d(TAG, "Starting media indexing (isFullScan=$isFullScan, limit=$limit)...")
        val scannedIds = HashSet<Long>()
        val batchList = ArrayList<MediaEntity>(CHUNK_SIZE)
        
        // Optimized projection
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
        )

        val queryUri = MediaStore.Files.getContentUri("external")
        val sortOrder = "${MediaStore.Files.FileColumns._ID} DESC"

        context.contentResolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val addedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            var count = 0
            while (cursor.moveToNext()) {
                ensureActive()
                
                if (limit != null && count >= limit) break

                val id = cursor.getLong(idCol)
                scannedIds.add(id)

                val mediaType = cursor.getInt(typeCol)
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI 
                             else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val contentUri = ContentUris.withAppendedId(baseUri, id).toString()

                val entity = MediaEntity(
                    id = id,
                    uri = contentUri,
                    bucketId = cursor.getString(bucketIdCol) ?: "",
                    folderName = cursor.getString(folderCol) ?: "",
                    dateTaken = cursor.getLong(dateCol).let { if (it == 0L && addedCol != -1) cursor.getLong(addedCol) * 1000 else it },
                    dateModified = cursor.getLong(modCol),
                    mimeType = cursor.getString(mimeCol) ?: "image/*",
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    size = cursor.getLong(sizeCol),
                    latitude = null,
                    longitude = null,
                    aiScore = -1f,
                    isBestTake = false,
                    rejectionReason = null,
                    clusterId = null,
                    isManuallyModified = false,
                    isArchived = false
                )

                batchList.add(entity)
                count++

                if (batchList.size >= CHUNK_SIZE) {
                    if (isFullScan) mediaDao.insertOrIgnorePreservingAI(batchList)
                    else mediaDao.insertNewMedia(batchList)
                    batchList.clear()
                }
            }

            if (batchList.isNotEmpty()) {
                if (isFullScan) mediaDao.insertOrIgnorePreservingAI(batchList)
                else mediaDao.insertNewMedia(batchList)
                batchList.clear()
            }
            Log.d(TAG, "Fast indexing pass finished. Scanned ${scannedIds.size} items.")
        } ?: Log.e(TAG, "Cursor was null during indexing")

        if (isFullScan && scannedIds.isNotEmpty()) {
            mediaDao.deleteMissingIds(scannedIds)
        }

        // Pass 2: Restore AI results from EXIF in background
        if (isFullScan) {
            restoreAiMetadata()
        }
    }

    /**
     * Lazy background pass to read EXIF from files and restore AI state.
     */
    private suspend fun restoreAiMetadata() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting background AI metadata restoration pass...")
        // Get all photos that haven't been "restored" yet
        val allMedia = mediaDao.getAllMediaSync().filter { it.aiScore == -1f }
        var restoredCount = 0

        allMedia.chunked(20).forEach { batch ->
            ensureActive()
            batch.forEach { entity ->
                try {
                    // Important: Use context.contentResolver.openInputStream(Uri.parse(entity.uri))
                    // inside the helper method to handle Scoped Storage correctly.
                    val exifData = ExifMetadataManager.readExifMetadata(context, Uri.parse(entity.uri))
                    
                    if (exifData != null && exifData.aiScore != -1f) {
                        mediaDao.updateFullMetadata(
                            id = entity.id,
                            isBest = exifData.isBestTake,
                            score = exifData.aiScore,
                            reason = exifData.rejectionReason,
                            clusterId = exifData.clusterId,
                            originalFolder = exifData.originalFolder,
                            description = exifData.aiDescription,
                            faceCount = exifData.mainFaceCount
                        )
                        restoredCount++
                    }
                } catch (e: Exception) {
                    // Skip failures
                }
            }
            // Give time for UI and other background tasks
            kotlinx.coroutines.yield()
        }
        Log.d(TAG, "AI metadata restoration finished. Restored $restoredCount photos.")
    }

    /**
     * Safely enriches media with location metadata from EXIF.
     * Runs as a lazy background task in small batches to keep UI reactive.
     */
    suspend fun enrichLocationMetadata(onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("location_indexing_enabled", true)) return@withContext

        val missing = mediaDao.getMediaMissingLocation()
        if (missing.isEmpty()) {
            onProgress(0)
            return@withContext
        }

        val total = missing.size
        var processed = 0

        missing.chunked(BATCH_SIZE_LOCATION).forEach { batch ->
            ensureActive()
            batch.forEach { entity ->
                try {
                    val uri = Uri.parse(entity.uri)
                    val photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.setRequireOriginal(uri)
                    } else {
                        uri
                    }

                    context.contentResolver.openInputStream(photoUri)?.use { input ->
                        val exif = ExifInterface(input)
                        val latLong = exif.latLong
                        if (latLong != null) {
                            val name = getLocationName(latLong[0], latLong[1])
                            mediaDao.updateLocation(entity.id, latLong[0], latLong[1], name)
                        } else {
                            // Mark as processed with a sentinel to avoid re-scanning
                            mediaDao.updateLocation(entity.id, 0.0, 0.0, null)
                        }
                    }
                } catch (e: Exception) {
                    // Skip errors
                }
                processed++
            }
            onProgress(total - processed)
        }
        onProgress(0)
    }

    private fun getLocationName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.locality ?: address.subAdminArea ?: address.adminArea ?: address.countryName
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun indexUri(uri: Uri) = withContext(Dispatchers.IO) {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return@withContext

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                    val addedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                    
                    // Priority restore EXIF for just-indexed item
                    val exifData = ExifMetadataManager.readExifMetadata(context, uri)

                    val entity = MediaEntity(
                        id = id,
                        uri = uri.toString(),
                        bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)) ?: "",
                        folderName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)) ?: "",
                        dateTaken = cursor.getLong(dateCol).let { if (it == 0L && addedCol != -1) cursor.getLong(addedCol) * 1000 else it },
                        dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)),
                        mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)) ?: "",
                        width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)),
                        height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)),
                        size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                        latitude = null,
                        longitude = null,
                        aiScore = exifData?.aiScore ?: -1f,
                        isBestTake = exifData?.isBestTake ?: false,
                        rejectionReason = exifData?.rejectionReason,
                        clusterId = exifData?.clusterId,
                        originalFolderName = exifData?.originalFolder
                    )
                    
                    mediaDao.insertOrUpdateSingle(entity)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun restoreMetadataForIds(ids: List<Long>): Boolean = withContext(Dispatchers.IO) {
        val entities = mediaDao.getMediaByIds(ids)
        if (entities.isEmpty()) return@withContext false
        
        var anyChanged = false
        
        coroutineScope {
            val deferreds = entities.map { entity ->
                async {
                    try {
                        val exifData = ExifMetadataManager.readExifMetadata(context, Uri.parse(entity.uri))
                        if (exifData != null) {
                            val isDifferent = entity.aiScore != exifData.aiScore || 
                                             entity.isBestTake != exifData.isBestTake ||
                                             entity.rejectionReason != exifData.rejectionReason
                                             
                            if (isDifferent) {
                                mediaDao.updateFullMetadata(
                                    id = entity.id,
                                    isBest = exifData.isBestTake,
                                    score = exifData.aiScore,
                                    reason = exifData.rejectionReason,
                                    clusterId = exifData.clusterId,
                                    originalFolder = exifData.originalFolder,
                                    description = exifData.aiDescription,
                                    faceCount = exifData.mainFaceCount
                                )
                                true
                            } else false
                        } else false
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            if (deferreds.awaitAll().any { it }) {
                anyChanged = true
            }
        }
        anyChanged
    }

    companion object {
        private const val CHUNK_SIZE = 500
        private const val BATCH_SIZE_LOCATION = 50
    }
}
