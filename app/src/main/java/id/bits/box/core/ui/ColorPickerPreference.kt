package id.bits.box.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.bits.box.R
import id.bits.box.ktx.getColorAttr
import kotlin.math.roundToInt

class ColorPickerPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    )
) : Preference(
    context, attrs, defStyle
) {

    var inited = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame = holder.findViewById(android.R.id.widget_frame) as LinearLayout

        if (!inited) {
            inited = true

            widgetFrame.addView(
                getBitsBoxImageViewAtColor(
                    context.getColorAttr(R.attr.colorPrimary),
                    48,
                    0
                )
            )
            widgetFrame.visibility = View.VISIBLE
        }
    }

    fun getBitsBoxImageViewAtColor(color: Int, sizeDp: Int, paddingDp: Int): ImageView {
        // dp to pixel
        val factor = context.resources.displayMetrics.density
        val size = (sizeDp * factor).roundToInt()
        val paddingSize = (paddingDp * factor).roundToInt()

        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setPadding(paddingSize)
            setImageDrawable(getBitsBoxAtColor(resources, color))
        }
    }

    fun getBitsBoxAtColor(res: Resources, color: Int): Drawable {
        val bitsBoxDrawable = ResourcesCompat.getDrawable(
            res,
            R.drawable.ic_baseline_fiber_manual_record_24,
            null
        )!!
        DrawableCompat.setTint(bitsBoxDrawable.mutate(), color)
        return bitsBoxDrawable
    }

    private fun isNightMode(): Boolean {
        val nightMode = AppCompatDelegate.getDefaultNightMode()
        return when (nightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
    }

    override fun onClick() {
        super.onClick()

        lateinit var dialog: AlertDialog

        val isNight = isNightMode()
        val colors = context.resources.getIntArray(if (isNight) R.array.material_colors_night else R.array.material_colors)

        val grid = GridLayout(context).apply {
            columnCount = 4

            for (i in colors.indices) {
                val themeId = i + 1 // Theme.kt constants are 1-based
                val color = colors[i]

                val view = getBitsBoxImageViewAtColor(color, 64, 0).apply {
                    setOnClickListener {
                        persistInt(themeId)
                        dialog.dismiss()
                        callChangeListener(themeId)
                    }
                    contentDescription = if (isNight) "Night mode" else "Light mode"
                }
                addView(view)
            }
        }

        dialog = MaterialAlertDialogBuilder(context).setTitle(title)
            .setView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(grid)
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
