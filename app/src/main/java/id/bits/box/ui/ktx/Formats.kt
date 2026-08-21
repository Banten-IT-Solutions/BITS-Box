package id.bits.box.ktx

import com.google.gson.JsonParser
import id.bits.box.fmt.AbstractBean
import id.bits.box.fmt.Serializable
import id.bits.box.fmt.parseUniversal
import id.bits.box.fmt.shadowsocks.parseShadowsocks
import id.bits.box.fmt.trojan.parseTrojan
import id.bits.box.fmt.trojan_go.parseTrojanGo
import id.bits.box.fmt.v2ray.parseV2Ray
import id.bits.box.utils.JavaUtil.gson
import id.bits.box.utils.Util
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// JSON & Base64

fun JSONObject.toStringPretty(): String {
    return gson.toJson(JsonParser.parseString(this.toString()))
}

inline fun <reified T : Any> JSONArray.filterIsInstance(): List<T> {
    val list = mutableListOf<T>()
    for (i in 0 until this.length()) {
        if (this[i] is T) list.add(this[i] as T)
    }
    return list
}

inline fun JSONArray.forEach(action: (Int, Any) -> Unit) {
    for (i in 0 until this.length()) {
        action(i, this[i])
    }
}

inline fun JSONObject.forEach(action: (String, Any) -> Unit) {
    for (k in this.keys()) {
        action(k, this.get(k))
    }
}

fun isJsonObjectValid(j: Any): Boolean {
    if (j is JSONObject) return true
    if (j is JSONArray) return true
    try {
        JSONObject(j as String)
    } catch (ex: JSONException) {
        try {
            JSONArray(j)
        } catch (ex1: JSONException) {
            return false
        }
    }
    return true
}

// wtf hutool
fun JSONObject.getStr(name: String): String? {
    val obj = this.opt(name) ?: return null
    if (obj is String) {
        if (obj.isBlank()) {
            return null
        }
        return obj
    } else {
        return null
    }
}

fun JSONObject.getBool(name: String): Boolean? {
    return try {
        getBoolean(name)
    } catch (ignored: Exception) {
        null
    }
}


// 重名了喵
fun JSONObject.getIntNya(name: String): Int? {
    return try {
        getInt(name)
    } catch (ignored: Exception) {
        null
    }
}


fun String.decodeBase64UrlSafe(): String {
    return String(Util.b64Decode(this))
}

// Sub

class SubscriptionFoundException(val link: String) : RuntimeException()

suspend fun parseProxies(text: String): List<AbstractBean> {
    val links = text.split('\n').flatMap { it.trim().split(' ') }
    val linksByLine = text.split('\n').map { it.trim() }

    val entities = ArrayList<AbstractBean>()
    val entitiesByLine = ArrayList<AbstractBean>()

    fun String.parseLink(entities: ArrayList<AbstractBean>) {
        if (startsWith("clash://install-config?") || startsWith("bitsbox://subscription?")) {
            throw SubscriptionFoundException(this)
        }

        if (startsWith("bitsbox://")) {
            Logs.d("Try parse universal link: $this")
            runCatching {
                entities.add(parseUniversal(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("vmess://")) {
            Logs.d("Try parse v2ray link: $this")
            runCatching {
                entities.add(parseV2Ray(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("vless://")) {
            Logs.d("Try parse vless link: $this")
            runCatching {
                entities.add(parseV2Ray(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("trojan://")) {
            Logs.d("Try parse trojan link: $this")
            runCatching {
                entities.add(parseTrojan(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("trojan-go://")) {
            Logs.d("Try parse trojan-go link: $this")
            runCatching {
                entities.add(parseTrojanGo(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ss://")) {
            Logs.d("Try parse shadowsocks link: $this")
            runCatching {
                entities.add(parseShadowsocks(this))
            }.onFailure {
                Logs.w(it)
            }
        }
    }

    for (link in links) {
        link.parseLink(entities)
    }
    for (link in linksByLine) {
        link.parseLink(entitiesByLine)
    }
//    var isBadLink = false
    if (entities.onEach { it.initializeDefaultValues() }.size == entitiesByLine.onEach { it.initializeDefaultValues() }.size) run test@{
        entities.forEachIndexed { index, bean ->
            val lineBean = entitiesByLine[index]
            if (bean == lineBean && bean.displayName() != lineBean.displayName()) {
//                isBadLink = true
                return@test
            }
        }
    }
    return if (entities.size > entitiesByLine.size) entities else entitiesByLine
}

fun <T : Serializable> T.applyDefaultValues(): T {
    initializeDefaultValues()
    return this
}