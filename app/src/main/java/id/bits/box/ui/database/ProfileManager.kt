package id.bits.box.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import id.bits.box.R
import id.bits.box.aidl.TrafficData
import id.bits.box.fmt.AbstractBean
import id.bits.box.ktx.Logs
import id.bits.box.ktx.app
import id.bits.box.ktx.applyDefaultValues
import java.io.IOException
import java.sql.SQLException


object ProfileManager {

    interface Listener {
        suspend fun onAdd(profile: ProxyEntity)
        suspend fun onUpdated(data: TrafficData)
        suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean)
        suspend fun onRemoved(groupId: Long, profileId: Long)
    }

    interface RuleListener {
        suspend fun onAdd(rule: RuleEntity)
        suspend fun onUpdated(rule: RuleEntity)
        suspend fun onRemoved(ruleId: Long)
        suspend fun onCleared()
    }

    private val listeners = ArrayList<Listener>()
    private val ruleListeners = ArrayList<RuleListener>()

    suspend fun iterator(what: suspend Listener.() -> Unit) {
        synchronized(listeners) {
            listeners.toList()
        }.forEach { listener ->
            what(listener)
        }
    }

    suspend fun ruleIterator(what: suspend RuleListener.() -> Unit) {
        val ruleListeners = synchronized(ruleListeners) {
            ruleListeners.toList()
        }
        for (listener in ruleListeners) {
            what(listener)
        }
    }

    fun addListener(listener: Listener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun addListener(listener: RuleListener) {
        synchronized(ruleListeners) {
            ruleListeners.add(listener)
        }
    }

    fun removeListener(listener: RuleListener) {
        synchronized(ruleListeners) {
            ruleListeners.remove(listener)
        }
    }

    suspend fun createProfile(groupId: Long, bean: AbstractBean): ProxyEntity {
        bean.applyDefaultValues()

        val profile = ProxyEntity(groupId = groupId).apply {
            id = 0
            putBean(bean)
            userOrder = BitsBoxDatabase.proxyDao.nextOrder(groupId) ?: 1
        }
        profile.id = BitsBoxDatabase.proxyDao.addProxy(profile)
        iterator { onAdd(profile) }
        return profile
    }

    suspend fun updateProfile(profile: ProxyEntity) {
        BitsBoxDatabase.proxyDao.updateProxy(profile)
        iterator { onUpdated(profile, false) }
    }

    suspend fun updateProfile(profiles: List<ProxyEntity>) {
        BitsBoxDatabase.proxyDao.updateProxy(profiles)
        profiles.forEach {
            iterator { onUpdated(it, false) }
        }
    }

    suspend fun deleteProfile2(groupId: Long, profileId: Long) {
        if (BitsBoxDatabase.proxyDao.deleteById(profileId) == 0) return
        if (DataStore.selectedProxy == profileId) {
            DataStore.selectedProxy = 0L
        }
    }

    suspend fun deleteProfile(groupId: Long, profileId: Long) {
        if (BitsBoxDatabase.proxyDao.deleteById(profileId) == 0) return
        if (DataStore.selectedProxy == profileId) {
            DataStore.selectedProxy = 0L
        }
        iterator { onRemoved(groupId, profileId) }
        if (BitsBoxDatabase.proxyDao.countByGroup(groupId) > 1) {
            GroupManager.rearrange(groupId)
        }
    }

    fun getProfile(profileId: Long): ProxyEntity? {
        if (profileId == 0L) return null
        return try {
            BitsBoxDatabase.proxyDao.getById(profileId)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            null
        }
    }

    fun getProfiles(profileIds: List<Long>): List<ProxyEntity> {
        if (profileIds.isEmpty()) return listOf()
        return try {
            BitsBoxDatabase.proxyDao.getEntities(profileIds)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            listOf()
        }
    }

    // postUpdate: post to listeners, don't change the DB

    suspend fun postUpdate(profileId: Long, noTraffic: Boolean = false) {
        postUpdate(getProfile(profileId) ?: return, noTraffic)
    }

    suspend fun postUpdate(profile: ProxyEntity, noTraffic: Boolean = false) {
        iterator { onUpdated(profile, noTraffic) }
    }

    suspend fun postUpdate(data: TrafficData) {
        iterator { onUpdated(data) }
    }

    suspend fun createRule(rule: RuleEntity, post: Boolean = true): RuleEntity {
        rule.userOrder = BitsBoxDatabase.rulesDao.nextOrder() ?: 1
        rule.id = BitsBoxDatabase.rulesDao.createRule(rule)
        if (post) {
            ruleIterator { onAdd(rule) }
        }
        return rule
    }

    suspend fun updateRule(rule: RuleEntity) {
        BitsBoxDatabase.rulesDao.updateRule(rule)
        ruleIterator { onUpdated(rule) }
    }

    suspend fun deleteRule(ruleId: Long) {
        BitsBoxDatabase.rulesDao.deleteById(ruleId)
        ruleIterator { onRemoved(ruleId) }
    }

    suspend fun deleteRules(rules: List<RuleEntity>) {
        BitsBoxDatabase.rulesDao.deleteRules(rules)
        ruleIterator {
            rules.forEach {
                onRemoved(it.id)
            }
        }
    }

    suspend fun getRules(): List<RuleEntity> {
        var rules = BitsBoxDatabase.rulesDao.allRules()
        if (rules.isEmpty() && !DataStore.rulesFirstCreate) {
            DataStore.rulesFirstCreate = true
            createRule(
                RuleEntity(
                    name = app.getString(R.string.route_opt_block_quic),
                    port = "443",
                    network = "udp",
                    outbound = -2
                )
            )
            createRule(
                RuleEntity(
                    name = app.getString(R.string.route_opt_block_ads),
                    domains = "geosite:rule-ads",
                    outbound = -2
                )
            )
            val displayCountry = "Indonesia"
            createRule(
                RuleEntity(
                    name = app.getString(R.string.route_bypass_domain, displayCountry),
                    domains = "geosite:rule-indo",
                    outbound = -1
                ), false
            )
            createRule(
                RuleEntity(
                    name = app.getString(R.string.route_bypass_ip, displayCountry),
                    ip = "geoip:id",
                    outbound = -1
                ), false
            )
            rules = BitsBoxDatabase.rulesDao.allRules()
        }
        return rules
    }

}
