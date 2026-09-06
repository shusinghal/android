package com.memorycurator.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorycurator.app.data.local.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences(context) }
    var isDeepAnalysis by remember { mutableStateOf(userPrefs.isDeepAnalysisEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "AI Analysis Settings",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Deep AI Analysis",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isDeepAnalysis) "Using Advanced Embeddings (Slow but Accurate)" else "Using Fast ML Triage (Standard)",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
            Switch(
                        checked = isDeepAnalysis,
                        onCheckedChange = {
                            isDeepAnalysis = it
                            userPrefs.isDeepAnalysisEnabled = it
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var isPhysicalStorage by remember { mutableStateOf(userPrefs.isPhysicalStorageEnabled) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permanent File Curation",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isPhysicalStorage) "Writing tags to Image properties" else "Storing results in App Database",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isPhysicalStorage,
                            onCheckedChange = {
                                isPhysicalStorage = it
                                userPrefs.isPhysicalStorageEnabled = it
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Why this is recommended:",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Privacy: Your AI ratings stay with your photos, even if you delete the app.\n" +
                                       "• Compatibility: Pro gallery apps and desktops can read these AI tags.\n" +
                                       "• Portability: When you move photos to a new phone, your work is preserved.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            
                            if (isPhysicalStorage) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Note: Android will ask for permission every time we save changes to a file.",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isDeepAnalysis) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Note: Deep analysis can take up to 60 seconds per image but provides professional-grade results.",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
