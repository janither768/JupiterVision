// LifestylePhase.kt - Discrete deterministic state machine phases representing the user's current context.
package com.jupiter.vision.engine.model

enum class LifestylePhase(val label: String) {
    MORNING_WAKEUP("MORNING WAKEUP"),
    COMMUTE_TO_WORK("COMMUTE TO WORK"),
    AT_WORK("AT WORK"),
    COMMUTE_HOME("COMMUTE HOME"),
    GYM_TIME("GYM TIME"),
    EVENING_RELAX("EVENING RELAX"),
    SLEEPING("SLEEPING"),
    UNKNOWN("UNKNOWN")
}
