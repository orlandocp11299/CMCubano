package cu.cuban.cmcubano.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.Toast
import cu.cuban.cmcubano.NotificationService
import cu.cuban.cmcubano.DataPlanWidgetProvider
import cu.cuban.cmcubano.DataUsageWidgetProvider
import cu.cuban.cmcubano.services.FloatingBubbleService
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cu.cuban.cmcubano.utils.VibrationManager
import cu.cuban.cmcubano.utils.DataUsageManager
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlDatosSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val dataUsageManager = remember { DataUsageManager(context) }
    
    var showLiveNotification by remember { mutableStateOf(prefs.getBoolean("data_usage_live_notification", false)) }
    var livePeriod by remember { mutableStateOf(prefs.getString("live_notification_period", "Hoy") ?: "Hoy") }
    var showFloatingBubble by remember { mutableStateOf(prefs.getBoolean("floating_bubble_enabled", false)) }
    var dataSource by remember { mutableStateOf(prefs.getString("bubble_data_source", "auto") ?: "auto") }
    var decimalFormat by remember { mutableStateOf(prefs.getString("decimal_format", "%.0f") ?: "%.0f") }
    
    var dataLimit by remember { mutableStateOf(prefs.getString("data_plan_limit", "") ?: "") }
    var dataUnit by remember { mutableStateOf(prefs.getString("data_plan_unit", "GB") ?: "GB") }
    // Preferir el valor "live" si existe para que la UI de ajustes esté siempre al día
    var consumedData by remember { mutableStateOf(prefs.getString("data_plan_consumed_live", prefs.getString("data_plan_consumed", "0")) ?: "0") }
    var purchaseDateMillis by remember { mutableStateOf(prefs.getLong("data_plan_purchase_date", 0L)) }
    var isDateSet by remember { mutableStateOf(prefs.contains("data_plan_purchase_date")) }
    
    var isUssdLoading by remember { mutableStateOf(false) }
    var showUssdCheck by remember { mutableStateOf(false) }
    
    val dateFormatter = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()) }
    
    val expirationDate = remember(purchaseDateMillis) {
        if (purchaseDateMillis > 0) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = purchaseDateMillis }
            cal.add(java.util.Calendar.DAY_OF_YEAR, 35)
            dateFormatter.format(cal.time)
        } else "--/--"
    }

    fun parseUssdDataToMb(text: String): Double {
        val pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(GB|MB)", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        var totalMb = 0.0
        while (matcher.find()) {
            val value = matcher.group(1)?.toDoubleOrNull() ?: 0.0
            val unit = matcher.group(2)?.uppercase() ?: "MB"
            totalMb += if (unit == "GB") value * 1024 else value
        }
        return totalMb
    }

    fun requestUssdData() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            isUssdLoading = true
            
            val tmBase = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            var tm = tmBase

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    val subId = SubscriptionManager.getDefaultDataSubscriptionId()
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        tm = tmBase.createForSubscriptionId(subId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ControlDatosSettings", "Error seleccionando SIM para USSD", e)
                }
            }

            try {
                tm.sendUssdRequest("*222*328#", object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(telephonyManager: TelephonyManager?, request: String?, response: CharSequence?) {
                        isUssdLoading = false
                        val responseStr = response?.toString() ?: ""
                        val remainingMb = parseUssdDataToMb(responseStr)
                        
                        val limitValue = dataLimit.toDoubleOrNull() ?: 0.0
                        val limitMb = if (dataUnit == "GB") limitValue * 1024 else limitValue
                        
                        val consumedMb = (limitMb - remainingMb).coerceAtLeast(0.0)
                        
                        val formattedResult = if (dataUnit == "GB") {
                            String.format(java.util.Locale.US, "%.3f", consumedMb / 1024.0)
                        } else {
                            String.format(java.util.Locale.US, "%.0f", consumedMb)
                        }
                        
                        consumedData = formattedResult
                        
                        // Guardar el consumo actualizado y reiniciar el punto de referencia temporal
                        prefs.edit()
                             .putString("data_plan_consumed", consumedData)
                             .putLong("data_plan_last_update_time", System.currentTimeMillis())
                             .apply()
                                     
                        VibrationManager.vibrate(context)
                        showUssdCheck = true
                        Handler(Looper.getMainLooper()).postDelayed({
                            showUssdCheck = false
                        }, 3000)
                    }

                    override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager?, request: String?, failureCode: Int) {
                        isUssdLoading = false
                        Toast.makeText(context, "Error USSD: $failureCode", Toast.LENGTH_SHORT).show()
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: SecurityException) {
                isUssdLoading = false
                Toast.makeText(context, "Sin permisos para USSD", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                isUssdLoading = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "USSD automático no soportado en esta versión", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    cu.cuban.cmcubano.components.ScreenTitlePill(title = "Ajustes de Control de Datos")
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
                        IconButton(onClick = {
                            VibrationManager.vibrate(context)
                            navController.popBackStack()
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Atrás",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sección: Notificaciones
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsHeader(icon = Icons.Default.Notifications, title = "Notificaciones")
                
                SettingsSwitch(
                    title = "Seguimiento en vivo",
                    subtitle = "Mostrar notificación persistente con el consumo actual",
                    checked = showLiveNotification,
                    onCheckedChange = { enabled ->
                        showLiveNotification = enabled
                        prefs.edit().putBoolean("data_usage_live_notification", enabled).apply()
                        VibrationManager.vibrate(context)
                        
                        val intent = Intent(context, NotificationService::class.java).apply {
                            action = if (enabled) NotificationService.ACTION_START_DATA_MONITOR 
                                     else NotificationService.ACTION_STOP_DATA_MONITOR
                        }
                        if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                )
                
                AnimatedVisibility(visible = showLiveNotification) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Periodo a mostrar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val availablePeriods = remember(isDateSet, dataLimit) {
                            val list = mutableListOf("Hoy", "Semana", "Mes")
                            if (isDateSet && dataLimit.isNotEmpty()) {
                                list.add("Plan")
                            }
                            list
                        }
                        
                        val selectedIndex = remember(livePeriod, availablePeriods) {
                            availablePeriods.indexOf(livePeriod).coerceAtLeast(0)
                        }
                        
                        val pillOffset by animateFloatAsState(
                            targetValue = selectedIndex.toFloat(),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pillOffset"
                        )

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                                .padding(4.dp)
                        ) {
                            val pillWidth = maxWidth / availablePeriods.size
                            Box(
                                modifier = Modifier
                                    .width(pillWidth)
                                    .fillMaxHeight()
                                    .offset(x = pillWidth * pillOffset)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            )
                            Row(modifier = Modifier.fillMaxSize()) {
                                availablePeriods.forEachIndexed { index, period ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                VibrationManager.vibrate(context)
                                                livePeriod = period
                                                prefs.edit().putString("live_notification_period", period).apply()
                                                
                                                val intent = Intent(context, NotificationService::class.java).apply {
                                                    action = NotificationService.ACTION_CHANGE_DATA_PERIOD
                                                    putExtra("period", period)
                                                }
                                                context.startService(intent)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = period,
                                            fontWeight = if (livePeriod == period) FontWeight.Bold else FontWeight.Normal,
                                            color = if (livePeriod == period) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                SettingsSwitch(
                    title = "Burbuja Flotante",
                    subtitle = "Burbuja flotante con el consumo de tus datos y el tráfico de red",
                    checked = showFloatingBubble,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // Check if overlay permission is granted
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (!Settings.canDrawOverlays(context)) {
                                    // Request overlay permission
                                    Toast.makeText(context, "Se requiere permiso para aparecer encima", Toast.LENGTH_LONG).show()
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    )
                                    context.startActivity(intent)
                                } else {
                                    // Permission already granted, start service
                                    showFloatingBubble = true
                                    prefs.edit().putBoolean("floating_bubble_enabled", true).apply()
                                    VibrationManager.vibrate(context)
                                    
                                    val intent = Intent(context, FloatingBubbleService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                }
                            } else {
                                // No need for permission on older versions
                                showFloatingBubble = true
                                prefs.edit().putBoolean("floating_bubble_enabled", true).apply()
                                VibrationManager.vibrate(context)
                                
                                val intent = Intent(context, FloatingBubbleService::class.java)
                                context.startService(intent)
                            }
                        } else {
                            // Stop service
                            showFloatingBubble = false
                            prefs.edit().putBoolean("floating_bubble_enabled", false).apply()
                            VibrationManager.vibrate(context)
                            
                            val intent = Intent(context, FloatingBubbleService::class.java)
                            context.stopService(intent)
                        }
                    }
                )

                AnimatedVisibility(visible = showFloatingBubble) {
                    var hideWhenOffline by remember { mutableStateOf(prefs.getBoolean("bubble_hide_offline", false)) }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsSwitch(
                            title = "Ocultar si no hay conexión",
                            subtitle = "Ocultar automáticamente la burbuja cuando no haya internet",
                            checked = hideWhenOffline,
                            onCheckedChange = { enabled ->
                                hideWhenOffline = enabled
                                prefs.edit().putBoolean("bubble_hide_offline", enabled).apply()
                                VibrationManager.vibrate(context)
                                
                                // Notificar al servicio para que se actualice inmediatamente
                                val intent = Intent(context, FloatingBubbleService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        )

                        Text(
                            "Fuente de datos",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            fontWeight = FontWeight.Bold
                        )

                        val sourceOptions = listOf(
                            "Auto" to "auto",
                            "Móvil" to "mobile",
                            "WiFi" to "wifi",
                            "Todo" to "both"
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sourceOptions.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (dataSource != value) {
                                                dataSource = value
                                                prefs.edit().putString("bubble_data_source", value).apply()
                                                VibrationManager.vibrate(context)
                                                // Reiniciar servicio
                                                val intent = Intent(context, FloatingBubbleService::class.java)
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    context.startForegroundService(intent)
                                                } else {
                                                    context.startService(intent)
                                                }
                                            }
                                        }
                                        .background(
                                            if (dataSource == value) MaterialTheme.colorScheme.primaryContainer 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (dataSource == value) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Decimales a mostrar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val decimalOptions = listOf(
                            "X" to "%.0f",
                            "X.X" to "%.1f",
                            "X.XX" to "%.2f"
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            decimalOptions.forEach { (label, format) ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            decimalFormat = format
                                            prefs.edit().putString("decimal_format", format).apply()
                                            VibrationManager.vibrate(context)
                                            // Reiniciar servicio para aplicar cambios
                                            val intent = Intent(context, FloatingBubbleService::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                        }
                                        .background(
                                            if (decimalFormat == format) MaterialTheme.colorScheme.primaryContainer 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (decimalFormat == format) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Sección: Plan de Datos
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsHeader(icon = Icons.Default.DataUsage, title = "Plan de Datos")
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Cuota y Consumo en una sola fila
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var showUnitPopup by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = dataLimit,
                                onValueChange = { 
                                    dataLimit = it.filter { char -> char.isDigit() || char == '.' }
                                },
                                label = { Text("Plan") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    Box {
                                        TextButton(
                                            onClick = { showUnitPopup = true },
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            Text(dataUnit, style = MaterialTheme.typography.bodySmall)
                                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showUnitPopup,
                                            onDismissRequest = { showUnitPopup = false }
                                        ) {
                                            listOf("MB", "GB").forEach { unit ->
                                                DropdownMenuItem(
                                                    text = { Text(unit) },
                                                    onClick = {
                                                        dataUnit = unit
                                                        showUnitPopup = false
                                                        VibrationManager.vibrate(context)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = consumedData,
                                onValueChange = { 
                                    consumedData = it.filter { char -> char.isDigit() || char == '.' }
                                },
                                label = { Text("Usado") },
                                readOnly = false,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    if (isUssdLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else if (showUssdCheck) {
                                        Icon(Icons.Default.Check, contentDescription = "Actualizado", modifier = Modifier.size(20.dp), tint = Color(0xFF4CAF50))
                                    } else {
                                        IconButton(onClick = { requestUssdData() }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Sincronizar", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            )
                        }
                        
                        // Sección de Fechas (Compra y Vencimiento)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Día de Compra", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                OutlinedButton(
                                    onClick = {
                                        val initialCal = java.util.Calendar.getInstance().apply { timeInMillis = if (purchaseDateMillis > 0) purchaseDateMillis else System.currentTimeMillis() }
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                val selectedCal = java.util.Calendar.getInstance()
                                                selectedCal.set(year, month, dayOfMonth)
                                                purchaseDateMillis = selectedCal.timeInMillis
                                                isDateSet = true
                                                VibrationManager.vibrate(context)
                                            },
                                            initialCal.get(java.util.Calendar.YEAR),
                                            initialCal.get(java.util.Calendar.MONTH),
                                            initialCal.get(java.util.Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(40.dp).fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (isDateSet) dateFormatter.format(java.util.Date(purchaseDateMillis)) 
                                        else "Fecha", 
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Vence", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = if (isDateSet) expirationDate else "--/--",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Botones de acción
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    dataLimit = ""
                                    consumedData = "0"
                                    purchaseDateMillis = 0L
                                    isDateSet = false
                                    prefs.edit().clear().apply()
                                    VibrationManager.vibrate(context)
                                    Toast.makeText(context, "Plan eliminado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Eliminar", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            Button(
                                onClick = {
                                    if (dataLimit.isEmpty() || !isDateSet) {
                                        Toast.makeText(context, "Establece la cuota y la fecha", Toast.LENGTH_LONG).show()
                                    } else {
                                        prefs.edit().apply {
                                            putString("data_plan_limit", dataLimit)
                                            putString("data_plan_unit", dataUnit)
                                            putString("data_plan_consumed", consumedData)
                                            putString("data_plan_consumed_live", consumedData)
                                            putLong("data_plan_purchase_date", purchaseDateMillis)
                                            putLong("data_plan_last_update_time", System.currentTimeMillis())
                                            apply()
                                        }
                                        VibrationManager.vibrate(context)
                                        
                                        // Actualizar widgets inmediatamente
                                        val refreshPlan = Intent(context, DataPlanWidgetProvider::class.java).apply {
                                            action = "cu.cuban.cmcubano.DATA_PLAN_REFRESH"
                                        }
                                        context.sendBroadcast(refreshPlan)
                                        
                                        val refreshUsage = Intent(context, DataUsageWidgetProvider::class.java).apply {
                                            action = "cu.cuban.cmcubano.DATA_USAGE_REFRESH"
                                        }
                                        context.sendBroadcast(refreshUsage)

                                        Toast.makeText(context, "Plan guardado", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Guardar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
