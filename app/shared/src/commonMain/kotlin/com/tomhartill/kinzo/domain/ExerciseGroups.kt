package com.tomhartill.kinzo.domain

/** One line of the exercise list: an exercise on its own, or a group of variations. */
sealed interface ExerciseListRow {
    val key: String
    val name: String

    data class Single(val exercise: ExerciseSummary) : ExerciseListRow {
        override val key: String get() = exercise.id
        override val name: String get() = exercise.name
    }

    /** Variations of one movement, such as every bench press, in alphabetical order. */
    data class Group(
        val id: String,
        override val name: String,
        val icon: String?,
        val exercises: List<ExerciseSummary>,
    ) : ExerciseListRow {
        override val key: String get() = "group-$id"
    }
}

object ExerciseGroups {
    /**
     * Folds [exercises] into rows: each group of variations becomes one row, placed by the
     * group's name among the other exercises. A group left with one exercise (after a filter,
     * say) shows that exercise on its own.
     */
    fun rows(exercises: List<ExerciseSummary>): List<ExerciseListRow> {
        val grouped = exercises.filter { it.groupId != null }.groupBy { it.groupId!! }
        val rows = mutableListOf<ExerciseListRow>()
        val seenGroups = mutableSetOf<String>()
        for (exercise in exercises) {
            val groupId = exercise.groupId
            val members = groupId?.let { grouped[it] }
            if (groupId == null || members == null || members.size < 2) {
                rows += ExerciseListRow.Single(exercise)
            } else if (seenGroups.add(groupId)) {
                rows += ExerciseListRow.Group(
                    id = groupId,
                    name = exercise.groupName ?: exercise.name,
                    icon = exercise.groupIcon ?: exercise.icon,
                    exercises = members.sortedBy { it.name.lowercase() },
                )
            }
        }
        return rows.sortedBy { it.name.lowercase() }
    }
}
