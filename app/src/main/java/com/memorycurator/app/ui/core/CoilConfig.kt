package com.memorycurator.app.ui.core

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

object CoilConfig {
    fun getImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
