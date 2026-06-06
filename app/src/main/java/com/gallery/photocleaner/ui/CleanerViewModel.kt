package com.gallery.photocleaner.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.photocleaner.ai.ImageAIService
import com.gallery.photocleaner.ai.ImageQualityEvaluator
import com.gallery.photocleaner.model.PhotoItem
import com.gallery.photocleaner.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val aiService = ImageAIService(application)
    private val qualityEvaluator = ImageQualityEvaluator(application)
    private val storageManager = StorageManager(application)

    private val _goodPhotos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val goodPhotos: StateFlow<List<PhotoItem>> = _goodPhotos.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val qualityThreshold = 0.55f // Customizable aesthetic baseline
    private val similarityThreshold = 0.88f // Dual threshold matching for similarity

    /**
     * Start processing selected gallery photo items in continuous steps of 10.
     */
    fun processSelectedPhotos(sourceUris: List<Uri>) {
        viewModelScope.launch {
            _isProcessing.value = true
            val parsedPhotos = sourceUris.mapIndexed { idx, uri ->
                PhotoItem(id = idx.toLong(), uri = uri, path = uri.toString())
            }

            val finalGoodList = mutableListOf<PhotoItem>()
            var currentStartIndex = 0

            while (currentStartIndex < parsedPhotos.size) {
                // Batch up exactly 10 photos at a time
                val batch = parsedPhotos.subList(
                    currentStartIndex,
                    (currentStartIndex + 10).coerceAtMost(parsedPhotos.size)
                )

                val processedBatch = evaluateAndFilterBatch(batch)
                finalGoodList.addAll(processedBatch)

                currentStartIndex += 10
            }

            _goodPhotos.value = finalGoodList
            _isProcessing.value = false
        }
    }

    private suspend fun evaluateAndFilterBatch(batch: List<PhotoItem>): List<PhotoItem> {
        // Step 1: Assign quality scores
        val scoredItems = batch.map { item ->
            val score = qualityEvaluator.evaluate(item.uri)
            item.copy(score = score)
        }

        // Step 2: Separate acceptable photos from poor captures
        val (goodQuality, badQuality) = scoredItems.partition { it.score >= qualityThreshold }

        // Process dropped assets (low-quality)
        for (badPhoto in badQuality) {
            storageManager.moveToDroppedFolder(badPhoto.uri)?.let {
                storageManager.deleteOriginal(badPhoto.uri)
            }
        }

        // Step 3: Run pairwise similarity analysis on survivors
        val survivedAndUnique = mutableListOf<PhotoItem>()
        val embeddingsMap = goodQuality.associateWith { item ->
            aiService.extractFeatureVector(item.uri)
        }

        val checkedSet = mutableSetOf<PhotoItem>()

        for (i in goodQuality.indices) {
            val itemA = goodQuality[i]
            if (itemA in checkedSet) continue

            var bestCandidate = itemA
            val duplicatesInGroup = mutableListOf<PhotoItem>()

            for (j in i + 1 until goodQuality.size) {
                val itemB = goodQuality[j]
                if (itemB in checkedSet) continue

                val embA = embeddingsMap[itemA]
                val embB = embeddingsMap[itemB]

                if (embA != null && embB != null) {
                    val similarity = aiService.calculateSimilarity(embA, embB)
                    if (similarity >= similarityThreshold) {
                        duplicatesInGroup.add(itemB)
                        checkedSet.add(itemB)

                        // Keep whichever file contains the higher quality score
                        if (itemB.score > bestCandidate.score) {
                            bestCandidate = itemB
                        }
                    }
                }
            }

            survivedAndUnique.add(bestCandidate)
            checkedSet.add(itemA)

            // Relocate duplicate candidates to target folder
            val duplicatesToDiscard = (duplicatesInGroup + itemA) - bestCandidate
            for (discarded in duplicatesToDiscard) {
                storageManager.moveToDroppedFolder(discarded.uri)?.let {
                    storageManager.deleteOriginal(discarded.uri)
                }
            }
        }

        return survivedAndUnique
    }

    /**
     * UI interaction to manually eject an asset during review.
     */
    fun userRemovePhoto(photo: PhotoItem) {
        viewModelScope.launch {
            _goodPhotos.value = _goodPhotos.value.filter { it.id != photo.id }
            storageManager.moveToDroppedFolder(photo.uri)?.let {
                storageManager.deleteOriginal(photo.uri)
            }
        }
    }
}