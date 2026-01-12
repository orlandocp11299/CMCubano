package cu.cuban.cmcubano.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.*
import android.widget.Toast
import com.google.android.gms.location.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.*

// Data class para planes y consultas con iconos
private data class PlanItem(
    val title: String,
    val subtitle: String?,
    val price: String,
    val ussd: String,
    val botonTexto: String = "Comprar",
    val icono: ImageVector = Icons.Default.ShoppingCart
)

private data class ConsultaItem(
    val descripcion: String,
    val ussd: String,
    val botonTexto: String,
    val icono: ImageVector
)

// Global cache for TA to survive recompositions and navigation
private val taGlobalCache = java.util.concurrent.ConcurrentHashMap<String, String>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilesScreen(
    navController: NavController,
    initialTab: Int = 0,
    showTabs: Boolean = true,
    standaloneTitle: String? = null
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val updateTrigger = remember { mutableStateOf(0) }
    var selectedSimIndex by remember { mutableIntStateOf(0) }

    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    val activeSubscriptions = remember(updateTrigger.value, hasPermissions) {
        if (hasPermissions) {
            try {
                subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }
        } else emptyList()
    }

    val scope = rememberCoroutineScope()

    // Launcher para solicitar permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.all { it.value }
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        // Debug: imprimir estado de permisos después de la respuesta
        println("DEBUG AFTER PERMISSION: hasPermissions = $hasPermissions")
        println("DEBUG AFTER PERMISSION: hasLocationPermission = $hasLocationPermission")
    }

    // Verificar permisos actuales cada vez que cambie el estado
    LaunchedEffect(Unit) {
        val requiredPermissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.READ_PRECISE_PHONE_STATE)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            hasPermissions = true
            hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        // Debug: imprimir estado de permisos
        println("DEBUG: hasPermissions = $hasPermissions")
        println("DEBUG: hasLocationPermission = $hasLocationPermission")
        println("DEBUG: FINE_LOCATION = ${ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)}")
        println("DEBUG: COARSE_LOCATION = ${ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)}")
    }

    // Direct SMS Sender Helper
    fun sendDirectSms(subId: Int?, destination: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId ?: SubscriptionManager.getDefaultSmsSubscriptionId())
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId ?: SubscriptionManager.getDefaultSmsSubscriptionId())
            }
            smsManager.sendTextMessage(destination, null, message, null, null)
            // Optional: Show toast success
            // Toast.makeText(context, "SMS enviado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Optional: Show toast error
            // Toast.makeText(context, "Error al enviar SMS", Toast.LENGTH_SHORT).show()
        }
    }

    // Verificar permisos en tiempo real
    LaunchedEffect(Unit) {
        while (true) {
            hasPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                    (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

            hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            delay(2000) // Verificar cada 2 segundos para ahorrar recursos
        }
    }

    // Estado para la lista de celdas
    var cellInfoList by remember { mutableStateOf<List<CellInfo>>(emptyList()) }

    // Estado para datos procesados con carga en segundo plano
    var cellRowsState by remember { mutableStateOf<Triple<String, String, List<CellRow>>?>(null) }
    var serviceState by remember { mutableStateOf<ServiceState?>(null) }
    var telephonyDisplayInfo by remember { mutableStateOf<TelephonyDisplayInfo?>(null) }
    var physicalChannelConfigList by remember { mutableStateOf<List<PhysicalChannelConfig>>(emptyList()) }
    var isProcessingCells by remember { mutableStateOf(false) }

    // Reset de estados al cambiar de SIM para evitar parpadeos (Flash de datos viejos de SIM anterior)
    LaunchedEffect(selectedSimIndex, activeSubscriptions) {
        cellInfoList = emptyList()
        cellRowsState = null
        serviceState = null
        telephonyDisplayInfo = null
        physicalChannelConfigList = emptyList()
        
        // Pequeño delay para que la UI se "limpie" visualmente
        delay(100)
    }
    // Cache para persistir el TA (Timing Advance) cuando deja de reportarse momentáneamente
    val taCache = taGlobalCache

    // Procesar datos de celdas en segundo plano
    LaunchedEffect(cellInfoList, serviceState, updateTrigger.value, physicalChannelConfigList) {
        if (cellInfoList.isEmpty()) {
            cellRowsState = null
            return@LaunchedEffect
        }

        isProcessingCells = true
        withContext(Dispatchers.Default) {
            val telephonyManagerBase = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val selectedSubId = activeSubscriptions.getOrNull(selectedSimIndex)?.subscriptionId
            val telephonyManager = if (selectedSubId != null) {
                telephonyManagerBase.createForSubscriptionId(selectedSubId)
            } else {
                telephonyManagerBase
            }
            val isDataEnabled = telephonyManager.isDataEnabled

            val globalSignalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { telephonyManager.signalStrength } catch (e: Exception) { null }
            } else null

            val processed = getCellRows(cellInfoList, serviceState, telephonyDisplayInfo, physicalChannelConfigList, taCache, isDataEnabled, globalSignalStrength)
            withContext(Dispatchers.Main) {
                cellRowsState = processed
                isProcessingCells = false
            }
        }
    }
    // Implementar Polling Activo (Estrategia Pro):
    // Refresco forzado cada 2.5 segundos para evitar datos congelados/basura
    LaunchedEffect(hasPermissions, hasLocationPermission, selectedSimIndex, activeSubscriptions) {
        if (!hasPermissions || !hasLocationPermission) return@LaunchedEffect
        
        val subId = activeSubscriptions.getOrNull(selectedSimIndex)?.subscriptionId ?: return@LaunchedEffect
        val telephonyManagerBase = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val specificTelephonyManager = telephonyManagerBase.createForSubscriptionId(subId)
        
        // Configuración para mantener el flujo de datos activo
        // El LocationClient actúa como "despertador" para el módem
        val locationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500)
            .setMinUpdateIntervalMillis(1000)
            .build()
            
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                // No necesitamos procesar la ubicación, solo mantener el callback activo
                // para que el módem refresque el CA y el TA casi en tiempo real
            }
        }

        try {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        try {
            while (isActive) {
                // Verificar cancelación antes de trabajo pesado
                yield()
                
                // Usar requestCellInfoUpdate para forzar actualización fresca del módem
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    specificTelephonyManager.requestCellInfoUpdate(
                        context.mainExecutor,
                        object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                if (isActive) {
                                    cellInfoList = cellInfo
                                    updateTrigger.value++
                                }
                            }
                        }
                    )
                } else {
                    // Fallback para versiones antiguas
                    val newCellInfo = specificTelephonyManager.allCellInfo ?: emptyList()
                    if (isActive) {
                        cellInfoList = newCellInfo
                        updateTrigger.value++
                    }
                }
                
                delay(2500)
            }
        } finally {
            // Asegurar limpieza al salir del Composable
            locationClient.removeLocationUpdates(locationCallback)
        }
    }

    // Implementar actualización en tiempo real solo si hay permisos de ubicación
    LaunchedEffect(hasPermissions, hasLocationPermission, selectedSimIndex, activeSubscriptions) {
        if (!hasPermissions || !hasLocationPermission) return@LaunchedEffect

        val subId = activeSubscriptions.getOrNull(selectedSimIndex)?.subscriptionId ?: return@LaunchedEffect
        val telephonyManagerBase = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val specificTelephonyManager = telephonyManagerBase.createForSubscriptionId(subId)

        // Configurar el listener para Android 12 y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), 
                TelephonyCallback.CellInfoListener, 
                TelephonyCallback.ServiceStateListener,
                TelephonyCallback.DisplayInfoListener,
                TelephonyCallback.PhysicalChannelConfigListener {
                
                override fun onCellInfoChanged(infoList: List<CellInfo>) {
                    cellInfoList = infoList
                    updateTrigger.value++
                }

                override fun onServiceStateChanged(state: ServiceState) {
                    serviceState = state
                    updateTrigger.value++
                }

                override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                    telephonyDisplayInfo = displayInfo
                    updateTrigger.value++
                }

                override fun onPhysicalChannelConfigChanged(configs: List<PhysicalChannelConfig>) {
                    physicalChannelConfigList = configs
                    updateTrigger.value++
                }
            }

            try {
                specificTelephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                try {
                    awaitCancellation()
                } finally {
                    specificTelephonyManager.unregisterTelephonyCallback(callback)
                }
            } catch (e: SecurityException) {
                // Si falla por falta de permisos, intentar registro básico
                val basicCallback = object : TelephonyCallback(),
                    TelephonyCallback.CellInfoListener,
                    TelephonyCallback.ServiceStateListener,
                    TelephonyCallback.DisplayInfoListener {
                    
                    override fun onCellInfoChanged(infoList: List<CellInfo>) {
                        cellInfoList = infoList
                        updateTrigger.value++
                    }

                    override fun onServiceStateChanged(state: ServiceState) {
                        serviceState = state
                        updateTrigger.value++
                    }

                    override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                        telephonyDisplayInfo = displayInfo
                        updateTrigger.value++
                    }
                }
                try {
                    specificTelephonyManager.registerTelephonyCallback(context.mainExecutor, basicCallback)
                    try {
                        awaitCancellation()
                    } finally {
                        specificTelephonyManager.unregisterTelephonyCallback(basicCallback)
                    }
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        } else {
            // Para versiones anteriores
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCellInfoChanged(infoList: List<CellInfo>?) {
                    cellInfoList = infoList ?: emptyList()
                    updateTrigger.value++
                }

                @Suppress("DEPRECATION")
                override fun onServiceStateChanged(state: ServiceState?) {
                    serviceState = state
                    updateTrigger.value++
                }

                @Suppress("DEPRECATION")
                override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                    telephonyDisplayInfo = displayInfo
                    updateTrigger.value++
                }
            }

            @Suppress("DEPRECATION")
            val events = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PhoneStateListener.LISTEN_CELL_INFO or 
                PhoneStateListener.LISTEN_SERVICE_STATE or 
                PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED
            } else {
                PhoneStateListener.LISTEN_CELL_INFO or 
                PhoneStateListener.LISTEN_SERVICE_STATE
            }
            
            @Suppress("DEPRECATION")
            specificTelephonyManager.listen(listener, events)
            try {
                awaitCancellation()
            } finally {
                @Suppress("DEPRECATION")
                specificTelephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
        }

    }

    var selectedTab by remember { mutableStateOf(initialTab) }
    // Estado para la categoría seleccionada en la sección de planes
    var selectedPlanesCategory by remember { mutableStateOf<String?>(null) }

    val tabs = listOf(
        Triple("Útiles", Icons.Default.Build, "Útiles"),
        Triple("Velocidad", Icons.Default.Speed, "Velocidad"),
        Triple("Planes", Icons.Default.ShoppingCart, "Planes"),
        Triple("Consulta", Icons.Default.Search, "Consulta"),
        Triple("Transferencia", Icons.Default.SwapHoriz, "Transferencia")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Removed verticalScroll from root to avoid conflict with fillMaxSize children (like SpeedTest)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Encabezado
        if (showTabs) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Herramientas Útiles",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    // Icono de SIM selector
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                            .clickable {
                                if (activeSubscriptions.size > 1) {
                                    selectedSimIndex = if (selectedSimIndex == 0) 1 else 0
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = "Cambiar SIM",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        // Badge para el número de SIM
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .size(18.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (selectedSimIndex + 1).toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                    )
                }
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.second,
                                contentDescription = tab.first,
                                modifier = Modifier.animateContentSize()
                            )
                        },
                        text = {
                            if (tabs.size <= 3) {
                                Text(
                                    tab.third,
                                    maxLines = 1,
                                    modifier = Modifier.animateContentSize()
                                )
                            }
                        }
                    )
                }
            }
        } else if (standaloneTitle != null) {
            CenterAlignedTopAppBar(
                title = {
                    val dynamicTitle = if (selectedTab == 2 && selectedPlanesCategory != null) {
                        "Planes de $selectedPlanesCategory"
                    } else {
                        standaloneTitle
                    }
                    Text(
                        dynamicTitle ?: "",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTab == 2 && selectedPlanesCategory != null) {
                            selectedPlanesCategory = null
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // Re-añadir el selector de SIM en modo standalone también
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                if (activeSubscriptions.size > 1) {
                                    selectedSimIndex = if (selectedSimIndex == 0) 1 else 0
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = "Cambiar SIM",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        // Badge para el número de SIM Small
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
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }


        AnimatedContent(
            targetState = selectedTab,
            label = "TabAnimation",
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(300))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(300))
                }
            }
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    // Sección Útiles
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()) // Scroll moved here
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!hasPermissions) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                tonalElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Advertencia",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Se requieren permisos",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Por favor, concede los permisos necesarios para acceder a la información de red",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else if (!hasLocationPermission) {
                            // Mostrar mensaje cuando la ubicación esté desactivada
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                tonalElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOff,
                                        contentDescription = "Ubicación desactivada",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Ubicación requerida",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Para ver los parámetros de red es necesario activar la Ubicación",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val requiredPermissions = arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                            permissionLauncher.launch(requiredPermissions)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Activar Ubicación")
                                    }
                                }
                            }
                        } else {
                            // Mostrar panel principal y tabla de celdas vecinas con animaciones
                            AnimatedVisibility(
                                visible = cellRowsState != null,
                                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    cellRowsState?.let { data ->
                                        NSGMainPanelOptimized(data)
                                        NSGCellTableOptimized(data)
                                    }
                                }
                            }

                            // Mostrar indicador de carga mientras se procesan los datos
                            AnimatedVisibility(
                                visible = isProcessingCells,
                                enter = fadeIn(animationSpec = tween(200)),
                                exit = fadeOut(animationSpec = tween(200))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Mensaje cuando no hay datos
                            if (cellRowsState == null && !isProcessingCells && cellInfoList.isEmpty()) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(300))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Esperando datos de red...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }


                    }
                }
                1 -> {
                    // Sección Velocidad
                    // SpeedTestScreen manages its own layout and doesn't need external scrolling
                    SpeedTestScreen(navController)
                }
                2 -> {
                    // Sección Planes
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PlanesSection(
                            context,
                            activeSubscriptions.getOrNull(selectedSimIndex),
                            selectedPlanesCategory,
                            onCategoryChange = { selectedPlanesCategory = it }
                        )
                    }
                }
                3 -> {
                    // Sección Consulta
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ConsultaSection(context, activeSubscriptions.getOrNull(selectedSimIndex))
                    }
                }
                4 -> {
                    // Sección Transferencia
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        TransferenciaSection(activeSubscriptions.getOrNull(selectedSimIndex))
                    }
                }
            }
        }
    }
}



@Composable
internal fun NSGMainPanelOptimized(data: Triple<String, String, List<CellRow>>) {
    val (tech, badgeText, rows) = data
    val mainCell = rows.firstOrNull { it.isPrimary } ?: return

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Technology and Primary Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when(tech) {
                                "LTE" -> Icons.Default.SignalCellularAlt
                                "WCDMA" -> Icons.Default.SignalCellularAlt
                                else -> Icons.Default.SignalCellularAlt
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = tech,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Celda Principal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (badgeText.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Main Parameters Grid
            when (tech) {
                "LTE" -> {
                    val eNB = mainCell.ciInt?.let { v -> (v / 256).toString() } ?: "-"
                    val ciValue = mainCell.ciInt?.let { String.format("%08d", it) } ?: "-"

                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("eNB", eNB, Modifier.weight(1f))
                            NSGParameterItem("Banda", mainCell.band, Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)
                            NSGParameterItem("Frecuencia", getLteFrequency(mainCell.earfcn.toIntOrNull() ?: 0), Modifier.weight(1f), horizontalAlignment = Alignment.End)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("PCI", mainCell.pci, Modifier.weight(1f))
                            NSGParameterItem("TAC", mainCell.tac ?: "-", Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)
                            NSGParameterItem("TA", mainCell.ta ?: "-", Modifier.weight(1f), horizontalAlignment = Alignment.End)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("CI", ciValue, Modifier.weight(1f))
                        }

                        // Ancho de Banda (Estilo Pro) - Debajo de TA
                        if (mainCell.bwList.isNotEmpty() && mainCell.bwTotal != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "BW",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        mainCell.bwList.forEachIndexed { index, comp ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = comp.band,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = comp.width,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            
                                            // Solo poner + si hay más de una portadora y no es el último elemento
                                            if (mainCell.bwList.size > 1 && index < mainCell.bwList.size - 1) {
                                                Text(
                                                    text = "+",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            }
                                        }

                                        // Signo de igual antes del total
                                        Text(
                                            text = "=",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Total",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = mainCell.bwTotal,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                color = Color.Red
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NSGQualityMiniBlock("RSRP", mainCell.rsrp, mainCell.colorRsrp)
                        NSGQualityMiniBlock("RSRQ", mainCell.rsrq, mainCell.colorRsrq)
                        NSGQualityMiniBlock("SNR", mainCell.snr, mainCell.colorSnr)
                        NSGQualityMiniBlock("RSSI", mainCell.rssi, mainCell.colorRssi)
                    }
                }
                "WCDMA" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("Banda", mainCell.band, Modifier.weight(1f), horizontalAlignment = Alignment.Start)
                            NSGParameterItem("Frecuencia", getWcdmaFrequency(mainCell.earfcn.toIntOrNull() ?: 0), Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)
                            NSGParameterItem("UARFCN", mainCell.earfcn, Modifier.weight(1f), horizontalAlignment = Alignment.End)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("CID", mainCell.ciInt?.toString() ?: "-", Modifier.weight(1f), horizontalAlignment = Alignment.Start)
                            NSGParameterItem("LAC", mainCell.tac ?: "-", Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)
                            NSGParameterItem("PSC", mainCell.pci, Modifier.weight(1f), horizontalAlignment = Alignment.End)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NSGQualityMiniBlock("RSCP", mainCell.rsrp, mainCell.colorRsrp, barWidth = 100.dp)
                        NSGQualityMiniBlock("RSSI", mainCell.rssi, mainCell.colorRssi, barWidth = 100.dp)
                    }
                }
                "GSM" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("Banda", mainCell.band, Modifier.weight(1f), horizontalAlignment = Alignment.Start)
                            NSGParameterItem("Frecuencia", getGsmFrequency(mainCell.earfcn.toIntOrNull() ?: 0), Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)
                            NSGParameterItem("ARFCN", mainCell.earfcn, Modifier.weight(1f), horizontalAlignment = Alignment.End)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("Cell ID", mainCell.ciInt?.toString() ?: "-", Modifier.weight(1f))
                            NSGParameterItem("LAC", mainCell.tac ?: "-", Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem("BSIC", mainCell.pci, Modifier.weight(1f))
                            NSGParameterItem("TA", mainCell.ta ?: "-", Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NSGQualityMiniBlock("RSSI", mainCell.rssi, mainCell.colorRssi, barWidth = 200.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun NSGParameterItem(
    label: String, 
    value: String, 
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
fun NSGQualityMiniBlock(label: String, value: String?, color: Color?, barWidth: Dp = 64.dp) {
    val valueInt = value?.replace("[^\\d-]".toRegex(), "")?.toIntOrNull()
    val (min, max) = when (label.uppercase()) {
        "RSRP" -> -140 to -80
        "RSRQ" -> -20 to -3
        "SNR"  -> 0 to 30
        "RSSI" -> -110 to -50
        "RSCP" -> -120 to -60
        "ECNO" -> -24 to 0
        else   -> 0 to 100
    }
    val percent = valueInt?.let { ((it - min).toFloat() / (max - min)).coerceIn(0f, 1f) } ?: 0f

    Column(
        Modifier.width(barWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent)
                    .background(color ?: Color.Gray)
            )
            Text(
                text = value ?: "-",
                color = if (percent > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun NSGQualityMiniBlockCompact(
    value: String?,
    color: Color?,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val valueInt = value?.replace("[^\\d-]".toRegex(), "")?.toIntOrNull()
    val (min, max) = when (label?.uppercase()) {
        "RSRP" -> -140 to -80
        "RSRQ" -> -20 to -3
        "SNR"  -> 0 to 30
        "RSSI" -> -110 to -50
        "RSCP" -> -120 to -60
        "ECNO" -> -24 to 0
        else   -> -140 to -44
    }
    val percent = valueInt?.let { ((it - min).toFloat() / (max - min)).coerceIn(0f, 1f) } ?: 0f

    Box(
        modifier = modifier
            .height(24.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp)
                )
                .clip(RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent)
                    .background(color ?: Color.Gray)
            )
            Text(
                text = value ?: "-",
                color = if (percent > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
internal fun NSGCellTableOptimized(data: Triple<String, String, List<CellRow>>) {
    val (tech, badgeText, rows) = data
    if (rows.isEmpty()) return

    val columns = when (tech) {
        "LTE" -> listOf("Band", "EARFCN", "PCI", "RSRP", "RSRQ")
        "WCDMA" -> listOf("UARFCN", "PSC", "RSCP", "RSSI")
        "GSM" -> listOf("ARFCN", "CID", "RSSI")
        else -> emptyList()
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Celdas Vecinas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Table Content - No horizontal scroll
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columns.forEach { col ->
                        Text(
                            text = col,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Data Rows
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (tech) {
                            "LTE" -> {
                                if (row.isPrimary || row.isAggregated) {
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = row.band,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    NSGCellDataText(row.band, Modifier.weight(1f))
                                }
                                NSGCellDataText(row.earfcn, Modifier.weight(1f))
                                NSGCellDataText(row.pci, Modifier.weight(1f))
                                NSGQualityMiniBlockCompact(row.rsrp, row.colorRsrp, "RSRP", modifier = Modifier.weight(1f))
                                NSGQualityMiniBlockCompact(row.rsrq, row.colorRsrq, "RSRQ", modifier = Modifier.weight(1f))
                            }
                            "WCDMA" -> {
                                NSGCellDataText(row.earfcn, Modifier.weight(1f))
                                NSGCellDataText(row.pci, Modifier.weight(1f))
                                NSGQualityMiniBlockCompact(row.rsrp, row.colorRsrp, "RSCP", modifier = Modifier.weight(1f))
                                NSGQualityMiniBlockCompact(row.rssi, row.colorRssi, "RSSI", modifier = Modifier.weight(1f))
                            }
                            "GSM" -> {
                                NSGCellDataText(row.earfcn, Modifier.weight(1f))
                                NSGCellDataText(row.ciInt?.toString() ?: "-", Modifier.weight(1f))
                                NSGQualityMiniBlockCompact(row.rssi, row.colorRssi, "RSSI", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (rowIndex < rows.size - 1) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NSGCellDataText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium,
        maxLines = 1
    )
}

// Helper wrapper to send SMS
private fun sendDirectSms(context: Context, subId: Int?, destination: String, message: String) {
    try {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId ?: SubscriptionManager.getDefaultSmsSubscriptionId())
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId ?: SubscriptionManager.getDefaultSmsSubscriptionId())
        }
        smsManager.sendTextMessage(destination, null, message, null, null)
        Toast.makeText(context, "SMS enviado al $destination", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al enviar SMS", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PlanesSection(
    context: Context,
    selectedSubscription: SubscriptionInfo?,
    selectedCategory: String?,
    onCategoryChange: (String?) -> Unit
) {
    val callPhonePermission = Manifest.permission.CALL_PHONE
    val hasCallPermission = ContextCompat.checkSelfPermission(context, callPhonePermission) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // No se ejecuta nada aquí, la acción se maneja en el botón
        }
    }

    val planesDatos = listOf(
        PlanItem("2GB", "+15MIN+20SMS", "120 CUP", "*133*1*4*2#"),
        PlanItem("4GB", "+35MIN+40SMS", "240 CUP", "*133*1*4*3#"),
        PlanItem("6GB", "+60MIN+70SMS", "360 CUP", "*133*1*4*4#"),
        PlanItem("4.5GB", null, "240 CUP", "*133*1*4*1#"),
        PlanItem("ToDus 600MB", null, "25 CUP", "*133*1*2#"),
        PlanItem("Plan Diario 200MB", null, "25 CUP", "*133*1*3#")
    )

    val planesVoz = listOf(
        PlanItem("5 MIN", null, "$37.5", "*133*3*1#", icono = Icons.Default.Phone),
        PlanItem("10 MIN", null, "$72.5", "*133*3*2#", icono = Icons.Default.Phone),
        PlanItem("15 MIN", null, "$105", "*133*3*3#", icono = Icons.Default.Phone),
        PlanItem("25 MIN", null, "$162.5", "*133*3*4#", icono = Icons.Default.Phone),
        PlanItem("40 MIN", null, "$250", "*133*3*5#", icono = Icons.Default.Phone)
    )

    val planesSms = listOf(
        PlanItem("20 SMS", null, "$15", "*133*2*1#", icono = Icons.Default.Email),
        PlanItem("50 SMS", null, "$30", "*133*2*2#", icono = Icons.Default.Email),
        PlanItem("90 SMS", null, "$50", "*133*2*3#", icono = Icons.Default.Email),
        PlanItem("120 SMS", null, "$60", "*133*2*4#", icono = Icons.Default.Email)
    )

    // Launcher for SMS permission
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val subId = selectedSubscription?.subscriptionId
            sendDirectSms(context, subId, "2266", "LTE")
        }
    }

    // Manejo del botón atrás de Android
    BackHandler(enabled = selectedCategory != null) {
        onCategoryChange(null)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Navegación de categorías
        if (selectedCategory == null) {
            // El encabezado "Categorías de Planes" ha sido removido como se solicitó

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryCard(
                    title = "Planes de Datos",
                    icon = Icons.Default.DataUsage,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onCategoryChange("Datos") }
                )
                CategoryCard(
                    title = "Planes de Voz",
                    icon = Icons.Default.Phone,
                    color = Color(0xFF4CAF50), // Un verde para Voz
                    onClick = { onCategoryChange("Voz") }
                )
                CategoryCard(
                    title = "Planes de SMS",
                    icon = Icons.Default.Email,
                    color = Color(0xFFFF9800), // Naranja para SMS
                    onClick = { onCategoryChange("SMS") }
                )
                CategoryCard(
                    title = "Activar 4G",
                    icon = Icons.Default.SettingsInputAntenna,
                    color = Color(0xFFE91E63), // Pink/Magenta for Activation
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                            val subId = selectedSubscription?.subscriptionId
                            sendDirectSms(context, subId, "2266", "LTE")
                        } else {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    }
                )
            }
        } else {
            // Vista de lista de planes
            val activePlanes = when (selectedCategory) {
                "Datos" -> planesDatos
                "Voz" -> planesVoz
                "SMS" -> planesSms
                else -> emptyList()
            }

            // El sub-encabezado ha sido removido. La navegación y el título se manejan ahora desde el TopAppBar principal.

            // Lista de planes
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                activePlanes.forEach { plan ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp), // Reduced from 16.dp
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icono
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = plan.icono,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Descripción
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = plan.title,
                                    style = MaterialTheme.typography.bodyMedium, // Reduced from bodyLarge
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                if (!plan.subtitle.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = plan.subtitle,
                                        style = MaterialTheme.typography.bodySmall, // Reduced from bodyMedium
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Botón de precio
                            Button(
                                onClick = {
                                    if (plan.ussd.startsWith("sms:")) {
                                        try {
                                            val parts = plan.ussd.removePrefix("sms:").split(":")
                                            val number = parts[0]
                                            val body = parts.getOrElse(1) { "" }
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:$number")
                                                putExtra("sms_body", body)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback or error handling
                                        }
                                    } else {
                                        if (ContextCompat.checkSelfPermission(context, callPhonePermission) == PackageManager.PERMISSION_GRANTED) {
                                            makeUssdCall(context, plan.ussd, selectedSubscription)
                                        } else {
                                            permissionLauncher.launch(callPhonePermission)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .widthIn(min = 70.dp)
                            ) {
                                Text(
                                    text = plan.price,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 2.dp,
        animationSpec = tween(100),
        label = "elevation"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp) // Reduced from 24.dp
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp) // Reduced from 56.dp
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp) // Reduced from 32.dp
                )
            }
            Spacer(modifier = Modifier.width(16.dp)) // Reduced from 20.dp
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp // Reduced from 18.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp) // Reduced from 16.dp
            )
        }
    }
}

@Composable
fun ConsultaSection(context: Context, selectedSubscription: SubscriptionInfo?) {
    val callPhonePermission = Manifest.permission.CALL_PHONE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val consultas = listOf(
        ConsultaItem("Saldo", "*222#", "Consultar", Icons.Default.AccountBalanceWallet),
        ConsultaItem("Datos", "*222*328#", "Consultar", Icons.Default.DataUsage),
        ConsultaItem("Bono", "*222*266#", "Consultar", Icons.Default.CardGiftcard),
        ConsultaItem("MIN", "*222*869#", "Consultar",Icons.Default.Phone),
        ConsultaItem("SMS", "*222*767#", "Consultar",Icons.Default.Email),
        ConsultaItem("Días para Recargar", "*222*732#", "Consultar", Icons.Default.Event)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // El encabezado redundante ha sido removido

        // Lista de consultas mejorada
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            consultas.forEach { consulta ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp), // Reduced from 16.dp
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icono y descripción
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp) // Reduced from 48.dp
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = consulta.icono,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp) // Reduced from 24.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp)) // Reduced from 16.dp
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = consulta.descripcion,
                                    style = MaterialTheme.typography.bodyMedium, // Reduced from bodyLarge
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp)) // Reduced from 12.dp

                        // Botón mejorado
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, callPhonePermission) == PackageManager.PERMISSION_GRANTED) {
                                    makeUssdCall(context, consulta.ussd, selectedSubscription)
                                } else {
                                    permissionLauncher.launch(callPhonePermission)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp) // Reduced from 44.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = consulta.botonTexto,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferenciaSection(selectedSubscription: SubscriptionInfo?) {
    val context = LocalContext.current
    var destinatario by remember { mutableStateOf("") }
    var contraseña by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var numeroError by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    var montoError by remember { mutableStateOf(false) }

    // Launcher para solicitar permisos de contactos
    val permissionContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    // Launcher para solicitar permiso de llamada
    val permissionCallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Nada más aquí; la llamada se intentará en el siguiente clic
        }
    }

    // Launcher para seleccionar contacto
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (columnIndex >= 0) {
                            val phoneNumber = it.getString(columnIndex) ?: ""
                            // Si empieza con +53, eliminarlo
                            val numeroProcesado = if (phoneNumber.startsWith("+53")) {
                                phoneNumber.substring(3)
                            } else {
                                phoneNumber
                            }
                            // Limpiar espacios y caracteres especiales, mantener solo números
                            destinatario = numeroProcesado.replace(Regex("[^0-9]"), "")
                        }
                    }
                }
            } catch (e: SecurityException) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionContactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sección de Destinatario
            OutlinedTextField(
                value = destinatario,
                onValueChange = { newValue ->
                    if (newValue.length <= 8 && newValue.all { it.isDigit() }) {
                        destinatario = newValue
                        numeroError = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Destinatario") },
                leadingIcon = {
                    Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_PICK).apply {
                                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                            }
                            contactPickerLauncher.launch(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPhone,
                            contentDescription = "Contactos",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                isError = numeroError,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    errorContainerColor = Color.White
                ),
                supportingText = {
                    if (numeroError) {
                        Text("Ingrese un número válido", color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            // PIN y Monto en fila
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Campo PIN
                OutlinedTextField(
                    value = contraseña,
                    onValueChange = {
                        if (it.length <= 4 && it.all { it.isDigit() }) {
                            contraseña = it
                            pinError = false
                        }
                    },
                    label = { Text("PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = pinError,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                // Campo Monto
                OutlinedTextField(
                    value = monto,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            monto = it
                            montoError = false
                        }
                    },
                    label = { Text("Monto") },
                    leadingIcon = { Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = montoError,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón Transferir con degradado o estilo premium
            Button(
                onClick = {
                    numeroError = destinatario.isBlank() || destinatario.length < 8
                    pinError = contraseña.isBlank()
                    montoError = monto.isBlank()
                    if (numeroError || pinError || montoError) return@Button

                    val ussd = "*234*1*$destinatario*$contraseña*$monto#"
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        makeUssdCall(context, ussd, selectedSubscription)
                    } else {
                        permissionCallLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Transferir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// Modifica la data class CellRow para guardar los valores como Double? y formatear con decimales
internal data class CellRow(
    val isPrimary: Boolean,
    val band: String,
    val earfcn: String,
    val pci: String,
    val tac: String?,
    val eNB: String?,
    val ciInt: Int?,
    val rsrp: String?,
    val rsrq: String?,
    val snr: String?,
    val rssi: String?,
    val colorRsrp: Color?,
    val colorRsrq: Color?,
    val colorSnr: Color?,
    val colorRssi: Color?,
    val isAggregated: Boolean = false,
    val ta: String? = null,
    val bwList: List<BwComponent> = emptyList(),
    val bwTotal: String? = null
)

// Helper class for structured display
internal data class BwComponent(
    val band: String,
    val width: String
)

// Modifica getCellRows para formatear los valores con un decimal y eNB con 6 dígitos
private fun getCellRows(
    cellInfoList: List<CellInfo>, 
    serviceState: ServiceState?,
    displayInfo: TelephonyDisplayInfo?,
    physicalConfigList: List<PhysicalChannelConfig>,
    taCache: MutableMap<String, String>,
    isDataEnabled: Boolean,
    signalStrength: SignalStrength? = null
): Triple<String, String, List<CellRow>> {
    // Clasificar y ordenar: primero registradas, luego por intensidad de señal
    val now = SystemClock.elapsedRealtimeNanos()
    val STALE_THRESHOLD_MS = 5000L

    // FILTRO CRÍTICO: Priorizar solo celdas registradas para evitar mezcla de SIMs
    // Si hay una celda registrada, solo procesamos celdas de esa tecnología
    val primaryCell = cellInfoList.firstOrNull { it.isRegistered }
    val filteredCells = if (primaryCell != null) {
        // Solo tomar celdas de la misma tecnología que la primaria
        cellInfoList.filter { cell ->
            when (primaryCell) {
                is CellInfoLte -> cell is CellInfoLte
                is CellInfoWcdma -> cell is CellInfoWcdma
                is CellInfoGsm -> cell is CellInfoGsm
                else -> true
            }
        }
    } else {
        cellInfoList
    }

    val lteCells = filteredCells.filterIsInstance<CellInfoLte>()
        .sortedWith(compareByDescending<CellInfoLte> { it.isRegistered }
            .thenByDescending { it.cellSignalStrength.rsrp.let { rsrp -> if (rsrp == Int.MAX_VALUE) -140 else rsrp } })

    val wcdmaCells = filteredCells.filterIsInstance<CellInfoWcdma>()
        .sortedWith(compareByDescending<CellInfoWcdma> { it.isRegistered }
            .thenByDescending { it.cellSignalStrength.dbm.let { dbm -> if (dbm == Int.MAX_VALUE) -120 else dbm } })

    val gsmCells = filteredCells.filterIsInstance<CellInfoGsm>()
        .sortedWith(compareByDescending<CellInfoGsm> { it.isRegistered }
            .thenByDescending { it.cellSignalStrength.dbm.let { dbm -> if (dbm == Int.MAX_VALUE) -120 else dbm } })

    return when {
        lteCells.any { it.isRegistered } -> {
            var badgeText = "LTE"
            
            // MÉTODO PROFESIONAL (NetMonster/NSG style):
            // 1. Usar DisplayInfo para detección instantánea del estado lógico de CA (LTE+)
            val isDisplayCa = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayInfo != null) {
                displayInfo.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA ||
                displayInfo.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
            } else false

            // 2. Usar ServiceState para contar portadoras físicas
            val bandwidths = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceState != null) {
                serviceState.cellBandwidths
            } else intArrayOf()

            // Lógica de Badge con Jerarquía: PhysicalChannelConfig > Bandwidths
            val bwComponents = mutableListOf<BwComponent>()
            var bwTotalStr: String? = null
            
            // Calculamos Ancho de Banda aunque los datos estén apagados
            val ccCount = when {
                !isDataEnabled -> 1
                physicalConfigList.size > 1 -> physicalConfigList.size
                bandwidths.size > 1 -> bandwidths.size
                else -> 1
            }
            if (ccCount > 1) {
                badgeText = "${ccCount}CC"
            }

            // Cálculo de Ancho de Banda (Estilo Pro)
            var totalBandwidthKhz = 0
            if (physicalConfigList.isNotEmpty()) {
                // Si los datos están desactivados, solo tomamos la portadora primaria (la primera)
                val configsToProcess = if (isDataEnabled) physicalConfigList else physicalConfigList.take(1)
                configsToProcess.forEach { config ->
                    val bwKhz = config.cellBandwidthDownlinkKhz
                    if (bwKhz > 0 && bwKhz != Int.MAX_VALUE) {
                         val bwMhz = bwKhz / 1000
                         var bandNum = config.band
                         
                         // Si la banda es inválida, intentamos inferirla de la primaria
                         if (bandNum <= 0 || bandNum > 256) {
                              if (lteCells.isNotEmpty()) {
                                  bandNum = getLteBand(lteCells[0].cellIdentity.earfcn).replace("Band ", "").toIntOrNull() ?: 0
                              }
                         }
                         
                         val bandName = if (bandNum > 0) "B$bandNum" else "Bx"
                         bwComponents.add(BwComponent(bandName, "${bwMhz}MHz"))
                         totalBandwidthKhz += bwKhz
                    }
                }
            } else if (bandwidths.isNotEmpty()) {
                // Fallback a ServiceState
                // Si los datos están desactivados, solo tomamos la primera portadora
                val bwsToProcess = if (isDataEnabled) bandwidths.toList() else bandwidths.take(1)
                bwsToProcess.forEachIndexed { index, bwKhz ->
                    if (bwKhz > 0 && bwKhz != Int.MAX_VALUE) {
                        val bwMhz = bwKhz / 1000
                        val bandName = if (index == 0 && lteCells.isNotEmpty()) {
                            "B" + getLteBand(lteCells[0].cellIdentity.earfcn).replace("Band ", "")
                        } else if (lteCells.isNotEmpty()) {
                             // Para secundarios en ServiceState, como último recurso asumimos la misma banda que la primaria
                             // si solo hay una banda LTE activa en el sector (común en CA intra-banda)
                            "B" + getLteBand(lteCells[0].cellIdentity.earfcn).replace("Band ", "")
                        } else {
                            "Bx"
                        }
                        bwComponents.add(BwComponent(bandName, "${bwMhz}MHz"))
                        totalBandwidthKhz += bwKhz
                    }
                }
            }
            
            if (totalBandwidthKhz > 0) {
                val totalMhz = totalBandwidthKhz / 1000
                bwTotalStr = "${totalMhz} MHz"
            }

            // Mapeamos las celdas a CellRow
            val initialRows = lteCells.map {
                val ci = it.cellIdentity as CellIdentityLte
                val ss = it.cellSignalStrength as CellSignalStrengthLte
                
                // Verificar si los datos son basura (stale data)
                val timestamp = try { it.javaClass.getMethod("getTimestamp").invoke(it) as Long } catch(e: Exception) { 0L }
                val ageMs = if (timestamp > 0) (now - timestamp) / 1_000_000 else 0L
                val isStale = ageMs > STALE_THRESHOLD_MS
                
                val ciInt = if (ci.ci != Int.MAX_VALUE) ci.ci else null
                val eNB = ciInt?.let { v -> String.format("%d", v / 256) } ?: "-"

                // SNR
                var snrRaw = if (ss.rssnr != Int.MAX_VALUE) ss.rssnr else null
                if (snrRaw == null) {
                    try {
                        val method = try {
                            ss.javaClass.getMethod("getSnr")
                        } catch (e: Exception) {
                            ss.javaClass.getMethod("getLteSnr")
                        }
                        val v = method.invoke(ss) as Int
                        if (v != Int.MAX_VALUE) snrRaw = v
                    } catch (e: Exception) {
                        val ssStr = ss.toString()
                        val rssnrStr = if (ssStr.contains("rssnr=")) {
                            ssStr.substringAfter("rssnr=").substringBefore(" ").substringBefore(",")
                        } else if (ssStr.contains("snr=")) {
                            ssStr.substringAfter("snr=").substringBefore(" ").substringBefore(",")
                        } else null
                        snrRaw = rssnrStr?.toDoubleOrNull()?.toInt()?.takeIf { it != Int.MAX_VALUE }
                    }
                }

                if (snrRaw != null && snrRaw > 100 && snrRaw != Int.MAX_VALUE) {
                    snrRaw /= 10
                }
                
                // Timing Advance
                val taRaw = ss.timingAdvance.takeIf { it != Int.MAX_VALUE && it >= 0 }
                var taStr = taRaw?.let { 
                    val km = it * 0.144 
                    if (km < 1.0) "${it} (${String.format("%.0f", km * 1000)}m)" 
                    else "${it} (${String.format("%.2f", km)}km)"
                }
                
                // Persistencia de TA para la celda principal
                if (it.isRegistered && !isStale) {
                    val key = "LTE_${ci.pci}_${ci.earfcn}"
                    val currentTa = taStr
                    if (currentTa != null) {
                        taCache[key] = currentTa
                    } else {
                        taStr = taCache[key]
                    }
                } else if (it.isRegistered && isStale) {
                    // Si los datos están congelados, no usamos el caché y mostramos vacío o el valor stale
                    // pero según el requerimiento, si es basura deberíamos limpiar
                    taStr = if (taStr != null) "$taStr (S)" else "-" 
                }

                CellRow(
                    isPrimary = it.isRegistered,
                    band = getLteBand(ci.earfcn),
                    earfcn = ci.earfcn.toString(),
                    pci = ci.pci.toString(),
                    tac = ci.tac?.toString() ?: "-",
                    eNB = eNB,
                    ciInt = ciInt,
                    rsrp = if (isStale) "${ss.rsrp} (S)" else ss.rsrp.takeIf { v -> v != Int.MAX_VALUE }?.let { v -> "$v dBm" },
                    rsrq = ss.rsrq.takeIf { v -> v != Int.MAX_VALUE }?.let { v -> "$v dB" },
                    snr = snrRaw?.let { v -> "$v dB" },
                    rssi = ss.rssi.takeIf { v -> v != Int.MAX_VALUE }?.let { v -> "$v dBm" },
                    colorRsrp = getParamColor("RSRP", ss.rsrp),
                    colorRsrq = getParamColor("RSRQ", ss.rsrq),
                    colorSnr = snrRaw?.let { getParamColor("SNR", it) } ?: Color.Gray,
                    colorRssi = getParamColor("RSSI", ss.rssi),
                    isAggregated = false,
                    ta = if (isStale && taStr == null) "-" else taStr,
                    bwList = if (it.isRegistered && bwComponents.isNotEmpty()) bwComponents else emptyList(),
                    bwTotal = if (it.isRegistered) bwTotalStr else null
                )
            }.toMutableList()

            // Post-proceso para marcar las celdas CA (solo si hay evidencia de agregación activa y datos activados)
            if (isDataEnabled) {
                val actualCaCount = when {
                    physicalConfigList.size > 1 -> physicalConfigList.size - 1
                    bandwidths.size > 1 -> bandwidths.size - 1
                    else -> 0
                }
                
                if (actualCaCount > 0) {
                    var markedCount = 0
                    // Iterar las celdas (que ya están ordenadas por señal)
                    for (i in initialRows.indices) {
                        val row = initialRows[i]
                        // Si no es primaria y aún necesitamos marcar
                        if (!row.isPrimary && markedCount < actualCaCount) {
                            initialRows[i] = row.copy(isAggregated = true)
                            markedCount++
                        }
                    }
                }
            }
            
            Triple("LTE", badgeText, initialRows)
        }
        wcdmaCells.any { it.isRegistered } -> {
            Triple("WCDMA", "", wcdmaCells.map { cellInfoItem ->
                val ci = cellInfoItem.cellIdentity as CellIdentityWcdma
                val ss = cellInfoItem.cellSignalStrength as CellSignalStrengthWcdma
                fun formatInt(value: Int): String = value.toString()
                
                // RSCP - Extracción Robusta "Samsung-proof"
                var rscpRawVal: Int = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val rscpMethod = try {
                            ss.javaClass.getMethod("getRscp")
                        } catch (e1: NoSuchMethodException) {
                            ss.javaClass.getDeclaredMethod("getRscp").apply { isAccessible = true }
                        }
                        val v = rscpMethod.invoke(ss) as Int
                        if (v != Int.MAX_VALUE && v != -24) v else Int.MAX_VALUE
                    } else {
                        Int.MAX_VALUE
                    }
                } catch (e2: Exception) { 
                    Int.MAX_VALUE 
                }

                // Fallback 1: Fórmula basada en ASU (rscp = asu - 115) para WCDMA
                if (rscpRawVal == Int.MAX_VALUE || rscpRawVal == -24) {
                    val asu = ss.asuLevel
                    if (asu in 1..31) {
                        rscpRawVal = asu - 115
                    }
                }

                // Fallback 2: Parsing del toString() de la celda (HACK efectivo en algunos Samsung)
                if (rscpRawVal == Int.MAX_VALUE || rscpRawVal == -24) {
                    try {
                        val cellString = cellInfoItem.toString()
                        val rscpRegex = "rscp=(-?\\d+)".toRegex()
                        val match = rscpRegex.find(cellString)
                        if (match != null) {
                            val valorExtraido = match.groupValues[1].toInt()
                            if (valorExtraido != Int.MAX_VALUE && valorExtraido < -24) {
                                rscpRawVal = valorExtraido
                            }
                        }
                    } catch (e: Exception) { /* ignorer */ }
                }

                // Fallback 3: Extraer del objeto SignalStrength global (si la celda es registrada)
                if ((rscpRawVal == Int.MAX_VALUE || rscpRawVal == -24) && cellInfoItem.isRegistered && signalStrength != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val wcdmaStates = signalStrength.getCellSignalStrengths(CellSignalStrengthWcdma::class.java)
                            if (wcdmaStates.isNotEmpty()) {
                                val wGlobal = wcdmaStates[0]
                                val rGlobal = try {
                                    val rscpMethod = try {
                                        wGlobal.javaClass.getMethod("getRscp")
                                    } catch (eG1: NoSuchMethodException) {
                                        wGlobal.javaClass.getDeclaredMethod("getRscp").apply { isAccessible = true }
                                    }
                                    rscpMethod.invoke(wGlobal) as Int
                                } catch (eG2: Exception) {
                                    Int.MAX_VALUE
                                }
                                
                                if (rGlobal != Int.MAX_VALUE && rGlobal < -25) {
                                    rscpRawVal = rGlobal
                                } else if (wGlobal.dbm != Int.MAX_VALUE && wGlobal.dbm < -25 && wGlobal.dbm != -24) {
                                    rscpRawVal = wGlobal.dbm
                                }
                            }
                        }
                    } catch (e: Exception) { /* ignorer */ }
                }

                // Fallback 4: Valor dbm genérico (final)
                if (rscpRawVal == Int.MAX_VALUE || rscpRawVal == -24) {
                    val dbm = ss.dbm
                    if (dbm != Int.MAX_VALUE && dbm < -30 && dbm != -24) {
                        rscpRawVal = dbm
                    }
                }

                val rscpValue = rscpRawVal.takeIf { it != Int.MAX_VALUE && it != -24 && it != 0 }

                // EC/NO - Calidad de señal 3G (Soporte API 30+ con reflexión)
                val ecnoValue = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val ecNoMethod = try {
                            ss.javaClass.getMethod("getEcNo")
                        } catch (e3: NoSuchMethodException) {
                            ss.javaClass.getDeclaredMethod("getEcNo").apply { isAccessible = true }
                        }
                        val v = ecNoMethod.invoke(ss) as Int
                        if (v != Int.MAX_VALUE && v != -24) v else null
                    } else {
                        val v = ss.ecNo
                        if (v != Int.MAX_VALUE && v != -24) v else null
                    }
                } catch (e4: Exception) {
                    val v = ss.ecNo
                    if (v != Int.MAX_VALUE && v != -24) v else null
                }

                // RSSI - En WCDMA, el RSSI puede no estar disponible directamente
                val rssiValue = try {
                    val rssiMethod = try {
                        ss.javaClass.getMethod("getRssi")
                    } catch (e5: NoSuchMethodException) {
                        ss.javaClass.getDeclaredMethod("getRssi").apply { isAccessible = true }
                    }
                    val rssiRaw = rssiMethod.invoke(ss) as? Int ?: Int.MAX_VALUE
                    if (rssiRaw != Int.MAX_VALUE && rssiRaw != 0) rssiRaw else null
                } catch (e6: Exception) {
                    try {
                        val level = ss.level
                        when (level) {
                            4 -> -51
                            3 -> -75
                            2 -> -91
                            1 -> -105
                            else -> null
                        }
                    } catch (e7: Exception) {
                        null
                    }
                }

                CellRow(
                    isPrimary = cellInfoItem.isRegistered,
                    band = getWcdmaBand(ci.uarfcn),
                    earfcn = ci.uarfcn.toString(),
                    pci = ci.psc.toString(),
                    tac = ci.lac.takeIf { v -> v != Int.MAX_VALUE }?.toString() ?: "-",
                    eNB = null,
                    ciInt = ci.cid.takeIf { v -> v != Int.MAX_VALUE },
                    rsrp = rscpValue?.let { "${formatInt(it)} dBm" } ?: "-",
                    rsrq = ecnoValue?.let { "${formatInt(it)} dB" },
                    snr = null,
                    rssi = rssiValue?.let { "${formatInt(it)} dBm" },
                    colorRsrp = rscpValue?.let { getParamColor("RSCP", it) },
                    colorRsrq = ecnoValue?.let { getParamColor("EC/IO", it) },
                    colorSnr = null,
                    colorRssi = rssiValue?.let { getParamColor("RSSI", it) }
                )
            })
        }
        gsmCells.any { it.isRegistered } -> {
            Triple("GSM", "", gsmCells.map {
                val ci = it.cellIdentity as CellIdentityGsm
                val ss = it.cellSignalStrength as CellSignalStrengthGsm
                fun formatInt(value: Int): String = value.toString()
                val taRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ss.timingAdvance.takeIf { it != Int.MAX_VALUE && it >= 0 }
                } else null
                
                var taStr = taRaw?.let { 
                    val km = it * 0.55
                    if (km < 1.0) "${it} (${String.format("%.0f", km * 1000)}m)" 
                    else "${it} (${String.format("%.2f", km)}km)"
                }

                // Persistencia de TA para GSM
                val gsmCi = ci.cid.takeIf { it != Int.MAX_VALUE }
                if (it.isRegistered && gsmCi != null) {
                    val key = "GSM_$gsmCi"
                    val currentTa = taStr
                    if (currentTa != null) {
                        taCache[key] = currentTa
                    } else {
                        taStr = taCache[key]
                    }
                }

                CellRow(
                    isPrimary = it.isRegistered,
                    band = getGsmBand(ci.arfcn),
                    earfcn = ci.arfcn.toString(),
                    pci = ci.bsic.takeIf { v -> v != Int.MAX_VALUE }?.toString() ?: "-",
                    tac = ci.lac.takeIf { v -> v != Int.MAX_VALUE }?.toString() ?: "-",
                    eNB = null,
                    ciInt = ci.cid,
                    rsrp = null,
                    rsrq = null,
                    snr = null,
                    rssi = ss.dbm.takeIf { v -> v != Int.MAX_VALUE }?.let { v -> "${formatInt(v)} dBm" },
                    colorRsrp = null,
                    colorRsrq = null,
                    colorSnr = Color.Gray,
                    colorRssi = getParamColor("RSSI", ss.dbm),
                    ta = taStr
                )
            })
        }
        else -> Triple("-", "", emptyList())
    }
}

// Utilidades para banda y frecuencia (simplificadas, puedes expandirlas con tablas reales)
private fun getLteBand(earfcn: Int): String {
    return when {
        earfcn in 0..599 -> "1"
        earfcn in 600..1199 -> "2"
        earfcn in 1200..1949 -> "3"
        earfcn in 1950..2399 -> "4"
        earfcn in 2400..2649 -> "5"
        earfcn in 2750..3449 -> "7"
        earfcn in 3450..3799 -> "8"
        earfcn in 5000..5999 -> "13"
        earfcn in 6150..6449 -> "20"
        earfcn in 9210..9659 -> "28"
        earfcn in 27210..27659 -> "28"
        earfcn in 37750..38249 -> "38"
        earfcn in 38650..39649 -> "40"
        earfcn in 40240..41239 -> "41"
        else -> "?"
    }
}
private fun getLteFrequency(earfcn: Int): String {
    return when {
        earfcn in 0..599 -> "2100 MHz"
        earfcn in 600..1199 -> "1900 MHz"
        earfcn in 1200..1949 -> "1800 MHz"
        earfcn in 1950..2399 -> "1700/2100 MHz"
        earfcn in 2400..2649 -> "850 MHz"
        earfcn in 2750..3449 -> "2600 MHz"
        earfcn in 3450..3799 -> "900 MHz"
        earfcn in 5000..5999 -> "700 MHz (B13)"
        earfcn in 6150..6449 -> "800 MHz"
        earfcn in 9210..9659 -> "700 MHz"
        earfcn in 27210..27659 -> "700 MHz"
        earfcn in 37750..38249 -> "2600 MHz TDD"
        earfcn in 38650..39649 -> "2300 MHz TDD"
        earfcn in 40240..41239 -> "2500 MHz TDD"
        else -> "-"
    }
}
private fun getWcdmaBand(uarfcn: Int): String {
    return when {
        uarfcn in 10562..10838 -> "1"
        uarfcn in 2937..3088 -> "8"
        else -> "?"
    }
}
private fun getWcdmaFrequency(uarfcn: Int): String {
    return when {
        uarfcn in 10562..10838 -> "2100 MHz"
        uarfcn in 2937..3088 -> "900 MHz"
        else -> "-"
    }
}
private fun getGsmBand(arfcn: Int): String {
    return when {
        arfcn in 1..124 -> "900"
        arfcn in 512..885 -> "1800"
        else -> "?"
    }
}
private fun getGsmFrequency(arfcn: Int): String {
    return when {
        arfcn in 1..124 -> "900 MHz"
        arfcn in 512..885 -> "1800 MHz"
        else -> "-"
    }
}
// Colores para parámetros clave
private fun getParamColor(param: String, value: Int): Color {
    return when (param) {
        "RSRP" -> when (value) {
            in -95..-44 -> Color(0xFF4CAF50)
            in -105..-96 -> Color(0xFFFFC107)
            in -115..-106 -> Color(0xFFFF9800)
            in -140..-116 -> Color(0xFFF44336)
            else -> Color.Gray
        }
        "RSRQ" -> when (value) {
            in -10..0 -> Color(0xFF4CAF50)
            in -15..-11 -> Color(0xFFFFC107)
            in -20..-16 -> Color(0xFFFF9800)
            in -34..-21 -> Color(0xFFF44336)
            else -> Color.Gray
        }
        "RSSI" -> when (value) {
            in -70..-44 -> Color(0xFF4CAF50)
            in -85..-71 -> Color(0xFFFFC107)
            in -100..-86 -> Color(0xFFFF9800)
            in -120..-101 -> Color(0xFFF44336)
            else -> Color.Gray
        }
        "SNR" -> when (value) {
            in 20..99 -> Color(0xFF4CAF50)
            in 13..19 -> Color(0xFFFFC107)
            in 5..12 -> Color(0xFFFF9800)
            in -10..4 -> Color(0xFFF44336)
            else -> Color.Gray
        }
        "RSCP" -> when (value) {
            in -60..-44 -> Color(0xFF4CAF50)
            in -75..-61 -> Color(0xFFFFC107)
            in -90..-76 -> Color(0xFFFF9800)
            in -120..-91 -> Color(0xFFF44336)
            else -> Color.Gray
        }
        "EC/IO" -> when (value) {
            in 0..3 -> Color(0xFF4CAF50)
            in -6..-1 -> Color(0xFFFFC107)
            in -12..-7 -> Color(0xFFFF9800)
            in -24..-13 -> Color(0xFFF44336)
            else -> Color.Gray
        }
        "BER" -> when (value) {
            0 -> Color(0xFF4CAF50)
            in 1..3 -> Color(0xFFFFC107)
            in 4..7 -> Color(0xFFFF9800)
            in 8..99 -> Color(0xFFF44336)
            else -> Color.Gray
        }
        else -> Color(0xFF1976D2)
    }
}

private fun makeUssdCall(context: Context, ussd: String, subscriptionInfo: SubscriptionInfo?) {
    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + ussd.replace("#", Uri.encode("#"))))

    if (subscriptionInfo != null) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        try {
            // 1. Método estándar usando PhoneAccountHandle
            val accounts = telecomManager.callCapablePhoneAccounts
            val handle = accounts.find { it.id == subscriptionInfo.subscriptionId.toString() }
                ?: accounts.find { it.id.contains(subscriptionInfo.iccId ?: "invalid_icc") }

            if (handle != null) {
                intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", handle)
            }
        } catch (e: Exception) {
            // Fallback si no se puede acceder a las cuentas
        }

        // 2. Extras adicionales para compatibilidad (MIUI, Samsung, etc.)
        try {
            val slotIndex = subscriptionInfo.simSlotIndex
            intent.putExtra("com.android.phone.extra.slot", slotIndex) // Standard/common
            intent.putExtra("simSlot", slotIndex) // Xiaomi/Others
            intent.putExtra("com.android.phone.extra.subscription", subscriptionInfo.subscriptionId) // Some versions
            intent.putExtra("subscription", subscriptionInfo.subscriptionId)
        } catch (e: Exception) {
            // Ignorar errores al poner extras
        }
    }

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
