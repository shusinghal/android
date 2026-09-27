package com.memorycurator.app.ui.navigation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
        val pm = context.packageManager

        Log.d(TAG, "[MediaNavigatorDebug] photo.id: ${photo.id}, uri: $uri, mimeType: $mimeType, isVideo: ${photo.isVideo}")

        // 1. Standard EDIT intent
        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, mimeType)
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Photo", uri)
        }

        // 2. Google Camera / OEM Editor intent
        val cameraEditorIntent = Intent("com.android.camera.action.EDITOR").apply {
            setDataAndType(uri, mimeType)
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Photo", uri)
        }

        // 3. SEND intent (Markup / Draw / Sharing Editors)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            setDataAndType(uri, mimeType)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Photo", uri)
        }

        fun queryAndLog(intent: Intent, actionName: String): List<android.content.pm.ResolveInfo> {
            val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            Log.d(TAG, "[MediaNavigatorDebug] Found ${list.size} handlers for $actionName:")
            for (info in list) {
                Log.d(TAG, "[MediaNavigatorDebug]   -> ${info.activityInfo.packageName} / ${info.activityInfo.name}")
            }
            return list
        }

        val editActivities = queryAndLog(editIntent, "ACTION_EDIT")
        val cameraEditorActivities = queryAndLog(cameraEditorIntent, "com.android.camera.action.EDITOR")
        queryAndLog(sendIntent, "ACTION_SEND")

        // Pick primary intent based on handlers found on this device
        val primaryIntent: Intent = when {
            editActivities.isNotEmpty() -> editIntent
            cameraEditorActivities.isNotEmpty() -> cameraEditorIntent
            else -> editIntent
        }

        val secondaryIntent: Intent = when {
            primaryIntent === editIntent && cameraEditorActivities.isNotEmpty() -> cameraEditorIntent
            primaryIntent === cameraEditorIntent && editActivities.isNotEmpty() -> editIntent
            else -> cameraEditorIntent
        }

        val chooser = Intent.createChooser(primaryIntent, "Edit Photo").apply {
            val bundle = android.os.Bundle().apply {
                putParcelableArray(Intent.EXTRA_INITIAL_INTENTS, arrayOf<android.os.Parcelable>(secondaryIntent))
            }
            putExtras(bundle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    private fun resolveMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "image/*"
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