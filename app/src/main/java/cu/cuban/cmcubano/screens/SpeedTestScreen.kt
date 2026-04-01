package cu.cuban.cmcubano.screens

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cu.cuban.cmcubano.utils.SpeedTestHistoryManager
import cu.cuban.cmcubano.utils.SpeedTestResult
import cu.cuban.cmcubano.utils.VibrationManager
import kotlinx.coroutines.Dispatchers
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

    // Detect current network type
    val networkType: String = remember {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        when {
            caps == null -> "Sin conexión"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos Móviles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Desconocido"
        }
    }

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
                    statusMessage = "Completado"
                    state = TestState.FINISHED
                    currentSpeed = 0f

                    // Guardar en historial
                    val result = SpeedTestResult(
                        download = downloadSpeed,
                        upload = uploadSpeed,
                        ping = ping,
                        jitter = jitter,
                        networkType = networkType,
                        status = "success"
                    )
                    SpeedTestHistoryManager.saveResult(context, result)
                }
            }

            // Error crítico durante el test
            override fun onCriticalFailure(err: String) {
                scope.launch {
                    statusMessage = "Error: ${err}"
                    state = TestState.ERROR

                    // Guardar en historial como fallido
                    val result = SpeedTestResult(
                        download = "—",
                        upload = "—",
                        ping = if (ping != "—") ping else "—",
                        jitter = if (jitter != "—") jitter else "—",
                        networkType = networkType,
                        status = "failed"
                    )
                    SpeedTestHistoryManager.saveResult(context, result)
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
                                        statusMessage = "No se encontró ningún servidor disponible"
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
                            statusMessage = "Error cargando servidores: ${e.message}"
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

    val primaryBlue = MaterialTheme.colorScheme.primary
    val accentGreen = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onBackground

    val statusLabel = when (state) {
        TestState.IDLE -> "Listo"
        TestState.LOADING_SERVERS, TestState.SELECTING_SERVER -> "Preparando..."
        TestState.PINGING -> "Midiendo ping..."
        TestState.DOWNLOADING -> "Descargando..."
        TestState.UPLOADING -> "Subiendo..."
        TestState.FINISHED -> "Completado"
        TestState.ERROR -> "Error"
    }
    val statusColor = when (state) {
        TestState.FINISHED -> accentGreen
        TestState.ERROR -> MaterialTheme.colorScheme.error
        TestState.IDLE -> labelColor
        else -> primaryBlue
    }

    val isRunning = state != TestState.IDLE && state != TestState.FINISHED && state != TestState.ERROR

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    cu.cuban.cmcubano.components.ScreenTitlePill(title = "Speed Test")
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        IconButton(onClick = {
                            if (vibrationEnabled) VibrationManager.vibrate(context)
                            navController.popBackStack()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    // Botón historial
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        IconButton(onClick = {
                            if (vibrationEnabled) VibrationManager.vibrate(context)
                            navController.navigate("speedtest_history")
                        }) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = "Historial",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Speedometer card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Speedometer with text overlaid inside
                    SemiCircleSpeedometer(
                        speed = currentSpeed,
                        maxSpeed = 100f,
                        modifier = Modifier.fillMaxWidth(),
                        primaryColor = primaryBlue,
                        speedText = if (isRunning) "%.1f".format(currentSpeed) else "0.0",
                        unitText = "Mbps",
                        statusText = statusLabel,
                        statusColor = statusColor
                    )

                    // Indicador de tipo de red — sutil, sin llamar la atención
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val netIcon = if (networkType == "WiFi") Icons.Filled.Wifi else Icons.Filled.SignalCellularAlt
                        Icon(
                            netIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            networkType,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }

                    // Server / IP info below arc
                    if (state != TestState.IDLE && state != TestState.LOADING_SERVERS && state != TestState.SELECTING_SERVER) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = serverName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        if (ipInfo.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = ipInfo,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Metrics grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        label = "Ping",
                        value = ping,
                        unit = "ms",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Jitter",
                        value = jitter,
                        unit = "ms",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        label = "Descarga",
                        value = downloadSpeed,
                        unit = "Mbps",
                        modifier = Modifier.weight(1f),
                        progress = downloadProgress / 100f,
                        showProgress = state == TestState.DOWNLOADING || state == TestState.FINISHED,
                        progressColor = primaryBlue
                    )
                    MetricCard(
                        label = "Subida",
                        value = uploadSpeed,
                        unit = "Mbps",
                        modifier = Modifier.weight(1f),
                        progress = uploadProgress / 100f,
                        showProgress = state == TestState.UPLOADING || state == TestState.FINISHED,
                        progressColor = accentGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Main button
            Button(
                onClick = if (isRunning) stopTest else startTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else primaryBlue,
                    contentColor = if (isRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = when (state) {
                        TestState.IDLE -> "Iniciar prueba"
                        TestState.FINISHED -> "Repetir prueba"
                        TestState.ERROR -> "Reintentar"
                        else -> "Detener"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Semi-circle speedometer with overlaid text inside the arc
@Composable
fun SemiCircleSpeedometer(
    speed: Float,
    maxSpeed: Float,
    modifier: Modifier = Modifier,
    primaryColor: Color,
    speedText: String,
    unitText: String,
    statusText: String,
    statusColor: Color
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(400, easing = EaseInOut),
        label = "speedAnim"
    )

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val glowColor = primaryColor.copy(alpha = 0.20f)
    val onBg = MaterialTheme.colorScheme.onBackground
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val diameter = minOf(maxWidth - 32.dp, 204.dp).coerceAtLeast(160.dp)
        val strokeWidth = 13.dp
        val topPad = 8.dp
        val radius = diameter / 2
        val startAngle = 140f
        val sweepTotal = 260f

        // Bottom of the arc ends at angle 40° (sin 40° ≈ 0.643):
        val gaugeHeight = topPad + strokeWidth + (radius * 1.643f)

        Box(
            modifier = Modifier
                .width(diameter + strokeWidth + 8.dp)
                .height(gaugeHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = strokeWidth.toPx()
                val radiusPx = radius.toPx()
                val centerX = size.width / 2f
                val centerY = (topPad + strokeWidth / 2).toPx() + radiusPx

                val topLeft = Offset(centerX - radiusPx, centerY - radiusPx)
                val arcSize = Size(radiusPx * 2f, radiusPx * 2f)
                val progress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)

                // Background track arc
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Progress arc
                if (progress > 0.005f) {
                    drawArc(
                        color = glowColor,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal * progress,
                        useCenter = false,
                        topLeft = Offset(topLeft.x - 2.dp.toPx(), topLeft.y - 2.dp.toPx()),
                        size = Size(arcSize.width + 4.dp.toPx(), arcSize.height + 4.dp.toPx()),
                        style = Stroke(width = strokePx + 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = primaryColor,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }

            // Text cluster positioned comfortably inside the arc (well below the top curve)
            Column(
                modifier = Modifier
                    .padding(top = topPad + strokeWidth + (radius * 0.28f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = speedText,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = onBg,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 42.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = unitText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = onSurface
                )
                if (statusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

// ── Metric card
@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    showProgress: Boolean = false,
    progressColor: Color = Color(0xFF29B6F6)
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                if (value != "—") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            if (showProgress && progress > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}

// Legacy Speedometer kept to avoid breaking any other references
@Composable
fun Speedometer(speed: Float, state: TestState, maxSpeed: Float, modifier: Modifier = Modifier) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(400, easing = EaseInOut),
        label = "legacySpeedAnim"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 150f
            val sweepAngle = 240f
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
            val progressFrac = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * progressFrac,
                useCenter = false,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.2f", animatedSpeed),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
