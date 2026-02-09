package cu.cuban.cmcubano.screens

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import cu.cuban.cmcubano.DataPlanWidgetProvider
import cu.cuban.cmcubano.DataUsageWidgetProvider
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cu.cuban.cmcubano.utils.DataUsageManager
import cu.cuban.cmcubano.utils.AppDataUsage
import cu.cuban.cmcubano.utils.DataUsageDiagnostics
import cu.cuban.cmcubano.utils.VibrationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlDatosScreen(navController: NavController) {
    val context = LocalContext.current
    val dataUsageManager = remember { DataUsageManager(context) }
    val diagnostics = remember { DataUsageDiagnostics(context) }
    val scope = rememberCoroutineScope()
    var hasUsageStatsPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var mobileDataUsage by remember { mutableStateOf(0L) }
    var wifiDataUsage by remember { mutableStateOf(0L) }
    var appUsageList by remember { mutableStateOf<List<AppDataUsage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPeriod by remember { mutableStateOf("Hoy") }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var currentRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    // Lógica de Premium y Prueba de 3 min
    val isPremium = remember { cu.cuban.cmcubano.utils.PremiumManager.isPremium(context) }
    val trialPrefs = remember { context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE) }
    
    // Bloqueo inmediato si la prueba expiró y no es premium
    val trialUsed = trialPrefs.getBoolean("control_datos_trial_used", false)
    val trialEndTime = trialPrefs.getLong("control_datos_trial_end_time", 0L)
    val isTrialExpired = trialUsed && System.currentTimeMillis() >= trialEndTime
    
    if (!isPremium && isTrialExpired) {
         LaunchedEffect(Unit) {
            val popped = navController.popBackStack()
            if (!popped) {
                navController.navigate("inicio") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    var showTrialDialog by remember { mutableStateOf(!isPremium && !trialUsed) }
    var trialStarted by remember { mutableStateOf(trialUsed && System.currentTimeMillis() < trialEndTime) }
    
    // Variables para el Plan de Datos
    var planLimit by remember { mutableStateOf("") }
    var planUnit by remember { mutableStateOf("GB") }
    var planRealTimeConsumed by remember { mutableStateOf(0.0) }
    var isPlanConfigured by remember { mutableStateOf(false) }

    // Función unificada para cargar datos
    fun refreshData(period: String, silent: Boolean = false) {
        if (!silent) isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val (startTime, endTime) = calculateTimeRange(period)
                
                // Ejecutar consultas en paralelo para mayor velocidad
                val (mobileUsage, wifiUsage, appUsage) = coroutineScope {
                    val mobileUsageDeferred = async { dataUsageManager.getMobileDataUsage(startTime, endTime) }
                    val wifiUsageDeferred = async { dataUsageManager.getWifiDataUsage(startTime, endTime) }
                    val appUsageDeferred = async { dataUsageManager.getAppDataUsage(startTime, endTime) }
                    
                    Triple(mobileUsageDeferred.await(), wifiUsageDeferred.await(), appUsageDeferred.await())
                }
                
                // LÓGICA DEL PLAN DE DATOS
                val prefs = context.getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
                val pLimit = prefs.getString("data_plan_limit", "") ?: ""
                val pUnit = prefs.getString("data_plan_unit", "GB") ?: "GB"
                val pConsumedStr = prefs.getString("data_plan_consumed", "0") ?: "0"
                val pConsumed = pConsumedStr.replace(',', '.').toDoubleOrNull() ?: 0.0
                // Convertir la base a MB si estaba en GB
                val pConsumedInMb = if (pUnit == "GB") pConsumed * 1024.0 else pConsumed
                
                val pLastUpdate = prefs.getLong("data_plan_last_update_time", 0L)
                val pPurchaseDate = prefs.getLong("data_plan_purchase_date", 0L)
                
                val hasPlan = pLimit.isNotEmpty() && pPurchaseDate > 0
                var calculatedRealTime = pConsumedInMb

                if (hasPlan && pLastUpdate > 0) {
                     // LÓGICA SIMPLE: Consumo = Base Manual + Lo que diga el sistema desde el momento de la base
                     val deltaUsageBytes = dataUsageManager.getMobileDataUsage(pLastUpdate, System.currentTimeMillis())
                     val deltaUsageMb = deltaUsageBytes / (1024.0 * 1024.0)
                     calculatedRealTime = pConsumedInMb + deltaUsageMb
                }

                withContext(Dispatchers.Main) {
                    mobileDataUsage = mobileUsage
                    wifiDataUsage = wifiUsage
                    currentRange = Pair(startTime, endTime)
                    
                    // Actualizar estado del plan para la UI
                    planLimit = pLimit
                    planUnit = pUnit
                    planRealTimeConsumed = calculatedRealTime
                    isPlanConfigured = hasPlan
                    
                    // Solo actualizamos la preferencia si queremos que el campo en Ajustes se vea actualizado,
                    // pero sin mover pLastUpdate para no perder la referencia original.
                    val formattedLive = if (pUnit == "GB") {
                        String.format(java.util.Locale.US, "%.3f", calculatedRealTime / 1024.0)
                    } else {
                        String.format(java.util.Locale.US, "%.0f", calculatedRealTime)
                    }
                    if (pConsumedStr != formattedLive) {
                        prefs.edit().putString("data_plan_consumed_live", formattedLive).apply()
                    }

                    if (appUsageList != appUsage) {
                        appUsageList = appUsage
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    // Carga inicial inmediata si hay permisos
    LaunchedEffect(Unit) {
        if (hasUsageStatsPermission) {
            refreshData(selectedPeriod)
        }
    }

    // Verificar permisos al iniciar y cuando se regresa a la pantalla
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPermission = checkUsageStatsPermission(context)
                val permissionChanged = hasUsageStatsPermission != currentPermission
                hasUsageStatsPermission = currentPermission
                
                if (!currentPermission) {
                    showPermissionDialog = true
                } else if (permissionChanged) {
                    // Solo refrescar si el permiso acaba de ser concedido
                    showPermissionDialog = false
                    refreshData(selectedPeriod)
                } else if (appUsageList.isEmpty() && !isLoading) {
                    // Refrescar si tenemos permiso pero no hay datos cargados
                    refreshData(selectedPeriod)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Refrescar datos periódicamente si la pantalla está activa
    LaunchedEffect(hasUsageStatsPermission, selectedPeriod) {
        if (hasUsageStatsPermission) {
            while (true) {
                delay(10000) // Actualizar cada 10 segundos
                refreshData(selectedPeriod, silent = true)
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onGrant = {
                requestUsageStatsPermission(context)
                showPermissionDialog = false
            },
            onDismiss = { navController.popBackStack() }
        )
    }

    if (showTrialDialog) {
        AlertDialog(
            onDismissRequest = { 
                showTrialDialog = false
                trialStarted = true
                val endTime = System.currentTimeMillis() + 180000
                trialPrefs.edit()
                    .putBoolean("control_datos_trial_used", true)
                    .putLong("control_datos_trial_end_time", endTime)
                    .apply()
                
                // Forzar actualización de widgets al iniciar prueba
                val refreshPlan = Intent(context, DataPlanWidgetProvider::class.java).apply {
                    action = "cu.cuban.cmcubano.DATA_PLAN_REFRESH"
                }
                context.sendBroadcast(refreshPlan)
                
                val refreshUsage = Intent(context, DataUsageWidgetProvider::class.java).apply {
                    action = "cu.cuban.cmcubano.DATA_USAGE_REFRESH"
                }
                context.sendBroadcast(refreshUsage)
            },
            title = {
                Text(
                    text = "Advertencia",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Para usar esta función es necesario activar la suscripción premium desde los ajustes de la aplicación. Solo podrá usar este apartado como prueba por 3 min.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showTrialDialog = false
                        trialStarted = true
                        val endTime = System.currentTimeMillis() + 180000
                        trialPrefs.edit()
                            .putBoolean("control_datos_trial_used", true)
                            .putLong("control_datos_trial_end_time", endTime)
                            .apply()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Aceptar")
                }
            }
        )
    }

    LaunchedEffect(trialStarted) {
        if (trialStarted) {
            val endTime = trialPrefs.getLong("control_datos_trial_end_time", 0L)
            val remaining = endTime - System.currentTimeMillis()
            if (remaining > 0) {
                delay(remaining)
                navController.popBackStack()
            } else if (!isPremium) {
                // Si el tiempo ya expiró y no es premium, fuera
                navController.popBackStack()
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            diagnostics = diagnostics,
            onDismiss = { showDiagnostics = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header con título
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }
            
            Text(
                text = "Control de Datos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )

            IconButton(onClick = { 
                VibrationManager.vibrate(context)
                navController.navigate("control_datos_settings") 
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selector de período
        PeriodSelector(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = { period ->
                selectedPeriod = period
                if (hasUsageStatsPermission) {
                    refreshData(period, silent = true)
                }
            }
        )

        // Mostrar rango de fechas si es Semana
        if (selectedPeriod == "Semana" && currentRange != null) {
            val sdf = SimpleDateFormat("d MMM", Locale("es"))
            val rangeName = "${sdf.format(Date(currentRange!!.first))} - ${sdf.format(Date(currentRange!!.second))}"
            
            Text(
                text = rangeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = selectedPeriod,
            transitionSpec = {
                fadeIn(animationSpec = tween(600, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(animationSpec = tween(500, easing = FastOutSlowInEasing))
            },
            label = "periodTransition",
            modifier = Modifier.weight(1f)
        ) { targetPeriod ->
            Column {
                if (!hasUsageStatsPermission) {
                    PermissionRequiredCard(onRequestPermission = {
                        requestUsageStatsPermission(context)
                    })
                } else if (isLoading && appUsageList.isEmpty()) {
                    LoadingIndicator()
                } else {
                    if (isPlanConfigured) {
                        DataPlanCard(
                            limit = planLimit.toDoubleOrNull() ?: 0.0,
                            unit = planUnit,
                            consumedMb = planRealTimeConsumed
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
        
                    // Tarjetas de resumen
                    DataSummaryCards(
                        mobileDataUsage = mobileDataUsage,
                        wifiDataUsage = wifiDataUsage,
                        totalDataUsage = mobileDataUsage + wifiDataUsage
                    )
        
                    Spacer(modifier = Modifier.height(24.dp))
        
                    // Lista de aplicaciones
                    Text(
                        text = "Consumo por Aplicaciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
        
                    Spacer(modifier = Modifier.height(12.dp))
        
                    AppUsageList(
                        appUsageList = appUsageList,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Permiso Requerido")
        },
        text = {
            Text("Para monitorear el uso de datos, necesitamos acceso a las estadísticas de uso del dispositivo. Por favor, concede el permiso de 'Acceso a uso de datos' en la siguiente pantalla.")
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text("Conceder Permiso")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun PermissionRequiredCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = "Permiso requerido",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permiso Requerido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Se necesita acceso a las estadísticas de uso para monitorear el consumo de datos.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Conceder Permiso")
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cargando datos de uso...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun PeriodSelector(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    val periods = listOf("Hoy", "Ayer", "Semana", "Mes")
    val context = LocalContext.current
    val selectedIndex = periods.indexOf(selectedPeriod).coerceAtLeast(0)
    
    val pillOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillOffset"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        val pillWidth = maxWidth / periods.size
        Box(
            modifier = Modifier
                .width(pillWidth)
                .fillMaxHeight()
                .offset(x = pillWidth * pillOffset)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
        )
        Row(modifier = Modifier.fillMaxSize()) {
            periods.forEachIndexed { index, period ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            VibrationManager.vibrate(context)
                            onPeriodSelected(period)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period,
                        fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedPeriod == period) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DataSummaryCards(
    mobileDataUsage: Long,
    wifiDataUsage: Long,
    totalDataUsage: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DataCard(
            title = "Móvil",
            usage = mobileDataUsage,
            icon = Icons.Default.SignalCellular4Bar,
            color = Color(0xFF2196F3),
            modifier = Modifier.weight(1f)
        )
        
        DataCard(
            title = "WiFi",
            usage = wifiDataUsage,
            icon = Icons.Default.Wifi,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
        
        DataCard(
            title = "Total",
            usage = totalDataUsage,
            icon = Icons.Default.DataUsage,
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
    }
}
@Composable
fun DataPlanCard(
    limit: Double,
    unit: String,
    consumedMb: Double
) {
    val limitMb = if (unit == "GB") limit * 1024.0 else limit
    val remainingMb = (limitMb - consumedMb).coerceAtLeast(0.0)
    val progress = if (limitMb > 0) (consumedMb / limitMb).toFloat().coerceIn(0f, 1f) else 0f
    
    val color = when {
        progress > 0.9f -> MaterialTheme.colorScheme.error
        progress > 0.7f -> Color(0xFFFF9800) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.15f),
                            color.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Plan de Datos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = "Cuota total: $limit $unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Consumo central grande
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (unit == "GB") String.format("%.3f GB", consumedMb / 1024.0) else String.format("%.0f MB", consumedMb),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Consumidos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Barra de progreso premium
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.1f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f%% usado", progress * 100),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = "Restante: " + if (unit == "GB") String.format("%.3f GB", remainingMb / 1024.0) else String.format("%.0f MB", remainingMb),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingMb < (limitMb * 0.1)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}


@Composable
fun DataCard(
    title: String,
    usage: Long,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.1f),
                            color.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatBytes(usage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AppUsageList(
    appUsageList: List<AppDataUsage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = appUsageList,
            key = { it.packageName }
        ) { appUsage ->
            AppUsageItem(appUsage = appUsage)
        }
    }
}

@Composable
fun AppUsageItem(appUsage: AppDataUsage) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    
    // Intentar cargar el icono real de la aplicación
    val appIcon = remember(appUsage.packageName) {
        try {
            val drawable = packageManager.getApplicationIcon(appUsage.packageName)
            if (drawable != null) {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono real de la app o placeholder
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = "App Icon",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appUsage.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Móvil
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellular4Bar,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color(0xFF2196F3)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatBytes(appUsage.mobileBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2196F3),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    // WiFi
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatBytes(appUsage.wifiBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    // Total
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatBytes(appUsage.mobileBytes + appUsage.wifiBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun requestUsageStatsPermission(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    context.startActivity(intent)
}

private fun calculateTimeRange(period: String): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    
    return when (period) {
        "Hoy" -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            // Para hoy, el fin puede ser el final del día o ahora mismo
            Pair(startTime, now)
        }
        "Ayer" -> {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis
            Pair(startTime, endTime)
        }
        "Semana" -> {
            // Establecer al primer día de la semana según el locale (usualmente Domingo o Lunes)
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // Si el resultado es en el futuro con respecto a hoy, retroceder una semana
            val tempNow = Calendar.getInstance()
            if (calendar.after(tempNow)) {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
            }
            
            val startTime = calendar.timeInMillis
            
            // Sumar 6 días para completar la semana (7 días en total)
            calendar.add(Calendar.DAY_OF_YEAR, 6)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis
            
            Pair(startTime, endTime)
        }
        "Mes" -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            // Ir al último día del mes actual
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis
            
            Pair(startTime, endTime)
        }
        else -> {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            Pair(calendar.timeInMillis, now)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

@Composable
fun DiagnosticsDialog(
    diagnostics: DataUsageDiagnostics,
    onDismiss: () -> Unit
) {
    val report = remember { diagnostics.generateDiagnosticReport() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Diagnóstico del Sistema",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Timestamp: ${report.timestamp}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "Android: ${report.androidVersion}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                HorizontalDivider()
                
                Text(
                    text = "Permisos:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "PACKAGE_USAGE_STATS: ${if (report.hasUsageStatsPermission) "✅ Concedido" else "❌ No concedido"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                HorizontalDivider()
                
                Text(
                    text = "TrafficStats:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Disponible: ${if (report.trafficStatsInfo.isAvailable) "✅" else "❌"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (report.trafficStatsInfo.isAvailable) {
                    Text(
                        text = "Total RX: ${formatBytes(report.trafficStatsInfo.totalRx)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Total TX: ${formatBytes(report.trafficStatsInfo.totalTx)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Móvil RX: ${formatBytes(report.trafficStatsInfo.mobileRx)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Móvil TX: ${formatBytes(report.trafficStatsInfo.mobileTx)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                HorizontalDivider()
                
                Text(
                    text = "NetworkStatsManager:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Disponible: ${if (report.networkStatsInfo.isAvailable) "✅" else "❌"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (!report.networkStatsInfo.isAvailable) {
                    Text(
                        text = "Razón: ${report.networkStatsInfo.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                HorizontalDivider()
                
                Text(
                    text = "Operador:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = report.carrierInfo,
                    style = MaterialTheme.typography.bodySmall
                )
                
                HorizontalDivider()
                
                Text(
                    text = "Recomendaciones:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                report.recommendations.forEach { recommendation ->
                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recommendation.startsWith("❌")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
