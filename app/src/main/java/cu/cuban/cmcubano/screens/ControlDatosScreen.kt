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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
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
    var monthOffset by remember { mutableStateOf(0) }
    var weekOffset by remember { mutableStateOf(0) }
    var dayOffset by remember { mutableStateOf(0) }

    // Lógica de preferencias persistentes (vista y filtro de aplicaciones)
    val prefs = remember { context.getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE) }
    var isGridView by remember { mutableStateOf(prefs.getBoolean("is_grid_view", true)) }
    var sortFilter by remember { mutableStateOf(prefs.getString("app_sort_filter", "Total") ?: "Total") }

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
                val (startTime, endTime) = calculateTimeRange(period, monthOffset, weekOffset, dayOffset)
                
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

    var showAllApps by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val titleTextColor = MaterialTheme.colorScheme.onSurface
    val subtitleTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBgColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(22.dp),
                color = cardBgColor,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                )
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = titleTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                color = cardBgColor,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Control de datos",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = titleTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(22.dp),
                color = cardBgColor,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                )
            ) {
                IconButton(onClick = {
                    VibrationManager.vibrate(context)
                    navController.navigate("control_datos_settings")
                }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Ajustes",
                        tint = titleTextColor
                    )
                }
            }
        }

        // Selector de período
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            PeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { period ->
                    selectedPeriod = period
                    if (period != "Mes") {
                        monthOffset = 0
                    }
                    if (period != "Semana") {
                        weekOffset = 0
                    }
                    if (period != "Ayer") {
                        dayOffset = 0
                    } else {
                        // Al seleccionar Ayer, asegurar que dayOffset sea 0
                        dayOffset = 0
                    }
                    // Invalidar currentRange para evitar mostrar datos incorrectos
                    currentRange = null
                    if (hasUsageStatsPermission) {
                        refreshData(period, silent = true)
                    }
                }
            )
        }

        // Mostrar fecha seleccionada si es Ayer, rango de fechas si es Semana o nombre del mes si es Mes
        if ((selectedPeriod == "Ayer" || selectedPeriod == "Semana" || selectedPeriod == "Mes") && currentRange != null) {
            if (selectedPeriod == "Ayer") {
                val sdf = SimpleDateFormat("d MMM yyyy", Locale("es"))
                val dateStr = sdf.format(Date(currentRange!!.first)).replaceFirstChar { it.uppercase() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            VibrationManager.vibrate(context)
                            if (dayOffset < 180) {
                                dayOffset++
                                refreshData("Ayer", silent = true)
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = dayOffset < 180
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Día anterior",
                            tint = if (dayOffset < 180) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            VibrationManager.vibrate(context)
                            val initialCal = java.util.Calendar.getInstance().apply {
                                timeInMillis = currentRange!!.first
                            }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCal = java.util.Calendar.getInstance()
                                    selectedCal.set(year, month, dayOfMonth)
                                    selectedCal.set(Calendar.HOUR_OF_DAY, 0)
                                    selectedCal.set(Calendar.MINUTE, 0)
                                    selectedCal.set(Calendar.SECOND, 0)
                                    selectedCal.set(Calendar.MILLISECOND, 0)

                                    val now = java.util.Calendar.getInstance()
                                    val diffDays = ((now.timeInMillis - selectedCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                                    dayOffset = diffDays.coerceIn(0, 180)
                                    refreshData("Ayer", silent = true)
                                    VibrationManager.vibrate(context)
                                },
                                initialCal.get(java.util.Calendar.YEAR),
                                initialCal.get(java.util.Calendar.MONTH),
                                initialCal.get(java.util.Calendar.DAY_OF_MONTH)
                            ).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            VibrationManager.vibrate(context)
                            if (dayOffset > 0) {
                                dayOffset--
                                refreshData("Ayer", silent = true)
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = dayOffset > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Día siguiente",
                            tint = if (dayOffset > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else if (selectedPeriod == "Semana") {
                val sdf = SimpleDateFormat("d MMM", Locale("es"))
                val rangeName = "${sdf.format(Date(currentRange!!.first))} - ${sdf.format(Date(currentRange!!.second))}"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            VibrationManager.vibrate(context)
                            if (weekOffset < 26) {
                                weekOffset++
                                refreshData("Semana", silent = true)
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = weekOffset < 26
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Semana anterior",
                            tint = if (weekOffset < 26) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = rangeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            VibrationManager.vibrate(context)
                            if (weekOffset > 0) {
                                weekOffset--
                                refreshData("Semana", silent = true)
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = weekOffset > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Semana siguiente",
                            tint = if (weekOffset > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                // Mes con navegación
                val sdf = SimpleDateFormat("MMMM yyyy", Locale("es"))
                val monthName = sdf.format(Date(currentRange!!.first)).replaceFirstChar { it.uppercase() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            VibrationManager.vibrate(context)
                            if (monthOffset < 6) {
                                monthOffset++
                                refreshData("Mes", silent = true)
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = monthOffset < 6
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Mes anterior",
                            tint = if (monthOffset < 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = monthName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            VibrationManager.vibrate(context)
                            if (monthOffset > 0) {
                                monthOffset--
                                refreshData("Mes", silent = true)
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = monthOffset > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Mes siguiente",
                            tint = if (monthOffset > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = selectedPeriod,
            transitionSpec = {
                fadeIn(animationSpec = tween(500, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing))
            },
            label = "periodTransition",
            modifier = Modifier.weight(1f)
        ) { _ ->
            if (!hasUsageStatsPermission) {
                Box(modifier = Modifier.padding(16.dp)) {
                    PermissionRequiredCard(onRequestPermission = {
                        requestUsageStatsPermission(context)
                    })
                }
            } else if (isLoading && appUsageList.isEmpty()) {
                LoadingIndicator()
            } else {
                // Ordenar según el filtro seleccionado
                val sortedApps = remember(appUsageList, sortFilter) {
                    when (sortFilter) {
                        "WiFi" -> appUsageList.sortedByDescending { it.wifiBytes }
                        "Móvil" -> appUsageList.sortedByDescending { it.mobileBytes }
                        else -> appUsageList.sortedByDescending { it.totalBytes }
                    }
                }
                val displayedApps = if (showAllApps) sortedApps else sortedApps.take(4)
                val totalAppsBytes = sortedApps.sumOf {
                    when (sortFilter) {
                        "WiFi" -> it.wifiBytes
                        "Móvil" -> it.mobileBytes
                        else -> it.totalBytes
                    }
                }.coerceAtLeast(1L)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "plan_and_summary_card") {
                        PlanAndSummaryCard(
                            isPlanConfigured = isPlanConfigured,
                            planLimit = planLimit.toDoubleOrNull() ?: 0.0,
                            planUnit = planUnit,
                            planConsumedMb = planRealTimeConsumed,
                            mobileDataUsage = mobileDataUsage,
                            wifiDataUsage = wifiDataUsage,
                            periodLabel = when (selectedPeriod) {
                                "Hoy" -> "hoy"
                                "Ayer" -> {
                                    if (dayOffset == 0) "ayer"
                                    else {
                                        val sdf = SimpleDateFormat("d-MMM", Locale("es"))
                                        sdf.format(Date(currentRange!!.first)).uppercase()
                                    }
                                }
                                "Semana" -> "semana"
                                else -> "mes"
                            }
                        )
                    }

                    item(key = "apps_header") {
                        val filterBg = if (isDark) Color(0xFF1E1E22) else Color(0xFFF1F5F9)
                        val filterBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.6f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Título "Aplic."
                            Text(
                                text = "Aplic.",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                ),
                                color = titleTextColor
                            )

                            // Selector de filtro entre "Total", "WiFi", "Móvil"
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(filterBg)
                                    .border(1.dp, filterBorder, RoundedCornerShape(12.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Total", "WiFi", "Móvil").forEach { filterOption ->
                                    val isSelected = sortFilter == filterOption
                                    val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(bg)
                                            .clickable {
                                                if (sortFilter != filterOption) {
                                                    VibrationManager.vibrate(context)
                                                    sortFilter = filterOption
                                                    prefs.edit().putString("app_sort_filter", filterOption).apply()
                                                }
                                            }
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = filterOption,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.5.sp
                                            ),
                                            color = textColor
                                        )
                                    }
                                }
                            }

                            // Botón selector de vista (cuadrícula / lista) + "Ver todas"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (appUsageList.size > 4) {
                                    Text(
                                        text = if (showAllApps) "Menos" else "Todas",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                VibrationManager.vibrate(context)
                                                showAllApps = !showAllApps
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        VibrationManager.vibrate(context)
                                        isGridView = !isGridView
                                        prefs.edit().putBoolean("is_grid_view", isGridView).apply()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Default.List else Icons.Default.Grid3x3,
                                        contentDescription = if (isGridView) "Cambiar a vista de lista" else "Cambiar a vista de cuadrícula",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isGridView) {
                        val chunkedApps = displayedApps.chunked(2)
                        items(
                            items = chunkedApps,
                            key = { row -> row.joinToString("-") { it.packageName } }
                        ) { rowApps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                for (app in rowApps) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        ModernAppCard(
                                            appUsage = app,
                                            totalUsageAcrossApps = totalAppsBytes,
                                            filterType = sortFilter
                                        )
                                    }
                                }
                                if (rowApps.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(
                            items = displayedApps,
                            key = { app -> app.packageName }
                        ) { app ->
                            ModernAppListItem(
                                appUsage = app,
                                totalUsageAcrossApps = totalAppsBytes,
                                filterType = sortFilter
                            )
                        }
                    }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cargando datos de uso...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
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
fun PlanAndSummaryCard(
    isPlanConfigured: Boolean,
    planLimit: Double,
    planUnit: String,
    planConsumedMb: Double,
    mobileDataUsage: Long,
    wifiDataUsage: Long,
    periodLabel: String
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.7f)

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 2.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            if (isPlanConfigured) {
                val limitMb = if (planUnit == "GB") planLimit * 1024.0 else planLimit
                val remainingMb = (limitMb - planConsumedMb).coerceAtLeast(0.0)
                val progress = if (limitMb > 0) (planConsumedMb / limitMb).toFloat().coerceIn(0f, 1f) else 0f

                // Top: Plan de datos Header y Consumidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Plan de datos",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Cuota total: $planLimit $planUnit",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = textSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val consumedDisplay = if (planUnit == "GB") {
                            String.format(java.util.Locale.US, "%.3f", planConsumedMb / 1024.0)
                        } else {
                            String.format(java.util.Locale.US, "%.1f", planConsumedMb)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = consumedDisplay,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                ),
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = planUnit,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = textSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Consumidos",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFE76E2E),
                    trackColor = trackBg
                )

                Spacer(modifier = Modifier.height(8.dp))

                // % usado y Restante
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f %% usado", progress * 100),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color(0xFFE76E2E)
                    )

                    val remainingDisplay = if (planUnit == "GB") {
                        String.format(java.util.Locale.US, "%.3f GB", remainingMb / 1024.0)
                    } else {
                        String.format(java.util.Locale.US, "%.1f MB", remainingMb)
                    }
                    Text(
                        text = "Restante: $remainingDisplay",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Circular Donut Gauge & Metrics Breakdown
            val totalBytes = (mobileDataUsage + wifiDataUsage).coerceAtLeast(0L)
            val wifiPct = if (totalBytes > 0) (wifiDataUsage.toDouble() / totalBytes * 100).toInt() else 0
            val mobilePct = if (totalBytes > 0) 100 - wifiPct else 0

            // Colores: Móvil con el mismo naranja del plan (0xFFE76E2E) y WiFi con el azul del selector (primary)
            val mobileColor = Color(0xFFE76E2E)
            val wifiColor = MaterialTheme.colorScheme.primary

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Chart
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val mobileFraction = if (totalBytes > 0) (mobileDataUsage.toFloat() / totalBytes) else 0.5f
                    val wifiFraction = 1f - mobileFraction

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 12.dp.toPx()
                        // Mobile Arc (Azul selector)
                        drawArc(
                            color = mobileColor,
                            startAngle = -90f,
                            sweepAngle = mobileFraction * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // WiFi Arc (Ámbar contrastante)
                        drawArc(
                            color = wifiColor,
                            startAngle = -90f + (mobileFraction * 360f),
                            sweepAngle = wifiFraction * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val (valStr, unitStr) = formatTotalValueAndUnit(totalBytes)
                        Text(
                            text = valStr,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp
                            ),
                            color = textPrimary
                        )
                        Text(
                            text = "$unitStr $periodLabel",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Breakdown list: Móvil y WiFi
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Mobile row
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .background(mobileColor, RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Móvil · $mobilePct %",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                            Text(
                                text = formatBytes(mobileDataUsage),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = textPrimary
                            )
                        }
                    }

                    // WiFi row
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .background(wifiColor, RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "WiFi · $wifiPct %",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                            Text(
                                text = formatBytes(wifiDataUsage),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTotalValueAndUnit(bytes: Long): Pair<String, String> {
    if (bytes < 1024 * 1024) {
        val kb = bytes / 1024.0
        return Pair(String.format(java.util.Locale.US, "%.1f", kb), "KB")
    }
    val mb = bytes / (1024.0 * 1024.0)
    if (mb < 1024.0) {
        return Pair(String.format(java.util.Locale.US, "%.1f", mb), "MB")
    }
    val gb = mb / 1024.0
    return Pair(String.format(java.util.Locale.US, "%.2f", gb), "GB")
}

@Composable
fun ModernAppCard(
    appUsage: AppDataUsage,
    totalUsageAcrossApps: Long,
    filterType: String = "Total"
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val isDark = isSystemInDarkTheme()

    // Colors matching dark/light mode with strong contrast
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val badgeBg = MaterialTheme.colorScheme.surfaceVariant
    val badgeText = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.7f)

    // App icon loader
    val appIcon = remember(appUsage.packageName) {
        try {
            val drawable = packageManager.getApplicationIcon(appUsage.packageName)
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    val totalBytes = appUsage.totalBytes
    val wifiBytes = appUsage.wifiBytes
    val mobileBytes = appUsage.mobileBytes

    val targetBytes = when (filterType) {
        "WiFi" -> wifiBytes
        "Móvil" -> mobileBytes
        else -> totalBytes
    }

    val overallPct = if (totalUsageAcrossApps > 0) {
        ((targetBytes.toDouble() / totalUsageAcrossApps) * 100).toInt().coerceIn(0, 100)
    } else 0

    // Pastel backgrounds based on app name initial (adaptive for dark/light)
    val initial = appUsage.appName.firstOrNull()?.uppercaseChar() ?: 'A'
    val (avatarBg, avatarText) = remember(initial, isDark) {
        if (isDark) {
            when (initial) {
                'A', 'B', 'C', 'D' -> Pair(Color(0xFF452405), Color(0xFFFDBA74))
                'E', 'F', 'G', 'H' -> Pair(Color(0xFF1E3A8A), Color(0xFF93C5FD))
                'I', 'J', 'K', 'L', 'P' -> Pair(Color(0xFF064E3B), Color(0xFF6EE7B7))
                'M', 'N', 'O' -> Pair(Color(0xFF3B0764), Color(0xFFD8B4FE))
                'Q', 'R', 'S', 'T' -> Pair(Color(0xFF500724), Color(0xFFF9A8D4))
                else -> Pair(Color(0xFF450A0A), Color(0xFFFCA5A5))
            }
        } else {
            when (initial) {
                'A', 'B', 'C', 'D' -> Pair(Color(0xFFFFEDD5), Color(0xFFC2410C))
                'E', 'F', 'G', 'H' -> Pair(Color(0xFFDBEAFE), Color(0xFF1D4ED8))
                'I', 'J', 'K', 'L', 'P' -> Pair(Color(0xFFD1FAE5), Color(0xFF0F766E))
                'M', 'N', 'O' -> Pair(Color(0xFFF3E8FF), Color(0xFF7E22CE))
                'Q', 'R', 'S', 'T' -> Pair(Color(0xFFFCE7F3), Color(0xFFBE185D))
                else -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 2.dp else 3.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header: Icon + App Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = appUsage.appName,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(avatarBg, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = avatarText
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = appUsage.appName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Consumo + Porcentaje Badge según el filtro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (valStr, unitStr) = formatTotalValueAndUnit(targetBytes)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = valStr,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        ),
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unitStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = textSecondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$overallPct %",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = badgeText
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de progreso y filas de desglose
            val mobileColor = Color(0xFFE76E2E) // Naranja del plan de datos
            val wifiColor = MaterialTheme.colorScheme.primary // Azul del selector de períodos

            when (filterType) {
                "Móvil" -> {
                    // Barra 100% móvil
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(mobileColor)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Solo fila Móvil
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(mobileColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Móvil",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                        }
                        Text(
                            text = formatShortBytes(mobileBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textPrimary
                        )
                    }
                }
                "WiFi" -> {
                    // Barra 100% WiFi
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(wifiColor)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Solo fila WiFi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(wifiColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WiFi",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                        }
                        Text(
                            text = formatShortBytes(wifiBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textPrimary
                        )
                    }
                }
                else -> {
                    // Total: Barra dividida (Móvil / WiFi)
                    val mobileRatio = if (totalBytes > 0) (mobileBytes.toFloat() / totalBytes) else 0.5f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(trackBg)
                    ) {
                        if (mobileRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(mobileRatio.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(mobileColor)
                            )
                        }
                        if (1f - mobileRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight((1f - mobileRatio).coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(wifiColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Móvil Stat Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(mobileColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Móvil",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                        }
                        Text(
                            text = formatShortBytes(mobileBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // WiFi Stat Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(wifiColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WiFi",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                        }
                        Text(
                            text = formatShortBytes(wifiBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernAppListItem(
    appUsage: AppDataUsage,
    totalUsageAcrossApps: Long,
    filterType: String = "Total",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)

    val totalBytes = appUsage.totalBytes
    val mobileBytes = appUsage.mobileBytes
    val wifiBytes = appUsage.wifiBytes

    val targetBytes = when (filterType) {
        "WiFi" -> wifiBytes
        "Móvil" -> mobileBytes
        else -> totalBytes
    }

    val overallPct = if (totalUsageAcrossApps > 0) {
        val calculated = (targetBytes.toDouble() / totalUsageAcrossApps.toDouble()) * 100.0
        String.format(java.util.Locale.US, "%.1f", calculated)
    } else "0.0"

    val packageManager = context.packageManager
    val appIcon = remember(appUsage.packageName) {
        try {
            val drawable = packageManager.getApplicationIcon(appUsage.packageName)
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    val initial = appUsage.appName.firstOrNull()?.uppercaseChar() ?: '?'
    val (avatarBg, avatarText) = remember(initial, isDark) {
        if (isDark) {
            when (initial) {
                'A', 'B', 'C', 'D' -> Pair(Color(0xFF3B2518), Color(0xFFFF9E66))
                'E', 'F', 'G', 'H' -> Pair(Color(0xFF1E293B), Color(0xFF60A5FA))
                'I', 'J', 'K', 'L', 'P' -> Pair(Color(0xFF143026), Color(0xFF34D399))
                'M', 'N', 'O' -> Pair(Color(0xFF2E1A47), Color(0xFFC084FC))
                'Q', 'R', 'S', 'T' -> Pair(Color(0xFF381528), Color(0xFFF472B6))
                else -> Pair(Color(0xFF3A1C1C), Color(0xFFF87171))
            }
        } else {
            when (initial) {
                'A', 'B', 'C', 'D' -> Pair(Color(0xFFFFEDD5), Color(0xFFC2410C))
                'E', 'F', 'G', 'H' -> Pair(Color(0xFFDBEAFE), Color(0xFF1D4ED8))
                'I', 'J', 'K', 'L', 'P' -> Pair(Color(0xFFD1FAE5), Color(0xFF0F766E))
                'M', 'N', 'O' -> Pair(Color(0xFFF3E8FF), Color(0xFF7E22CE))
                'Q', 'R', 'S', 'T' -> Pair(Color(0xFFFCE7F3), Color(0xFFBE185D))
                else -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
            }
        }
    }

    val mobileColor = Color(0xFFE76E2E)
    val wifiColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 2.dp else 3.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: App icon + Title + Consumo según filtro + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = appUsage.appName,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(avatarBg, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = avatarText
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appUsage.appName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$overallPct % del total",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val (valStr, unitStr) = formatTotalValueAndUnit(targetBytes)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = valStr,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        ),
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unitStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = textSecondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Progress bar según el filtro
            when (filterType) {
                "Móvil" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(mobileColor)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Solo Móvil
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(mobileColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Móvil: ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = textSecondary
                        )
                        Text(
                            text = formatShortBytes(mobileBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textPrimary
                        )
                    }
                }
                "WiFi" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(wifiColor)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Solo WiFi
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(wifiColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WiFi: ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = textSecondary
                        )
                        Text(
                            text = formatShortBytes(wifiBytes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textPrimary
                        )
                    }
                }
                else -> {
                    // Total: barra dividida y ambas estadísticas
                    val mobileRatio = if (totalBytes > 0) (mobileBytes.toFloat() / totalBytes) else 0.5f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(trackBg)
                    ) {
                        if (mobileRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(mobileRatio.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(mobileColor)
                            )
                        }
                        if (1f - mobileRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight((1f - mobileRatio).coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(wifiColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 3: Móvil stat & WiFi stat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Móvil
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(mobileColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Móvil: ",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                            Text(
                                text = formatShortBytes(mobileBytes),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = textPrimary
                            )
                        }

                        // WiFi
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(wifiColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WiFi: ",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                            Text(
                                text = formatShortBytes(wifiBytes),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatShortBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
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

private fun calculateTimeRange(period: String, monthOffset: Int = 0, weekOffset: Int = 0, dayOffset: Int = 0): Pair<Long, Long> {
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
            calendar.add(Calendar.DAY_OF_YEAR, -(dayOffset + 1))
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
            // Aplicar offset de semanas (negativo para retroceder en el tiempo)
            calendar.add(Calendar.WEEK_OF_YEAR, -weekOffset)

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
            calendar.add(Calendar.MONTH, -monthOffset)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            // Ir al último día del mes
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
