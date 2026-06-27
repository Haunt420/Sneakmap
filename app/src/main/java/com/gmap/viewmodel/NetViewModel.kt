package com.gmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmap.data.HostEntity
import com.gmap.data.NetDatabase
import com.gmap.data.PortEntity
import com.gmap.data.ScanEntity
import kotlinx.coroutines.flow.Flow
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
}
