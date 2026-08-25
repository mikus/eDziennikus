/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-11.
 */
package eu.mikus.edziennik

import android.widget.TextView
import androidx.databinding.BindingAdapter

object Binding {
    private fun resizeDrawable(textView: TextView, index: Int, size: Int) {
        val drawables = textView.compoundDrawables
        drawables[index]?.setBounds(0, 0, size, size)
        textView.setCompoundDrawables(drawables[0], drawables[1], drawables[2], drawables[3])
    }

    @JvmStatic
    @BindingAdapter("android:drawableLeftAutoSize")
    fun drawableLeftAutoSize(textView: TextView, enable: Boolean) = resizeDrawable(
        textView,
        index = 0,
        size = textView.textSize.toInt(),
    )
}
