/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.dialogs.base

import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.textfield.TextInputLayout
import eu.mikus.edziennik.App
import eu.mikus.edziennik.utils.TextInputKeyboardEdit
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase

/**
 * Compose bridge for the app's rich-text field. There is no native Compose rich-text editor; the
 * styling pipeline ([TextStylingManager.attachToField] + [TextStylingManager.getHtmlText]) is built
 * around a Material [TextInputLayout] wrapping a [TextInputKeyboardEdit], so this hosts exactly that
 * pair via [AndroidView] and hands the caller back a [StylingConfigBase] to read at save-time.
 *
 * The heavy wiring runs ONCE, in [AndroidView] `factory` (which is not re-invoked on recompose):
 * inflate the two views, attach the styling manager (installs the end-icon that opens
 * [StyledTextDialog]), set the hint, seed the field from [initialHtml] a single time (typically a
 * `BetterHtml.fromHtml(activity, html, nl2br = true)` result), build the [StylingConfigBase] and
 * publish it through [onConfigReady]. The caller stashes that config in a plain field — mirroring the
 * legacy `topicStylingConfig` / `bodyStylingConfig` / `stylingConfig` `lateinit` fields — and calls
 * `getHtmlText(config)` in its `onPositiveClick`. Because [onConfigReady] fires from `factory`, it
 * must only assign to such a field, never mutate Compose state.
 *
 * [error] is the inline error slot: it is pushed to [TextInputLayout.setError] from the `update`
 * block, so passing a non-null string on recompose shows the validation error (and `null` clears it),
 * replacing the legacy `layout.error = getString(...)` calls.
 *
 * Reusable by NoteEditor (topic + body, [HtmlMode.SIMPLE]) and EventManual (topic, [HtmlMode.SIMPLE]).
 */
@Composable
fun RichTextFieldBridge(
    app: App,
    activity: AppCompatActivity,
    hint: String,
    initialHtml: CharSequence?,
    htmlMode: HtmlMode,
    onConfigReady: (StylingConfigBase) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    minLines: Int = 1,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val layout = TextInputLayout(ctx).apply {
                // Outlined box to match the OutlinedBox.Dense style the legacy XML layouts used.
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                // attachToField() only sets the end-icon drawable + click listener; the custom mode
                // (app:endIconMode="custom" in the old XML) has to be enabled here so it renders.
                endIconMode = TextInputLayout.END_ICON_CUSTOM
                isHintEnabled = true
                this.hint = hint
            }
            val edit = TextInputKeyboardEdit(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                // All three rich-text fields (note topic/body, event topic) are declared
                // textMultiLine in the legacy layouts, so MULTI_LINE is always enabled; minLines
                // only raises the visible height for the taller body field.
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                if (minLines > 1) {
                    this.minLines = minLines
                    gravity = Gravity.TOP or Gravity.START
                }
            }
            // TextInputLayout intercepts the EditText child and hosts it in its input frame.
            layout.addView(edit)

            // Seed the field ONCE from the stored HTML. factory runs a single time, so this cannot
            // clobber user edits on recompose; the spans in `initialHtml` survive into the Editable.
            initialHtml?.let { edit.setText(it) }

            // Installs the end-icon that opens StyledTextDialog (full-screen styling editor).
            app.textStylingManager.attachToField(
                activity = activity,
                textLayout = layout,
                textEdit = edit,
                onShowListener = onShowListener,
                onDismissListener = onDismissListener,
            )

            // Hand the config back so the dialog can call getHtmlText(config) at save-time.
            onConfigReady(StylingConfigBase(editText = edit, htmlMode = htmlMode))

            layout
        },
        update = { layout ->
            // Inline error slot — set/clear on recompose.
            layout.error = error
            layout.isErrorEnabled = error != null
        },
    )
}
