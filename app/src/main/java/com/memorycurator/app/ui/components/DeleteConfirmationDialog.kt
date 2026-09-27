package com.memorycurator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorycurator.app.ui.theme.MemoryCuratorTheme

@Composable
fun DeleteConfirmationDialog(
    itemCount: Int = 1,
    title: String? = null,
    message: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayTitle = title ?: if (itemCount > 1) "Delete $itemCount Photos?" else "Delete Photo?"
    val displayMessage = message ?: if (itemCount > 1) {
        "Are you sure you want to delete these $itemCount photos? They will be moved to system trash."
    } else {
        "Are you sure you want to delete this photo? It will be moved to system trash."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1C1C1E).copy(alpha = 0.85f))
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Black.copy(alpha = 0.2f),
                        blendMode = BlendMode.Overlay
                    )
                }
            }
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(28.dp)
            ),
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = displayTitle,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = displayMessage,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text("Cancel")
            }
        },
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(28.dp)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun DeleteConfirmationDialogPreview() {
    MemoryCuratorTheme {
        DeleteConfirmationDialog(
            itemCount = 1,
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun DeleteConfirmationDialogMultiplePreview() {
    MemoryCuratorTheme {
        DeleteConfirmationDialog(
            itemCount = 5,
            onConfirm = {},
            onDismiss = {}
        )
    }
}
