package com.memorycurator.app.feature.timeline.data

import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimelineGrouper {

    fun groupByDate(

        photos: List<MediaPhoto>

    ): List<TimelineGroup> {

        val formatter = SimpleDateFormat(
            "dd MMMM yyyy",
            Locale.getDefault()
        )

        return photos

            .groupBy {

                formatter.format(
                    Date(it.dateTaken)
                )
            }

            .map {

                TimelineGroup(

                    title = it.key,

                    photos = it.value
                )
            }
    }
}