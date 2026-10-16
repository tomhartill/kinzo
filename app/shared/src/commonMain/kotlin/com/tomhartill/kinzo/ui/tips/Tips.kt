package com.tomhartill.kinzo.ui.tips

import com.tomhartill.kinzo.domain.ScreenTips
import com.tomhartill.kinzo.domain.Tip

/**
 * Every first-time tip's words, in one place so they're easy to review and change. Keep them
 * plain and short: one sentence where possible. Ids are stored once seen, so never reuse one.
 */
object KinzoTips {
    val Today = ScreenTips(
        screen = "today",
        intro = page("today.intro", "Today", "Start an empty workout or one of your saved ones. When you follow a mesocycle, today's workout shows here."),
    )

    val History = ScreenTips(
        screen = "history",
        intro = page("history.intro", "Your history", "Every workout you've finished, newest first. Tap one to see it or change it."),
    )

    val Library = ScreenTips(
        screen = "library",
        intro = page("library.intro", "Your library", "Your saved workouts, ready-made ones and mesocycles. Copy a ready-made workout to make it your own."),
    )

    val Mesocycle = ScreenTips(
        screen = "mesocycle",
        intro = page("mesocycle.intro", "Mesocycles", "A block of weeks that follows a split. Today shows which workout is next."),
    )

    val ExercisePicker = ScreenTips(
        screen = "exercise-picker",
        intro = page("exercise-picker.intro", "Adding exercises", "Search by name or muscle. Tick as many as you like, then add them all at once."),
    )

    val ExerciseDetail = ScreenTips(
        screen = "exercise-detail",
        intro = page("exercise-detail.intro", "Exercise details", "How to do it and what it works. Once you've logged it, your progress shows here too."),
    )

    val Friends = ScreenTips(
        screen = "friends",
        intro = page("friends.intro", "Friends", "Share your invite link to add someone. Friends see your workouts and PRs, never your body stats or photos."),
    )

    val LogSet = spotlight("workout.log-set", "Logging a set", "Type the weight and reps, then tick the box. Swipe a set left to delete it.")
    val AddSet = spotlight(
        "workout.add-set",
        "Adding sets",
        "Tap to copy the last set. Hold and slide to change the weight. You can also hold any weight or reps box and slide to change it.",
    )
    val RestBar = spotlight("workout.rest-bar", "Rest between sets", "This shows the rest for the exercise you're on. It counts down when you tick a set.")

    val ActiveWorkout = ScreenTips(screen = "active-workout", spotlights = listOf(LogSet, AddSet, RestBar))

    private fun page(id: String, title: String, body: String) = Tip(id, title, body, Tip.Kind.Page)

    private fun spotlight(id: String, title: String, body: String) = Tip(id, title, body, Tip.Kind.Spotlight)
}
