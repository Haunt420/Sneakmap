package com.gmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmap.data.HostEntity
import com.gmap.data.PortEntity
import com.gmap.data.ScanEntity
import com.gmap.viewmodel.NetViewModel
import com.gmap.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Modern deep slate background
                ) {
                    NetInsightApp()
                }
            }
        }
    }
}

// Data model for active interactive NSE scripts in manager
data class NseScript(
    val id: String,
    val name: String,
    val description: String,
    val defaultArgName: String?,
    val defaultArgPlaceholder: String?,
    val securityObjective: String,
    val targetPort: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetInsightApp(viewModel: NetViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Navigation and screen states
    var currentTab by remember { mutableIntStateOf(0) } // 0=DISCOVER, 1=HISTORY, 2=SCRIPTS, 3=SETTINGS/ABOUT

    // Active Scan Parameters
    var targetIp by remember { mutableStateOf("192.168.1.0/24") }
    var tcpSynScan by remember { mutableStateOf(true) }
    var osDetection by remember { mutableStateOf(true) }
    var fragmentPackets by remember { mutableStateOf(false) }
    var timingTemplate by remember { mutableFloatStateOf(4f) }
    var customPortRange by remember { mutableStateOf("") }
    
    // NSE Scripts integrated state
    val nseScriptsList = remember {
        listOf(
            NseScript("http-title", "http-title", "Grabs HTML title of target web servers", "http-title.url", "e.g., /admin", "Identify forgotten or misconfigured server control boards", 80),
            NseScript("ssl-cert", "ssl-cert", "Retrieves complete target SSL certificate details", "ssl-cert.fields", "e.g., subject,issuer", "Analyze insecure or self-signed crypto signatures", 443),
            NseScript("dns-brute", "dns-brute", "Performs multithreaded domain subdomain lookup", "dns-brute.threads", "e.g., 10", "Map unauthorized internal microservices", 53),
            NseScript("ftp-anon", "ftp-anon", "Checks if target server allows anonymous credentials", null, null, "Locate open backup file leaks", 21),
            NseScript("ssh-auth-methods", "ssh-auth-methods", "Discovers allowed SSH login mechanisms", "ssh-auth-methods.user", "e.g., root", "Enforce key-only authentication audits", 22)
        )
    }
    var activeNseScriptId by remember { mutableStateOf<String?>(null) }
    var activeNseScriptArgValue by remember { mutableStateOf("") }

    // Preset profiles
    var selectedPresetName by remember { mutableStateOf("Intense Scan") }
    val presetExplanation = when (selectedPresetName) {
        "Quick Scan" -> "Scans top 100 most common ports rapidly. Minimizes footprint while giving immediate asset mapping."
        "Ping Scan" -> "Sends ICMP echo, TCP SYN, and ARP queries without scanning individual ports. Used for fast host discovery."
        "Intense Scan" -> "Comprehensive scanning including open port auditing, OS fingerprinting, and standard script engines."
        "Evasion Scan" -> "Uses packet fragmentation (-f) and decoy addresses to challenge stateful local IDS/IPS rules."
        "Port Audit" -> "Locks onto standard vulnerability points (22, 80, 443, 3389, 8080) for targeted security configuration audits."
        else -> ""
    }

    // Active Database states
    val allHosts by viewModel.allHosts.collectAsState(initial = emptyList())
    val allScans by viewModel.allScans.collectAsState(initial = emptyList())
    var selectedNodeForDetail by remember { mutableStateOf<HostEntity?>(null) }

    // Observe active scan state
    var isScanning by remember { mutableStateOf(false) }
    
    // Command generation formula
    val generatedCommand by remember {
        derivedStateOf {
            buildString {
                append("nmap ")
                if (tcpSynScan) append("-sS ")
                if (osDetection) append("-O ")
                if (fragmentPackets) append("-f ")
                if (customPortRange.isNotBlank()) append("-p $customPortRange ")
                
                activeNseScriptId?.let { scriptId ->
                    append("--script=$scriptId ")
                    val script = nseScriptsList.firstOrNull { it.id == scriptId }
                    if (script?.defaultArgName != null && activeNseScriptArgValue.isNotBlank()) {
                        append("--script-args ${script.defaultArgName}=$activeNseScriptArgValue ")
                    }
                }
                
                append("-T${timingTemplate.toInt()} ")
                append(targetIp)
            }
        }
    }

    // Educational Bottomsheet state
    var showExplanationDialog by remember { mutableStateOf<String?>(null) }

    // Local Web Server Simulated Streaming states
    var streamingEnabled by remember { mutableStateOf(false) }
    var activeStreamingPort by remember { mutableStateOf("8080") }
    var streamLogs = remember { mutableStateListOf<String>() }

    // Automation notification settings state
    var automationEnabled by remember { mutableStateOf(true) }
    var triggerAlertOnSsh by remember { mutableStateOf(true) }
    var triggerAlertOnHttp by remember { mutableStateOf(false) }

    // Temporal Diff state
    var diffScanAId by remember { mutableStateOf<Long?>(null) }
    var diffScanBId by remember { mutableStateOf<Long?>(null) }
    var diffResultStringList by remember { mutableStateOf<List<String>?>(null) }
    var isDiffingByEngine by remember { mutableStateOf(false) }

    // Periodic real network interface and JVM telemetry stream generator
    LaunchedEffect(streamingEnabled) {
        if (streamingEnabled) {
            streamLogs.add("Telemetry Broadcaster Server initialized on local port $activeStreamingPort")
            streamLogs.add("WebSockets streaming active connection pool: listening...")
            
            // Log actual active network interfaces on start
            try {
                val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                for (iface in interfaces) {
                    if (iface.isUp) {
                        val ips = java.util.Collections.list(iface.inetAddresses)
                            .filter { !it.isLoopbackAddress }
                            .map { it.hostAddress }
                        if (ips.isNotEmpty()) {
                            streamLogs.add("Active Interface: ${iface.name} | MTU: ${iface.mtu} | IP: ${ips.joinToString()}")
                        }
                    }
                }
            } catch (e: Exception) {
                streamLogs.add("Error reading interface configurations: ${e.message}")
            }

            while (isActive) {
                delay(3000)
                val telemetryFrame = when (Random.nextInt(4)) {
                    0 -> {
                        // Real network interface IP check
                        val activeIps = mutableListOf<String>()
                        try {
                            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                            for (iface in interfaces) {
                                if (iface.isUp && !iface.isLoopback) {
                                    activeIps.addAll(java.util.Collections.list(iface.inetAddresses).map { it.hostAddress })
                                }
                            }
                        } catch (e: Exception) {}
                        "{\"event\":\"interfaces_status\",\"uptime_sec\":${System.currentTimeMillis()/1000},\"active_ips\":${activeIps.joinToString(prefix="[", postfix="]") { "\"$it\"" }}}"
                    }
                    1 -> {
                        // Real hardware core count and thread telemetry
                        val cores = Runtime.getRuntime().availableProcessors()
                        val freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024)
                        "{\"event\":\"host_resources\",\"available_cpu_cores\":$cores,\"free_jvm_memory_mb\":$freeMem}"
                    }
                    2 -> {
                        // Query the database to stream real statistics
                        val scanCount = try {
                            val scansList = viewModel.allScans.first()
                            scansList.size
                        } catch(e: Exception) { 0 }
                        val hostCount = try {
                            val hostsList = viewModel.allHosts.first()
                            hostsList.size
                        } catch(e: Exception) { 0 }
                        "{\"event\":\"database_telemetry\",\"total_recorded_scans\":$scanCount,\"total_discovered_hosts\":$hostCount}"
                    }
                    else -> {
                        // Standard telemetry heartbeat with actual local timestamp
                        "{\"event\":\"stream_heartbeat\",\"server_time_ms\":${System.currentTimeMillis()},\"thread_pool_active\":true}"
                    }
                }
                streamLogs.add(0, "[WebSocket Broadcast] $telemetryFrame")
                if (streamLogs.size > 25) streamLogs.removeLast()
            }
        } else {
            streamLogs.clear()
        }
    }

    // Monitor scanning state from foreground database updates and trigger real compliance notifications
    LaunchedEffect(allHosts.size) {
        if (isScanning && allHosts.isNotEmpty()) {
            val lastAdded = allHosts.lastOrNull()
            if (lastAdded != null) {
                selectedNodeForDetail = lastAdded
                
                if (automationEnabled) {
                    try {
                        val ports = viewModel.getPortsForHost(lastAdded.id).first()
                        for (port in ports) {
                            if (port.portNumber == 22 && triggerAlertOnSsh) {
                                android.widget.Toast.makeText(
                                    context,
                                    "⚠️ COMPLIANCE ALERT: Open SSH port 22 found on ${lastAdded.ipAddress}!",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            if ((port.portNumber == 80 || port.portNumber == 443) && triggerAlertOnHttp) {
                                android.widget.Toast.makeText(
                                    context,
                                    "⚠️ COMPLIANCE ALERT: Open Web port ${port.portNumber} found on ${lastAdded.ipAddress}!",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Share, contentDescription = "Discover", tint = if (currentTab == 0) Color(0xFF22D3EE) else Color.Gray) },
                    label = { Text("Discover", color = if (currentTab == 0) Color(0xFF22D3EE) else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "History", tint = if (currentTab == 1) Color(0xFF22D3EE) else Color.Gray) },
                    label = { Text("History & Diff", color = if (currentTab == 1) Color(0xFF22D3EE) else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Scripts", tint = if (currentTab == 2) Color(0xFF22D3EE) else Color.Gray) },
                    label = { Text("NSE Scripts", color = if (currentTab == 2) Color(0xFF22D3EE) else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Me", tint = if (currentTab == 3) Color(0xFF22D3EE) else Color.Gray) },
                    label = { Text("Handbook", color = if (currentTab == 3) Color(0xFF22D3EE) else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A))
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF22D3EE), RoundedCornerShape(50))
                            .padding(end = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NetInsight Sneakgo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isScanning) Color(0x3310B981) else Color(0x33F1F5F9),
                    modifier = Modifier.padding(start = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isScanning) Color(0xFF10B981) else Color.Gray, RoundedCornerShape(50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanning) "SCAN IN PROGRESS" else "READY ENGINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isScanning) Color(0xFF10B981) else Color.LightGray
                        )
                    }
                }
            }

            // Tab Navigation Router Content
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    0 -> DiscoverScreen(
                        targetIp = targetIp,
                        onTargetIpChange = { targetIp = it },
                        tcpSynScan = tcpSynScan,
                        onTcpSynScanChange = { tcpSynScan = it },
                        osDetection = osDetection,
                        onOsDetectionChange = { osDetection = it },
                        fragmentPackets = fragmentPackets,
                        onFragmentPacketsChange = { fragmentPackets = it },
                        timingTemplate = timingTemplate,
                        onTimingTemplateChange = { timingTemplate = it },
                        customPortRange = customPortRange,
                        onCustomPortRangeChange = { customPortRange = it },
                        selectedPresetName = selectedPresetName,
                        onPresetSelected = { preset ->
                            selectedPresetName = preset
                            when (preset) {
                                "Quick Scan" -> {
                                    tcpSynScan = true
                                    osDetection = false
                                    fragmentPackets = false
                                    timingTemplate = 5f
                                    customPortRange = "21,22,23,25,80,110,443,3389"
                                }
                                "Ping Scan" -> {
                                    tcpSynScan = false
                                    osDetection = false
                                    fragmentPackets = false
                                    timingTemplate = 4f
                                    customPortRange = ""
                                }
                                "Intense Scan" -> {
                                    tcpSynScan = true
                                    osDetection = true
                                    fragmentPackets = false
                                    timingTemplate = 4f
                                    customPortRange = ""
                                }
                                "Evasion Scan" -> {
                                    tcpSynScan = true
                                    osDetection = true
                                    fragmentPackets = true
                                    timingTemplate = 2f
                                    customPortRange = "80,443"
                                }
                                "Port Audit" -> {
                                    tcpSynScan = true
                                    osDetection = true
                                    fragmentPackets = false
                                    timingTemplate = 4f
                                    customPortRange = "22,80,443,1433,3306,3389,8080"
                                }
                            }
                        },
                        presetExplanation = presetExplanation,
                        generatedCommand = generatedCommand,
                        allHosts = allHosts,
                        isScanning = isScanning,
                        onScanStateToggle = {
                            isScanning = !isScanning
                            if (isScanning) {
                                val intent = android.content.Intent(context, com.gmap.service.NmapForegroundService::class.java).apply {
                                    putExtra("TARGET_SCOPE", targetIp)
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            } else {
                                val intent = android.content.Intent(context, com.gmap.service.NmapForegroundService::class.java)
                                context.stopService(intent)
                            }
                        },
                        selectedNodeForDetail = selectedNodeForDetail,
                        onNodeSelected = { selectedNodeForDetail = it },
                        viewModel = viewModel,
                        onShowTooltip = { showExplanationDialog = it }
                    )
                    1 -> HistoryAndDiffScreen(
                        allScans = allScans,
                        diffScanAId = diffScanAId,
                        diffScanBId = diffScanBId,
                        onScanASelected = { diffScanAId = it },
                        onScanBSelected = { diffScanBId = it },
                        diffResultStringList = diffResultStringList,
                        onRunDiff = {
                            if (diffScanAId != null && diffScanBId != null) {
                                isDiffingByEngine = true
                                coroutineScope.launch {
                                    val results = viewModel.compareScans(diffScanAId!!, diffScanBId!!)
                                    diffResultStringList = results
                                    isDiffingByEngine = false
                                }
                            }
                        },
                        isDiffingByEngine = isDiffingByEngine,
                        viewModel = viewModel
                    )
                    2 -> ScriptsScreen(
                        nseScriptsList = nseScriptsList,
                        activeNseScriptId = activeNseScriptId,
                        onNseScriptSelected = { scriptId ->
                            activeNseScriptId = if (activeNseScriptId == scriptId) null else scriptId
                            activeNseScriptArgValue = ""
                        },
                        activeNseScriptArgValue = activeNseScriptArgValue,
                        onArgValueChange = { activeNseScriptArgValue = it },
                        generatedCommand = generatedCommand
                    )
                    3 -> SettingsScreen(
                        streamingEnabled = streamingEnabled,
                        onStreamingToggleChange = { streamingEnabled = it },
                        activeStreamingPort = activeStreamingPort,
                        onStreamingPortChange = { activeStreamingPort = it },
                        streamLogs = streamLogs,
                        automationEnabled = automationEnabled,
                        onAutomationToggleChange = { automationEnabled = it },
                        triggerAlertOnSsh = triggerAlertOnSsh,
                        onTriggerAlertOnSshChange = { triggerAlertOnSsh = it },
                        triggerAlertOnHttp = triggerAlertOnHttp,
                        onTriggerAlertOnHttpChange = { triggerAlertOnHttp = it }
                    )
                }
            }
        }
    }

    if (showExplanationDialog != null) {
        AlertDialog(
            onDismissRequest = { showExplanationDialog = null },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF22D3EE)) },
            title = { Text("Educational Insight", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(showExplanationDialog!!, color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { showExplanationDialog = null }) {
                    Text("Understood", color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

// ---------------------- TAB 0: DISCOVER SCREEN ----------------------
@Composable
fun DiscoverScreen(
    targetIp: String,
    onTargetIpChange: (String) -> Unit,
    tcpSynScan: Boolean,
    onTcpSynScanChange: (Boolean) -> Unit,
    osDetection: Boolean,
    onOsDetectionChange: (Boolean) -> Unit,
    fragmentPackets: Boolean,
    onFragmentPacketsChange: (Boolean) -> Unit,
    timingTemplate: Float,
    onTimingTemplateChange: (Float) -> Unit,
    customPortRange: String,
    onCustomPortRangeChange: (String) -> Unit,
    selectedPresetName: String,
    onPresetSelected: (String) -> Unit,
    presetExplanation: String,
    generatedCommand: String,
    allHosts: List<HostEntity>,
    isScanning: Boolean,
    onScanStateToggle: () -> Unit,
    selectedNodeForDetail: HostEntity?,
    onNodeSelected: (HostEntity?) -> Unit,
    viewModel: NetViewModel,
    onShowTooltip: (String) -> Unit
) {
    var showConstructorCard by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        
        // Target scope input field and presets
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = targetIp,
                onValueChange = onTargetIpChange,
                label = { Text("Target Subnet / Scope IP") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF22D3EE)) },
                trailingIcon = {
                    if (targetIp.isNotBlank()) {
                        IconButton(onClick = { onTargetIpChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedLabelColor = Color(0xFF22D3EE),
                    focusedBorderColor = Color(0xFF22D3EE),
                    unfocusedBorderColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Presets row selection
            Text("SCAN PROFILE PRESET:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val presets = listOf("Intense Scan", "Quick Scan", "Ping Scan", "Evasion Scan", "Port Audit")
                items(presets) { preset ->
                    FilterChip(
                        selected = selectedPresetName == preset,
                        onClick = { onPresetSelected(preset) },
                        label = { Text(preset, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0x3322D3EE),
                            selectedLabelColor = Color(0xFF22D3EE),
                            containerColor = Color(0x1A000000),
                            labelColor = Color.Gray
                        )
                    )
                }
            }

            // Animated Preset Explanation Banner
            if (presetExplanation.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = presetExplanation,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Expandable Command Constructor Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showConstructorCard = !showConstructorCard }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (showConstructorCard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF22D3EE)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VISUAL COMMAND CONSTRUCTOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = if (showConstructorCard) "HIDE ADVANCED" else "SHOW ADVANCED",
                    fontSize = 10.sp,
                    color = Color(0xFF22D3EE),
                    fontWeight = FontWeight.ExtraBold
                )
            }

            AnimatedVisibility(visible = showConstructorCard) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    // Switch 1: TCP SYN
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onShowTooltip("TCP SYN Scan (-sS): Sends SYN packet and waits for reply. If SYN-ACK is received, host is active. We reply with RST to avoid creating a full TCP session. Half-open signature, very hard for simple applications to log.") }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TCP SYN Stealth Scan (-sS)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                            }
                            Text("Long press to learn stealth handshake mechanics", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = tcpSynScan,
                            onCheckedChange = onTcpSynScanChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF22D3EE),
                                checkedTrackColor = Color(0xFF0891B2)
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF334155))

                    // Switch 2: OS Detection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onShowTooltip("OS Detection (-O): Inspects TCP window sizes, TCP options, IP time-to-live values (TTL), and initial sequence numbers to match target stack fingerprint against database of known kernels.") }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("OS Stack Fingerprinting (-O)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                            }
                            Text("Probes TCP flags to guess Operating System", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = osDetection,
                            onCheckedChange = onOsDetectionChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF22D3EE),
                                checkedTrackColor = Color(0xFF0891B2)
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF334155))

                    // Switch 3: Fragment
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onShowTooltip("Packet Fragmentation (-f): Splits TCP headers into tiny 8-byte fragments to slip past standard router packet-filters and simple firewall inspection engines.") }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Evade Firewall via Fragment (-f)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                            }
                            Text("Splits IP packets to bypass local filter controls", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = fragmentPackets,
                            onCheckedChange = onFragmentPacketsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF22D3EE),
                                checkedTrackColor = Color(0xFF0891B2)
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF334155))

                    // Slider: Aggressiveness Timing
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Timing Template Level: -T${timingTemplate.toInt()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { onShowTooltip("Timing Templates (-T0 to -T5): Controls speed, delays, and packet drop tolerances. -T0 (Paranoid) is extremely slow to bypass Intrusion Detection Systems. -T5 (Insane) is rapid and aggressive, optimized for fast internal networks.") },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                            }
                        }
                        Slider(
                            value = timingTemplate,
                            onValueChange = onTimingTemplateChange,
                            valueRange = 0f..5f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF22D3EE),
                                activeTrackColor = Color(0xFF22D3EE),
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paranoid (-T0)", color = Color.Gray, fontSize = 9.sp)
                            Text("Insane (-T5)", color = Color.Gray, fontSize = 9.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Outlined Field: Custom ports
                    OutlinedTextField(
                        value = customPortRange,
                        onValueChange = onCustomPortRangeChange,
                        label = { Text("Restrict Ports (leave blank for default 1000)") },
                        placeholder = { Text("e.g. 22,80,443") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            }
        }

        // Live Command Bar Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("LIVE COMPILING CONSOLE COMMAND", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("AUTO-FORMULATING", color = Color(0xFF22D3EE), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text = generatedCommand,
                    color = Color(0xFF22D3EE),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Interactive Canvas Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, Color(0xFF334155)), RoundedCornerShape(20.dp))
        ) {
            if (isScanning || allHosts.isNotEmpty()) {
                // Render the spectacular dynamic topology map
                HighFidelityTopologyCanvas(
                    hosts = allHosts,
                    isScanning = isScanning,
                    selectedNode = selectedNodeForDetail,
                    onNodeSelected = onNodeSelected,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Status Ring Banner
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanning) "DISCOVERING LIVE TARGETS" else "SCANNING TOPOLOGY RENDERED",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                // Empty state view explaining how to scan
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color(0xFF22D3EE).copy(alpha = 0.3f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "High-Fidelity Topology Engine Ready",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Press the big scan button below to start the background process. Live nodes will populate as animated particles with OS identifiers, MAC signatures, and port lists.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                }
            }

            // Slide Up host detail overlay panel
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedNodeForDetail != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedNodeForDetail?.let { host ->
                    HostDetailedOverlayCard(
                        host = host,
                        onClose = { onNodeSelected(null) },
                        viewModel = viewModel
                    )
                }
            }
        }

        // Action Trigger Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    // Instantly insert a mock completed scan into DB so user doesn't wait
                    viewModel.insertMockScan(targetIp)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                modifier = Modifier.weight(0.4f)
            ) {
                Text("Insert Demo Data", fontSize = 11.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onScanStateToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) Color(0xFFEF4444) else Color(0xFF22D3EE)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(0.6f)
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Close else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) "ABORT DISCOVERY" else "LAUNCH ACTIVE ENGINE",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ---------------------- RENDER TOPOLOGY MAP ----------------------
@Composable
fun HighFidelityTopologyCanvas(
    hosts: List<HostEntity>,
    isScanning: Boolean,
    selectedNode: HostEntity?,
    onNodeSelected: (HostEntity?) -> Unit,
    viewModel: NetViewModel,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    
    val nodes = remember { mutableStateListOf<NetNode>() }
    val edges = remember { mutableStateListOf<NetEdge>() }

    // Synchronize hosts array into physics node states
    LaunchedEffect(hosts) {
        val existingIps = nodes.map { it.id }.toSet()
        val newHosts = hosts.filter { it.ipAddress !in existingIps }
        
        if (nodes.isEmpty() && hosts.isNotEmpty()) {
            // Main Router Gateway
            nodes.add(NetNode("router_gateway", color = Color(0xFF22D3EE)))
        }
        
        newHosts.forEach { host ->
            val isRouter = host.osGuess?.contains("Router") == true || host.ipAddress.endsWith(".1")
            val isSwitch = host.osGuess?.contains("Switch") == true || host.ipAddress.endsWith(".254")
            val color = when {
                isRouter -> Color(0xFF22D3EE)
                isSwitch -> Color(0xFFE11D48)
                else -> Color(0xFF10B981)
            }
            val newNode = NetNode(host.ipAddress, color = color)
            nodes.add(newNode)
            
            val router = nodes.firstOrNull { it.id == "router_gateway" }
            if (router != null && newNode.id != router.id) {
                edges.add(NetEdge(router, newNode))
            }
        }
    }

    // Active Force-Directed physics loop
    LaunchedEffect(canvasSize, nodes.size) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        
        val width = canvasSize.width.toFloat()
        val height = canvasSize.height.toFloat()
        
        nodes.forEach {
            if (it.id == "router_gateway") {
                it.x = width / 2f
                it.y = height / 2f
            } else if (it.x == 0f && it.y == 0f) {
                it.x = width / 2f + (Random.nextFloat() - 0.5f) * 150f
                it.y = height / 2f + (Random.nextFloat() - 0.5f) * 150f
            }
        }

        val repulsion = 250000f
        val spring = 0.004f
        val idealLength = 260f
        val damping = 0.75f

        while (isActive) {
            withFrameNanos {
                // 1. Repulsion forces
                for (i in nodes.indices) {
                    for (j in i + 1 until nodes.size) {
                        val n1 = nodes[i]
                        val n2 = nodes[j]
                        val dx = n1.x - n2.x
                        val dy = n1.y - n2.y
                        var dist = sqrt(dx * dx + dy * dy)
                        if (dist < 1f) dist = 1f
                        val force = repulsion / (dist * dist)
                        val fx = (dx / dist) * force
                        val fy = (dy / dist) * force
                        
                        n1.vx += fx
                        n1.vy += fy
                        n2.vx -= fx
                        n2.vy -= fy
                    }
                }

                // 2. Spring attractions along connections
                edges.forEach { edge ->
                    val dx = edge.target.x - edge.source.x
                    val dy = edge.target.y - edge.source.y
                    var dist = sqrt(dx * dx + dy * dy)
                    if (dist < 1f) dist = 1f
                    val diff = dist - idealLength
                    val force = diff * spring
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    
                    edge.source.vx += fx
                    edge.source.vy += fy
                    edge.target.vx -= fx
                    edge.target.vy -= fy
                }

                // 3. Wall boundaries and central pull updates
                nodes.forEach { n ->
                    if (n.id == "router_gateway") {
                        n.x += (width / 2f - n.x) * 0.12f
                        n.y += (height / 2f - n.y) * 0.12f
                        n.vx = 0f
                        n.vy = 0f
                    } else {
                        n.vx += (width / 2f - n.x) * 0.0008f
                        n.vy += (height / 2f - n.y) * 0.0008f
                        
                        n.vx *= damping
                        n.vy *= damping
                        n.x += n.vx
                        n.y += n.vy
                        
                        n.x = n.x.coerceIn(60f, width - 60f)
                        n.y = n.y.coerceIn(60f, height - 60f)
                    }
                }
            }
        }
    }

    // Refresh frames for fluid custom drawings
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frame++ }
        }
    }

    // Interactive panning / zoom
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.6f, 3.5f)
        offset += offsetChange
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .onSizeChanged { canvasSize = it }
            .transformable(state = transformableState)
            .pointerInput(nodes.size) {
                detectTapGestures { tapOffset ->
                    // Calculate touch collision to select nodes
                    val adjustedTapX = (tapOffset.x - offset.x) / scale
                    val adjustedTapY = (tapOffset.y - offset.y) / scale
                    
                    val clickedNode = nodes.minByOrNull { n ->
                        val dx = n.x - adjustedTapX
                        val dy = n.y - adjustedTapY
                        dx * dx + dy * dy
                    }
                    
                    if (clickedNode != null) {
                        val dx = clickedNode.x - adjustedTapX
                        val dy = clickedNode.y - adjustedTapY
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance < 50f) {
                            // Fetch corresponding database host details
                            val hostObj = hosts.firstOrNull { it.ipAddress == clickedNode.id }
                            if (hostObj != null) {
                                onNodeSelected(hostObj)
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            val currentFrame = frame // force recomposition each frame
            
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Cyber Slate Tech Dot Grid Background
            val gridSize = 32.dp.toPx()
            for (x in 0..canvasWidth.toInt() step gridSize.toInt()) {
                for (y in 0..canvasHeight.toInt() step gridSize.toInt()) {
                    drawCircle(
                        color = Color(0xFF334155).copy(alpha = 0.35f),
                        radius = 1.2f.dp.toPx(),
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }

            // 2. Draw connections (Edges) with animated moving packets
            edges.forEach { edge ->
                // Draw high tech connection line
                drawLine(
                    color = edge.target.color.copy(alpha = 0.45f),
                    start = Offset(edge.source.x, edge.source.y),
                    end = Offset(edge.target.x, edge.target.y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )

                // Animated Packet light traversing along the line
                val progress = (currentFrame % 120) / 120f
                val packetX = edge.source.x + (edge.target.x - edge.source.x) * progress
                val packetY = edge.source.y + (edge.target.y - edge.source.y) * progress
                
                drawCircle(
                    color = Color(0xFF22D3EE),
                    radius = 3.5f.dp.toPx(),
                    center = Offset(packetX, packetY)
                )
            }

            // 3. Draw Nodes with tech glowing rings
            nodes.forEach { node ->
                val isGateway = node.id == "router_gateway"
                val baseRadius = if (isGateway) 26.dp.toPx() else 18.dp.toPx()
                
                // Outer rotating ring glow effect
                val glowAlpha = 0.15f + 0.05f * kotlin.math.sin(currentFrame / 15f)
                drawCircle(
                    color = node.color.copy(alpha = glowAlpha),
                    radius = baseRadius + 14.dp.toPx(),
                    center = Offset(node.x, node.y)
                )

                // Main Node circle fill
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = baseRadius,
                    center = Offset(node.x, node.y)
                )

                // Highlight border ring
                drawCircle(
                    color = node.color,
                    radius = baseRadius,
                    center = Offset(node.x, node.y),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (selectedNode?.ipAddress == node.id) 3.dp.toPx() else 1.5f.dp.toPx()
                    )
                )

                // Small central indicator core
                drawCircle(
                    color = node.color,
                    radius = 5.dp.toPx(),
                    center = Offset(node.x, node.y)
                )
            }
        }
    }
}

// ---------------------- POPUP: NODE DETAIL DRAWER ----------------------
@Composable
fun HostDetailedOverlayCard(
    host: HostEntity,
    onClose: () -> Unit,
    viewModel: NetViewModel
) {
    val portsList by viewModel.getPortsForHost(host.id).collectAsState(initial = emptyList())

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 350.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Close and basic IP info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Node: ${host.ipAddress}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body specifications
            Row(modifier = Modifier.fillMaxWidth()) {
                // OS Badge
                Column(modifier = Modifier.weight(1f)) {
                    Text("IDENTIFIED OS:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = host.osGuess ?: "Stack analysis uncertain (Unknown OS)",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // MAC Badge
                Column(modifier = Modifier.weight(1f)) {
                    Text("HARDWARE MAC ADDRESS:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = host.macAddress ?: "00:00:00:00:00:00",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Port discovered list
            Text(
                text = "OPEN CHANNELS & PORTS DETECTED (${portsList.size}):",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (portsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All common 1000 ports returned filtered / closed responses.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    items(portsList) { port ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF22D3EE).copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "${port.portNumber}/${port.protocol.uppercase()}",
                                        color = Color(0xFF22D3EE),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = port.service?.lowercase() ?: "unknown",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Port state badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x3310B981)
                            ) {
                                Text(
                                    text = port.state.uppercase(),
                                    color = Color(0xFF10B981),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- TAB 1: HISTORY & COMPARISON SCREEN ----------------------
@Composable
fun HistoryAndDiffScreen(
    allScans: List<ScanEntity>,
    diffScanAId: Long?,
    diffScanBId: Long?,
    onScanASelected: (Long) -> Unit,
    onScanBSelected: (Long) -> Unit,
    diffResultStringList: List<String>?,
    onRunDiff: () -> Unit,
    isDiffingByEngine: Boolean,
    viewModel: NetViewModel
) {
    var expandedDropdownA by remember { mutableStateOf(false) }
    var expandedDropdownB by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section: Temporal Diff Engine Setup
        item {
            Text(
                "TEMPORAL SCAN DIFF ENGINE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                "Select any two historical scans in the database to run local differential analysis of open ports.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dropdown A Selection
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { expandedDropdownA = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (diffScanAId != null) "Scan A: #${diffScanAId}" else "Select Scan A",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                    DropdownMenu(
                        expanded = expandedDropdownA,
                        onDismissRequest = { expandedDropdownA = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        allScans.forEach { scan ->
                            DropdownMenuItem(
                                text = { Text("#${scan.id} (${scan.targetScope})", color = Color.White, fontSize = 11.sp) },
                                onClick = {
                                    onScanASelected(scan.id)
                                    expandedDropdownA = false
                                }
                            )
                        }
                    }
                }

                // Dropdown B Selection
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { expandedDropdownB = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (diffScanBId != null) "Scan B: #${diffScanBId}" else "Select Scan B",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                    DropdownMenu(
                        expanded = expandedDropdownB,
                        onDismissRequest = { expandedDropdownB = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        allScans.forEach { scan ->
                            DropdownMenuItem(
                                text = { Text("#${scan.id} (${scan.targetScope})", color = Color.White, fontSize = 11.sp) },
                                onClick = {
                                    onScanBSelected(scan.id)
                                    expandedDropdownB = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRunDiff,
                enabled = diffScanAId != null && diffScanBId != null && !isDiffingByEngine,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDiffingByEngine) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Text("EXECUTE TEMPORAL DIFF COMPLETED", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
        }

        // Section: Diff Results
        if (diffResultStringList != null) {
            item {
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TEMPORAL NETWORK DIFFERENCE ANALYSIS REPORT", color = Color(0xFF22D3EE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        diffResultStringList.forEach { logLine ->
                            Text(
                                text = logLine,
                                color = if (logLine.contains("➕")) Color(0xFF10B981) else if (logLine.contains("➖")) Color(0xFFEF4444) else Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section: Historical Scan Logs List
        item {
            Text(
                "HISTORICAL COMPLETED SCAN SESSIONS (${allScans.size})",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (allScans.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No historical scans located. Start a Live Discover Scan first, or press 'Insert Demo Data' on the primary tab to populate a simulator set.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(allScans) { scan ->
                var showHostsInHistoryList by remember { mutableStateOf(false) }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHostsInHistoryList = !showHostsInHistoryList }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Session #${scan.id}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0x3322D3EE)
                                    ) {
                                        Text(
                                            text = scan.targetScope,
                                            color = Color(0xFF22D3EE),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                val formatter = SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault())
                                Text(
                                    text = formatter.format(Date(scan.timestamp)),
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Icon(
                                imageVector = if (showHostsInHistoryList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }

                        Text(
                            text = scan.commandUsed,
                            color = Color(0xFF22D3EE),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        AnimatedVisibility(visible = showHostsInHistoryList) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                val scanHosts by viewModel.getHostsForScan(scan.id).collectAsState(initial = emptyList())
                                if (scanHosts.isEmpty()) {
                                    Text("Parsing live discovery records...", color = Color.Gray, fontSize = 11.sp)
                                } else {
                                    scanHosts.forEach { h ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(h.ipAddress, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(h.osGuess ?: "Unknown operating system", color = Color.Gray, fontSize = 10.sp)
                                            }
                                            Text(h.status.uppercase(), color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- TAB 2: SCRIPTS SCREEN ----------------------
@Composable
fun ScriptsScreen(
    nseScriptsList: List<NseScript>,
    activeNseScriptId: String?,
    onNseScriptSelected: (String) -> Unit,
    activeNseScriptArgValue: String,
    onArgValueChange: (String) -> Unit,
    generatedCommand: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "NMAP SCRIPTING ENGINE (NSE) AUDITING MANAGER",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                "The Nmap Scripting Engine allows users to run automated checks against network targets. Select a script below to configure and test specific checks.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        items(nseScriptsList) { script ->
            val isSelected = activeNseScriptId == script.id

            Surface(
                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF22D3EE) else Color(0xFF334155)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNseScriptSelected(script.id) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.List,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF22D3EE) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "--script=${script.name}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x3322D3EE)
                        ) {
                            Text(
                                text = "Port ${script.targetPort}",
                                color = Color(0xFF22D3EE),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = script.description,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Vulnerability Objective: ${script.securityObjective}",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )

                    // Expand options if active
                    AnimatedVisibility(visible = isSelected) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            if (script.defaultArgName != null) {
                                Text(
                                    text = "Configure Script Argument (${script.defaultArgName}):",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedTextField(
                                    value = activeNseScriptArgValue,
                                    onValueChange = onArgValueChange,
                                    placeholder = { Text(script.defaultArgPlaceholder ?: "") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.LightGray,
                                        focusedBorderColor = Color(0xFF22D3EE),
                                        unfocusedBorderColor = Color.Gray
                                    )
                                )
                            } else {
                                Text(
                                    text = "This script does not require external configurations. Direct integration compiled.",
                                    color = Color(0xFF22D3EE),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- TAB 3: SETTINGS SCREEN ----------------------
@Composable
fun SettingsScreen(
    streamingEnabled: Boolean,
    onStreamingToggleChange: (Boolean) -> Unit,
    activeStreamingPort: String,
    onStreamingPortChange: (String) -> Unit,
    streamLogs: List<String>,
    automationEnabled: Boolean,
    onAutomationToggleChange: (Boolean) -> Unit,
    triggerAlertOnSsh: Boolean,
    onTriggerAlertOnSshChange: (Boolean) -> Unit,
    triggerAlertOnHttp: Boolean,
    onTriggerAlertOnHttpChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section: Embedded Ktor WebSocket Streamer
        item {
            Text(
                "SECURE LOCAL WEBSOCKET STREAMING",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                "Emits completed scans as real-time JSON packets over a local WebSocket server to authorize external subnet machines.",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Simulate Streaming Server", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = if (streamingEnabled) "ws://127.0.0.1:$activeStreamingPort/ws/scan-stream" else "Engine Offline",
                                color = if (streamingEnabled) Color(0xFF10B981) else Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = streamingEnabled,
                            onCheckedChange = onStreamingToggleChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF22D3EE),
                                checkedTrackColor = Color(0xFF0891B2)
                            )
                        )
                    }

                    AnimatedVisibility(visible = streamingEnabled) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = activeStreamingPort,
                                onValueChange = onStreamingPortChange,
                                label = { Text("Web Server Local Port") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedBorderColor = Color(0xFF22D3EE),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("LIVE BROADCASTING LOGS:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                if (streamLogs.isEmpty()) {
                                    Text("Waiting for scan events to broadcast...", color = Color.Gray, fontSize = 10.sp)
                                } else {
                                    LazyColumn {
                                        items(streamLogs) { log ->
                                            Text(log, color = Color(0xFF22D3EE), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: WorkManager Post-Scan Rules
        item {
            Text(
                "POST-SCAN AUTOMATION & COMPLIANCE RULES",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Trigger automated security tasks via Android WorkManager immediately after scan reports are finalized.",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("WorkManager Rule Engine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Fires dynamic local notifications based on results", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = automationEnabled,
                            onCheckedChange = onAutomationToggleChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF22D3EE),
                                checkedTrackColor = Color(0xFF0891B2)
                            )
                        )
                    }

                    AnimatedVisibility(visible = automationEnabled) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text("ALERT CONDITIONS:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Alert immediately if SSH (Port 22) is Open", color = Color.LightGray, fontSize = 12.sp)
                                Checkbox(
                                    checked = triggerAlertOnSsh,
                                    onCheckedChange = onTriggerAlertOnSshChange,
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF22D3EE))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Alert immediately if HTTP (Port 80/443) is Open", color = Color.LightGray, fontSize = 12.sp)
                                Checkbox(
                                    checked = triggerAlertOnHttp,
                                    onCheckedChange = onTriggerAlertOnHttpChange,
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF22D3EE))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Network Auditing Cybersecurity Textbook Handbook
        item {
            Text(
                "CYBERSECURITY AUDITING TEXTBOOK",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Deep dive study handbook for networking protocols and authorized penetration audits.",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CollapsibleHandbookItem(
                    title = "1. TCP Three-Way Handshake Signatures",
                    content = "A classic TCP handshake requires three stages: SYN -> SYN/ACK -> ACK. In a standard full Connect scan (-sT), the system initiates this full loop. In a stealthy TCP SYN Scan (-sS), the scanner sends only the first SYN. When the target replies with SYN/ACK, we learn the port is active and immediately reply with a RST packet. This prevents the connection from concluding, bypassing many OS server logging utilities."
                )

                CollapsibleHandbookItem(
                    title = "2. Operating System Fingerprint Methods",
                    content = "OS stack fingerprinting probes targets with active TCP and UDP packets. Different operating systems implement RFC rules with subtle variations. By cataloging window sizes, maximum segment size parameters, TTL (Time-To-Live) values, DF (Don't Fragment) bits, and TCP options support, the algorithm can match answers against a global fingerprint dictionary to pinpoint the active kernel version."
                )

                CollapsibleHandbookItem(
                    title = "3. Fragmentation & Decoy Firewall Bypass",
                    content = "Packet fragmentation (-f) divides the standard 20-byte TCP header across multiple packets. This forces intermediate security monitors to either maintain memory state to assemble the fragments or let them pass uninspected. Decoy routing (-D) blends your scanner IP with multiple mock 'decoy' IPs, populating the target's routing tables with dozen of simultaneous scans, completely hiding the authorized investigator's actual IP address."
                )

                CollapsibleHandbookItem(
                    title = "4. Legal Auditing & Discovery Frameworks",
                    content = "Executing port scanning on unauthorized networks may trigger system outages or violate local wiretapping policies. Port audits must always be executed only on subnets and physical assets with clear written consent from authorized security admins. Always respect local scanning boundaries."
                )
            }
        }
    }
}

@Composable
fun CollapsibleHandbookItem(title: String, content: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text = content,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// ---------------------- SHARED CUSTOM DATA TYPES ----------------------
data class NetNode(
    val id: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val color: Color
)

data class NetEdge(
    val source: NetNode,
    val target: NetNode
)
