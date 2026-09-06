/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.grades

/**
 * Pure, Context-free derivation of the two text lines on an expanded grade row (ports GradeViewHolder):
 * description wins the top line; when blank the category is promoted up and the category field goes empty.
 * A replacing note's substitute text outranks both on the top line but never changes which field was
 * promoted — GradeViewHolder branches on the description first and only then applies the
 * substitute, so a note cannot make a described grade look like an undescribed one.
 * The "(poprawa)" improvement wrap is a string resource applied at the Composable edge, not here.
 * [topText] is a CharSequence so a Spanned note keeps its HTML formatting up to that edge.
 */
data class GradeRowTexts(
    val topText: CharSequence,
    val categoryText: String,
    val categoryIsImprovement: Boolean,
)

fun gradeRowTexts(
    description: String?,
    category: String?,
    isImprovement: Boolean,
    noteSubstitute: CharSequence? = null,
): GradeRowTexts {
    val descBlank = description.isNullOrBlank()
    return GradeRowTexts(
        topText = noteSubstitute ?: (if (descBlank) (category ?: "") else description),
        categoryText = if (descBlank) "" else (category ?: ""),
        categoryIsImprovement = isImprovement,
    )
}
