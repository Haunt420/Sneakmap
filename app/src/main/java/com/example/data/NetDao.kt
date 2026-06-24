package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NetDao {
    @Insert
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert
    suspend fun insertHost(host: HostEntity): Long

    @Insert
    suspend fun insertPort(port: PortEntity): Long

    @Insert
    suspend fun insertScriptResult(scriptResult: ScriptResultEntity)

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM hosts WHERE scanId = :scanId")
    fun getHostsForScan(scanId: Long): Flow<List<HostEntity>>

    @Query("SELECT * FROM ports WHERE hostId = :hostId")
    fun getPortsForHost(hostId: Long): Flow<List<PortEntity>>
    
    @Query("SELECT * FROM script_results WHERE portId = :portId")
    fun getScriptResultsForPort(portId: Long): Flow<List<ScriptResultEntity>>

    @Query("SELECT * FROM hosts")
    fun getAllHosts(): Flow<List<HostEntity>>

    // Search query joining hosts and ports as per DESIGN.md
    @Transaction
    @Query("""
        SELECT hosts.* FROM hosts 
        INNER JOIN ports ON hosts.id = ports.hostId 
        WHERE ports.service LIKE '%' || :query || '%' 
        OR hosts.ipAddress LIKE '%' || :query || '%'
    """)
    fun searchHosts(query: String): Flow<List<HostEntity>>
}
