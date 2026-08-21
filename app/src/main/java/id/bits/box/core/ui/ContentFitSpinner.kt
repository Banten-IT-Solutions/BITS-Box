package id.bits.box.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatSpinner
import id.bits.box.R

/**
 * Spinner whose closed-state width grows to fit the currently selected item.
 *
 * The platform [AppCompatSpinner] measures the item at position 0 when laid out with
 * WRAP_CONTENT, so a long selected value gets clipped even though the row has
 * room for it. This subclass instead measures the selected item's width so the
 * dropdown row never cuts off the current value.
 */
class ContentFitSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatSpinner(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.AT_MOST) {
            val adapter = adapter
            if (adapter != null && adapter.count > 0) {
                val selectedView = adapter.getView(selectedItemPosition, null, this)
                selectedView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val contentWidth = selectedView.measuredWidth +
                    paddingLeft + paddingRight +
                    resources.getDimensionPixelSize(R.dimen.spinner_arrow_allowance)
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                    heightMeasureSpec
                )
                return
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}