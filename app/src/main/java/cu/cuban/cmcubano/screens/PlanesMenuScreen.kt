package cu.cuban.cmcubano.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import android.telephony.SubscriptionManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import cu.cuban.cmcubano.UssdManager
import kotlinx.coroutines.flow.collectLatest
import cu.cuban.cmcubano.utils.VibrationManager

data class MenuCardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@Composable
fun BalanceItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value.takeIf { it != "N/A" } ?: "---",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanesMenuScreen(navController: NavController) {
    val menuItems = listOf(
        MenuCardItem(
            "Compra de Planes y Paquetes",
            "Datos, Voz y SMS",
            Icons.Default.ShoppingCart,
            Color(0xFFFF9800),
            "planes_list"
        ),
        MenuCardItem(
            "Consultas Rápidas",
            "Saldo, bonos y recursos disponibles",
            Icons.Default.Search,
            Color(0xFF2196F3),
            "consultas_list"
        ),
        MenuCardItem(
            "Transferencia de Saldo",
            "Envía saldo a otros números",
            Icons.Default.SwapHoriz,
            Color(0xFF4CAF50),
            "transferencia_list"
        ),
        MenuCardItem(
            "Atención al Cliente",
            "Contacta con ETECSA",
            Icons.Default.Headset,
            Color(0xFF009688),
            "atencion_cliente_list"
        ),
        MenuCardItem(
            "Recordatorios",
            "Aviso de vigencias, compra automatica y más",
            Icons.Default.Notifications,
            Color(0xFF9C27B0),
            "recordatorio_screen"
        )
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("planes_menu_prefs", Context.MODE_PRIVATE) }
    var isGridView by remember { mutableStateOf(prefs.getBoolean("is_grid_view", true)) }

    var saldo by remember { mutableStateOf(prefs.getString("saldo", "N/A") ?: "N/A") }
    var datos by remember { mutableStateOf(prefs.getString("datos", "N/A") ?: "N/A") }
    var voz by remember { mutableStateOf(prefs.getString("voz", "N/A") ?: "N/A") }
    var sms by remember { mutableStateOf(prefs.getString("sms", "N/A") ?: "N/A") }
    var vencimiento by remember { mutableStateOf(prefs.getString("vencimiento", "N/A") ?: "N/A") }
    var ultimaConsulta by remember { mutableStateOf(prefs.getString("ultima_consulta", null)) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Selector de SIM
    var selectedSimIndex by remember { mutableStateOf(0) }
    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    val activeSubscriptions = remember {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    val ussdResponse by UssdManager.ussdResponse.collectAsState()
    val isLoading by UssdManager.isLoading.collectAsState()

    LaunchedEffect(isGridView) {
        prefs.edit().putBoolean("is_grid_view", isGridView).apply()
    }

    LaunchedEffect(ussdResponse) {
        android.util.Log.d("USSD", "LaunchedEffect triggered, ussdResponse: $ussdResponse")
        ussdResponse?.let { response ->
            android.util.Log.d("USSD", "Respuesta completa: $response")

            // Verificar si la respuesta contiene error
            if (response.contains("Error", ignoreCase = true) ||
                response.contains("falló", ignoreCase = true) ||
                response.contains("no disponible", ignoreCase = true) ||
                response.contains("Android < 8", ignoreCase = true)) {
                // Mostrar error con mensaje predeterminado
                errorMessage = "La solicitud ha fallado, revise si tiene cobertura, si tiene la red 4G forzada o el modo avión activo."
                showErrorDialog = true
                android.util.Log.d("USSD", "Error en consulta: $response")
            } else {
                // Procesar respuesta exitosa
                val parsed = parseUssdResponse(response)
                android.util.Log.d("USSD", "Parsed: $parsed")
                saldo = parsed["saldo"] ?: "N/A"
                datos = parsed["datos"] ?: "N/A"
                voz = parsed["voz"] ?: "N/A"
                sms = parsed["sms"] ?: "N/A"
                vencimiento = parsed["vencimiento"] ?: "N/A"

                // Guardar datos en SharedPreferences solo si es exitoso
                prefs.edit().apply {
                    putString("saldo", saldo)
                    putString("datos", datos)
                    putString("voz", voz)
                    putString("sms", sms)
                    putString("vencimiento", vencimiento)
                    val timestamp = System.currentTimeMillis()
                    putLong("ultima_consulta_timestamp", timestamp)
                    val fecha = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                    putString("ultima_consulta", fecha)
                    apply()
                }

                ultimaConsulta = prefs.getString("ultima_consulta", null)

                android.util.Log.d("USSD", "Saldo asignado: $saldo")
                android.util.Log.d("USSD", "Datos asignado: $datos")
                android.util.Log.d("USSD", "Voz asignado: $voz")
                android.util.Log.d("USSD", "SMS asignado: $sms")
                android.util.Log.d("USSD", "Vencimiento asignado: $vencimiento")
            }

            // Limpiar la respuesta para que no aparezca en otras pantallas
            UssdManager.clearResponse()
        }
    }

    fun consultarTodo() {
        VibrationManager.vibrate(context)
        val simIndex = if (activeSubscriptions.size > 1) selectedSimIndex else -1
        UssdManager.sendUssdRequest(context, "*222#", simIndex)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    cu.cuban.cmcubano.components.ScreenTitlePill(title = "Planes y Consultas")
                },
                navigationIcon = {
                    val isDark = isSystemInDarkTheme()
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                        )
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Atrás",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    val isDark = isSystemInDarkTheme()
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                        )
                    ) {
                        IconButton(
                            onClick = { isGridView = !isGridView }
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.List else Icons.Default.Grid3x3,
                                contentDescription = if (isGridView) "Cambiar a vista de lista" else "Cambiar a vista de cuadrícula",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp, 16.dp, 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                                        .clickable {
                                            if (activeSubscriptions.size > 1) {
                                                VibrationManager.vibrate(context)
                                                selectedSimIndex = if (selectedSimIndex == 0) 1 else 0
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = "Cambiar SIM",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    // Badge para el número de SIM
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = (-2).dp, y = (-2).dp)
                                            .size(16.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = (selectedSimIndex + 1).toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Saldo y Recursos",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (ultimaConsulta != null) {
                                        Text(
                                            text = "Actualizado: $ultimaConsulta",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                IconButton(
                                    onClick = { consultarTodo() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Actualizar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BalanceItem(
                                icon = Icons.Default.AccountBalance,
                                label = "Saldo",
                                value = saldo,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.weight(1f)
                            )
                            BalanceItem(
                                icon = Icons.Default.DataUsage,
                                label = "Datos",
                                value = datos,
                                color = Color(0xFF2196F3),
                                modifier = Modifier.weight(1f)
                            )
                            BalanceItem(
                                icon = Icons.Default.Phone,
                                label = "Voz",
                                value = voz,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.weight(1f)
                            )
                            BalanceItem(
                                icon = Icons.Default.Email,
                                label = "SMS",
                                value = sms,
                                color = Color(0xFF9C27B0),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp, 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (vencimiento != "N/A") "Vence: $vencimiento" else "Vencimiento: ---",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp, 8.dp, 16.dp, 16.dp)
            ) {
                Crossfade(
                targetState = isGridView,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                ),
                label = "ViewTransition"
            ) { gridView ->
                if (gridView) {
                    Column {
                        menuItems.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { item ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(160.dp)
                                            .padding(vertical = 6.dp)
                                            .clickable { navController.navigate(item.route) },
                                        shape = RoundedCornerShape(20.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            item.color.copy(alpha = 0.12f),
                                                            item.color.copy(alpha = 0.03f)
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .padding(bottom = 8.dp),
                                                    tint = item.color
                                                )
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 18.sp
                                                    ),
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = item.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        menuItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clickable { navController.navigate(item.route) },
                                shape = RoundedCornerShape(24.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    item.color.copy(alpha = 0.15f),
                                                    item.color.copy(alpha = 0.05f)
                                                )
                                            )
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(56.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            color = item.color.copy(alpha = 0.2f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = item.color
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(20.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.subtitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
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

    if (showErrorDialog) {
        Dialog(
            onDismissRequest = { showErrorDialog = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icono de error
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Título
                    Text(
                        text = "Error al actualizar",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Contenido del mensaje
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Botón de acción
                    Button(
                        onClick = { showErrorDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Text(
                            text = "Cerrar",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

fun parseUssdResponse(response: String): Map<String, String> {
    val result = mutableMapOf<String, String>()

    android.util.Log.d("USSD", "Respuesta completa: $response")

    // Buscar cada sección directamente en la respuesta completa
    val saldoPattern = Regex("Saldo: ([\\d.]+\\s*[A-Z]+)")
    val datosPattern = Regex("Datos: ([\\d.]+\\s*[A-Z]+)")
    val vozPattern = Regex("Voz: ([\\d:]+)")
    val smsPattern = Regex("SMS: (\\d+)")
    val vencePattern = Regex("vence (\\d{2}-\\d{2}-\\d{2})")

    val saldoMatch = saldoPattern.find(response)
    if (saldoMatch != null) {
        val value = saldoMatch.groupValues[1].trim().replace("CUP", "").trim()
        android.util.Log.d("USSD", "Saldo encontrado: $value")
        result["saldo"] = value
    }

    val datosMatch = datosPattern.find(response)
    if (datosMatch != null) {
        val value = datosMatch.groupValues[1].trim()
        android.util.Log.d("USSD", "Datos encontrado: $value")
        result["datos"] = value
    }

    val vozMatch = vozPattern.find(response)
    if (vozMatch != null) {
        val value = vozMatch.groupValues[1].trim()
        android.util.Log.d("USSD", "Voz encontrado: $value")
        result["voz"] = value
    }

    val smsMatch = smsPattern.find(response)
    if (smsMatch != null) {
        val value = smsMatch.groupValues[1].trim()
        android.util.Log.d("USSD", "SMS encontrado: $value")
        result["sms"] = value
    }

    val venceMatch = vencePattern.find(response)
    if (venceMatch != null) {
        val fecha = venceMatch.groupValues[1]
        android.util.Log.d("USSD", "Vencimiento encontrado: $fecha")
        result["vencimiento"] = fecha
    }

    android.util.Log.d("USSD", "Resultado final: $result")
    return result
}

fun extractValueFromLine(line: String): String {
    android.util.Log.d("USSD", "Extrayendo valor de: $line")

    val colonIndex = line.indexOf(":")
    if (colonIndex != -1) {
        val value = line.substring(colonIndex + 1).trim()
        android.util.Log.d("USSD", "Valor por ':': $value")
        return value
    }

    android.util.Log.d("USSD", "Retornando línea completa: $line")
    return line.trim()
}

