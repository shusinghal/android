package com.memorycurator.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(

    val route: String,

    val label: String,

    val icon: ImageVector
) {

    data object Photos : BottomNavItem(

        route = "photos",

        label = "Photos",

        icon = Icons.Rounded.PhotoLibrary
    )

    data object Albums : BottomNavItem(

        route = "albums",

        label = "Albums",

        icon = Icons.Rounded.Collections
    )

    data object Timeline : BottomNavItem(

        route = "timeline",

        label = "Timeline",

        icon = Icons.Rounded.Schedule
    )

    data object Cleanup : BottomNavItem(

        route = "cleanup",

        label = "Cleanup",

        icon = Icons.Rounded.AutoAwesome
    )
}