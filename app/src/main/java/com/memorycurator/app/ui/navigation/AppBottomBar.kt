package com.memorycurator.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.memorycurator.app.ui.theme.AppTheme

@Composable
fun AppBottomBar(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem.Timeline,
        BottomNavItem.Maps,
        BottomNavItem.Albums,
        BottomNavItem.Profile
    )

    NavigationBar(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp)),
        containerColor = Color.White.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val isSelected = selectedRoute == item.route

            NavigationBarItem(
                selected = isSelected,
                onClick = { onRouteSelected(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (isSelected)
                            AppTheme.Colors.TextPrimary
                        else
                            AppTheme.Colors.TextSecondary // Assuming Typography exists in your theme
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppTheme.Colors.TextPrimary,
                    unselectedIconColor = AppTheme.Colors.TextSecondary,
                    selectedTextColor = AppTheme.Colors.TextPrimary,
                    unselectedTextColor = AppTheme.Colors.TextSecondary,
                    indicatorColor = Color.White.copy(alpha = 0.10f)
                )
            )
        }
    }
}