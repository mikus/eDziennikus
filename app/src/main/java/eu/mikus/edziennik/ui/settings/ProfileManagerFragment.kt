package eu.mikus.edziennik.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.mikus.edziennik.App
import eu.mikus.edziennik.MainActivity
import eu.mikus.edziennik.databinding.FragmentProfileManagerBinding
import eu.mikus.edziennik.utils.Themes

class ProfileManagerFragment : Fragment() {

    private lateinit var app: App
    private lateinit var activity: MainActivity
    private lateinit var b: FragmentProfileManagerBinding
/*
    private val navController: NavController by lazy { Navigation.findNavController(b.root) }
*/

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as MainActivity?) ?: return null
        if (context == null)
            return null
        app = activity.application as App
        requireContext().theme.applyStyle(Themes.appTheme, true)
        // activity, context and profile is valid
        b = FragmentProfileManagerBinding.inflate(inflater)
        b.refreshLayout.setParent(activity.swipeRefreshLayout)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (app.profile == null || !isAdded)
            return


    }
}
