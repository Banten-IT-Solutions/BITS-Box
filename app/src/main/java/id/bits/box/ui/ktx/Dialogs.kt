package id.bits.box.ktx

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.bits.box.R

fun Context.alert(text: String): AlertDialog {
    return MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .create()
}

/** Modern confirmation dialog with a colored icon header. */
fun Context.confirmDialog(
    @DrawableRes icon: Int,
    @ColorRes iconColorRes: Int,
    title: String,
    message: String,
    positive: String,
    negative: String? = null,
    onPositive: () -> Unit,
): AlertDialog {
    val view = LayoutInflater.from(this).inflate(R.layout.layout_dialog_icon_header, null)
    val iconColor = resolveColor(iconColorRes)
    view.findViewById<TextView>(R.id.dialog_title).text = title
    view.findViewById<TextView>(R.id.dialog_message).text = message
    view.findViewById<ImageView>(R.id.dialog_icon).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(iconColor)
    }
    view.findViewById<MaterialCardView>(R.id.dialog_icon_circle).setCardBackgroundColor(
        Color.argb(0x1F, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor))
    )
    val builder = MaterialAlertDialogBuilder(this)
        .setView(view)
        .setPositiveButton(positive) { _, _ -> onPositive() }
    if (negative != null) {
        builder.setNegativeButton(negative, null)
    }
    return builder.create()
}

/**
 * Resolves a color resource that may be a literal color or a reference to a theme
 * attribute (e.g. `<color name="x">?attr/colorPrimary</color>`), which
 * [ContextCompat.getColor] alone cannot resolve and would throw
 * [android.content.res.Resources.NotFoundException].
 */
private fun Context.resolveColor(@ColorRes colorRes: Int): Int {
    val value = TypedValue()
    resources.getValue(colorRes, value, true)
    if (value.type == TypedValue.TYPE_ATTRIBUTE) {
        check(theme.resolveAttribute(value.data, value, true)) {
            "Theme attribute not defined for color 0x${Integer.toHexString(colorRes)}"
        }
    }
    return when {
        value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> value.data
        value.resourceId != 0 -> ContextCompat.getColor(this, value.resourceId)
        else -> ContextCompat.getColor(this, colorRes)
    }
}

fun Fragment.alert(text: String) = requireContext().alert(text)

fun AlertDialog.tryToShow() {
    try {
        val activity = context as Activity
        if (!activity.isFinishing) {
            show()
        }
    } catch (e: Exception) {
        Logs.e(e)
    }
}
