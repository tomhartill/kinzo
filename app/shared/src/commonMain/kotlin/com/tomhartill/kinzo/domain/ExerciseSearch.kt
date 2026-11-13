package com.tomhartill.kinzo.domain

/** Filters and ranks the exercise library for the search box. */
object ExerciseSearch {
    /**
     * Every word in [query] must appear in the exercise's name, one of its other names
     * (aliases), its group's name or aliases, its equipment or its muscles, in any order.
     * Punctuation is optional, so "pullup", "pull up" and "pull-up" all find the Pull-Up.
     *
     * Name matches rank first: names starting with the query, then names containing it, then
     * names containing every word. An alias match ranks just below the same kind of name match,
     * so "military press" puts the Overhead Press first. Then come exercises whose group
     * matches, so "overhead press" goes on to list every variation, and then the rest. Ties
     * keep alphabetical order.
     */
    fun filter(
        exercises: List<ExerciseSummary>,
        query: String,
        muscle: Muscle? = null,
        equipmentId: String? = null,
    ): List<ExerciseSummary> {
        val needle = query.trim().lowercase().replace(WHITESPACE, " ")
        val words = needle.split(' ').filter(String::isNotEmpty)
        return exercises
            .asSequence()
            .filter { muscle == null || muscle in it.primaryMuscles || muscle in it.secondaryMuscles }
            .filter { equipmentId == null || it.equipmentId == equipmentId }
            .mapNotNull { exercise ->
                if (words.isEmpty()) return@mapNotNull exercise to 0
                rank(exercise, needle, words)?.let { exercise to it }
            }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
    }

    /** Lower is better; null when [words] don't all match. */
    private fun rank(exercise: ExerciseSummary, needle: String, words: List<String>): Int? {
        val name = exercise.name.lowercase()
        val aliases = exercise.aliases.map(String::lowercase)
        val groupTerms = (listOfNotNull(exercise.groupName) + exercise.groupAliases).map(String::lowercase)
        val haystack = buildString {
            (listOf(name) + aliases + groupTerms).forEach { append(it).append(' ').append(compact(it)).append(' ') }
            exercise.equipmentName?.let { append(it.lowercase()).append(' ') }
            (exercise.primaryMuscles + exercise.secondaryMuscles).forEach { append(it.label.lowercase()).append(' ') }
        }
        if (!words.all { it in haystack }) return null
        nameRank(name, needle, words)?.let { return it * 2 }
        aliases.mapNotNull { nameRank(it, needle, words) }.minOrNull()?.let { return it * 2 + 1 }
        if (groupTerms.any { nameRank(it, needle, words) != null }) return GROUP_RANK
        return GROUP_RANK + 1
    }

    /** 0 when [text] starts with the query, 1 when it contains it, 2 when it has every word. */
    private fun nameRank(text: String, needle: String, words: List<String>): Int? {
        val compactText = compact(text)
        val compactNeedle = compact(needle)
        return when {
            text.startsWith(needle) || compactText.startsWith(compactNeedle) -> 0
            needle in text || compactNeedle in compactText -> 1
            words.all { it in text || compact(it) in compactText } -> 2
            else -> null
        }
    }

    /** Letters and digits only, so "pull-up" and "pull up" both read "pullup". */
    private fun compact(text: String): String = text.filter(Char::isLetterOrDigit)

    private val WHITESPACE = Regex("\\s+")

    /** After every name and alias rank (0 to 5). */
    private const val GROUP_RANK = 6
}
