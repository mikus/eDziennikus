/*
 * Copyright (c) Mikolaj Olszewski 2026-6-23.
 */

package eu.mikus.edziennik.ui.compose

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import eu.mikus.edziennik.ext.cleanDiacritics
import eu.mikus.edziennik.utils.span.BoldSpan
import eu.mikus.edziennik.utils.span.ItalicSpan
import eu.mikus.edziennik.utils.span.SubscriptSizeSpan
import eu.mikus.edziennik.utils.span.SuperscriptSizeSpan
import eu.mikus.edziennik.utils.span.UnderlineCustomSpan

/**
 * Converts a [Spanned] produced by `BetterHtml.fromHtml(context = null, …)` into a Compose
 * [AnnotatedString]. Maps the concrete custom span classes BetterHtml emits; unmapped/exotic spans
 * (e.g. `ImprovedBulletSpan`) drop to plain text. Inline `ForegroundColorSpan`s are mapped to a
 * Compose color (the legacy `TextView` paints them); note HTML is built with `context = null`, so
 * BetterHtml's color-contrast remap is skipped and colors render as authored.
 */
fun Spanned.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    val source = this@toAnnotatedString
    append(source.toString())
    for (span in source.getSpans(0, source.length, Any::class.java)) {
        val start = source.getSpanStart(span)
        val end = source.getSpanEnd(span)
        if (start < 0 || end <= start) continue
        val style = when (span) {
            is BoldSpan -> SpanStyle(fontWeight = FontWeight.Bold)
            is ItalicSpan -> SpanStyle(fontStyle = FontStyle.Italic)
            is UnderlineCustomSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
            is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            is ForegroundColorSpan -> SpanStyle(color = Color(span.foregroundColor))
            // sub/superscript: BaselineShift + a relative ~0.8em size approximates the legacy fixed 10dp
            // (SpanStyle has no absolute sp size without a density, which this non-composable helper lacks).
            is SubscriptSizeSpan -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.8.em)
            is SuperscriptSizeSpan -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.8.em)
            else -> null
        }
        if (style != null) addStyle(style, start, end)
    }
}

/**
 * Overlays a search highlight (bold + [highlightColor] background) onto every occurrence of [query]
 * in the receiver, preserving any existing styles. Mirrors the legacy `CharSequence.asSpannable`:
 * folds both sides with [cleanDiacritics] (length-preserving) for index-finding, applies the span to
 * the ORIGINAL text over `[index, index + query.length)`, `ignoreCase = true`, all occurrences.
 */
fun AnnotatedString.withSearchHighlight(query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return this
    val haystack = text.cleanDiacritics()
    val needle = query.cleanDiacritics()
    if (needle.isEmpty()) return this
    val builder = AnnotatedString.Builder(this)
    val style = SpanStyle(fontWeight = FontWeight.Bold, background = highlightColor)
    var index = haystack.indexOf(needle, startIndex = 0, ignoreCase = true)
    while (index >= 0) {
        builder.addStyle(style, index, index + query.length)
        index = haystack.indexOf(needle, startIndex = index + query.length.coerceAtLeast(1), ignoreCase = true)
    }
    return builder.toAnnotatedString()
}
