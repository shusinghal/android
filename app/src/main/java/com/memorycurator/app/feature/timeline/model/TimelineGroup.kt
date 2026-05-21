package com.memorycurator.app.feature.timeline.model

import com.memorycurator.app.data.media.MediaPhoto

data class TimelineGroup(

    val title: String,

    val photos: List<MediaPhoto>
)