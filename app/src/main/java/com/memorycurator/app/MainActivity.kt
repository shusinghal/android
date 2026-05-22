package com.memorycurator.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaRepositoryImpl
import com.memorycurator.app.ui.core.enableEdgeToEdge
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.gallery.GalleryViewModelFactory
import com.memorycurator.app.ui.navigation.MainNavigation
import com.memorycurator.app.ui.theme.GlassTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge(this)

        setContent {

            GlassTheme {

                val galleryViewModel: GalleryViewModel = viewModel(

                    factory = GalleryViewModelFactory(

                        repository = MediaRepositoryImpl(
                            applicationContext
                        ),

                        mediaIndexer = MediaIndexer(

                            applicationContext,

                            DatabaseProvider
                                .getDatabase(applicationContext)
                                .mediaDao()
                        )
                    )
                )

                MainNavigation(
                    viewModel = galleryViewModel
                )
            }
        }
    }
}