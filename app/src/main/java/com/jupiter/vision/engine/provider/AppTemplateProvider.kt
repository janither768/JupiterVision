// AppTemplateProvider.kt - Fallback and baseline application packages for each Lifestyle Phase.
package com.jupiter.vision.engine.provider

import com.jupiter.vision.engine.model.LifestylePhase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTemplateProvider @Inject constructor() {

    private val templates: Map<LifestylePhase, List<String>> = mapOf(
        LifestylePhase.MORNING_WAKEUP to listOf(
            "com.spotify.music",
            "com.whatsapp",
            "com.google.android.apps.maps"
        ),
        LifestylePhase.COMMUTE_TO_WORK to listOf(
            "com.spotify.music",
            "com.google.android.apps.maps",
            "com.whatsapp"
        ),
        LifestylePhase.AT_WORK to listOf(
            "com.Slack",
            "com.google.android.gm",
            "com.google.android.calendar"
        ),
        LifestylePhase.COMMUTE_HOME to listOf(
            "com.spotify.music",
            "com.whatsapp"
        ),
        LifestylePhase.GYM_TIME to listOf(
            "com.fitbit.FitbitMobile",
            "com.spotify.music"
        ),
        LifestylePhase.EVENING_RELAX to listOf(
            "com.netflix.mediaclient",
            "com.google.android.youtube",
            "com.whatsapp"
        ),
        LifestylePhase.SLEEPING to emptyList(),
        LifestylePhase.UNKNOWN to listOf(
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.chrome"
        )
    )

    fun getTemplatesFor(phase: LifestylePhase): List<String> {
        return templates[phase] ?: templates[LifestylePhase.UNKNOWN].orEmpty()
    }
}
