package com.gmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmap.data.HostEntity
import com.gmap.viewmodel.NetViewModel
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.random.Random
import com.gmap.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetInsightApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetInsightApp(viewModel: NetViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var targetIp by remember { mutableStateOf("192.168.1.0/24") }
    
    val allHosts by viewModel.allHosts.collectAsState(initial = emptyList())
    var tcpSynScan by remember { mutableStateOf(true) }
    var osDetection by remember { mutableStateOf(false) }
    var fragmentPackets by remember { mutableStateOf(false) }
    var timingTemplate by remember { mutableFloatStateOf(3f) }
    
    var isScanning by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf<String?>(null) }
    
    val generatedCommand by remember {
        derivedStateOf {
            buildString {
                append("nmap ")
                if (tcpSynScan) append("-sS ")
                if (osDetection) append("-O ")
                if (fragmentPackets) append("-f ")
                append("-T${timingTemplate.toInt()} ")
                append(targetIp)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NetLens Pro",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                }
                Text(
                    text = "AUTHORIZED SESSION · ETH0",
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(
                onClick = { },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Scan Profile Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Visual Builder", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            TextButton(
                onClick = { },
                modifier = Modifier.weight(1f)
            ) {
                Text("Library", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            TextButton(
                onClick = { },
                modifier = Modifier.weight(1f)
            ) {
                Text("Scripts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
        ) {
            if (isScanning || allHosts.isNotEmpty()) {
                MockTopologyCanvas(hosts = allHosts, modifier = Modifier.fillMaxSize())

                // Floating Status Pill
                if (isScanning) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF34D399), RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SYN SCAN ACTIVE · DISCOVERING", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("Target Scope") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Visual Command Constructor",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { showTooltip = "TCP SYN (-sS): Half-open stealth scan. Educational signature: SYN -> SYN/ACK -> RST." }
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TCP SYN Scan", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Stealthy half-open scan", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(
                                checked = tcpSynScan,
                                onCheckedChange = { tcpSynScan = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { showTooltip = "OS Detection (-O): Uses TCP/IP stack fingerprinting." }
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("OS Detection", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Fingerprint target OS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(
                                checked = osDetection,
                                onCheckedChange = { osDetection = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { showTooltip = "Fragment Packets (-f): Splits TCP headers over several packets." }
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fragment Packets", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Test packet reassembly defenses", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(
                                checked = fragmentPackets,
                                onCheckedChange = { fragmentPackets = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        Text("Aggressiveness (Timing Template)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Slider(
                            value = timingTemplate,
                            onValueChange = { timingTemplate = it },
                            valueRange = 0f..5f,
                            steps = 4,
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paranoid (0)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            Text("Insane (5)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Live Translation Bar (Command Preview)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("LIVE COMMAND GENERATION", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                    Text("STABLE", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp)
                }
                Text(
                    text = generatedCommand,
                    color = Color(0xFF67E8F9), // cyan-300
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Bottom Navigation / Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, contentDescription = "Discover", tint = MaterialTheme.colorScheme.secondary)
                        Text("DISCOVER", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.List, contentDescription = "History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("HISTORY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.List, contentDescription = "Scripts", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("SCRIPTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Settings, contentDescription = "Me", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("ME", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            
            // Main Action FAB
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-24).dp)
            ) {
                IconButton(
                    onClick = { 
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
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Close else Icons.Default.CheckCircle,
                        contentDescription = "Scan",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    if (showTooltip != null) {
        AlertDialog(
            onDismissRequest = { showTooltip = null },
            title = { Text("Educational Insight", color = MaterialTheme.colorScheme.primary) },
            text = { Text(showTooltip!!) },
            confirmButton = {
                TextButton(onClick = { showTooltip = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

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

@Composable
fun MockTopologyCanvas(hosts: List<HostEntity>, modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    
    val nodes = remember { androidx.compose.runtime.mutableStateListOf<NetNode>() }
    val edges = remember { androidx.compose.runtime.mutableStateListOf<NetEdge>() }

    // Sync database hosts to the physics engine nodes
    LaunchedEffect(hosts) {
        val existingIps = nodes.map { it.id }.toSet()
        val newHosts = hosts.filter { it.ipAddress !in existingIps }
        
        if (nodes.isEmpty() && hosts.isNotEmpty()) {
            nodes.add(NetNode("router_gateway", color = Color(0xFF06B6D4)))
        }
        
        newHosts.forEach { host ->
            val isRouter = host.osGuess?.contains("Router") == true
            val color = if (isRouter) Color(0xFFFB7185) else Color(0xFF34D399)
            val newNode = NetNode(host.ipAddress, color = color)
            nodes.add(newNode)
            
            val router = nodes.firstOrNull { it.id == "router_gateway" }
            if (router != null && newNode.id != router.id) {
                edges.add(NetEdge(router, newNode))
            }
        }
    }

    LaunchedEffect(canvasSize, nodes.size) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        
        val width = canvasSize.width.toFloat()
        val height = canvasSize.height.toFloat()
        
        nodes.forEach {
            if (it.id == "router_gateway") {
                it.x = width / 2f
                it.y = height / 2f
            } else if (it.x == 0f && it.y == 0f) {
                // Initialize new nodes near the center
                it.x = width / 2f + (Random.nextFloat() - 0.5f) * 100f
                it.y = height / 2f + (Random.nextFloat() - 0.5f) * 100f
            }
        }

        val repulsion = 200000f
        val spring = 0.005f
        val idealLength = 300f
        val damping = 0.8f

        while (isActive) {
            withFrameNanos {
                // Apply repulsion
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

                // Apply spring forces
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

                // Center gravity and bounds, update positions
                nodes.forEach { n ->
                    if (n.id == "router_gateway") {
                        // Keep router near center
                        n.x += (width / 2f - n.x) * 0.1f
                        n.y += (height / 2f - n.y) * 0.1f
                        n.vx = 0f
                        n.vy = 0f
                    } else {
                        // Pull towards center gently
                        n.vx += (width / 2f - n.x) * 0.001f
                        n.vy += (height / 2f - n.y) * 0.001f
                        
                        n.vx *= damping
                        n.vy *= damping
                        n.x += n.vx
                        n.y += n.vy
                        
                        // Bounds
                        n.x = n.x.coerceIn(50f, width - 50f)
                        n.y = n.y.coerceIn(50f, height - 50f)
                    }
                }
            }
        }
    }

    // Force recomposition when nodes update
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frame++ }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += offsetChange
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .transformable(state = transformableState)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    ) {
        // use frame to recompose
        val currentFrame = frame
        
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Grid Background
        val gridSize = 24.dp.toPx()
        for (x in 0..canvasWidth.toInt() step gridSize.toInt()) {
            for (y in 0..canvasHeight.toInt() step gridSize.toInt()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.03f),
                    radius = 1.dp.toPx(),
                    center = Offset(x.toFloat(), y.toFloat())
                )
            }
        }

        // Draw connecting lines
        edges.forEach { edge ->
            drawLine(
                color = edge.target.color.copy(alpha = 0.3f),
                start = Offset(edge.source.x, edge.source.y),
                end = Offset(edge.target.x, edge.target.y),
                strokeWidth = 2f
            )
        }

        // Draw Nodes
        nodes.forEach { node ->
            val isRouter = node.id == "router_gateway"
            val radius = if (isRouter) 24.dp.toPx() else 16.dp.toPx()
            
            drawCircle(
                color = node.color.copy(alpha = 0.1f),
                radius = radius,
                center = Offset(node.x, node.y)
            )
            drawCircle(
                color = node.color,
                radius = radius,
                center = Offset(node.x, node.y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isRouter) 2.dp.toPx() else 1.dp.toPx())
            )
            drawCircle(
                color = node.color,
                radius = 4.dp.toPx(),
                center = Offset(node.x, node.y)
            )
        }
    }
}
