package com.tomhartill.kinzo.domain

import kotlinx.serialization.Serializable

/** How an exercise is logged, which decides the numbers each of its sets records. */
enum class MeasurementType(val key: String) {
    /** Weight and reps, for example a bench press. */
    WeightReps("weight_reps"),

    /** Reps only, for example a push-up. */
    Reps("reps"),

    /** A held or timed effort, for example a plank or a stretch. */
    Duration("duration"),

    /** Distance and time, for example a run or a row. */
    DistanceDuration("distance_duration");

    companion object {
        fun fromKey(key: String): MeasurementType = entries.firstOrNull { it.key == key } ?: WeightReps
    }
}

enum class Muscle(val key: String, val label: String) {
    Abdominals("abdominals", "Abs"),
    Abductors("abductors", "Abductors"),
    Adductors("adductors", "Adductors"),
    Biceps("biceps", "Biceps"),
    Calves("calves", "Calves"),
    Chest("chest", "Chest"),
    Forearms("forearms", "Forearms"),
    Glutes("glutes", "Glutes"),
    Hamstrings("hamstrings", "Hamstrings"),
    Lats("lats", "Lats"),
    LowerBack("lower_back", "Lower back"),
    MiddleBack("middle_back", "Middle back"),
    Neck("neck", "Neck"),
    Quadriceps("quadriceps", "Quads"),
    Shoulders("shoulders", "Shoulders"),
    Traps("traps", "Traps"),
    Triceps("triceps", "Triceps");

    companion object {
        fun fromKey(key: String): Muscle? = entries.firstOrNull { it.key == key }

        /** Parses a comma-separated list of muscle keys, skipping any it doesn't know. */
        fun parseList(keys: String?): List<Muscle> =
            keys?.split(',')?.mapNotNull { fromKey(it.trim()) }.orEmpty()
    }
}

data class Equipment(val id: String, val name: String, val type: String)

data class ExerciseSummary(
    val id: String,
    val name: String,
    val measurementType: MeasurementType,
    val equipmentId: String?,
    val equipmentName: String?,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
)

data class ExerciseDetail(
    val id: String,
    val name: String,
    val description: String?,
    val instructions: List<String>,
    val measurementType: MeasurementType,
    val equipmentName: String?,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
)

/**
 * The numbers recorded for one set, in storage units: kilograms, seconds and metres.
 * Which of them matter depends on the exercise's [MeasurementType].
 */
@Serializable
data class SetValues(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationS: Int? = null,
    val distanceM: Double? = null,
) {
    /** The same values with anything this measurement type doesn't record cleared. */
    fun keepOnly(type: MeasurementType): SetValues = when (type) {
        MeasurementType.WeightReps -> SetValues(reps = reps, weightKg = weightKg)
        MeasurementType.Reps -> SetValues(reps = reps)
        MeasurementType.Duration -> SetValues(durationS = durationS)
        MeasurementType.DistanceDuration -> SetValues(durationS = durationS, distanceM = distanceM)
    }

    /** Weight times reps, or zero when either is missing. */
    val volumeKg: Double get() = if (reps != null && weightKg != null) reps * weightKg else 0.0
}

data class LoggedSet(
    val id: String,
    val values: SetValues,
    val isWarmup: Boolean,
    val completedAt: Long?,
) {
    val isCompleted: Boolean get() = completedAt != null
}

/** A completed set from an earlier workout, shown as "previous" or in an exercise's history. */
data class PreviousSet(val values: SetValues, val isWarmup: Boolean)

data class WorkoutExercise(
    val id: String,
    val exerciseId: String,
    val name: String,
    val measurementType: MeasurementType,
    val sets: List<LoggedSet>,
    /** The completed sets from the last finished workout with this exercise, in order. */
    val previous: List<PreviousSet>,
    /** Rest after each set in seconds, 0 for none, or null to use the default setting. */
    val restSeconds: Int? = null,
) {
    /** The rest that applies, given the default from settings. */
    fun rest(defaultSeconds: Int): Int = restSeconds ?: defaultSeconds
}

data class Workout(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val exercises: List<WorkoutExercise>,
) {
    val completedSetCount: Int get() = exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } }

    /** Total weight lifted in completed working sets. Warm-ups don't count. */
    val volumeKg: Double
        get() = exercises.sumOf { exercise ->
            exercise.sets.filter { it.isCompleted && !it.isWarmup }.sumOf { it.values.volumeKg }
        }
}

/** Just enough about the workout in progress to show it above the tabs. */
data class ActiveWorkoutInfo(val id: String, val name: String, val startedAt: Long)

data class ExerciseLine(val name: String, val setCount: Int)

data class WorkoutSummary(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val setCount: Int,
    val volumeKg: Double,
    val exercises: List<ExerciseLine>,
)

/** One workout's sets for a single exercise, for that exercise's history. */
data class ExerciseSession(
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long,
    val sets: List<PreviousSet>,
)

/** Who made a saved workout. */
enum class RoutineSource(val key: String) {
    /** Made in the app by the user. */
    User("user"),

    /** Ready-made, bundled with the app. Read-only; copying one makes a [User] workout. */
    Kinzo("kinzo"),

    /** Generated by the AI coach (later). */
    Ai("ai");

    companion object {
        fun fromKey(key: String): RoutineSource = entries.firstOrNull { it.key == key } ?: User
    }
}

/** A saved workout as listed in the library. */
data class RoutineSummary(
    val id: String,
    val name: String,
    val notes: String?,
    val source: RoutineSource,
    val exerciseNames: List<String>,
    /** When a workout was last started from it, if ever. */
    val lastUsedAt: Long?,
)

/** One exercise in a saved workout, with what to aim for in each set. */
@Serializable
data class RoutineExercise(
    val id: String,
    val exerciseId: String,
    val name: String,
    val measurementType: MeasurementType,
    val targetSets: Int,
    /** Reps, weight or time to aim for; blanks are left for the user (or last time) to fill. */
    val target: SetValues = SetValues(),
    /** Rest after each set in seconds, 0 for none, or null to use the default setting. */
    val restSeconds: Int? = null,
) {
    companion object {
        const val MIN_SETS = 1
        const val MAX_SETS = 20
        const val DEFAULT_SETS = 3
    }
}

/**
 * A saved workout to start from. Serializable so the editor can keep an unsaved one when the
 * system kills the app in the background.
 */
@Serializable
data class Routine(
    val id: String,
    val name: String,
    val notes: String?,
    val source: RoutineSource,
    val exercises: List<RoutineExercise>,
) {
    /** Only the user's own workouts can be changed; ready-made ones are copied first. */
    val isEditable: Boolean get() = source == RoutineSource.User

    val totalSets: Int get() = exercises.sumOf { it.targetSets }

    /**
     * A new workout of the user's own with the same plan, not yet saved. Every row gets a new ID
     * so the copy and the original never share one.
     */
    fun copyAsNew(name: String = this.name): Routine = Routine(
        id = Ids.newId(),
        name = name,
        notes = notes,
        source = RoutineSource.User,
        exercises = exercises.map { it.copy(id = Ids.newId()) },
    )
}

data class Settings(
    val weightUnit: WeightUnit = WeightUnit.Kilograms,
    val restSeconds: Int = DEFAULT_REST_SECONDS,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 90
        const val MAX_REST_SECONDS = 600

        /** The rest times offered in menus; 0 is off. */
        val REST_OPTIONS = listOf(0, 15, 30, 45, 60, 75, 90, 120, 150, 180, 240, 300)
    }
}
