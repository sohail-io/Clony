package io.nekohasekai.sagernet.database

import android.os.Parcelable
import android.os.Parcel
import androidx.room.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app

@Entity(tableName = "rules")
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

    private constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        name = parcel.readString().orEmpty(),
        config = parcel.readString().orEmpty(),
        userOrder = parcel.readLong(),
        enabled = parcel.readByte() != 0.toByte(),
        domains = parcel.readString().orEmpty(),
        ip = parcel.readString().orEmpty(),
        port = parcel.readString().orEmpty(),
        sourcePort = parcel.readString().orEmpty(),
        network = parcel.readString().orEmpty(),
        source = parcel.readString().orEmpty(),
        protocol = parcel.readString().orEmpty(),
        outbound = parcel.readLong(),
        packages = parcel.createStringArrayList()?.toSet() ?: emptySet()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(name)
        dest.writeString(config)
        dest.writeLong(userOrder)
        dest.writeByte((if (enabled) 1 else 0).toByte())
        dest.writeString(domains)
        dest.writeString(ip)
        dest.writeString(port)
        dest.writeString(sourcePort)
        dest.writeString(network)
        dest.writeString(source)
        dest.writeString(protocol)
        dest.writeLong(outbound)
        dest.writeStringList(packages.toList())
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RuleEntity> {
            override fun createFromParcel(source: Parcel) = RuleEntity(source)
            override fun newArray(size: Int): Array<RuleEntity?> = arrayOfNulls(size)
        }
    }

    fun displayName(): String {
        return name.takeIf { it.isNotBlank() } ?: "Rule $id"
    }

    fun mkSummary(): String {
        var summary = ""
        if (config.isNotBlank()) summary += "[config]\n"
        if (domains.isNotBlank()) summary += "$domains\n"
        if (ip.isNotBlank()) summary += "$ip\n"
        if (source.isNotBlank()) summary += "src ip: $source\n"
        if (sourcePort.isNotBlank()) summary += "src port: $sourcePort\n"
        if (port.isNotBlank()) summary += "dst port: $port\n"
        if (network.isNotBlank()) summary += "network: $network\n"
        if (protocol.isNotBlank()) summary += "protocol: $protocol\n"
        if (packages.isNotEmpty()) summary += app.getString(
            R.string.apps_message, packages.size
        ) + "\n"
        val lines = summary.trim().split("\n")
        return if (lines.size > 3) {
            lines.subList(0, 3).joinToString("\n", postfix = "\n...")
        } else {
            summary.trim()
        }
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
