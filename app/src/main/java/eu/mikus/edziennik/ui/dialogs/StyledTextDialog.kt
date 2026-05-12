/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-11.
 */

package eu.mikus.edziennik.ui.dialogs

import android.content.res.ColorStateList
import android.text.Editable
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.StyledTextDialogBinding
import eu.mikus.edziennik.ui.dialogs.base.BindingDialog
import eu.mikus.edziennik.utils.DefaultTextStyles
import eu.mikus.edziennik.utils.Themes
import eu.mikus.edziennik.utils.managers.TextStylingManager.HtmlMode.SIMPLE
import eu.mikus.edziennik.utils.managers.TextStylingManager.StylingConfig

class StyledTextDialog(
    activity: AppCompatActivity,
    val initialText: Editable?,
    val onSuccess: (text: Editable) -> Unit,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<StyledTextDialogBinding>(activity, onShowListener, onDismissListener) {

    override val TAG = "StyledTextDialog"

    private lateinit var config: StylingConfig

    private val manager
        get() = app.textStylingManager

    override fun getTitleRes() = R.string.styled_text_dialog_title
    override fun inflate(layoutInflater: LayoutInflater) =
        StyledTextDialogBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.save
    override fun getNeutralButtonText() = R.string.cancel

    override suspend fun onPositiveClick(): Boolean {
        onSuccess(b.editText.text ?: SpannableStringBuilder(""))
        return DISMISS
    }

    override suspend fun onShow() {
        config = StylingConfig(
            editText = b.editText,
            fontStyleGroup = b.fontStyle.styles,
            fontStyleClear = b.fontStyle.clear,
            styles = DefaultTextStyles.getAsList(b.fontStyle),
            textHtml = if (App.devMode) b.htmlText else null,
            htmlMode = SIMPLE,
        )

        manager.attach(config)

        b.editText.text = initialText

        // this is awful
        if (Themes.isDark) {
            val colorStateList = ColorStateList.valueOf(0x40ffffff)
            b.fontStyle.bold.strokeColor = colorStateList
            b.fontStyle.italic.strokeColor = colorStateList
            b.fontStyle.underline.strokeColor = colorStateList
            b.fontStyle.strike.strokeColor = colorStateList
            b.fontStyle.subscript.strokeColor = colorStateList
            b.fontStyle.superscript.strokeColor = colorStateList
            b.fontStyle.clear.strokeColor = colorStateList
        }
    }
}

