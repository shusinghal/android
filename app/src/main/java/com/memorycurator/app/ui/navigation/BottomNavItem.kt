package com.memorycurator.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(

    val route: String,

    val label: String,

    val icon: ImageVector
) {

    data object Timeline : BottomNavItem(
        "timeline",
        "Timeline",
        Icons.Rounded.AutoAwesomeMotion
    )

    data object Maps : BottomNavItem(
        "maps",
        "Maps",
        Icons.Rounded.Map
    )

    data object Albums : BottomNavItem(
        "albums",
        "Albums",
        Icons.Rounded.Collections
    )

    data object Profile : BottomNavItem(
        "profile",
        "Profile",
        Icons.Rounded.Person
    )
}