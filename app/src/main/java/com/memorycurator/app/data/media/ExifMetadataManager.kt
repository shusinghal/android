package com.memorycurator.app.data.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.io.File
import java.io.InputStream

data class CurationExifData(
    val aiScore: Float,
    val isBestTake: Boolean,
    val rejectionReason: String?,
    val clusterId: String?,
    val originalFolder: String? = null
)

object ExifMetadataManager {
    private const val TAG = "ExifMetadataManager"

    /**
     * Writes AI curation metadata to the photo's EXIF header using direct file path for reliability.
     */
    fun writeExifMetadata(filePath: String, metadata: CurationExifData) {
        try {
            val json = JSONObject().apply {
                put("aiScore", metadata.aiScore.toDouble())
                put("isBestTake", metadata.isBestTake)
                put("rejectionReason", metadata.rejectionReason ?: JSONObject.NULL)
                put("clusterId", metadata.clusterId ?: JSONObject.NULL)
                put("originalFolder", metadata.originalFolder ?: JSONObject.NULL)
            }.toString()

            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, json)
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing EXIF metadata to $filePath", e)
        }
    }

    /**
     * Reads AI curation metadata from the photo's EXIF header using an InputStream for reliability on scoped storage.
     */
    fun readExifMetadata(inputStream: InputStream): CurationExifData? {
        try {
            val exif = ExifInterface(inputStream)
            val jsonString = exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: return null
            
            return try {
                val json = JSONObject(jsonString)
                if (!json.has("aiScore")) return null
                
                CurationExifData(
                    aiScore = json.optDouble("aiScore", -1.0).toFloat(),
                    isBestTake = json.optBoolean("isBestTake", false),
                    rejectionReason = if (json.isNull("rejectionReason")) null else json.optString("rejectionReason"),
                    clusterId = if (json.isNull("clusterId")) null else json.optString("clusterId"),
                    originalFolder = if (json.isNull("originalFolder")) null else json.optString("originalFolder")
                )
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EXIF metadata from stream", e)
        }
        return null
    }

    /**
     * Reads AI curation metadata from the photo's EXIF header using direct file path.
     */
    fun readExifMetadata(filePath: String): CurationExifData? {
        try {
            val exif = ExifInterface(filePath)
            val jsonString = exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: return null
            
            return try {
                val json = JSONObject(jsonString)
                if (!json.has("aiScore")) return null
                
                CurationExifData(
                    aiScore = json.optDouble("aiScore", -1.0).toFloat(),
                    isBestTake = json.optBoolean("isBestTake", false),
                    rejectionReason = if (json.isNull("rejectionReason")) null else json.optString("rejectionReason"),
                    clusterId = if (json.isNull("clusterId")) null else json.optString("clusterId"),
                    originalFolder = if (json.isNull("originalFolder")) null else json.optString("originalFolder")
                )
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EXIF metadata from $filePath", e)
        }
        return null
    }

    // Compatibility wrappers for Uri-based calls
    fun readExifMetadata(context: Context, uri: Uri): CurationExifData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readExifMetadata(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for $uri", e)
            null
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
