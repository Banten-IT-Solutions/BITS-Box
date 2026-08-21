package id.bits.box.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import id.bits.box.R

class MTUPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = R.attr.dropdownPreferenceStyle
) : ListPreference(context, attrs, defStyle, 0) {

    init {
        setSummaryProvider {
            value.toString()
        }
        dialogLayoutResource = R.layout.layout_mtu_help
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val itemView: View = holder.itemView
        itemView.setOnLongClickListener {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.layout_preference_edittext_dialog, null)
            view.findViewById<TextInputEditText>(android.R.id.edit).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setText(preferenceDataStore?.getString(key, "") ?: "")
            }

            MaterialAlertDialogBuilder(context).setTitle("MTU")
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val mtu = view.findViewById<TextInputEditText>(android.R.id.edit).text?.toString().orEmpty()
                        .toIntOrNull() ?: return@setPositiveButton
                    if (mtu < 1000 || mtu > 10000) return@setPositiveButton
                    value = mtu.toString()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

}
