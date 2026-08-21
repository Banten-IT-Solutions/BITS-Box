package id.bits.box.ui.profile

import id.bits.box.fmt.trojan.TrojanBean

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = TrojanBean()

}
