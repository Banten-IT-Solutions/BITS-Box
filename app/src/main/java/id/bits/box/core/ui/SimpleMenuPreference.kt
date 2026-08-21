/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package id.bits.box.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder
import id.bits.box.R
import id.bits.box.ktx.getColorAttr

/**
 * Bend [DropDownPreference] to support
 * [Simple Menus](https://material.google.com/components/menus.html#menus-behavior).
 */


open class SimpleMenuPreference
@JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dropdownPreferenceStyle,
    defStyleRes: Int = 0
) : DropDownPreference(context!!, attrs, defStyleAttr, defStyleRes) {

    private lateinit var mAdapter: SimpleMenuAdapter

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Spinner stays an invisible 0dp anchor so the popup opens aligned with
        // the icon + selected text instead of the screen edge.
        // No width override: layout_width="0dp" must be preserved.
    }

    override fun createAdapter(): ArrayAdapter<CharSequence?> {
        mAdapter = SimpleMenuAdapter(getContext(), R.layout.simple_menu_dropdown_item)
        return mAdapter
    }

    override fun setValue(value: String?) {
        super.setValue(value)
        if (::mAdapter.isInitialized) {
            mAdapter.currentPosition = entryValues.indexOf(value)
            mAdapter.notifyDataSetChanged()
        }
    }

    private class SimpleMenuAdapter(context: Context, resource: Int) :
        ArrayAdapter<CharSequence?>(context, resource) {

        var currentPosition = -1

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View = super.getDropDownView(position, convertView, parent)
            val bg = if (position == currentPosition) {
                context.getColorAttr(R.attr.colorMaterial100)
            } else {
                context.getColorAttr(R.attr.colorPrimary)
            }
            view.setBackgroundColor(bg)
            // Pick a readable text color for the actual background: black on light,
            // white on dark. Fixes white-on-light text in night mode where
            // colorMaterial100 stays a light pastel.
            (view as? TextView)?.setTextColor(
                if (ColorUtils.calculateLuminance(bg) > 0.5) Color.BLACK else Color.WHITE
            )
            return view
        }
    }
}