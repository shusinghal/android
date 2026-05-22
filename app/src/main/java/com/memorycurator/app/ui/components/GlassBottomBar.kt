package com.memorycurator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.memorycurator.app.ui.navigation.BottomNavItem

@Composable
fun GlassBottomBar(

    items: List<BottomNavItem>,

    selected: String,

    onSelected: (String) -> Unit
) {

    Row(

        modifier = Modifier

            .fillMaxWidth()

            .padding(
                horizontal = 20.dp,
                vertical = 16.dp
            )

            .clip(
                RoundedCornerShape(32.dp)
            )

            .background(
                Color(0x22FFFFFF)
            )

            .padding(
                horizontal = 12.dp,
                vertical = 14.dp
            ),

        horizontalArrangement = Arrangement.SpaceAround,

        verticalAlignment = Alignment.CenterVertically
    ) {

        items.forEach { item ->

            val isSelected = selected == item.route

            Box(

                modifier = Modifier

                    .clip(
                        RoundedCornerShape(22.dp)
                    )

                    .background(
                        if (isSelected)
                            Color(0x33FFFFFF)
                        else
                            Color.Transparent
                    )

                    .clickable {
                        onSelected(item.route)
                    }

                    .padding(
                        horizontal = 18.dp,
                        vertical = 10.dp
                    ),

                contentAlignment = Alignment.Center
            ) {

                Row(

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = Color.White
                    )

                    if (isSelected) {

                        Text(
                            text = item.label,
                            color = Color.White,
                            modifier = Modifier.padding(
                                start = 8.dp
                            )
                        )
                    }
                }
            }
        }
    }
}