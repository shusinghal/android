package com.memorycurator.app.ui.navigation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.runtime.staticCompositionLocalOf
import com.memorycurator.app.data.media.MediaPhoto
import java.util.ArrayList

/**
 * Professional-grade Navigator for media actions.
 * Centralizes all Intent logic and simplifies cross-screen integration.
 */
interface MediaNavigator {
    fun share(context: Context, photos: List<MediaPhoto>)
    fun edit(context: Context, photo: MediaPhoto)
    fun setAsWallpaper(context: Context, photo: MediaPhoto)
    fun print(context: Context, photo: MediaPhoto)
}

/**
 * Standard implementation of MediaNavigator.
 */
class DefaultMediaNavigator : MediaNavigator {
    private val TAG = "MediaNavigator"

    override fun share(context: Context, photos: List<MediaPhoto>) {
        if (photos.isEmpty()) return
        val uris = ArrayList(photos.map { it.contentUri })
        
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                val mime = context.contentResolver.getType(uris[0]) ?: getMimeType(uris[0])
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*" 
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        attachClipData(intent, uris)

        val chooser = Intent.createChooser(intent, "Share with")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    override fun edit(context: Context, photo: MediaPhoto) {
        val uri = photo.contentUri
        val mimeType = context.contentResolver.getType(uri) ?: getMimeType(uri)
        
        try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }
            
            val chooser = Intent.createChooser(intent, "Edit with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_EDIT failed, falling back to Share", e)
            share(context, listOf(photo))
        }
    }

    override fun setAsWallpaper(context: Context, photo: MediaPhoto) {
        if (photo.isVideo) return
        val uri = photo.contentUri
        
        try {
            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, "image/*")
                putExtra("mimeType", "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }
            val chooser = Intent.createChooser(intent, "Set as")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "SetAs intent failed", e)
        }
    }

    override fun print(context: Context, photo: MediaPhoto) {
        try {
            val printHelper = androidx.print.PrintHelper(context)
            printHelper.scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
            printHelper.printBitmap("MemoryCurator_${photo.id}", photo.contentUri)
        } catch (e: Exception) {
            Log.e(TAG, "Print job failed", e)
        }
    }

    private fun getMimeType(uri: Uri): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/*"
    }

    private fun attachClipData(intent: Intent, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val clipData = ClipData.newRawUri("", uris[0])
        for (i in 1 until uris.size) {
            clipData.addItem(ClipData.Item(uris[i]))
        }
        intent.clipData = clipData
    }
}

/**
 * CompositionLocal to access the navigator anywhere in the UI tree.
 */
val LocalMediaNavigator = staticCompositionLocalOf<MediaNavigator> {
    DefaultMediaNavigator()
}
