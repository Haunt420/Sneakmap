package com.gmap.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val targetScope: String,
    val commandUsed: String
)

@Entity(
    tableName = "hosts",
    foreignKeys = [
        ForeignKey(
            entity = ScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("scanId")]
)
data class HostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val ipAddress: String,
    val macAddress: String?,
    val osGuess: String?,
    val status: String // "up", "down"
)

@Entity(
    tableName = "ports",
    foreignKeys = [
        ForeignKey(
            entity = HostEntity::class,
            parentColumns = ["id"],
            childColumns = ["hostId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("hostId")]
)
data class PortEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostId: Long,
    val portNumber: Int,
    val protocol: String, // "tcp", "udp"
    val state: String, // "open", "closed", "filtered"
    val service: String?
)

@Entity(
    tableName = "script_results",
    foreignKeys = [
        ForeignKey(
            entity = PortEntity::class,
            parentColumns = ["id"],
            childColumns = ["portId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("portId")]
)
data class ScriptResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portId: Long,
    val scriptId: String,
    val output: String
)
