package cu.cuban.cmcubano.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cu.cuban.cmcubano.utils.VibrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fdossena.speedtest.core.Speedtest
import com.fdossena.speedtest.core.config.SpeedtestConfig
import com.fdossena.speedtest.core.serverSelector.TestPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class TestState {
    IDLE, LOADING_SERVERS, SELECTING_SERVER, PINGING, DOWNLOADING, UPLOADING, FINISHED, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(navController: NavController) {
    var state by remember { mutableStateOf(TestState.IDLE) }
    var ping by remember { mutableStateOf("—") }
    var jitter by remember { mutableStateOf("—") }
    var downloadSpeed by remember { mutableStateOf("—") }
    var uploadSpeed by remember { mutableStateOf("—") }
    var currentSpeed by remember { mutableStateOf(0f) }
    var serverName by remember { mutableStateOf("Buscando servidor...") }
    var ipInfo by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(0) }
    var uploadProgress by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var speedtestInstance by remember { mutableStateOf<Speedtest?>(null) }
    
    // Check vibration preference
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val vibrationEnabled = prefs.getBoolean("vibration_enabled", false)

    // ────────────────────────────────────────────────────────────────────────
    // CALLBACKS DEL TEST — mismos eventos que la web
    // ────────────────────────────────────────────────────────────────────────
    val speedtestHandler = remember {
        object : Speedtest.SpeedtestHandler() {

            // Llamado periódicamente durante la descarga
            // dl = velocidad actual en Mbps, progress = 0.0 a 1.0
            override fun onDownloadUpdate(dl: Double, progress: Double) {
                scope.launch {
                    downloadSpeed = "%.2f".format(dl)
                    currentSpeed = dl.toFloat()
                    downloadProgress = (progress * 100).toInt()
                    statusMessage = "Midiendo descarga..."
                    state = TestState.DOWNLOADING
                }
            }

            // Llamado periódicamente durante la subida
            // ul = velocidad actual en Mbps, progress = 0.0 a 1.0
            override fun onUploadUpdate(ul: Double, progress: Double) {
                scope.launch {
                    uploadSpeed = "%.2f".format(ul)
                    currentSpeed = ul.toFloat()
                    uploadProgress = (progress * 100).toInt()
                    statusMessage = "Midiendo subida..."
                    state = TestState.UPLOADING
                }
            }

            // Llamado periódicamente durante el test de ping
            // ping = ms, jitter = ms, progress = 0.0 a 1.0
            override fun onPingJitterUpdate(pingValue: Double, jitterValue: Double, progress: Double) {
                scope.launch {
                    ping = "%.0f".format(pingValue)
                    jitter = "%.0f".format(jitterValue)
                    statusMessage = "Midiendo ping..."
                }
            }

            // Recibe IP pública + ISP del usuario
            override fun onIPInfoUpdate(ipInfoValue: String) {
                scope.launch {
                    ipInfo = ipInfoValue
                }
            }

            // Si la telemetría está activa, recibe el ID del resultado
            override fun onTestIDReceived(id: String?, shareURL: String?) {
                // Puedes usar el ID para generar un enlace compartible
                // https://librespeed.org/result/<id>
            }

            // Test completado correctamente
            override fun onEnd() {
                scope.launch {
                    downloadProgress = 100
                    uploadProgress = 100
                    statusMessage = "✅ Test completado"
                    state = TestState.FINISHED
                    currentSpeed = 0f
                }
            }

            // Error crítico durante el test
            override fun onCriticalFailure(err: String) {
                scope.launch {
                    statusMessage = "❌ Error: $err"
                    state = TestState.ERROR
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // HELPERS DE UI
    // ────────────────────────────────────────────────────────────────────────
    fun resetUI() {
        downloadSpeed = "—"
        uploadSpeed = "—"
        ping = "—"
        jitter = "—"
        serverName = "Buscando servidor..."
        ipInfo = ""
        downloadProgress = 0
        uploadProgress = 0
        currentSpeed = 0f
    }

    fun setStatus(msg: String) {
        statusMessage = msg
    }

    fun setBotones(corriendo: Boolean) {
        // Esta función se maneja mediante el estado
    }

    val startTest: () -> Unit = {
        if (state == TestState.IDLE || state == TestState.FINISHED || state == TestState.ERROR) {
            if (vibrationEnabled) VibrationManager.vibrate(context)
            resetUI()
            statusMessage = "Cargando servidores..."
            state = TestState.LOADING_SERVERS
            
            speedtestInstance = Speedtest()
            val config = SpeedtestConfig()
            speedtestInstance!!.setSpeedtestConfig(config)

            // Cargar servidores y seleccionar automáticamente
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        // Carga la lista oficial de servidores de librespeed.org
                        speedtestInstance!!.loadServerList("//librespeed.org/backend-servers/servers.php")

                        withContext(Dispatchers.Main) {
                            statusMessage = "Seleccionando servidor automáticamente..."
                            state = TestState.SELECTING_SERVER
                        }

                        // Selecciona el servidor con menor ping de forma asíncrona
                        speedtestInstance!!.selectServer(object : Speedtest.ServerSelectedHandler() {
                            override fun onServerSelected(server: TestPoint?) {
                                if (server == null) {
                                    scope.launch {
                                        statusMessage = "❌ No se encontró ningún servidor disponible"
                                        state = TestState.ERROR
                                    }
                                    return
                                }

                                scope.launch {
                                    serverName = "Servidor: ${server.name}"
                                    statusMessage = "Iniciando test..."
                                    state = TestState.PINGING
                                    
                                    // Ejecutar el test
                                    speedtestInstance!!.start(speedtestHandler)
                                }
                            }
                        })

                    } catch (e: Exception) {
                        scope.launch {
                            statusMessage = "❌ Error cargando servidores: ${e.message}"
                            state = TestState.ERROR
                        }
                    }
                }
            }
        }
    }
    
    val stopTest: () -> Unit = {
        if (vibrationEnabled) VibrationManager.vibrate(context)
        speedtestInstance?.abort()
        statusMessage = "Test detenido"
        state = TestState.FINISHED
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Speed Test",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = if (MaterialTheme.colorScheme.background == Color.Black) Color.White else Color.Black
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (vibrationEnabled) VibrationManager.vibrate(context)
                        navController.popBackStack() 
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Atrás",
                            tint = if (MaterialTheme.colorScheme.background == Color.Black) Color.White else Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = if (MaterialTheme.colorScheme.background == Color.Black) Color.White else Color.Black,
                    navigationIconContentColor = if (MaterialTheme.colorScheme.background == Color.Black) Color.White else Color.Black
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
            val gaugeSize = minOf(screenWidth * 0.75f, screenHeight * 0.35f).coerceAtLeast(140.dp)

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Speed results row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Download Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "DOWNLOAD",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = downloadSpeed,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mbps",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }

                    // Upload Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "UPLOAD",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uploadSpeed,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mbps",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { uploadProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }

                // Ping and Jitter Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "PING",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = ping,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ms",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "JITTER",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = jitter,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ms",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Server info card - only show when server is found
                if (state != TestState.IDLE && state != TestState.LOADING_SERVERS && state != TestState.SELECTING_SERVER) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = serverName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                            
                            if (ipInfo.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ipInfo,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Status message
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Buttons at the bottom (pinned)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = startTest,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = state == TestState.IDLE || state == TestState.FINISHED || state == TestState.ERROR,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = if (state == TestState.IDLE) "START" else "RESTART",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Button(
                    onClick = stopTest,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    enabled = state != TestState.IDLE && state != TestState.FINISHED && state != TestState.ERROR,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "STOP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Speedometer(speed: Float, state: TestState, maxSpeed: Float, modifier: Modifier = Modifier) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(400, easing = EaseInOut)
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val inactiveTickColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val activeColor = MaterialTheme.colorScheme.primary
    val numberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val centerTextColor = MaterialTheme.colorScheme.onBackground
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(androidx.compose.foundation.shape.CircleShape),
            color = MaterialTheme.colorScheme.background,
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

            // Progress Arc
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
                color = numberColor
            )
        }
    }
}
}