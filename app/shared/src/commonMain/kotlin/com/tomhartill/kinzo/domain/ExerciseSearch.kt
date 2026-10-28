package com.tomhartill.kinzo.domain

/** Filters and ranks the exercise library for the search box. */
object ExerciseSearch {
    /**
     * Every word in [query] must appear in the exercise's name, equipment or muscles, in any
     * order. Name matches rank first: names starting with the query, then names containing
     * it, then names containing every word, then the rest. Ties keep alphabetical order.
     */
    fun filter(
        exercises: List<ExerciseSummary>,
        query: String,
        muscle: Muscle? = null,
        equipmentId: String? = null,
    ): List<ExerciseSummary> {
        val needle = query.trim().lowercase()
        val words = needle.split(Regex("\\s+")).filter(String::isNotEmpty)
        return exercises
            .asSequence()
            .filter { muscle == null || muscle in it.primaryMuscles || muscle in it.secondaryMuscles }
            .filter { equipmentId == null || it.equipmentId == equipmentId }
            .mapNotNull { exercise ->
                if (words.isEmpty()) return@mapNotNull exercise to 0
                val name = exercise.name.lowercase()
                val haystack = buildString {
                    append(name)
                    exercise.equipmentName?.let { append(' ').append(it.lowercase()) }
                    (exercise.primaryMuscles + exercise.secondaryMuscles).forEach { append(' ').append(it.label.lowercase()) }
                }
                if (!words.all { it in haystack }) return@mapNotNull null
                val rank = when {
                    name.startsWith(needle) -> 0
                    needle in name -> 1
                    words.all { it in name } -> 2
                    else -> 3
                }
                exercise to rank
            }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
    }
}
