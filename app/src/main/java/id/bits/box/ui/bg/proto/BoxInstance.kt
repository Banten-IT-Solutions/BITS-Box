package id.bits.box.bg.proto

import id.bits.box.BitsBoxApp
import id.bits.box.bg.AbstractInstance
import id.bits.box.bg.GuardedProcessPool
import id.bits.box.database.DataStore
import id.bits.box.database.ProxyEntity
import id.bits.box.fmt.ConfigBuildResult
import id.bits.box.fmt.buildConfig
import id.bits.box.ktx.*
import kotlinx.coroutines.*
import libcore.BoxInstance
import libcore.Libcore
import id.bits.box.net.LocalResolverImpl
import java.io.File

abstract class BoxInstance(
    val profile: ProxyEntity
) : AbstractInstance {

    lateinit var config: ConfigBuildResult
    lateinit var box: BoxInstance

    open lateinit var processes: GuardedProcessPool
    private var cacheFiles = ArrayList<File>()
    fun isInitialized(): Boolean {
        return ::config.isInitialized && ::box.isInitialized
    }

    protected open fun buildConfig() {
        config = buildConfig(profile)
    }

    protected open suspend fun loadConfig() {
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

    open suspend fun init() {
        buildConfig()
        check(config.externalIndex.all { it.chain.isEmpty() }) {
            "External plugins are not supported"
        }
        loadConfig()
    }

    override fun launch() {
        // Cache directory for temporary configurations (may move to a separate utility class later)
        val cacheDir = File(BitsBoxApp.application.cacheDir, "tmpcfg")
        cacheDir.mkdirs()

        box.start()
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun close() {
        cacheFiles.removeAll { it.delete(); true }

        if (::processes.isInitialized) processes.close(BitsBoxApp.application.applicationScope)

        if (::box.isInitialized) {
            box.close()
        }
    }

}
