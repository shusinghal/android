package com.memorycurator.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HeaderSection() {

    Column(
        modifier = Modifier.padding(20.dp)
    ) {

        Text(
            text = "AI Memory Curation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Best moments automatically selected",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}