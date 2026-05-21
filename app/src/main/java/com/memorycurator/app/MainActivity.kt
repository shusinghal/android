package com.memorycurator.app

import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorycurator.app.data.media.MediaRepositoryImpl
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.gallery.GalleryViewModelFactory
import com.memorycurator.app.ui.theme.MemoryCuratorTheme
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.ui.navigation.MainNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoryCuratorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // Determine correct permission based on Android version
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Initialize state by checking current permission status
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(permission)
        }
    }
    if (hasPermission) {

        val database = remember {

            DatabaseProvider.getDatabase(
                context
            )
        }

        val mediaDao = remember {
            database.mediaDao()
        }

        val mediaIndexer = remember {

            MediaIndexer(
                context = context,
                mediaDao = mediaDao
            )
        }


        val repository = remember {

            MediaRepositoryImpl(
                context.applicationContext
            )
        }

        val viewModel: GalleryViewModel = viewModel(

            factory = GalleryViewModelFactory(
                repository,
                mediaIndexer
            )
        )
        MainNavigation(viewModel)
    } else {
        // KEY FIX: Provide a fallback UI if the user denies the permission
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { launcher.launch(permission) }) {
                Text("Grant Storage Permission")
            }
        }
    }
}