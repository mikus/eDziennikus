/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-18.
 */

package eu.mikus.edziennik.ui.dialogs.base

import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.launch
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R

abstract class ConfigDialog<B : ViewBinding>(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : BindingDialog<B>(activity, onShowListener, onDismissListener) {

    final override fun getPositiveButtonText() = R.string.ok
    final override suspend fun onShow() = Unit

    protected val config by lazy { app.config.grades }

    protected open suspend fun loadConfig() = Unit
    protected open suspend fun saveConfig() = Unit
    protected open fun initView() = Unit

    final override suspend fun onBeforeShow(): Boolean {
        initView()
        loadConfig()
        return true
    }

    final override fun onDismiss() {
        launch {
            saveConfig()
        }
        if (reloadOnDismiss && activity is MainActivity)
            activity.reloadTarget()
    }
}
