package id.bits.box.widget

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import id.bits.box.R
import id.bits.box.bg.BaseService
import id.bits.box.database.DataStore
import id.bits.box.ktx.*
import id.bits.box.ui.MainActivity
import id.bits.box.utils.formatTraffic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StatsBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private lateinit var statusText: TextView
    private lateinit var txText: TextView
    private lateinit var rxText: TextView
    lateinit var pingText: TextView

    var allowShow = true
    private var animating = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (isInEditMode) return
        statusText = findViewById(R.id.status)
        txText = findViewById(R.id.tx)
        rxText = findViewById(R.id.rx)
        pingText = findViewById(R.id.ping)

        setOnClickListener {
            if (DataStore.serviceState.connected) testConnection()
        }
    }

    fun setStatus(text: CharSequence) {
        statusText.text = text
        TooltipCompat.setTooltipText(this, text.toString())
    }

    fun showPing(text: CharSequence) {
        pingText.text = text
        pingText.visibility = View.VISIBLE
        statusText.visibility = View.INVISIBLE
        pingText.alpha = 0f
        pingText.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        postDelayed({
            pingText.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    pingText.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                }
                .start()
        }, 3000)
    }

    fun hidePing() {
        pingText.visibility = View.GONE
        statusText.visibility = View.VISIBLE
    }

    private fun resolveActivity(): MainActivity? {
        var ctx: Context? = context
        while (ctx != null) {
            if (ctx is MainActivity) return ctx
            ctx = if (ctx is ContextWrapper) ctx.baseContext else null
        }
        return null
    }

    fun changeState(state: BaseService.State) {
        val activity = resolveActivity() ?: return
        fun postWhenStarted(what: () -> Unit) = activity.lifecycleScope.launch(Dispatchers.Main) {
            delay(100L)
            activity.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                what()
            }
        }
        when (state) {
            BaseService.State.Connected -> {
                postWhenStarted {
                    txText.visibility = View.VISIBLE
                    rxText.visibility = View.VISIBLE
                    setStatus(app.getText(R.string.vpn_connected))
                }
                hidePing()
            }
            BaseService.State.Connecting -> {
                postWhenStarted {
                    txText.visibility = View.GONE
                    rxText.visibility = View.GONE
                    setStatus(app.getText(R.string.connecting))
                }
                hidePing()
            }
            BaseService.State.Stopping -> {
                setStatus(app.getText(R.string.stopping))
                txText.visibility = View.GONE
                rxText.visibility = View.GONE
                hidePing()
            }
            BaseService.State.Stopped -> {
                setStatus(app.getText(R.string.not_connected))
                txText.visibility = View.GONE
                rxText.visibility = View.GONE
                hidePing()
            }
            else -> {
                setStatus(app.getText(R.string.not_connected))
                txText.visibility = View.GONE
                rxText.visibility = View.GONE
                hidePing()
            }
        }
    }

    fun updateSpeed(txRate: Long, rxRate: Long) {
        val tx = context.getString(R.string.speed, txRate.formatTraffic())
        val rx = context.getString(R.string.speed, rxRate.formatTraffic())
        txText.text = "▲  $tx"
        rxText.text = "▼  $rx"
        txText.isVisible = txRate > 0L
        rxText.isVisible = rxRate > 0L
    }

    fun testConnection() {
        val activity = resolveActivity() ?: return
        val statusView = statusText
        statusView.isEnabled = false
        setStatus(activity.getText(R.string.connection_test_testing))
        runOnDefaultDispatcher {
            try {
                val elapsed = activity.urlTest()
                onMainDispatcher {
                    statusView.isEnabled = true
                    showPing(activity.getString(
                        if (DataStore.connectionTestURL.startsWith("https://")) {
                            R.string.connection_test_available
                        } else {
                            R.string.connection_test_available_http
                        }, elapsed
                    ))
                }
            } catch (e: Exception) {
                Logs.w(e.toString())
                onMainDispatcher {
                    statusView.isEnabled = true
                    showPing(e.readableMessage)
                    activity.snackbar(
                        activity.getString(
                            R.string.connection_test_error, e.readableMessage
                        )
                    ).show()
                }
            }
        }
    }

    fun performShow() {
        if (!allowShow || animating || isShown) return
        animating = true
        visibility = View.VISIBLE
        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { animating = false }
            .start()
    }

    fun performHide() {
        if (animating || !isShown) return
        animating = true
        animate()
            .translationY(height.toFloat())
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                animating = false
                visibility = View.GONE
            }
            .start()
    }

}
