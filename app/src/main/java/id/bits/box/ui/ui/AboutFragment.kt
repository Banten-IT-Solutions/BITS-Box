package id.bits.box.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import com.google.android.material.card.MaterialCardView
import id.bits.box.BitsBoxApp
import id.bits.box.R
import id.bits.box.databinding.LayoutAboutBinding
import id.bits.box.ktx.app
import id.bits.box.ktx.dp2px
import id.bits.box.ktx.launchCustomTab
import id.bits.box.widget.ListListener
import libcore.Libcore

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutAboutBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(view, ListListener)
        toolbar.setTitle(R.string.menu_about)
        setNavigationIcon(R.drawable.ic_baseline_info_24)

        val list = binding.versionList
        // Use the fragment context (activity theme) so ?attr/mainIconText and
        // ?attr/ContentIconText in the item layouts resolve against the active
        // color theme. The application context uses Theme.Start, which does not
        // define those custom attrs and would render the items with wrong colors.
        val ctx = requireContext()

        // App version
        list.item(
            ctx, R.drawable.ic_baseline_info_24,
            R.string.app_version,
            BitsBoxApp.appVersionNameForDisplay,
        )

        // sing-box core version (multi-line value)
        list.item(
            ctx, R.drawable.ic_baseline_info_24,
            R.string.about_sing_box,
            Libcore.versionBox(),
        )
        list.linkItem(
            ctx, R.drawable.ic_baseline_link_24,
            R.string.about_website,
            ctx.getString(R.string.about_website_value),
            ctx.getString(R.string.about_website_url),
        )
        list.linkItem(
            ctx, R.drawable.ic_baseline_link_24,
            R.string.about_github,
            ctx.getString(R.string.about_github_value),
            ctx.getString(R.string.about_github_url),
        )

        // Battery optimization (now part of About list, with icon)
        val canRequestBatteryExemption = !(app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(app.packageName)
        if (canRequestBatteryExemption) {
            list.item(
                ctx, R.drawable.ic_baseline_info_24,
                R.string.ignore_battery_optimizations,
                ctx.getString(R.string.ignore_battery_optimizations_sum),
                clickable = true,
                onClick = {
                    // BatteryLifetime: VPN keeps persistent connections, so allow the user to opt out of Doze
                    @SuppressLint("BatteryLife")
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            "package:${app.packageName}".toUri()
                        )
                    )
                }
            )
        }
    }
}

/** Inflate a non-clickable info row (icon + label + value), optionally clickable. */
private fun LinearLayout.item(
    ctx: Context,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    value: String,
    clickable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val row = View.inflate(ctx, R.layout.layout_about_list_item, null) as MaterialCardView
    row.findViewById<ImageView>(R.id.icon).setImageResource(icon)
    row.findViewById<TextView>(R.id.label).text = ctx.getString(label)
    val valueView = row.findViewById<TextView>(R.id.value)
    val trailing = row.findViewById<ImageView>(R.id.trailing)
    if (value.isNotBlank()) {
        valueView.visibility = View.VISIBLE
        valueView.text = value
    } else {
        valueView.visibility = View.GONE
    }
    trailing.visibility = if (clickable) View.VISIBLE else View.GONE
    row.isClickable = clickable
    row.isFocusable = clickable
    if (clickable && onClick != null) {
        row.setOnClickListener { onClick() }
    }
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.setMargins(0, dp2px(8), 0, dp2px(8))
    addView(row, lp)
}

/** Inflate a clickable link row (icon + label + value + trailing arrow). */
private fun LinearLayout.linkItem(
    ctx: Context,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    value: String,
    url: String,
) {
    val row = View.inflate(ctx, R.layout.layout_about_list_item, null) as MaterialCardView
    row.findViewById<ImageView>(R.id.icon).setImageResource(icon)
    row.findViewById<TextView>(R.id.label).text = ctx.getString(label)
    val valueView = row.findViewById<TextView>(R.id.value)
    val trailing = row.findViewById<ImageView>(R.id.trailing)
    if (value.isNotBlank()) {
        valueView.visibility = View.VISIBLE
        valueView.text = value
    } else {
        valueView.visibility = View.GONE
    }
    trailing.visibility = View.VISIBLE
    row.setOnClickListener { ctx.launchCustomTab(url) }
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.setMargins(0, dp2px(8), 0, dp2px(8))
    addView(row, lp)
}
