package id.bits.box.fmt

import id.bits.box.database.ProxyEntity

object TypeMap : HashMap<String, Int>() {
    init {
        this["ss"] = ProxyEntity.TYPE_SS
        this["vmess"] = ProxyEntity.TYPE_VMESS
        this["trojan"] = ProxyEntity.TYPE_TROJAN
        this["trojan-go"] = ProxyEntity.TYPE_TROJAN_GO
        this["custom"] = ProxyEntity.TYPE_BITS
        this["config"] = ProxyEntity.TYPE_CONFIG
    }

    val reversed = HashMap<Int, String>()

    init {
        TypeMap.forEach { (key, type) ->
            reversed[type] = key
        }
    }

}