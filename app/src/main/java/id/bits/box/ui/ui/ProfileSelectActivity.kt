package id.bits.box.ui

import android.content.Intent
import android.os.Bundle
import id.bits.box.R
import id.bits.box.database.ProxyEntity

class ProfileSelectActivity : ThemedActivity(R.layout.layout_empty),
    ConfigurationFragment.SelectCallback {

    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_PROFILE_ID = "id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selected = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_SELECTED, ProxyEntity::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra<ProxyEntity>(EXTRA_SELECTED)
        }

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_holder,
                ConfigurationFragment(true, selected, R.string.select_profile)
            )
            .commitAllowingStateLoss()
    }

    override fun returnProfile(profileId: Long) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_PROFILE_ID, profileId)
        })
        finish()
    }

}
