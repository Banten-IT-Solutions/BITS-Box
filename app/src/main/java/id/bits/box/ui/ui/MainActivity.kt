package id.bits.box.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.preference.PreferenceDataStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import id.bits.box.BuildConfig
import id.bits.box.GroupType
import id.bits.box.Key
import id.bits.box.R
import id.bits.box.BitsBoxApp
import id.bits.box.aidl.IBitsBoxService
import id.bits.box.aidl.SpeedDisplayData
import id.bits.box.aidl.TrafficData
import id.bits.box.bg.BaseService
import id.bits.box.bg.BitsBoxConnection
import id.bits.box.database.DataStore
import id.bits.box.database.GroupManager
import id.bits.box.database.ProfileManager
import id.bits.box.database.ProxyGroup
import id.bits.box.database.SubscriptionBean
import id.bits.box.database.preference.OnPreferenceDataStoreChangeListener
import id.bits.box.databinding.LayoutMainBinding
import id.bits.box.fmt.AbstractBean
import id.bits.box.fmt.KryoConverters
import id.bits.box.group.GroupInterfaceAdapter
import id.bits.box.group.GroupUpdater
import id.bits.box.ktx.alert
import id.bits.box.ktx.getColorAttr
import id.bits.box.ktx.isPreview
import id.bits.box.ktx.onMainDispatcher
import id.bits.box.ktx.parseProxies
import id.bits.box.ktx.readableMessage
import id.bits.box.ktx.runOnDefaultDispatcher
import id.bits.box.utils.Util

    class MainActivity : ThemedActivity(),
    BitsBoxConnection.Callback,
    OnPreferenceDataStoreChangeListener {

    lateinit var binding: LayoutMainBinding

    // guards against setSelectedItemId re-triggering the item-selected listener
    private var updatingNav = false
    private var currentNavId = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutMainBinding.inflate(layoutInflater)

        // BottomNavigationView
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (updatingNav) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_more -> showOverflowMenu()
                R.id.nav_connect -> {
                    if (DataStore.serviceState.canStop) BitsBoxApp.stopService() else connect.launch(
                        null
                    )
                    selectNavItem(currentNavId)
                }
                else -> {
                    currentNavId = item.itemId
                    displayFragmentWithId(item.itemId)
                }
            }
            true
        }

        if (savedInstanceState == null) {
            displayFragmentWithId(R.id.nav_configuration)
        }
        onBackPressedDispatcher.addCallback {
            if (supportFragmentManager.findFragmentById(R.id.fragment_holder) is ConfigurationFragment) {
                moveTaskToBack(true)
            } else {
                displayFragmentWithId(R.id.nav_configuration)
            }
        }

        setContentView(binding.root)
        changeState(BaseService.State.Idle)
        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)
        GroupManager.userInterface = GroupInterfaceAdapter(this)

        if (intent?.action == Intent.ACTION_VIEW) {
            onNewIntent(intent)
        }

        refreshNavMenu(DataStore.enableClashAPI)

        // sdk 33 notification
        if (Build.VERSION.SDK_INT >= 33) {
            val checkPermission =
                ContextCompat.checkSelfPermission(this@MainActivity, POST_NOTIFICATIONS)
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                //动态申请
                ActivityCompat.requestPermissions(
                    this@MainActivity, arrayOf(POST_NOTIFICATIONS), 0
                )
            }
        }

        if (isPreview) {
            MaterialAlertDialogBuilder(this)
                .setTitle(BuildConfig.PRE_VERSION_NAME)
                .setMessage(R.string.preview_version_hint)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    fun refreshNavMenu(clashApi: Boolean) {
        // Dashboard always visible now, regardless of Clash API state
    }

    private fun showOverflowMenu() {
        // Anchor to the "More" item view so the popup opens at the right edge.
        val anchor = binding.bottomNav.findViewById<View>(R.id.nav_more) ?: binding.bottomNav
        val popup = PopupMenu(
            this,
            anchor,
            Gravity.NO_GRAVITY,
            0,
            R.style.Widget_BITSBox_PopupMenu_More
        )
        popup.menuInflater.inflate(R.menu.menu_overflow, popup.menu)
        // Force-show icons and tint them (plus the text) with mainIconText
        // so the menu always matches the active theme in both light & night mode.
        popup.setForceShowIcon(true)
        val textColor = getColorAttr(R.attr.mainIconText)
        for (i in 0 until popup.menu.size) {
            val item = popup.menu.get(i)
            item.icon?.mutate()?.setTint(textColor)
            item.title = SpannableString(item.title).apply {
                setSpan(ForegroundColorSpan(textColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        popup.setOnMenuItemClickListener { item ->
            displayFragmentWithId(item.itemId)
            true
        }
        popup.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data ?: return

        runOnDefaultDispatcher {
            if (uri.scheme == "bitsbox" && uri.host == "subscription" || uri.scheme == "clash") {
                importSubscription(uri)
            } else {
                importProfile(uri)
            }
        }
    }

    fun urlTest(): Int {
        if (!DataStore.serviceState.connected || connection.service == null) {
            error("not started")
        }
        return connection.service!!.urlTest()
    }

    suspend fun importSubscription(uri: Uri) {
        val group: ProxyGroup

        val url = uri.getQueryParameter("url")
        if (!url.isNullOrBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            val subscription = SubscriptionBean()
            group.subscription = subscription

            // cleartext format
            subscription.link = url
            group.name = uri.getQueryParameter("name")
        } else {
            val data = uri.encodedQuery.takeIf { !it.isNullOrBlank() } ?: return
            try {
                group = KryoConverters.deserialize(
                    ProxyGroup().apply { export = true }, Util.zlibDecompress(Util.b64Decode(data))
                ).apply {
                    export = false
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    alert(e.readableMessage).show()
                }
                return
            }
        }

        val name = group.name.takeIf { !it.isNullOrBlank() } ?: group.subscription?.link
        ?: group.subscription?.token
        if (name.isNullOrBlank()) return

        group.name = group.name.takeIf { !it.isNullOrBlank() }
            ?: ("Subscription #" + System.currentTimeMillis())

        onMainDispatcher {

            displayFragmentWithId(R.id.nav_group)

            MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.subscription_import)
                .setMessage(getString(R.string.subscription_import_message, name))
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        finishImportSubscription(group)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        }

    }

    private suspend fun finishImportSubscription(subscription: ProxyGroup) {
        GroupManager.createGroup(subscription)
        GroupUpdater.startUpdate(subscription, true)
    }

    suspend fun importProfile(uri: Uri) {
        val profile = try {
            parseProxies(uri.toString()).getOrNull(0) ?: error(getString(R.string.no_proxies_found))
        } catch (e: Exception) {
            onMainDispatcher {
                alert(e.readableMessage).show()
            }
            return
        }

        onMainDispatcher {
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.profile_import)
                .setMessage(getString(R.string.profile_import_message, profile.displayName()))
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        finishImportProfile(profile)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

    }

    private suspend fun finishImportProfile(profile: AbstractBean) {
        val targetId = DataStore.selectedGroupForImport()

        ProfileManager.createProfile(targetId, profile)

        onMainDispatcher {
            displayFragmentWithId(R.id.nav_configuration)

            snackbar(resources.getQuantityString(R.plurals.added, 1, 1)).show()
        }
    }

    @SuppressLint("CommitTransaction")
    fun displayFragment(fragment: ToolbarFragment) {
        if (fragment is ConfigurationFragment) {
            binding.stats.allowShow = true
            binding.stats.performShow()
        } else {
            binding.stats.allowShow = false
            binding.stats.performHide()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, fragment)
            .commitAllowingStateLoss()
    }

    fun displayFragmentWithId(@IdRes id: Int): Boolean {
        val actualId = when (id) {
            R.id.nav_home -> R.id.nav_configuration
            R.id.nav_dashboard -> R.id.nav_traffic
            R.id.nav_settings -> R.id.nav_settings
            R.id.nav_more -> return false
            R.id.nav_connect -> return false
            else -> id
        }
        when (actualId) {
            R.id.nav_configuration -> {
                displayFragment(ConfigurationFragment())
                selectNavItem(R.id.nav_home)
            }
            R.id.nav_group -> {
                displayFragment(GroupFragment())
                selectNavItem(R.id.nav_more)
            }
            R.id.nav_route -> {
                displayFragment(RouteFragment())
                selectNavItem(R.id.nav_more)
            }
            R.id.nav_settings -> {
                displayFragment(SettingsFragment())
                selectNavItem(R.id.nav_settings)
            }
            R.id.nav_traffic -> {
                displayFragment(WebviewFragment())
                selectNavItem(R.id.nav_dashboard)
            }
            R.id.nav_tools -> {
                displayFragment(BackupFragment())
                selectNavItem(R.id.nav_more)
            }
            R.id.nav_logcat -> {
                displayFragment(LogcatFragment())
                selectNavItem(R.id.nav_more)
            }
            R.id.nav_about -> {
                displayFragment(AboutFragment())
                selectNavItem(R.id.nav_more)
            }
            else -> return false
        }
        return true
    }

    private fun selectNavItem(@IdRes id: Int) {
        updatingNav = true
        binding.bottomNav.selectedItemId = id
        updatingNav = false
    }

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
    ) {
        DataStore.serviceState = state

        binding.stats.changeState(state)
        // Connect icon per state
        val iconRes = when (state) {
            BaseService.State.Connected -> R.drawable.ic_file_cloud_queue
            BaseService.State.Connecting, BaseService.State.Stopping -> R.drawable.ic_cloud_connecting
            else -> R.drawable.ic_action_lock
        }
        val connectItem = binding.bottomNav.menu?.findItem(R.id.nav_connect)
        connectItem?.setIcon(iconRes)
        // Override bottom nav itemIconTint for connect icon to preserve
        // state-specific rocket colors (multi-color vector)
        connectItem?.setIconTintList(null)
        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.coordinator, text, Snackbar.LENGTH_LONG).apply {
            anchorView = binding.bottomNav
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
    }

    val connection = BitsBoxConnection(BitsBoxConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    override fun onServiceConnected(service: IBitsBoxService) = changeState(
        try {
            BaseService.State.values()[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    )

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) snackbar(R.string.vpn_permission_denied).show()
    }

    // may NOT called when app is in background
    // ONLY do UI update here, write DB in bg process
    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        binding.stats.updateSpeed(stats.txRateProxy, stats.rxRateProxy)
    }

    override fun cbTrafficUpdate(data: TrafficData) {
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(data)
        }
    }

    override fun cbSelectorUpdate(id: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = id
        DataStore.currentProfile = id
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(id, true)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.SERVICE_MODE -> onBinderDied()
            Key.PROXY_APPS, Key.BYPASS_MODE, Key.INDIVIDUAL -> {
                if (DataStore.serviceState.canStop) {
                    snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
                        BitsBoxApp.reloadService()
                    }.show()
                }
            }
        }
    }

    override fun onStart() {
        connection.updateConnectionId(BitsBoxConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(BitsBoxConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        GroupManager.userInterface = null
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val fragment =
            supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
        return fragment != null && fragment.onKeyDown(keyCode, event)
    }

}
