package id.bits.box.bg.proto

import id.bits.box.database.DataStore
import id.bits.box.database.ProxyEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UrlTest {

    val link = DataStore.connectionTestURL
    private val timeout = 5000
    private val mutex = Mutex()

    suspend fun doTest(profile: ProxyEntity): Int {
        return mutex.withLock {
            TestInstance(profile, link, timeout).doTest()
        }
    }

}
