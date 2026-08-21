@file:Suppress("EXPERIMENTAL_API_USAGE")

package id.bits.box.ktx

import androidx.fragment.app.Fragment
import id.bits.box.BitsBoxApp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

fun block(block: suspend CoroutineScope.() -> Unit): suspend CoroutineScope.() -> Unit {
    return block
}

fun runOnDefaultDispatcher(block: suspend CoroutineScope.() -> Unit) =
    BitsBoxApp.application.applicationScope.launch(Dispatchers.Default, block = block)

fun Fragment.runOnLifecycleDispatcher(block: suspend CoroutineScope.() -> Unit) =
    lifecycleScope.launch(Dispatchers.Default, block = block)

suspend fun <T> onDefaultDispatcher(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.Default, block = block)

fun runOnIoDispatcher(block: suspend CoroutineScope.() -> Unit) =
    BitsBoxApp.application.applicationScope.launch(Dispatchers.IO, block = block)

suspend fun <T> onIoDispatcher(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.IO, block = block)

fun runOnMainDispatcher(block: suspend CoroutineScope.() -> Unit) =
    BitsBoxApp.application.applicationScope.launch(Dispatchers.Main.immediate, block = block)

suspend fun <T> onMainDispatcher(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.Main.immediate, block = block)

fun runBlockingOnMainDispatcher(block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        launch(Dispatchers.Main.immediate, block = block).join()
    }
}
