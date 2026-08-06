/*
 * Copyright (c) Mikolaj Olszewski 2026-7-24.
 */

package eu.mikus.edziennik.ui.dialogs.base

import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import eu.mikus.edziennik.App
import eu.mikus.edziennik.databinding.StyledTextButtonsBinding
import eu.mikus.edziennik.utils.DefaultTextStyles
import eu.mikus.edziennik.utils.TextInputKeyboardEdit
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfig
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfigBase

/**
 * How a [RichTextFieldBridge] wires styling. DIALOG = the end-icon StyledTextDialog (Note/EventManual);
 * INLINE = the styled_text_buttons toolbar under the field (message body, textStyling ON);
 * PLAIN = no styling affordance at all (message body, textStyling OFF).
 */
enum class RichTextStyling { DIALOG, INLINE, PLAIN }

/**
 * Compose bridge for the app's rich-text field. There is no native Compose rich-text editor; the
 * styling pipeline ([TextStylingManager.attachToField] + [TextStylingManager.getHtmlText]) is built
 * around a Material [TextInputLayout] wrapping a [TextInputKeyboardEdit], so this hosts exactly that
 * pair via [AndroidView] and hands the caller back a [StylingConfigBase] to read at save-time.
 *
 * [stylingMode] picks how the field offers styling:
 * - [RichTextStyling.DIALOG] — the pre-existing P18 behavior: outlined box plus the custom end-icon
 *   that opens the full-screen [StyledTextDialog] ([TextStylingManager.attachToField]). Published
 *   config is a plain [StylingConfigBase]. Used by NoteEditor (topic + body) and EventManual (topic).
 * - [RichTextStyling.INLINE] — filled box, no end-icon; the `styled_text_buttons` toolbar (format
 *   toggle group + clear button) is inflated and stacked under the field, and the richer
 *   [StylingConfig] is wired through [TextStylingManager.attach] and published through
 *   [onConfigReady]. Used by the message body when `messagesConfig.textStyling` is on.
 * - [RichTextStyling.PLAIN] — filled box, no end-icon and no toolbar; a [StylingConfigBase] is still
 *   published so the caller can convert the text with `getHtmlText(config)`. Used by the message body
 *   when `messagesConfig.textStyling` is off.
 *
 * The heavy wiring runs ONCE, in [AndroidView] `factory` (which is not re-invoked on recompose):
 * inflate the views, wire styling per [stylingMode], set the hint, seed the field from [initialHtml]
 * a single time (typically a `BetterHtml.fromHtml(activity, html, nl2br = true)` result), build the
 * config and publish it through [onConfigReady]. The caller stashes that config in a plain field —
 * mirroring the legacy `topicStylingConfig` / `bodyStylingConfig` / `stylingConfig` `lateinit`
 * fields — and calls `getHtmlText(config)` in its `onPositiveClick`. Because [onConfigReady] fires
 * from `factory`, it must only assign to such a field, never mutate Compose state.
 *
 * [error] is the inline error slot: it is pushed to [TextInputLayout.setError] from the `update`
 * block, so passing a non-null string on recompose shows the validation error (and `null` clears it),
 * replacing the legacy `layout.error = getString(...)` calls.
 *
 * [counterEnabled] + [counterMaxLength] turn on the visible character counter (the legacy
 * `counterMaxLength` set on the message subject/body layouts). [onChanged] fires after every text
 * change, for callers that dirty-track the field.
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
    stylingMode: RichTextStyling = RichTextStyling.DIALOG,
    counterEnabled: Boolean = false,
    counterMaxLength: Int = -1,
    onChanged: (() -> Unit)? = null,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) {
    // The TextInputLayout is NOT always the AndroidView root (in INLINE mode it is the first child of
    // a LinearLayout holding the field + the styling toolbar), so the reference is hoisted out of
    // `factory` in a one-slot holder — `update` needs it to push the inline error in every mode.
    val layoutRef = remember { arrayOfNulls<TextInputLayout>(1) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val layout = TextInputLayout(ctx).apply {
                // Outlined box to match the OutlinedBox.Dense style the legacy XML layouts used;
                // the message-compose layouts use the filled box, with the toolbar (if any) below.
                boxBackgroundMode = when (stylingMode) {
                    RichTextStyling.DIALOG -> TextInputLayout.BOX_BACKGROUND_OUTLINE
                    else -> TextInputLayout.BOX_BACKGROUND_FILLED
                }
                // attachToField() only sets the end-icon drawable + click listener; the custom mode
                // (app:endIconMode="custom" in the old XML) has to be enabled here so it renders.
                if (stylingMode == RichTextStyling.DIALOG)
                    endIconMode = TextInputLayout.END_ICON_CUSTOM
                isHintEnabled = true
                this.hint = hint
            }
            if (counterEnabled) {
                layout.isCounterEnabled = true
                layout.counterMaxLength = counterMaxLength
            }
            val edit = TextInputKeyboardEdit(ctx).apply {
                // TextInputLayout.addView() re-parents the EditText and copies the child's
                // layoutParams onto its internal inputFrame, which it later casts to
                // LinearLayout.LayoutParams — so the child MUST carry LinearLayout.LayoutParams.
                // A plain ViewGroup.LayoutParams (or the auto-generated default) → ClassCastException.
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
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
            // In PLAIN mode strip them first (legacy did `text.setText(text.toString())` when
            // textStyling was off) so a seeded reply quote isn't persisted as <b>/<i> in a draft.
            initialHtml?.let {
                edit.setText(if (stylingMode == RichTextStyling.PLAIN) it.toString() else it)
            }

            if (onChanged != null)
                edit.doAfterTextChanged { onChanged() }

            layoutRef[0] = layout

            val root: View = when (stylingMode) {
                RichTextStyling.DIALOG -> {
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
                }
                RichTextStyling.INLINE -> {
                    val buttons = StyledTextButtonsBinding.inflate(LayoutInflater.from(ctx))
                    val config = StylingConfig(
                        editText = edit,
                        fontStyleGroup = buttons.styles,
                        fontStyleClear = buttons.clear,
                        styles = DefaultTextStyles.getAsList(buttons),
                        textHtml = null,
                        htmlMode = htmlMode,
                    )
                    app.textStylingManager.attach(config)
                    // Format-only edits go through Editable.setSpan, which notifies SpanWatchers -
                    // NOT the TextWatcher above. Report them too, so a caller tracking "dirty" sees
                    // a bold/clear press (legacy: fontStyle.styles.addOnButtonCheckedListener).
                    // Format-only edits go through Editable.setSpan, which notifies SpanWatchers -
                    // NOT the TextWatcher above - so report a style press too (legacy did this with
                    // `fontStyle.styles.addOnButtonCheckedListener { changedBody = true }`).
                    // addOnButtonCheckedListener is additive, so this is safe. The CLEAR button is
                    // deliberately left alone: attach() SETS its listener (no public getter to wrap),
                    // so overriding it here would break clearing - a clear-only edit not marking the
                    // body dirty is the accepted residual gap.
                    onChanged?.let { changed ->
                        buttons.styles.addOnButtonCheckedListener { _, _, _ -> changed() }
                    }
                    // A StylingConfig IS-A StylingConfigBase, so the richer subtype travels through
                    // the same callback — the caller casts it back if it needs the toolbar bits.
                    onConfigReady(config)
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(layout)
                        addView(buttons.root)
                    }
                }
                RichTextStyling.PLAIN -> {
                    onConfigReady(StylingConfigBase(editText = edit, htmlMode = htmlMode))
                    layout
                }
            }
            root
        },
        update = {
            // Inline error slot — set/clear on recompose. Pushed onto the TextInputLayout, which is
            // not necessarily the root passed here (see layoutRef).
            layoutRef[0]?.let { layout ->
                layout.error = error
                layout.isErrorEnabled = error != null
            }
        },
    )
}
