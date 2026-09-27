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

interface MediaNavigator {
    fun share(context: Context, photos: List<MediaPhoto>)
    fun edit(context: Context, photo: MediaPhoto)
    fun getEditIntent(context: Context, photo: MediaPhoto): Intent
    fun setAsWallpaper(context: Context, photo: MediaPhoto)
    fun print(context: Context, photo: MediaPhoto)
}

class DefaultMediaNavigator : MediaNavigator {
    private val TAG = "MediaNavigator"

    override fun share(context: Context, photos: List<MediaPhoto>) {
        if (photos.isEmpty()) return
        val uris = ArrayList(photos.map { it.contentUri })

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                val mime = resolveMimeType(context, uris[0])
                setDataAndType(uris[0], mime)
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        attachClipData(intent, uris)

        val chooser = Intent.createChooser(intent, "Share with").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            attachClipData(this, uris)
        }
        context.startActivity(chooser)
    }

    override fun edit(context: Context, photo: MediaPhoto) {
        try {
            val intent = getEditIntent(context, photo)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Edit failed", e)
        }
    }

    override fun getEditIntent(context: Context, photo: MediaPhoto): Intent {
        val uri = photo.contentUri
        val mimeType = resolveMimeType(context, uri)

        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, mimeType)
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri("Photo", uri)
        }

        // Return the chooser with proper flags and clipData attached to BOTH intents
        val chooser = Intent.createChooser(editIntent, "Edit Photo").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri("Photo", uri)
        }

        return chooser
    }

    override fun setAsWallpaper(context: Context, photo: MediaPhoto) {
        if (photo.isVideo) return
        val uri = photo.contentUri
        val mimeType = resolveMimeType(context, uri)

        try {
            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, mimeType)
                putExtra("mimeType", mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Photo", uri)
            }
            val chooser = Intent.createChooser(intent, "Set as").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Photo", uri)
            }
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

    /**
     * Resolves the actual MIME type (e.g. image/jpeg, image/png) from ContentResolver.
     */
    private fun resolveMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "image/jpeg"
    }

    private fun attachClipData(intent: Intent, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val clipData = ClipData.newRawUri("Media", uris[0])
        for (i in 1 until uris.size) {
            clipData.addItem(ClipData.Item(uris[i]))
        }
        intent.clipData = clipData
    }
}

val LocalMediaNavigator = staticCompositionLocalOf<MediaNavigator> {
    DefaultMediaNavigator()
}