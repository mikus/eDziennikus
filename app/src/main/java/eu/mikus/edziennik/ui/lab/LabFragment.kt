/*
 * Copyright (c) Mikolaj Olszewski 2026-9-15.
 */

package eu.mikus.edziennik.ui.lab

import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chuckerteam.chucker.api.Chucker
import com.chuckerteam.chucker.api.Chucker.SCREEN_HTTP
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.R
import eu.mikus.edziennik.databinding.LabFragmentBinding
import eu.mikus.edziennik.ext.input
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.ProfileRemoveDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Hosts [LabScreen] and owns everything Compose must not: the edit dialog, the Chucker Intent, the
 * profile-clear dialog and the two process kills.
 */
class LabFragment : Fragment() {
    companion object {
        private const val TAG = "LabFragment"

        /** `LabPageFragment.kt@53a07964:176` polled the cookie readout every 300 ms. Same cadence. */
        private const val PollMs = 300L
    }

    private lateinit var app: App
    private lateinit var activity: AppCompatActivity
    private var b: LabFragmentBinding? = null
    private lateinit var viewModel: LabViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as? AppCompatActivity) ?: return null
        if (context == null) return null
        app = activity.application as App
        val binding = LabFragmentBinding.inflate(inflater, container, false)
        b = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = b ?: return
        if (!isAdded) return

        viewModel = ViewModelProvider(this, LabViewModel.Factory(app))[LabViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect(::handleEffect)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(PollMs)
                    viewModel.refreshPanel()
                }
            }
        }

        b.composeView.setAppThemeContent {
            val panel by viewModel.panel.collectAsStateWithLifecycle()
            val tree by viewModel.tree.collectAsStateWithLifecycle()
            LabScreen(
                panel = panel,
                tree = tree,
                onToggle = viewModel::onToggle,
                onAction = viewModel::onAction,
                onProfileSelected = { id -> (activity as? MainActivity)?.navigate(profileId = id) },
                onNodeClick = { node ->
                    when (node) {
                        is LabNode.Container -> viewModel.toggleNode(node.path)
                        is LabNode.Leaf -> viewModel.onLeafClick(node.path)
                    }
                },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        b = null
    }

    private fun handleEffect(effect: LabEffect) {
        when (effect) {
            is LabEffect.ParseFailed -> Toast.makeText(activity, effect.message, Toast.LENGTH_LONG).show()
            is LabEffect.OpenEditor -> showEditor(effect)
            LabEffect.RestartRequired -> restartDialog()
            LabEffect.OpenChucker -> startActivity(Chucker.getLaunchIntent(activity, SCREEN_HTTP))
            // The dialog is the confirm; it clears 23 tables plus the metadata rows itself, before
            // its own `if (noProfileRemoval)` guard. noProfileRemoval = true is load-bearing -
            // dropping it deletes the profile instead of clearing it.
            //
            // The id and the name arrive on the effect, so this host reads neither off App. Design D7
            // is exactly "pass the real profile name" - LabPageFragment.kt@53a07964:93 passed the literal
            // "FAKE", which the dialog interpolated into "Zamierzasz usunac profil FAKE" while wiping
            // the profile the operator was actually on - so that value belongs where a test sees it.
            is LabEffect.ConfirmClearProfile -> ProfileRemoveDialog(
                activity,
                effect.profileId,
                effect.profileName,
                noProfileRemoval = true,
                onRemove = { viewModel.onProfileCleared() },
            ).show()
            LabEffect.ConfirmDisableDevMode -> confirmDisableDevMode()
        }
    }

    private fun showEditor(effect: LabEffect.OpenEditor) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(effect.title)
            .input(
                hint = "value",
                value = effect.prefill,
                positiveButton = R.string.ok,
                positiveListener = { _, input ->
                    viewModel.writeValue(effect.path, input)
                    true
                },
            )
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * `LabPageFragment.kt@53a07964:113-122` and `:135-144` were byte-identical; this is that dialog, once.
     *
     * The kill is genuinely required: `App.enableChucker` and `App.devMode` are read at process start
     * (`App.kt:219-220`). Do **not** replace it with a graceful restart - that is a behaviour change
     * dressed as a migration, with no test behind it.
     */
    private fun restartDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Restart")
            .setMessage("Wymagany restart aplikacji")
            .setPositiveButton(R.string.ok) { _, _ ->
                Process.killProcess(Process.myPid())
                Runtime.getRuntime().exit(0)
                exitProcess(0)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Design D6: this one gets its own confirm, because it is the phase's own foot-gun. `App.kt:219`
     * reads `devMode = config.devMode ?: debugMode`, so once `config.devMode` is a non-null `false`
     * the `BuildConfig.DEBUG` fallback is bypassed forever, and the only re-enable
     * (`checkDevModePassword`, `App.kt:396`) is gated on `config.devModePassword`. The single writer
     * of that key is the v3 prefs migration (`AppConfigMigrationV3.kt:38`), which
     * `ConfigMigration.kt:18` gates on a legacy prefs key it deletes as it runs - so it cannot fire
     * again on a migrated device, and no in-app path re-enables. One mis-tap hides LAB on that device
     * until stored state is edited from outside the app: the config row keyed `"debugMode"`
     * (`Config.kt:38`) at `profileId = -1`, or the legacy prefs pair that replays the migration.
     */
    private fun confirmDisableDevMode() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Disable Dev Mode")
            .setMessage("This hides LAB on this device permanently. The only way back is the config row \"debugMode\" at profileId -1.")
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.confirmDisableDevMode() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
