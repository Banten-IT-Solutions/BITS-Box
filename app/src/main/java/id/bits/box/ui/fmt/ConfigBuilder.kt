package id.bits.box.fmt

import android.widget.Toast
import id.bits.box.*
import id.bits.box.bg.VpnService
import id.bits.box.database.DataStore
import id.bits.box.database.ProxyEntity
import id.bits.box.database.ProxyEntity.Companion.TYPE_CONFIG
import id.bits.box.database.BitsBoxDatabase
import id.bits.box.fmt.ConfigBuildResult.IndexEntity
import id.bits.box.fmt.internal.ChainBean
import id.bits.box.fmt.shadowsocks.ShadowsocksBean
import id.bits.box.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import id.bits.box.fmt.v2ray.StandardV2RayBean
import id.bits.box.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import id.bits.box.ktx.isIpAddress
import id.bits.box.ktx.Logs
import id.bits.box.ktx.mkPort
import id.bits.box.utils.PackageCache
import id.bits.box.SingBoxOptions.*
import id.bits.box.BitsBoxApp
import id.bits.box.proxy.config.ConfigBean
import id.bits.box.utils.JavaUtil.gson
import id.bits.box.utils.Util
import id.bits.box.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.net.URI

const val TAG_MIXED = "mixed-in"

const val TAG_PROXY = "proxy"
const val TAG_DIRECT = "direct"
const val TAG_BYPASS = "bypass"
const val TAG_BLOCK = "block"

const val LOCALHOST = "127.0.0.1"

class ConfigBuildResult(
    var config: String,
    var externalIndex: List<IndexEntity>,
    var mainEntId: Long,
    var trafficMap: Map<String, List<ProxyEntity>>,
    var profileTagMap: Map<Long, String>,
    val selectorGroupId: Long,
) {
    data class IndexEntity(var chain: LinkedHashMap<Int, ProxyEntity>)
}

fun buildConfig(
    proxy: ProxyEntity, forTest: Boolean = false, forExport: Boolean = false
): ConfigBuildResult {

    if (proxy.type == TYPE_CONFIG) {
        val bean = proxy.requireBean() as ConfigBean
        if (bean.type == 0) {
            return ConfigBuildResult(
                bean.config,
                listOf(),
                proxy.id, //
                mapOf(TAG_PROXY to listOf(proxy)), //
                mapOf(proxy.id to TAG_PROXY), //
                -1L
            )
        }
    }

    val trafficMap = HashMap<String, List<ProxyEntity>>()
    val tagMap = HashMap<Long, String>()
    val globalOutbounds = HashMap<Long, String>()
    val selectorNames = ArrayList<String>()
    val group = BitsBoxDatabase.groupDao.getById(proxy.groupId)

    fun ProxyEntity.resolveChainInternal(): MutableList<ProxyEntity> {
        val bean = requireBean()
        if (bean is ChainBean) {
            val beans = BitsBoxDatabase.proxyDao.getEntities(bean.proxies)
            val beansMap = beans.associateBy { it.id }
            val beanList = ArrayList<ProxyEntity>()
            for (proxyId in bean.proxies) {
                val item = beansMap[proxyId] ?: continue
                beanList.addAll(item.resolveChainInternal())
            }
            return beanList.asReversed()
        }
        return mutableListOf(this)
    }

    fun selectorName(name_: String): String {
        var name = name_
        var count = 0
        while (selectorNames.contains(name)) {
            count++
            name = "$name_-$count"
        }
        selectorNames.add(name)
        return name
    }

    fun ProxyEntity.resolveChain(): MutableList<ProxyEntity> {
        val thisGroup = BitsBoxDatabase.groupDao.getById(groupId)
        val frontProxy = thisGroup?.frontProxy?.let { BitsBoxDatabase.proxyDao.getById(it) }
        val landingProxy = thisGroup?.landingProxy?.let { BitsBoxDatabase.proxyDao.getById(it) }
        val list = resolveChainInternal()
        if (frontProxy != null) {
            list.add(frontProxy)
        }
        if (landingProxy != null) {
            list.add(0, landingProxy)
        }
        return list
    }

    val extraRules = if (forTest) listOf() else BitsBoxDatabase.rulesDao.enabledRules()
    val extraProxies =
        if (forTest) mapOf() else BitsBoxDatabase.proxyDao.getEntities(extraRules.mapNotNull { rule ->
            rule.outbound.takeIf { it > 0 && it != proxy.id }
        }.toHashSet().toList()).associateBy { it.id }
    val buildSelector = !forTest && group?.isSelector == true && !forExport
    val userDNSRuleList = mutableListOf<DNSRule_DefaultOptions>()
    val domainListDNSDirectForce = mutableListOf<String>()
    val bypassDNSBeans = hashSetOf<AbstractBean>()
    val isVPN = DataStore.serviceMode == Key.MODE_VPN
    val bind = if (!forTest && DataStore.allowAccess) "0.0.0.0" else LOCALHOST
    val remoteDns = DataStore.remoteDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val directDNS = DataStore.directDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val enableDnsRouting = DataStore.enableDnsRouting
    val useFakeDns = DataStore.enableFakeDns && !forTest
    val needSniff = DataStore.trafficSniffing > 0
    val externalIndexMap = ArrayList<IndexEntity>()
    val ipv6Mode = if (forTest) IPv6Mode.ENABLE else DataStore.ipv6Mode

    fun genDomainStrategy(noAsIs: Boolean): String {
        return when {
            !noAsIs -> ""
            ipv6Mode == IPv6Mode.DISABLE -> "ipv4_only"
            ipv6Mode == IPv6Mode.PREFER -> "prefer_ipv6"
            ipv6Mode == IPv6Mode.ONLY -> "ipv6_only"
            else -> "prefer_ipv4"
        }
    }

    fun typedDnsServer(
        tag: String,
        address: String,
        detour: String? = null,
        resolver: String? = null,
        strategy: String? = null,
    ): DNSServerOptions {
        val uri = URI(if (address.contains("://")) address else "udp://$address")
        val type = when (uri.scheme) {
            "h3" -> "http3"
            else -> uri.scheme
        }
        require(type in setOf("udp", "tcp", "tls", "quic", "https", "http3")) {
            "Unsupported DNS scheme: ${uri.scheme}"
        }
        return DNSServerOptions().apply {
            this.tag = tag
            this.type = type
            server = uri.host ?: throw IllegalArgumentException("Invalid DNS server: $address")
            if (uri.port != -1) server_port = uri.port
            if ((type == "https" || type == "http3") && uri.path != "/dns-query") path = uri.path
            this.detour = detour
            if (resolver != null || !strategy.isNullOrBlank()) {
                domain_resolver = buildMap {
                    resolver?.let { put("server", it) }
                    strategy?.takeIf { it.isNotBlank() }?.let { put("strategy", it) }
                }
            }
        }
    }

    return MyOptions().apply {
        if (!forTest) experimental = (experimental ?: ExperimentalOptions()).apply {
            if (DataStore.enableClashAPI) {
                clash_api = ClashAPIOptions().apply {
                    external_controller = "127.0.0.1:9090"
                    external_ui = "../files/yacd"
                }
            }
            // Persist fakeip (and other) mappings across restarts so that
            // apps using a previously-allocated fakeip address are still
            // unmapped to their domain instead of failing with
            // "missing fakeip record, try enable `experimental.cache_file`".
            cache_file = CacheFile().apply {
                enabled = true
                path = File(BitsBoxApp.application.filesDir, "cache.db").absolutePath
                cache_id = proxy.id.toString()
                store_fakeip = true
            }
        }

        log = LogOptions().apply {
            level = when (DataStore.logLevel) {
                0 -> "panic"
                1 -> "warn"
                2 -> "info"
                3 -> "debug"
                4 -> "trace"
                else -> "info"
            }
        }

        dns = DNSOptions().apply {
            servers = mutableListOf()
            rules = mutableListOf()
            independent_cache = true
        }

        fun autoDnsDomainStrategy(s: String): String? {
            if (s.isNotEmpty()) {
                return s
            }
            return when (ipv6Mode) {
                IPv6Mode.DISABLE -> "ipv4_only"
                IPv6Mode.ENABLE -> "prefer_ipv4"
                IPv6Mode.PREFER -> "prefer_ipv6"
                IPv6Mode.ONLY -> "ipv6_only"
                else -> null
            }
        }

        inbounds = mutableListOf()

        if (!forTest) {
            if (isVPN) inbounds.add(Inbound_TunOptions().apply {
                type = "tun"
                tag = "tun-in"
                // sing-box 1.6+: bind TUN to tun0 so auto_detect_interface has a target
                interface_name = "tun0"
                stack = when (DataStore.tunImplementation) {
                    TunImplementation.GVISOR -> "gvisor"
                    TunImplementation.SYSTEM -> "system"
                    else -> "mixed"
                }
                mtu = DataStore.mtu
                address = mutableListOf()
                when (ipv6Mode) {
                    IPv6Mode.DISABLE -> {
                        address = listOf(VpnService.PRIVATE_VLAN4_CLIENT + "/28")
                    }

                    IPv6Mode.ONLY -> {
                        address = listOf(VpnService.PRIVATE_VLAN6_CLIENT + "/126")
                    }

                    else -> {
                        address = listOf(
                            VpnService.PRIVATE_VLAN4_CLIENT + "/28",
                            VpnService.PRIVATE_VLAN6_CLIENT + "/126"
                        )
                    }
                }
            })
            inbounds.add(Inbound_MixedOptions().apply {
                type = "mixed"
                tag = TAG_MIXED
                listen = bind
                listen_port = DataStore.mixedPort
            })
        }

        outbounds = mutableListOf()

        // init routing object
        route = RouteOptions().apply {
            rules = mutableListOf()
            rule_set = mutableListOf()
            // auto_detect_interface is required on Android: it makes sing-box
            // enable the platform protect hook (AutoDetectInterfaceControl ->
            // VpnService.protect) so DNS/proxy-server sockets escape the TUN
            // and go over the real network. A fixed "default_interface" value
            // (e.g. "tun0") must NOT be set: it would bind outbound sockets to
            // the VPN device which has no internet route. Interface enumeration
            // must not depend on Go netlink (EPERM for normal apps); libcore
            // falls back to /sys/class/net via procfs (see procfs/interfaces.go).
            auto_detect_interface = true
        }

        // returns outbound tag
        fun buildChain(
            chainId: Long, entity: ProxyEntity
        ): String {
            val profileList = entity.resolveChain()
            val chainTrafficSet = HashSet<ProxyEntity>().apply {
                plusAssign(profileList)
                add(entity)
            }

            var currentOutbound: SingBoxOption
            lateinit var pastOutbound: SingBoxOption
            lateinit var pastInboundTag: String
            var pastEntity: ProxyEntity? = null
            val externalChainMap = LinkedHashMap<Int, ProxyEntity>()
            externalIndexMap.add(IndexEntity(externalChainMap))
            val chainOutbounds = ArrayList<SingBoxOption>()

            // chainTagOut: v2ray outbound tag for this chain
            var chainTagOut = ""
            val chainTag = "c-$chainId"
            var muxApplied = false

            val defaultServerDomainStrategy = SingBoxOptionsUtil.domainStrategy("server")

            profileList.forEachIndexed { index, proxyEntity ->
                val bean = proxyEntity.requireBean()

                // tagOut: v2ray outbound tag for a profile
                // profile2 (in) (global)   tag g-(id)
                // profile1                 tag (chainTag)-(id)
                // profile0 (out)           tag (chainTag)-(id) / single: "proxy"
                var tagOut = "$chainTag-${proxyEntity.id}"

                // needGlobal: can only contain one?
                var needGlobal = false

                // first profile set as global
                if (index == profileList.lastIndex) {
                    needGlobal = true
                    tagOut = "g-" + proxyEntity.id
                    bypassDNSBeans += proxyEntity.requireBean()
                }

                // last profile set as "proxy"
                if (chainId == 0L && index == 0) {
                    tagOut = TAG_PROXY
                }

                // selector human readable name
                if (buildSelector && index == 0) {
                    tagOut = selectorName(bean.displayName())
                }


                // chain rules
                if (index > 0) {
                    // chain route/proxy rules
                    if (pastEntity!!.needExternal()) {
                        route.rules.add(Rule_DefaultOptions().apply {
                            inbound = listOf(pastInboundTag)
                            outbound = tagOut
                        })
                    } else {
                        pastOutbound._hack_config_map["detour"] = tagOut
                    }
                } else {
                    // index == 0 means last profile in chain / not chain
                    chainTagOut = tagOut
                }

                // now tagOut is determined
                if (needGlobal) {
                    globalOutbounds[proxyEntity.id]?.let {
                        if (index == 0) chainTagOut = it // single, duplicate chain
                        return@forEachIndexed
                    }
                    globalOutbounds[proxyEntity.id] = tagOut
                }

                if (proxyEntity.needExternal()) { // externel outbound
                    val localPort = mkPort()
                    externalChainMap[localPort] = proxyEntity
                    currentOutbound = Outbound_SocksOptions().apply {
                        type = "socks"
                        server = LOCALHOST
                        server_port = localPort
                    }
                } else {
                    // internal outbound

                    currentOutbound = when (bean) {
                        is ConfigBean -> CustomSingBoxOption(bean.config)

                        is StandardV2RayBean -> // http/trojan/vmess/vless
                            buildSingBoxOutboundStandardV2RayBean(bean, defaultServerDomainStrategy)

                        is ShadowsocksBean ->
                            buildSingBoxOutboundShadowsocksBean(bean, defaultServerDomainStrategy)

                        else -> throw IllegalStateException("can't reach")
                    }

                    // internal mux
                    if (!muxApplied) {
                        val muxObj = proxyEntity.singMux()
                        if (muxObj != null && muxObj.enabled) {
                            muxApplied = true
                            currentOutbound._hack_config_map["multiplex"] = muxObj.asMap()
                        }
                    }
                }

                // internal & external
                currentOutbound.apply {
                    // udp over tcp
                    try {
                        val field = bean.javaClass.getDeclaredField("sUoT")
                        field.isAccessible = true
                        val sUoT = field.get(bean)
                        if (sUoT is Boolean && sUoT) {
                            _hack_config_map["udp_over_tcp"] = true
                        }
                    } catch (e: NoSuchFieldException) {
                        // Expected: not all beans have sUoT field
                    } catch (e: Exception) {
                        Logs.w("Hack field sUoT failed: ${e.message}")
                    }

                    pastEntity?.requireBean()?.apply {
                        // don't loopback
                        if (defaultServerDomainStrategy != "" && !serverAddress.isIpAddress()) {
                            domainListDNSDirectForce.add("full:$serverAddress")
                        }
                    }
                    _hack_config_map["tag"] = tagOut

                    _hack_custom_config = bean.customOutboundJson
                }

                // External proxy need a dokodemo-door inbound to forward the traffic
                // For external proxy software, their traffic must goes to v2ray-core to use protected fd.
                bean.finalAddress = bean.serverAddress
                bean.finalPort = bean.serverPort
                if (bean.canMapping() && proxyEntity.needExternal()) {
                    // With ss protect, don't use mapping
                    var needExternal = true
                    if (index == profileList.lastIndex) {
                        throw UnsupportedOperationException("External plugins are not supported")
                    }
                    if (needExternal) {
                        val mappingPort = mkPort()
                        bean.finalAddress = LOCALHOST
                        bean.finalPort = mappingPort

                        inbounds.add(Inbound_DirectOptions().apply {
                            type = "direct"
                            listen = LOCALHOST
                            listen_port = mappingPort
                            tag = "$chainTag-mapping-${proxyEntity.id}"

                            override_address = bean.serverAddress
                            override_port = bean.serverPort

                            pastInboundTag = tag

                            // no chain rule and not outbound, so need to set to direct
                            if (index == profileList.lastIndex) {
                                route.rules.add(Rule_DefaultOptions().apply {
                                    inbound = listOf(tag)
                                    outbound = TAG_DIRECT
                                })
                            }
                        })
                    }
                }

                outbounds.add(currentOutbound)
                chainOutbounds.add(currentOutbound)
                pastOutbound = currentOutbound
                pastEntity = proxyEntity
            }

            trafficMap[chainTagOut] = chainTrafficSet.toList()
            return chainTagOut
        }

        // build outbounds
        if (buildSelector) {
            val list = group.id.let { BitsBoxDatabase.proxyDao.getByGroup(it) }
            list.forEach {
                tagMap[it.id] = buildChain(it.id, it)
            }
            outbounds.add(0, Outbound_SelectorOptions().apply {
                type = "selector"
                tag = TAG_PROXY
                default_ = tagMap[proxy.id]
                outbounds = tagMap.values.toList()
            })
        } else {
            buildChain(0, proxy)
        }
        // build outbounds from route item
        extraProxies.forEach { (key, p) ->
            tagMap[key] = buildChain(key, p)
        }

        // apply user rules
        for (rule in extraRules) {
            if (rule.packages.isNotEmpty()) {
                PackageCache.awaitLoadSync()
            }
            val uidList = rule.packages.map {
                if (!isVPN) {
                    Toast.makeText(
                        BitsBoxApp.application,
                        BitsBoxApp.application.getString(R.string.route_need_vpn, rule.displayName()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                PackageCache[it]?.takeIf { uid -> uid >= 1000 }
            }.toHashSet().filterNotNull()
            val ruleSets = mutableListOf<RuleSet>()

            val ruleObj = Rule_DefaultOptions().apply {
                if (uidList.isNotEmpty()) {
                    PackageCache.awaitLoadSync()
                    user_id = uidList
                }
                var domainList: List<String>? = null
                if (rule.domains.isNotBlank()) {
                    domainList = rule.domains.listByLineOrComma()
                    makeSingBoxRule(domainList, false)
                }
                if (rule.ip.isNotBlank()) {
                    makeSingBoxRule(rule.ip.listByLineOrComma(), true)
                }

                if (rule_set != null) generateRuleSet(rule_set, ruleSets)

                if (rule.port.isNotBlank()) {
                    port = mutableListOf<Int>()
                    port_range = mutableListOf<String>()
                    rule.port.listByLineOrComma().map {
                        if (it.contains(":")) {
                            port_range.add(it)
                        } else {
                            it.toIntOrNull()?.apply { port.add(this) }
                        }
                    }
                }
                if (rule.sourcePort.isNotBlank()) {
                    source_port = mutableListOf<Int>()
                    source_port_range = mutableListOf<String>()
                    rule.sourcePort.listByLineOrComma().map {
                        if (it.contains(":")) {
                            source_port_range.add(it)
                        } else {
                            it.toIntOrNull()?.apply { source_port.add(this) }
                        }
                    }
                }
                if (rule.network.isNotBlank()) {
                    network = listOf(rule.network)
                }
                if (rule.source.isNotBlank()) {
                    source_ip_cidr = rule.source.listByLineOrComma()
                }
                if (rule.protocol.isNotBlank()) {
                    protocol = rule.protocol.listByLineOrComma()
                }

                fun makeDnsRuleObj(): DNSRule_DefaultOptions {
                    return DNSRule_DefaultOptions().apply {
                        if (uidList.isNotEmpty()) user_id = uidList
                        domainList?.let { makeSingBoxRule(it) }
                    }
                }

                when (rule.outbound) {
                    -1L -> {
                        userDNSRuleList += makeDnsRuleObj().apply { server = "dns-direct" }
                    }

                    0L -> {
                        if (useFakeDns) userDNSRuleList += makeDnsRuleObj().apply {
                            server = "dns-fake"
                            inbound = listOf("tun-in")
                        }
                        userDNSRuleList += makeDnsRuleObj().apply {
                            server = "dns-remote"
                        }
                    }

                    -2L -> {
                        userDNSRuleList += makeDnsRuleObj().apply {
                            action = "reject"
                        }
                    }
                }

                outbound = when (val outId = rule.outbound) {
                    0L -> TAG_PROXY
                    -1L -> TAG_BYPASS
                    -2L -> TAG_BLOCK
                    else -> if (outId == proxy.id) TAG_PROXY else tagMap[outId] ?: ""
                }

                _hack_custom_config = rule.config
            }

            if (!ruleObj.checkEmpty()) {
                if (ruleObj.outbound.isNullOrBlank()) {
                    Toast.makeText(
                        BitsBoxApp.application,
                        "Warning: " + rule.displayName() + ": A non-existent outbound was specified.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // block 改用新的写法
                    if (ruleObj.outbound == TAG_BLOCK) {
                        ruleObj.outbound = null
                        ruleObj.action = "reject"
                    }
                    route.rules.add(ruleObj)
                    route.rule_set.addAll(ruleSets)
                }
            }
        }

        // 对 rule_set tag 去重
        if (route.rule_set != null) {
            route.rule_set = route.rule_set.distinctBy { it.tag }
        }

        // The protect server (unix socket "protect_path") only exists in the
        // :bg process, started by ProxyInstance.launch() → setAsMain() →
        // goServeProtect(true). Both processes resolve the relative socket
        // path to the same app-data directory, so a main-process server would
        // hijack the bg server's socket — never start one there.
        //
        // - :bg process (VpnService/ProxyService): set protect_path on the
        //   direct outbounds AND detour DNS to "direct". protect_path makes
        //   the outbound non-empty, which keeps sing-box's DetourDialer happy
        //   ("detour to an empty direct outbound makes no sense"), and routes
        //   every direct/DNS dial through VpnService.protect().
        // - Main process (url test without an active service): NO protect_path
        //   and NO DNS detour. sing-box's ProtectPath control fails hard when
        //   no server is listening, and a bare direct outbound would trip the
        //   empty-detour check. Protection there is handled by ProtectFunc →
        //   AutoDetectInterfaceControl, which ignores a missing server.
        val isBgProcess = BitsBoxApp.application.isBgProcess
        val dnsDetour = if (isBgProcess) TAG_DIRECT else null
        for (freedom in arrayOf(TAG_DIRECT, TAG_BYPASS)) outbounds.add(Outbound().apply {
            tag = freedom
            type = "direct"
            if (isBgProcess) {
                _hack_config_map["protect_path"] = "protect_path"
            }
        })

        // Bypass Lookup for the first profile
        bypassDNSBeans.forEach {
            var serverAddr = it.serverAddress

            if (it is ConfigBean) {
                var config = mutableMapOf<String, Any>()
                config = gson.fromJson(it.config, config.javaClass)
                config["server"]?.apply {
                    serverAddr = toString()
                }
            }

            if (!serverAddr.isIpAddress()) {
                domainListDNSDirectForce.add("full:${serverAddr}")
            }
        }

        remoteDns.forEach {
            var address = it
            if (address.contains("://")) {
                address = address.substringAfter("://")
            }
            "https://$address".toHttpUrlOrNull()?.apply {
                if (!host.isIpAddress()) {
                    domainListDNSDirectForce.add("full:$host")
                }
            }
        }

        // dns-local (1.1.1.1) is an IP so no domain_resolver needed, but its
        // UDP packets still need protect() to bypass TUN → detour to "direct".
        dns.servers.add(typedDnsServer("dns-local", "1.1.1.1", detour = dnsDetour))

        directDNS.firstOrNull().let {
            // dns-direct (HTTPS DoH) also needs protect() for its TLS dial.
            dns.servers.add(typedDnsServer(
                "dns-direct", it ?: throw Exception("No direct DNS, check your settings!"),
                detour = dnsDetour,
                resolver = "dns-local", strategy = autoDnsDomainStrategy(SingBoxOptionsUtil.domainStrategy("dns-direct"))
            ))
        }

        remoteDns.firstOrNull().let {
            // Always use direct DNS for urlTest; remote DNS goes through the proxy.
            if (!forTest) dns.servers.add(typedDnsServer(
                "dns-remote", it ?: throw Exception("No remote DNS, check your settings!"), TAG_PROXY,
                "dns-direct", autoDnsDomainStrategy(SingBoxOptionsUtil.domainStrategy("dns-remote"))
            ))
        }

        dns.final_ = if (forTest) "dns-direct" else "dns-remote"

        val serverDomainStrategy = SingBoxOptionsUtil.domainStrategy("server")
        if (!forTest && serverDomainStrategy.isNotBlank()) {
            route.default_domain_resolver = mapOf(
                "server" to "dns-direct"
            )
        }

        // dns object user rules
        if (enableDnsRouting) {
            userDNSRuleList.forEach {
                if (!it.checkEmpty()) dns.rules.add(it)
            }
        }

        if (forTest) {
            dns.rules = listOf()
        } else {
            // Migrate legacy inbound fields (sniff, domain_strategy) to rule actions
            val inboundTags = listOf("tun-in", TAG_MIXED)
            val inboundDomainStrategy = genDomainStrategy(DataStore.resolveDestination)

            if (needSniff) {
                route.rules.add(0, Rule_DefaultOptions().apply {
                    inbound = inboundTags
                    action = "sniff"
                })
            }
            if (inboundDomainStrategy.isNotEmpty()) {
                route.rules.add(0, Rule_DefaultOptions().apply {
                    inbound = inboundTags
                    action = "resolve"
                    strategy = inboundDomainStrategy
                })
            }

            // built-in DNS rules
            route.rules.add(0, Rule_DefaultOptions().apply {
                protocol = listOf("dns")
                action = "hijack-dns"
            })
            route.rules.add(0, Rule_DefaultOptions().apply {
                port = listOf(53)
                action = "hijack-dns"
            })
            if (DataStore.bypassLanInCore) {
                route.rules.add(Rule_DefaultOptions().apply {
                    outbound = TAG_BYPASS
                    ip_is_private = true
                })
            }
            // block mcast
            // NOTE: conditions inside a single rule are AND-ed, so ip_cidr and
            // source_ip_cidr together would never match (a multicast source is
            // practically never seen). Split into two rules to keep both checks.
            route.rules.add(Rule_DefaultOptions().apply {
                ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                action = "reject"
            })
            route.rules.add(Rule_DefaultOptions().apply {
                source_ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                action = "reject"
            })
            // FakeDNS obj
            if (useFakeDns) {
                dns.servers.add(DNSServerOptions().apply {
                    tag = "dns-fake"
                    type = "fakeip"
                    inet4_range = "198.18.0.0/15"
                    inet6_range = "fc00::/18"
                })
                dns.rules.add(DNSRule_DefaultOptions().apply {
                    inbound = listOf("tun-in")
                    server = "dns-fake"
                    disable_cache = true
                })
            }
            // force bypass (always top DNS rule)
            if (domainListDNSDirectForce.isNotEmpty()) {
                dns.rules.add(0, DNSRule_DefaultOptions().apply {
                    makeSingBoxRule(domainListDNSDirectForce.toHashSet().toList())
                    server = "dns-direct"
                })
            }
        }
        // Custom global config removed (crashes on click)
    }.let {
        val configMap = it.asMap()
        Util.mergeJSON(configMap, proxy.requireBean().customConfigJson)
        val configJson = gson.toJson(configMap)
        Logs.i("Sing-Box config: ${configJson.take(8000)}")
        ConfigBuildResult(
            configJson,
            externalIndexMap,
            proxy.id,
            trafficMap,
            tagMap,
            if (buildSelector) group.id else -1L
        )
    }

}
