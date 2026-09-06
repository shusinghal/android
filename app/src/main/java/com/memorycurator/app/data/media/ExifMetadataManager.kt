package com.memorycurator.app.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.io.InputStream

data class CurationExifData(
    val aiScore: Float,
    val isBestTake: Boolean,
    val rejectionReason: String?,
    val clusterId: String?,
    val originalFolder: String? = null,
    val aiDescription: String? = null,
    val mainFaceCount: Int = 0
)

object ExifMetadataManager {
    private const val TAG = "ExifMetadataManager"

    /**
     * Writes AI curation metadata to the photo's EXIF header using Scoped Storage compatible methods.
     * Returns null on success, or a Throwable (like RecoverableSecurityException) if permission is needed.
     */
    fun writeExifMetadata(context: Context, uri: Uri, metadata: CurationExifData): Throwable? {
        try {
            val json = JSONObject().apply {
                put("aiScore", metadata.aiScore.toDouble())
                put("isBestTake", metadata.isBestTake)
                put("rejectionReason", metadata.rejectionReason ?: JSONObject.NULL)
                put("clusterId", metadata.clusterId ?: JSONObject.NULL)
                put("originalFolder", metadata.originalFolder ?: JSONObject.NULL)
                put("aiDescription", metadata.aiDescription ?: JSONObject.NULL)
                put("mainFaceCount", metadata.mainFaceCount)
            }.toString()

            // For modern Android, we must use openFileDescriptor with "rw" mode
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, json)
                exif.saveAttributes()
                Log.d(TAG, "Successfully wrote EXIF metadata to $uri")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing EXIF metadata to $uri", e)
            return e
        }
        return Exception("Failed to open file descriptor")
    }

    /**
     * Reads AI curation metadata from the photo's EXIF header using an InputStream.
     */
    fun readExifMetadata(inputStream: InputStream): CurationExifData? {
        try {
            val exif = ExifInterface(inputStream)
            val jsonString = exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: return null
            
            // Clean JSON string if some systems add prefixes (like ASCII)
            val cleanedJson = if (jsonString.startsWith("ASCII")) {
                jsonString.substring(8).trim() // ASCII prefix is usually 8 bytes in raw EXIF
            } else if (jsonString.contains("{")) {
                jsonString.substring(jsonString.indexOf("{")).trim()
            } else jsonString.trim()

            if (!cleanedJson.startsWith("{")) return null

            return try {
                val json = JSONObject(cleanedJson)
                if (!json.has("aiScore")) return null
                
                CurationExifData(
                    aiScore = json.optDouble("aiScore", -1.0).toFloat(),
                    isBestTake = json.optBoolean("isBestTake", false),
                    rejectionReason = if (json.isNull("rejectionReason")) null else json.optString("rejectionReason"),
                    clusterId = if (json.isNull("clusterId")) null else json.optString("clusterId"),
                    originalFolder = if (json.isNull("originalFolder")) null else json.optString("originalFolder"),
                    aiDescription = if (json.isNull("aiDescription")) null else json.optString("aiDescription"),
                    mainFaceCount = json.optInt("mainFaceCount", 0)
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
     * Compatibility wrapper for Uri-based calls. Safe for Scoped Storage.
     */
    fun readExifMetadata(context: Context, uri: Uri): CurationExifData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readExifMetadata(input)
            }
        } catch (e: Exception) {
            null
        }
    }
}
