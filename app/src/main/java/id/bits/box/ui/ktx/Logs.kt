package id.bits.box.ktx

import libcore.Libcore
import java.io.InputStream
import java.io.OutputStream

object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    // level int use logrus.go

    fun d(message: String) {
        Libcore.bitsBoxLogPrintln("[Debug] [${mkTag()}] $message")
    }

    fun d(message: String, exception: Throwable) {
        Libcore.bitsBoxLogPrintln("[Debug] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun i(message: String) {
        Libcore.bitsBoxLogPrintln("[Info] [${mkTag()}] $message")
    }

    fun i(message: String, exception: Throwable) {
        Libcore.bitsBoxLogPrintln("[Info] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun w(message: String) {
        Libcore.bitsBoxLogPrintln("[Warning] [${mkTag()}] $message")
    }

    fun w(message: String, exception: Throwable) {
        Libcore.bitsBoxLogPrintln("[Warning] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun w(exception: Throwable) {
        Libcore.bitsBoxLogPrintln("[Warning] [${mkTag()}] " + exception.stackTraceToString())
    }

    fun e(message: String) {
        Libcore.bitsBoxLogPrintln("[Error] [${mkTag()}] $message")
    }

    fun e(message: String, exception: Throwable) {
        Libcore.bitsBoxLogPrintln("[Error] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun e(exception: Throwable) {
        Libcore.bitsBoxLogPrintln("[Error] [${mkTag()}] " + exception.stackTraceToString())
    }

}

fun InputStream.use(out: OutputStream) {
    use { input ->
        out.use { output ->
            input.copyTo(output)
        }
    }
}