package id.bits.box.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import id.bits.box.R
import id.bits.box.widget.ListListener

class SettingsFragment : ToolbarFragment(R.layout.layout_config_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view, ListListener)
        toolbar.setTitle(R.string.settings)
        setNavigationIcon(R.drawable.ic_action_settings)

        parentFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsPreferenceFragment())
            .commitAllowingStateLoss()
    }

}