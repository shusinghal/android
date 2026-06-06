package com.gallery.photocleaner.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class StorageManager(private val context: Context) {

    /**
     * Copies a photo stream to a new physical folder: Pictures/DroppedPhotos.
     * Once copied, it returns the Uri of the new file.
     */
    suspend fun moveToDroppedFolder(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val filename = "dropped_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // Places it inside a physical external directory "Pictures/DroppedPhotos"
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DroppedPhotos")
        }

        val targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext null

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            inputStream = resolver.openInputStream(sourceUri)
            outputStream = resolver.openOutputStream(targetUri)

            if (inputStream != null && outputStream != null) {
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                targetUri
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    /**
     * Call this helper method to delete the original file from the MediaStore.
     */
    fun deleteOriginal(uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (securityException: SecurityException) {
            // Android 15 requires developer runtime user consent for updates/deletions if the file
            // is not owned by this app. You will need to handle the intent result in your activity.
            false
        }
    }
}