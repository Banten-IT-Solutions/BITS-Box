package id.bits.box

import android.content.Context
import id.bits.box.R
import id.bits.box.fmt.AbstractBean
import id.bits.box.ktx.app
import id.bits.box.proxy.config.ConfigBean

// Settings for all protocols, built-in or plugin
object Protocols {

    // Deduplication

    class Deduplication(
        val bean: AbstractBean, val type: String
    ) {

        fun hash(): String {
            if (bean is ConfigBean) {
                return bean.config
            }
            return bean.serverAddress + bean.serverPort + type
        }

        override fun hashCode(): Int {
            return hash().toByteArray().contentHashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Deduplication

            return hash() == other.hash()
        }

    }

    // Display

    fun Context.getProtocolColor(type: Int): Int {
        return getColor(R.color.material_cyan_500)
    }

    // Test

    fun genFriendlyMsg(msg: String): String {
        val msgL = msg.lowercase()
        return when {
            msgL.contains("timeout") || msgL.contains("deadline") -> {
                app.getString(R.string.connection_test_timeout)
            }

            msgL.contains("refused") || msgL.contains("closed pipe") -> {
                app.getString(R.string.connection_test_refused)
            }

            else -> msg
        }
    }

}