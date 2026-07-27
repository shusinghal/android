package com.memorycurator.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaRepositoryImpl
import com.memorycurator.app.ui.core.enableEdgeToEdge
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.gallery.GalleryViewModelFactory
import com.memorycurator.app.ui.navigation.MainNavigation
import com.memorycurator.app.ui.theme.GlassTheme

import com.memorycurator.app.ui.core.CoilConfig
import coil.Coil

class MainActivity : ComponentActivity() {

    private lateinit var galleryViewModel: GalleryViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            if (::galleryViewModel.isInitialized) {
                galleryViewModel.indexMedia()
            }
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(this)

        // Initialize Coil for video thumbnails
        Coil.setImageLoader(CoilConfig.getImageLoader(applicationContext))

        val factory = GalleryViewModelFactory(
            repository = MediaRepositoryImpl(applicationContext),
            mediaIndexer = MediaIndexer(
                applicationContext,
                DatabaseProvider.getDatabase(applicationContext).mediaDao()
            ),
            context = applicationContext
        )
        galleryViewModel = ViewModelProvider(this, factory)[GalleryViewModel::class.java]
        
        checkAndRequestPermissions()

        setContent {
            GlassTheme {
                MainNavigation(
                    viewModel = galleryViewModel
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            val list = mutableListOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            if (Build.VERSION.SDK_INT >= 34) {
                list.add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
            }
            list.toTypedArray()
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
