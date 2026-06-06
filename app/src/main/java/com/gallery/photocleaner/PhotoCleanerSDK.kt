package com.gallery.photocleaner

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gallery.photocleaner.ui.CarouselScreen
import com.gallery.photocleaner.ui.CleanerViewModel

object PhotoCleanerSDK {

    /**
     * Entry interface to launch the evaluation flow inside your existing App.
     */
    @Composable
    fun PhotoCleanerFlow(
        selectedUris: List<Uri>,
        onExit: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val viewModel: CleanerViewModel = viewModel()

        // Trigger sorting pipeline immediately upon initialization
        androidx.compose.runtime.LaunchedEffect(selectedUris) {
            viewModel.processSelectedPhotos(selectedUris)
        }

        CarouselScreen(
            viewModel = viewModel,
            onExitNavigation = onExit,
            modifier = modifier
        )
    }
}