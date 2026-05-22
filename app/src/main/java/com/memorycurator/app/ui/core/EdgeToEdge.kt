package com.memorycurator.app.ui.core

import android.app.Activity
import androidx.core.view.WindowCompat

fun enableEdgeToEdge(
    activity: Activity
) {

    WindowCompat.setDecorFitsSystemWindows(
        activity.window,
        false
    )
}