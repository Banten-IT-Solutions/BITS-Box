package id.bits.box.widget

import android.content.Context
import android.util.AttributeSet
import id.bits.box.R
import id.bits.box.database.BitsBoxDatabase
import id.bits.box.ui.SimpleMenuPreference

class GroupPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = R.attr.dropdownPreferenceStyle
) : SimpleMenuPreference(context, attrs, defStyle, 0) {

    init {
        val groups = BitsBoxDatabase.groupDao.allGroups()

        entries = groups.map { it.displayName() }.toTypedArray()
        entryValues = groups.map { "${it.id}" }.toTypedArray()
    }

    override fun getSummary(): CharSequence? {
        if (!value.isNullOrBlank() && value != "0") {
            return BitsBoxDatabase.groupDao.getById(value.toLong())?.displayName()
                ?: super.getSummary()
        }
        return super.getSummary()
    }

}