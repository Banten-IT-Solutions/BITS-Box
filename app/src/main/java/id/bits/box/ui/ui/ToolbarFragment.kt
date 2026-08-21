package id.bits.box.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import id.bits.box.R
import id.bits.box.ktx.getColorAttr

open class ToolbarFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    lateinit var toolbar: Toolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        // Nav drawer removed: no hamburger icon. Fragments that need a
        // navigation icon (e.g. close button) set it themselves.
    }

    /**
     * Sets the navigation icon tinted with the toolbar's actionBarTheme
     * colorControlNormal. Plain setNavigationIcon(resId) resolves the
     * drawable's ?colorControlNormal against the activity theme, which is
     * dark in light mode and invisible on the colored toolbar background.
     */
    protected fun setNavigationIcon(@DrawableRes icon: Int) {
        toolbar.setNavigationIcon(icon)
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(R.attr.actionBarTheme, tv, true)
        val tint = ContextThemeWrapper(requireContext(), tv.resourceId)
            .getColorAttr(R.attr.colorControlNormal)
        toolbar.navigationIcon?.setTint(tint)
    }

    open fun onKeyDown(ketCode: Int, event: KeyEvent) = false
    open fun onBackPressed(): Boolean = false
}
