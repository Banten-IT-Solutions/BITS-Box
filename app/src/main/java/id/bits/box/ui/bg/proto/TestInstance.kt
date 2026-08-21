package id.bits.box.bg.proto

import id.bits.box.BuildConfig
import id.bits.box.bg.GuardedProcessPool
import id.bits.box.database.ProxyEntity
import id.bits.box.fmt.buildConfig
import id.bits.box.ktx.Logs
import id.bits.box.ktx.runOnDefaultDispatcher
import id.bits.box.ktx.tryResume
import id.bits.box.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import id.bits.box.net.LocalResolverImpl
import kotlin.coroutines.suspendCoroutine

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    suspend fun doTest(): Int {
        return suspendCoroutine { c ->
            processes = GuardedProcessPool {
                Logs.w(it)
                c.tryResumeWithException(it)
            }
            runOnDefaultDispatcher {
                use {
                    try {
                        init()
                        launch()
                        if (processes.processCount > 0) {
                            // wait for plugin start
                            delay(500)
                        }
                        c.tryResume(Libcore.urlTest(box, link, timeout))
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                    }
                }
            }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        // don't call destroyAllJsi here
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

}
