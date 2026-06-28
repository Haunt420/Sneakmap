package com.gmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmap.data.HostEntity
import com.gmap.data.NetDatabase
import com.gmap.data.PortEntity
import com.gmap.data.ScanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NetViewModel(application: Application) : AndroidViewModel(application) {
    private val db = NetDatabase.getDatabase(application)
    private val dao = db.netDao()

    val allHosts: Flow<List<HostEntity>> = dao.getAllHosts()
    val allScans: Flow<List<ScanEntity>> = dao.getAllScans()

    fun getHostsForScan(scanId: Long): Flow<List<HostEntity>> {
        return dao.getHostsForScan(scanId)
    }

    fun getPortsForHost(hostId: Long): Flow<List<PortEntity>> {
        return dao.getPortsForHost(hostId)
    }

    fun insertMockScan(target: String, delayHours: Long = 0) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis() - (delayHours * 3600 * 1000)
            val scanId = dao.insertScan(
                ScanEntity(
                    targetScope = target,
                    timestamp = timestamp,
                    commandUsed = "nmap -sS -O -T4 $target"
                )
            )

            // Populate some hosts and ports
            val hosts = listOf(
                Triple("192.168.1.1", "00:1A:2C:3C:4D:01", "Linux 2.6.x (Cisco Linksys Router)"),
                Triple("192.168.1.15", "08:00:27:A3:B4:15", "Linux 5.15 (Ubuntu Server)"),
                Triple("192.168.1.42", "3C:06:30:1F:B1:42", "macOS Sequoia 15.1 (Workstation)"),
                Triple("192.168.1.100", "2C:F0:EE:D4:A5:10", "Windows 11 Professional")
            )

            for ((ip, mac, os) in hosts) {
                val hostId = dao.insertHost(
                    HostEntity(
                        scanId = scanId,
                        ipAddress = ip,
                        macAddress = mac,
                        osGuess = os,
                        status = "up"
                    )
                )

                // Add sample ports
                when (ip) {
                    "192.168.1.1" -> {
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 80, protocol = "tcp", state = "open", service = "http"))
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 443, protocol = "tcp", state = "open", service = "https"))
                    }
                    "192.168.1.15" -> {
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 22, protocol = "tcp", state = "open", service = "ssh"))
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 80, protocol = "tcp", state = "open", service = "http"))
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 3306, protocol = "tcp", state = "open", service = "mysql"))
                    }
                    "192.168.1.42" -> {
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 22, protocol = "tcp", state = "open", service = "ssh"))
                    }
                    "192.168.1.100" -> {
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 135, protocol = "tcp", state = "open", service = "msrpc"))
                        dao.insertPort(PortEntity(hostId = hostId, portNumber = 445, protocol = "tcp", state = "open", service = "microsoft-ds"))
                    }
                }
            }
        }
    }

    suspend fun compareScans(scanAId: Long, scanBId: Long): List<String> {
        val results = mutableListOf<String>()
        
        if (scanAId == scanBId) {
            results.add("[Same Scans] Comparison target is identical. No differences found.")
            return results
        }
        
        results.add("Analyzing Node Topologies for Scans #$scanAId and #$scanBId...")
        
        val hostsA = dao.getHostsForScan(scanAId).first()
        val hostsB = dao.getHostsForScan(scanBId).first()
        
        val mapA = hostsA.associateBy { it.ipAddress }
        val mapB = hostsB.associateBy { it.ipAddress }
        
        // Find added hosts
        val addedIps = mapB.keys - mapA.keys
        // Find removed hosts
        val removedIps = mapA.keys - mapB.keys
        // Common hosts
        val commonIps = mapA.keys intersect mapB.keys
        
        var diffCount = 0
        
        if (addedIps.isNotEmpty() || removedIps.isNotEmpty() || commonIps.isNotEmpty()) {
            results.add("✔ [DIFF SUCCESS] Difference detected in Target Ports/Hosts:")
        }
        
        for (ip in addedIps) {
            val host = mapB[ip]!!
            results.add("  ➕ Host [$ip] added (OS: ${host.osGuess ?: "Unknown"})")
            diffCount++
            
            // Also show ports added with this host
            val ports = dao.getPortsForHost(host.id).first()
            for (port in ports) {
                results.add("    ➕ Port ${port.portNumber}/${port.protocol} (${port.service ?: "unknown service"})")
            }
        }
        
        for (ip in removedIps) {
            results.add("  ➖ Host [$ip] removed")
            diffCount++
        }
        
        for (ip in commonIps) {
            val hostA = mapA[ip]!!
            val hostB = mapB[ip]!!
            
            // Check OS Fingerprint change
            if (hostA.osGuess != hostB.osGuess) {
                results.add("  ▲ Host [$ip] OS Fingerprint modified: '${hostA.osGuess ?: "Unknown"}' ➔ '${hostB.osGuess ?: "Unknown"}'")
                diffCount++
            }
            
            val portsA = dao.getPortsForHost(hostA.id).first()
            val portsB = dao.getPortsForHost(hostB.id).first()
            
            val portsMapA = portsA.associateBy { "${it.portNumber}/${it.protocol}" }
            val portsMapB = portsB.associateBy { "${it.portNumber}/${it.protocol}" }
            
            val addedPorts = portsMapB.keys - portsMapA.keys
            val removedPorts = portsMapA.keys - portsMapB.keys
            
            for (pKey in addedPorts) {
                val port = portsMapB[pKey]!!
                results.add("  ➕ Host [$ip] discovered open port ${port.portNumber} (${port.service ?: "unknown"})")
                diffCount++
            }
            
            for (pKey in removedPorts) {
                val port = portsMapA[pKey]!!
                results.add("  ➖ Host [$ip] closed port ${port.portNumber} (${port.service ?: "unknown"})")
                diffCount++
            }
        }
        
        if (diffCount == 0) {
            results.add("No network topological differences detected between Scan #$scanAId and Scan #$scanBId.")
        }
        
        return results
    }
}
