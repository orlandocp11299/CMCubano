package cu.cuban.cmcubano.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class TestState {
    IDLE, PINGING, DOWNLOADING, UPLOADING, FINISHED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(navController: NavController) {
    var state by remember { mutableStateOf(TestState.IDLE) }
    var ping by remember { mutableStateOf("0") }
    var jitter by remember { mutableStateOf("0") }
    var downloadSpeed by remember { mutableStateOf(0f) }
    var uploadSpeed by remember { mutableStateOf(0f) }
    var currentSpeed by remember { mutableStateOf(0f) }
    
    val scope = rememberCoroutineScope()

    val startTest: () -> Unit = {
        if (state == TestState.IDLE || state == TestState.FINISHED) {
            scope.launch {
                val connected = isOnline()
                if (!connected) {
                    ping = "Error"
                    jitter = "Error"
                    return@launch
                }

                state = TestState.PINGING
                currentSpeed = 0f
                downloadSpeed = 0f
                uploadSpeed = 0f
                ping = "..."
                jitter = "..."
                
                // 1. Ping & Jitter
                val pings = measurePing()
                if (pings.size < 2) {
                    ping = "Error"
                    jitter = "Error"
                    state = TestState.FINISHED
                    return@launch
                }
                
                val avgPing = pings.average()
                
                // Jitter: standard deviation of successive RTTs
                var totalDiff = 0.0
                for (i in 0 until pings.size - 1) {
                    totalDiff += kotlin.math.abs(pings[i + 1] - pings[i])
                }
                val jitterVal = totalDiff / (pings.size - 1)
                
                ping = String.format("%.0f", avgPing)
                jitter = String.format("%.1f", jitterVal)
                delay(500)

                // 2. Download
                state = TestState.DOWNLOADING
                runDownloadTest { speed ->
                    currentSpeed = speed
                }.let { finalSpeed ->
                    downloadSpeed = finalSpeed
                    currentSpeed = 0f // Reset gauge after download phase
                }
                delay(800)

                // 3. Upload
                state = TestState.UPLOADING
                runUploadTest { speed ->
                    currentSpeed = speed
                }.let { finalSpeed ->
                    uploadSpeed = finalSpeed
                    currentSpeed = 0f // Reset gauge after test finishes
                }
                
                state = TestState.FINISHED
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Test de Velocidad",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val screenHeight = maxHeight
            val screenWidth = maxWidth
            
            // Calculate dynamic gauge size
            val gaugeSize = minOf(screenWidth * 0.75f, screenHeight * 0.40f).coerceAtLeast(160.dp)

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 100.dp), // Padding for button
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Download & Upload (Top Results)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResultHeaderItem(
                        label = "Download Mbps",
                        value = String.format("%.2f", downloadSpeed),
                        icon = Icons.Default.ArrowDownward,
                        color = Color(0xFF00E5FF)
                    )
                    ResultHeaderItem(
                        label = "Upload Mbps",
                        value = String.format("%.2f", uploadSpeed),
                        icon = Icons.Default.ArrowUpward,
                        color = Color(0xFF00E5FF)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Latency: Ping & Jitter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LatencyItem(icon = Icons.Default.SwapCalls, label = "Ping", value = "$ping ms")
                    Spacer(modifier = Modifier.width(32.dp))
                    LatencyItem(icon = Icons.Default.GraphicEq, label = "Jitter", value = "$jitter ms")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Gauge Section
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(gaugeSize)
                ) {
                    Speedometer(
                        speed = currentSpeed,
                        state = state,
                        maxSpeed = 80f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Start Button at the bottom (pinned)
            Button(
                onClick = startTest,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .heightIn(min = 48.dp, max = 60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = state == TestState.IDLE || state == TestState.FINISHED
            ) {
                Text(
                    text = if (state == TestState.IDLE) "INICIAR TEST" else if (state == TestState.FINISHED) "REPETIR TEST" else "PROBANDO...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
}

@Composable
fun ResultHeaderItem(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LatencyItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label $value",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 13.sp
        )
    }
}

@Composable
fun Speedometer(speed: Float, state: TestState, maxSpeed: Float, modifier: Modifier = Modifier) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(400, easing = EaseInOut)
    )

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val inactiveTickColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val activeColor = MaterialTheme.colorScheme.primary
    val numberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val centerTextColor = MaterialTheme.colorScheme.onSurface
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {}

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2.2f
            val startAngle = 150f
            val sweepAngle = 240f
            
            // Background Arc (Track)
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )

            // Inner markings and numbers
            val step = 10
            for (i in 0..maxSpeed.toInt() step step) {
                val angle = startAngle + (i / maxSpeed) * sweepAngle
                val rad = (angle * PI / 180f).toFloat()
                
                // Numbers
                val textRadius = radius - 45f
                val x = center.x + textRadius * cos(rad)
                val y = center.y + textRadius * sin(rad)
                
                // Note: Drawing text in Canvas requires native canvas or text measurer
                // For simplicity, we'll focus on ticks and the progress arc
                
                // Tick marks
                val tickStartRadius = radius - 15f
                val tickEndRadius = radius - 5f
                val startX = center.x + tickStartRadius * cos(rad)
                val startY = center.y + tickStartRadius * sin(rad)
                val endX = center.x + tickEndRadius * cos(rad)
                val endY = center.y + tickEndRadius * sin(rad)
                
                drawLine(
                    color = inactiveTickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2f
                )
            }
            
            // Outer Track Ticks (many small ticks)
            for (i in 0..100) {
                val angle = startAngle + (i / 100f) * sweepAngle
                val rad = (angle * PI / 180f).toFloat()
                val tickStartRadius = radius + 2f
                val tickEndRadius = radius + 10f
                
                drawLine(
                    color = if (angle <= startAngle + (animatedSpeed / maxSpeed) * sweepAngle) 
                        activeColor else inactiveTickColor,
                    start = Offset(center.x + tickStartRadius * cos(rad), center.y + tickStartRadius * sin(rad)),
                    end = Offset(center.x + tickEndRadius * cos(rad), center.y + tickEndRadius * sin(rad)),
                    strokeWidth = 2f
                )
            }

            // Progress Arc (Cyan Glow)
            val progressProgress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * progressProgress,
                useCenter = false,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
        }
        
        // Gauge Numbers (Overlay)
        // Manual positioning for numbers around the gauge
        val numberMax = maxSpeed.toInt()
        val step = 10
        Box(modifier = Modifier.fillMaxSize()) {
            for (i in 0..numberMax step step) {
                val angle = 150f + (i / maxSpeed) * 240f
                val rad = (angle * PI / 180f)
                val distance = 95.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(
                            x = (distance.value * cos(rad)).dp,
                            y = (distance.value * sin(rad)).dp
                        )
                ) {
                    Text(
                        text = i.toString(),
                        color = numberColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Central Speed Text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.2f", animatedSpeed),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = centerTextColor
            )
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

// Network Logic

// Check connectivity first
suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://www.google.com")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.connect()
        val code = conn.responseCode
        conn.disconnect()
        code == 200
    } catch (e: Exception) {
        false
    }
}

suspend fun measurePing(): List<Long> = withContext(Dispatchers.IO) {
    val results = mutableListOf<Long>()
    val targetIp = "1.1.1.1"
    
    try {
        // Use system ping command for most accurate ICMP results
        // -c 10: 10 packets
        // -i 0.2: 200ms interval
        // -w 5: timeout 5s
        val process = Runtime.getRuntime().exec("ping -c 10 -i 0.2 -w 5 $targetIp")
        val reader = process.inputStream.bufferedReader()
        reader.forEachLine { line ->
            if (line.contains("time=")) {
                val timeStr = line.substringAfter("time=").substringBefore(" ms").trim()
                timeStr.toDoubleOrNull()?.let { results.add(it.toLong()) }
            }
        }
        process.waitFor()
    } catch (e: Exception) {
        // Fallback to socket if ping command fails
        repeat(5) {
            val start = System.nanoTime()
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(targetIp, 80), 1500)
                results.add((System.nanoTime() - start) / 1_000_000)
                socket.close()
            } catch (ex: Exception) { }
            delay(100)
        }
    }
    results
}

suspend fun runDownloadTest(onProgress: (Float) -> Unit): Float = withContext(Dispatchers.IO) {
    // 200MB download to allow for longer 15s sampling
    val downloadUrl = "https://speed.cloudflare.com/__down?bytes=200000000"
    val numConnections = 4
    val connectionResults = MutableList(numConnections) { 0L }
    val startTime = System.currentTimeMillis()
    var isRunning = true
    val testDurationLimit = 15000L // 15 seconds

    val jobs = (0 until numConnections).map { index ->
        launch {
            try {
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                val input: InputStream = conn.inputStream
                val buffer = ByteArray(65536) // Larger buffer (64KB)
                var bytesRead: Int
                while (isRunning && (System.currentTimeMillis() - startTime) < testDurationLimit) {
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    connectionResults[index] += bytesRead.toLong()
                }
                input.close()
                conn.disconnect()
            } catch (e: Exception) { }
        }
    }

    var finalSpeed = 0f
    while (System.currentTimeMillis() - startTime < testDurationLimit + 500 && jobs.any { it.isActive }) {
        val totalBytes = connectionResults.sum()
        val durationSec = (System.currentTimeMillis() - startTime) / 1000.0
        if (durationSec > 0.5) {
            val speedMbps = (totalBytes * 8 / durationSec / 1_000_000).toFloat()
            finalSpeed = speedMbps
            withContext(Dispatchers.Main) { onProgress(speedMbps) }
        }
        delay(200)
    }
    isRunning = false
    jobs.forEach { it.join() }
    finalSpeed
}

suspend fun runUploadTest(onProgress: (Float) -> Unit): Float = withContext(Dispatchers.IO) {
    val url = URL("https://httpbin.org/post") 
    var speedMbps = 0f
    val startTime = System.currentTimeMillis()
    val testDurationLimit = 15000L // 15 seconds
    
    try {
        val conn = url.openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        val payloadSize = 30 * 1024 * 1024 // 30MB
        conn.setFixedLengthStreamingMode(payloadSize)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.connectTimeout = 5000
        
        val output: OutputStream = conn.outputStream
        val buffer = ByteArray(32768) { 0x01 } // 32KB buffer
        var totalBytesWritten: Long = 0
        var lastUpdate = startTime

        while (totalBytesWritten < payloadSize && (System.currentTimeMillis() - startTime) < testDurationLimit) {
            output.write(buffer)
            totalBytesWritten += buffer.size
            
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 250) {
                val durationSec = (now - startTime) / 1000.0
                if (durationSec > 0.5) {
                    speedMbps = (totalBytesWritten * 8 / durationSec / 1_000_000).toFloat()
                    withContext(Dispatchers.Main) { onProgress(speedMbps) }
                }
                lastUpdate = now
            }
        }
        output.close()
        conn.responseCode
        conn.disconnect()
    } catch (e: Exception) { }
    
    speedMbps
}