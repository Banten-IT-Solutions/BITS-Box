package id.bits.box.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import id.bits.box.BitsBoxApp
import id.bits.box.ktx.Logs
import java.io.File

// BITS Box Application Class

const val KB = 1024L
const val MB = KB * 1024
const val GB = MB * 1024

fun BitsBoxApp.cleanWebview() {
    var pathToClean = "app_webview"
    if (isBgProcess) pathToClean += "_$process"
    try {
        val dataDir = filesDir.parentFile!!
        File(dataDir, "$pathToClean/BrowserMetrics").recreate(true)
        File(dataDir, "$pathToClean/BrowserMetrics-spare.pma").recreate(false)
    } catch (e: Exception) {
        Logs.e(e)
    }
}

fun File.recreate(dir: Boolean) {
    if (parentFile?.isDirectory != true) return
    if (dir && !isFile) {
        if (exists()) deleteRecursively()
        createNewFile()
    } else if (!dir && !isDirectory) {
        if (exists()) delete()
        mkdir()
    }
}

// Context utils

@SuppressLint("DiscouragedApi")
fun Context.getDrawableByName(name: String?): Drawable? {
    val resourceId: Int = resources.getIdentifier(name, "drawable", packageName)
    return AppCompatResources.getDrawable(this, resourceId)
}

// Traffic display

fun Long.toBytesString(): String {
    val size = this.toDouble()
    return when {
        this >= GB -> "%.2f GiB".format(size / GB)
        this >= MB -> "%.2f MiB".format(size / MB)
        this >= KB -> "%.2f KiB".format(size / KB)
        else -> "$this Bytes"
    }
}

// List

fun String.listByLineOrComma(): List<String> {
    return this.split(",","\n").map { it.trim() }.filter { it.isNotEmpty() }
}
