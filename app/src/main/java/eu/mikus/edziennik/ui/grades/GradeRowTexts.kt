/*
 * Copyright (c) Mikolaj Olszewski 2026-7-16.
 */
package eu.mikus.edziennik.ui.grades

/**
 * Pure, Context-free derivation of the two text lines on an expanded grade row (ports GradeViewHolder):
 * description wins the top line; when blank the category is promoted up and the category field goes empty.
 * The "(poprawa)" improvement wrap is a string resource applied at the Composable edge, not here.
 */
data class GradeRowTexts(
    val topText: String,
    val categoryText: String,
    val categoryIsImprovement: Boolean,
)

fun gradeRowTexts(description: String?, category: String?, isImprovement: Boolean): GradeRowTexts {
    val descBlank = description.isNullOrBlank()
    return GradeRowTexts(
        topText = if (descBlank) (category ?: "") else description,
        categoryText = if (descBlank) "" else (category ?: ""),
        categoryIsImprovement = isImprovement,
    )
}
