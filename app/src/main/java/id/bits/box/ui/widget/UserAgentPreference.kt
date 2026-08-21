package id.bits.box.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.EditTextPreference
import id.bits.box.R
import id.bits.box.ktx.USER_AGENT

class UserAgentPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = TypedArrayUtils.getAttr(
        context, R.attr.editTextPreferenceStyle, android.R.attr.editTextPreferenceStyle
    )
) : EditTextPreference(context, attrs, defStyle) {

    public override fun notifyChanged() {
        super.notifyChanged()
    }

    override fun getSummary(): CharSequence? {
        if (text.isNullOrBlank()) {
            return USER_AGENT
        }
        return super.getSummary()
    }

}