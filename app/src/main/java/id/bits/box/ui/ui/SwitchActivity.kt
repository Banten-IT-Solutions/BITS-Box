package id.bits.box.ui

import android.os.Bundle
import id.bits.box.R
import id.bits.box.BitsBoxApp
import id.bits.box.database.DataStore
import id.bits.box.database.ProfileManager
import id.bits.box.ktx.runOnMainDispatcher

class SwitchActivity : ThemedActivity(R.layout.layout_empty),
    ConfigurationFragment.SelectCallback {

    override val isDialog = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_holder,
                ConfigurationFragment(true, null, R.string.action_switch)
            )
            .commitAllowingStateLoss()
    }

    override fun returnProfile(profileId: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = profileId
        runOnMainDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(profileId, true)
        }
        BitsBoxApp.reloadService()
        finish()
    }
}