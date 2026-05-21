package com.memorycurator.app.ui.fake

import com.memorycurator.app.ui.models.PhotoItem

object FakePhotos {

    fun getPhotos(): List<PhotoItem> {

        return List(60) {

            PhotoItem(
                id = it.toLong(),
                imageUrl = "https://picsum.photos/600/900?random=$it",
                score = (75..99).random() / 100f,
                badge = listOf(
                    "Sharp",
                    "Portrait",
                    "Best Smile",
                    "Cinematic",
                    "Instagram Ready"
                ).random()
            )
        }
    }
}