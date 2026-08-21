package id.bits.box.database

import android.os.Parcelable
import androidx.room.*
import id.bits.box.R
import id.bits.box.ktx.app
import kotlinx.parcelize.Parcelize

@Entity(tableName = "rules")
@Parcelize
@TypeConverters(StringCollectionConverter::class)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var name: String = "",
    @ColumnInfo(defaultValue = "")
    var config: String = "",
    var userOrder: Long = 0L,
    var enabled: Boolean = false,
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var sourcePort: String = "",
    var network: String = "",
    var source: String = "",
    var protocol: String = "",
    var outbound: Long = 0,
    var packages: Set<String> = emptySet(),
) : Parcelable {

    fun displayName(): String {
        return name.takeIf { it.isNotBlank() } ?: "Rule $id"
    }

    fun mkSummary(): String {
        var summary = ""
        if (config.isNotBlank()) summary += "[${config.uppercase()}]\n"
        if (domains.isNotBlank()) summary += "${capitalizeRouteField("Domains")}: ${formatList(domains)}\n"
        if (ip.isNotBlank()) summary += "${capitalizeRouteField("IP Address")}: $ip\n"
        if (source.isNotBlank()) summary += "${capitalizeRouteField("Source IP")}: $source\n"
        if (sourcePort.isNotBlank()) summary += "${capitalizeRouteField("Source Port")}: $sourcePort\n"
        if (port.isNotBlank()) summary += "${capitalizeRouteField("Destination Port")}: $port\n"
        if (network.isNotBlank()) summary += "${capitalizeRouteField("Network")}: $network\n"
        if (protocol.isNotBlank()) summary += "${capitalizeRouteField("Protocol")}: $protocol\n"
        if (packages.isNotEmpty()) summary += "${app.getString(R.string.apps_message, packages.size)}\n"
        val lines = summary.trim().split("\n")
        return if (lines.size > 3) {
            lines.subList(0, 3).joinToString("\n", postfix = "\n...")
        } else {
            summary.trim()
        }
    }

    private fun capitalizeRouteField(field: String): String {
        return field.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            }
        }
    }

    private fun formatList(value: String): String {
        return value.take(40).ifBlank { value }.plus(if (value.length > 40) "..." else "")
    }

    fun displayOutbound(): String {
        return when (outbound) {
            0L -> app.getString(R.string.route_proxy)
            -1L -> app.getString(R.string.route_bypass)
            -2L -> app.getString(R.string.route_block)
            else -> ProfileManager.getProfile(outbound)?.displayName()
                ?: app.getString(R.string.error_title)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * from rules WHERE (packages != '') AND enabled = 1")
        fun checkVpnNeeded(): List<RuleEntity>

        @Query("SELECT * FROM rules ORDER BY userOrder")
        fun allRules(): List<RuleEntity>

        @Query("SELECT * FROM rules WHERE enabled = :enabled ORDER BY userOrder")
        fun enabledRules(enabled: Boolean = true): List<RuleEntity>

        @Query("SELECT MAX(userOrder) + 1 FROM rules")
        fun nextOrder(): Long?

        @Query("SELECT * FROM rules WHERE id = :ruleId")
        fun getById(ruleId: Long): RuleEntity?

        @Query("DELETE FROM rules WHERE id = :ruleId")
        fun deleteById(ruleId: Long): Int

        @Delete
        fun deleteRule(rule: RuleEntity)

        @Delete
        fun deleteRules(rules: List<RuleEntity>)

        @Insert
        fun createRule(rule: RuleEntity): Long

        @Update
        fun updateRule(rule: RuleEntity)

        @Update
        fun updateRules(rules: List<RuleEntity>)

        @Query("DELETE FROM rules")
        fun reset()

        @Insert
        fun insert(rules: List<RuleEntity>)

    }


}