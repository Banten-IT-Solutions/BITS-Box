package id.bits.box.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import id.bits.box.BuildConfig
import id.bits.box.R
import id.bits.box.BitsBoxApp
import id.bits.box.ktx.Logs
import id.bits.box.ktx.app
import id.bits.box.ktx.use
import id.bits.box.utils.CrashHandler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object SendLog {
    // Create full log and send
    fun sendLog(context: Context, title: String) {
        val logFile = File.createTempFile(
            "$title ",
            ".log",
            File(app.cacheDir, "log").also { it.mkdirs() })

        var report = CrashHandler.buildReportHeader()

        report += "Logcat: \n\n"

        logFile.writeText(report)

        try {
            Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.use(
                FileOutputStream(
                    logFile, true
                )
            )
            logFile.appendText("\n")
        } catch (e: IOException) {
            Logs.w(e)
            logFile.appendText("Export logcat error: " + CrashHandler.formatThrowable(e))
        }

        logFile.appendText("\n")
        logFile.appendBytes(getBITSBoxLog(0))

        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/x-log")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(
                        Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                            context, "${BuildConfig.APPLICATION_ID}.cache", logFile
                        )
                    ), context.getString(R.string.abc_shareactionprovider_share_with)
            )
        )
    }

    // Get log bytes from bitsbox.log
    fun getBITSBoxLog(max: Long): ByteArray {
        return try {
            val file = File(
                BitsBoxApp.application.cacheDir,
                "bitsbox.log"
            )
            val len = file.length()
            val stream = FileInputStream(file)
            val bytes = if (max in 1 until len) {
                stream.skip(len - max)
                ByteArrayOutputStream().use { buffer ->
                    val tempBuffer = ByteArray(8192)
                    var bytesRead: Int
                    while (stream.read(tempBuffer).also { bytesRead = it } != -1) {
                        buffer.write(tempBuffer, 0, bytesRead)
                    }
                    buffer.toByteArray()
                }
            } else {
                stream.use { it.readBytes() }
            }
            bytes
        } catch (e: Exception) {
            e.stackTraceToString().toByteArray()
        }
    }
}
