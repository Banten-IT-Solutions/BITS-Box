package id.bits.box.ui

import id.bits.box.ui.ToolbarFragment
import androidx.fragment.app.Fragment

abstract class NamedFragment : ToolbarFragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    private val name by lazy { name0() }
    fun name() = name
    protected abstract fun name0(): String

}
