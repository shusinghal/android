package com.memorycurator.app.ui.core

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.glassBlur(): Modifier {

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

        this.graphicsLayer {

            renderEffect = RenderEffect
                .createBlurEffect(
                    1f,
                    1f,
                    Shader.TileMode.MIRROR
                )
                .asComposeRenderEffect()
        }

    } else {
        this
    }
}