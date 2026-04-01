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

import android.provider.Settings

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

import androidx.compose.foundation.isSystemInDarkTheme

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

import androidx.compose.ui.window.Dialog

import androidx.compose.ui.window.DialogProperties

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.*

import androidx.compose.ui.graphics.*

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.PlatformTextStyle

import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.*

import androidx.compose.ui.text.style.LineHeightStyle

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextDecoration

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.*

import androidx.core.content.ContextCompat

import androidx.navigation.NavController

import kotlinx.coroutines.*

import cu.cuban.cmcubano.utils.VibrationManager

import cu.cuban.cmcubano.utils.EnbNameManager

import cu.cuban.cmcubano.UssdManager



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

    standaloneTitle: String? = null,

    initialPlanesCategory: String? = null,

    useNavForPlanesCategory: Boolean = false

) {

    val context = LocalContext.current

    var hasPermissions by remember { mutableStateOf(false) }

    var hasLocationPermission by remember { mutableStateOf(false) }

    var isAirplaneModeOn by remember { mutableStateOf(false) }

    var isLocationServiceEnabled by remember { mutableStateOf(false) }

    var isCheckingPermissions by remember { mutableStateOf(true) } // Estado inicial: verificando permisos

    val updateTrigger = remember { mutableStateOf(0) }

    var selectedSimIndex by remember { mutableIntStateOf(0) }



    // Verificar estado del modo avión y servicios de ubicación

    LaunchedEffect(Unit) {

        while (true) {

            isAirplaneModeOn = Settings.Global.getInt(

                context.contentResolver,

                Settings.Global.AIRPLANE_MODE_ON,

                0

            ) != 0

            

            val locationMode = Settings.Secure.getInt(

                context.contentResolver,

                Settings.Secure.LOCATION_MODE,

                Settings.Secure.LOCATION_MODE_OFF

            )

            isLocationServiceEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF

            

            delay(1000) // Verificar cada segundo

        }

    }



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

        

        // Finalizar verificación de permisos después de la respuesta

        isCheckingPermissions = false



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



        // Dar tiempo para la verificación inicial

        delay(1500) // Esperar 1.5 segundos antes de verificar

        

        if (missingPermissions.isNotEmpty()) {

            permissionLauncher.launch(missingPermissions.toTypedArray())

        } else {

            hasPermissions = true

            hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||

                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        }

        

        // Finalizar verificación de permisos

        isCheckingPermissions = false



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

    var telephonyDisplayInfo by remember { mutableStateOf<Any?>(null) }
    var physicalChannelConfigList by remember { mutableStateOf<List<Any>>(emptyList()) }

    var isProcessingCells by remember { mutableStateOf(false) }

    var showInitialLoading by remember { mutableStateOf(true) }

    

    // Estados para el diálogo de edición de nombre de eNB

    var showEnbNameDialog by remember { mutableStateOf(false) }

    var currentEnbId by remember { mutableStateOf("") }

    var enbNameInput by remember { mutableStateOf("") }

    var pciInput1 by remember { mutableStateOf("") }

    var pciInput2 by remember { mutableStateOf("") }

    var pciInput3 by remember { mutableStateOf("") }

    var enbNameUpdateTrigger by remember { mutableIntStateOf(0) }



    // Ocultar carga inicial después de un tiempo

    LaunchedEffect(Unit) {

        delay(3000) // Mostrar carga inicial por 3 segundos máximo

        showInitialLoading = false

    }



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

            val isDataEnabled = try {
                telephonyManager.isDataEnabled
            } catch (e: Exception) {
                false
            }



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



    var selectedTab by remember { mutableStateOf(initialTab) }

    // Estado para la categoría seleccionada en la sección de planes

    var selectedPlanesCategory by remember(initialPlanesCategory) { mutableStateOf<String?>(initialPlanesCategory) }



    // Implementar Polling Activo (Estrategia Pro):

    // Refresco forzado cada 2.5 segundos para evitar datos congelados/basura

    // SOLO activar monitoreo cuando estemos en la pestaña de Útiles (selectedTab == 0)

    LaunchedEffect(hasPermissions, hasLocationPermission, selectedSimIndex, activeSubscriptions, selectedTab) {

        if (!hasPermissions || !hasLocationPermission || selectedTab != 0) return@LaunchedEffect

        

        val subId = activeSubscriptions.getOrNull(selectedSimIndex)?.subscriptionId

        val telephonyManagerBase = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val specificTelephonyManager = if (subId != null) {
            telephonyManagerBase.createForSubscriptionId(subId)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            telephonyManagerBase // Android 8/9: usar TelephonyManager por defecto
        } else {
            return@LaunchedEffect // Android 10+: sin subId no podemos continuar
        }

        

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
                    // Fallback para Android 8/9 con protección de permisos y excepciones
                    val hasLocation = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasLocation) {
                        val newCellInfo = try {
                            specificTelephonyManager.allCellInfo ?: emptyList()
                        } catch (e: SecurityException) {
                            emptyList()
                        } catch (e: RuntimeException) {
                            // Algunos chips Mediatek lanzan RuntimeException en Android 8
                            emptyList()
                        }
                        if (isActive) {
                            cellInfoList = newCellInfo
                            updateTrigger.value++
                        }
                    }
                }

                

                delay(2500)

            }

        } finally {

            // Asegurar limpieza al salir del Composable

            locationClient.removeLocationUpdates(locationCallback)

        }

    }



    // Implementar actualización en tiempo real solo si hay permisos de ubicación Y estamos en pestaña Útiles

    LaunchedEffect(hasPermissions, hasLocationPermission, selectedSimIndex, activeSubscriptions, selectedTab) {

        if (!hasPermissions || !hasLocationPermission || selectedTab != 0) return@LaunchedEffect



        val subId = activeSubscriptions.getOrNull(selectedSimIndex)?.subscriptionId

        val telephonyManagerBase = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val specificTelephonyManager = if (subId != null) {
            telephonyManagerBase.createForSubscriptionId(subId)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            telephonyManagerBase // Android 8/9: usar TelephonyManager por defecto
        } else {
            return@LaunchedEffect // Android 10+: sin subId no podemos continuar
        }



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
            @Suppress("DEPRECATION")
            val listener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                object : PhoneStateListener() {
                    override fun onCellInfoChanged(infoList: List<CellInfo>?) {
                        cellInfoList = infoList ?: emptyList()
                        updateTrigger.value++
                    }
                    override fun onServiceStateChanged(state: ServiceState?) {
                        serviceState = state
                        updateTrigger.value++
                    }
                    override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                        telephonyDisplayInfo = displayInfo
                        updateTrigger.value++
                    }
                }
            } else {
                object : PhoneStateListener() {
                    override fun onCellInfoChanged(infoList: List<CellInfo>?) {
                        cellInfoList = infoList ?: emptyList()
                        updateTrigger.value++
                    }
                    override fun onServiceStateChanged(state: ServiceState?) {
                        serviceState = state
                        updateTrigger.value++
                    }
                }
            }



            @Suppress("DEPRECATION")
            val events = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PhoneStateListener.LISTEN_CELL_INFO or
                PhoneStateListener.LISTEN_SERVICE_STATE or
                PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED
            } else {
                // Android 10 y anteriores: LISTEN_DISPLAY_INFO_CHANGED no existe, NO incluirlo
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

                                VibrationManager.vibrate(context)

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

                                animationSpec = tween(200, easing = EaseInOut)

                            )

                    )

                }

            ) {

                tabs.forEachIndexed { index, tab ->

                    Tab(

                        selected = selectedTab == index,

                        onClick = { 

                            VibrationManager.vibrate(context)

                            selectedTab = index 

                        },

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

                    cu.cuban.cmcubano.components.ScreenTitlePill(title = dynamicTitle ?: "")
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
                            if (selectedTab == 2 && selectedPlanesCategory != null) {
                                if (useNavForPlanesCategory) {
                                    navController.popBackStack()
                                } else {
                                    selectedPlanesCategory = null
                                }
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Atrás",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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

                                VibrationManager.vibrate(context)

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

                        animationSpec = tween(200, easing = EaseInOut)

                    ) + fadeIn(animationSpec = tween(200)) togetherWith

                            slideOutHorizontally(

                                targetOffsetX = { -it },

                                animationSpec = tween(200, easing = EaseInOut)

                            ) + fadeOut(animationSpec = tween(200))

                } else {

                    slideInHorizontally(

                        initialOffsetX = { -it },

                        animationSpec = tween(200, easing = EaseInOut)

                    ) + fadeIn(animationSpec = tween(200)) togetherWith

                            slideOutHorizontally(

                                targetOffsetX = { it },

                                animationSpec = tween(200, easing = EaseInOut)

                            ) + fadeOut(animationSpec = tween(200))

                }

            }

        ) { tabIndex ->

            when (tabIndex) {

                0 -> {

                    // Sección Útiles

                    Column(

                        modifier = Modifier

                            .fillMaxSize()

                            .padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(16.dp)

                    ) {

                        if (isCheckingPermissions) {

                            // Mostrar animación de carga mientras se verifican los permisos

                            Box(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .padding(32.dp),

                                contentAlignment = Alignment.Center

                            ) {

                                Column(

                                    horizontalAlignment = Alignment.CenterHorizontally,

                                    verticalArrangement = Arrangement.spacedBy(16.dp)

                                ) {

                                    CircularProgressIndicator(

                                        modifier = Modifier.size(48.dp),

                                        color = MaterialTheme.colorScheme.primary,

                                        strokeWidth = 4.dp

                                    )

                                    Text(

                                        text = "Verificando permisos...",

                                        style = MaterialTheme.typography.bodyMedium,

                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),

                                        textAlign = TextAlign.Center

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

                                        text = "Active la ubicación",

                                        style = MaterialTheme.typography.titleMedium,

                                        color = MaterialTheme.colorScheme.onErrorContainer

                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(

                                        text = "Para ver los parámetros de red es necesario activar la ubicación",

                                        style = MaterialTheme.typography.bodyMedium,

                                        textAlign = TextAlign.Center,

                                        color = MaterialTheme.colorScheme.onErrorContainer

                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(

                                        onClick = {

                                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

                                            context.startActivity(intent)

                                        },

                                        colors = ButtonDefaults.buttonColors(

                                            containerColor = MaterialTheme.colorScheme.error

                                        )

                                    ) {

                                        Text("Activar ubicación")

                                    }

                                }

                            }

                        } else if (!isLocationServiceEnabled) {

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

                                        text = "Active la Ubicación",

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

                                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

                                            context.startActivity(intent)

                                        },

                                        colors = ButtonDefaults.buttonColors(

                                            containerColor = MaterialTheme.colorScheme.error

                                        )

                                    ) {

                                        Text("Activar Ubicación")

                                    }

                                }

                            }

                        } else if (isAirplaneModeOn) {

                            // Mostrar mensaje cuando el modo avión está activado

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

                                        imageVector = Icons.Default.AirplanemodeActive,

                                        contentDescription = "Modo avión activado",

                                        tint = MaterialTheme.colorScheme.error,

                                        modifier = Modifier.size(48.dp)

                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(

                                        text = "Desactive el Modo Avión",

                                        style = MaterialTheme.typography.titleMedium,

                                        color = MaterialTheme.colorScheme.onErrorContainer

                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(

                                        text = "El modo avión está activado y bloquea el acceso a la información de red",

                                        style = MaterialTheme.typography.bodyMedium,

                                        textAlign = TextAlign.Center,

                                        color = MaterialTheme.colorScheme.onErrorContainer

                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(

                                        onClick = {

                                            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)

                                            context.startActivity(intent)

                                        },

                                        colors = ButtonDefaults.buttonColors(

                                            containerColor = MaterialTheme.colorScheme.error

                                        )

                                    ) {

                                        Text("Configurar Modo Avión")

                                    }

                                }

                            }

                        } else {

                            // Mostrar panel principal y tabla de celdas vecinas con animaciones

                            AnimatedVisibility(

                                visible = cellRowsState != null,

                                enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),

                                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))

                            ) {

                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {

                                    cellRowsState?.let { data ->

                                        // Panel principal estático

                                        NSGMainPanelOptimized(

                                            data = data,

                                            onEnbClick = { enbId ->

                                                currentEnbId = enbId

                                                enbNameInput = EnbNameManager.getEnbName(context, enbId) ?: ""

                                                

                                                val currentCell = data.third.firstOrNull { it.isPrimary }

                                                val storedPcisStr = EnbNameManager.getEnbPcis(context, enbId)

                                                

                                                if (storedPcisStr.isNotEmpty()) {

                                                    val parts = storedPcisStr.split(",")

                                                    pciInput1 = parts.getOrElse(0) { "" }.trim()

                                                    pciInput2 = parts.getOrElse(1) { "" }.trim()

                                                    pciInput3 = parts.getOrElse(2) { "" }.trim()

                                                } else if (currentCell != null) {

                                                    val pciVal = currentCell.pci.toIntOrNull()

                                                    if (pciVal != null) {

                                                        val sss = pciVal / 3

                                                        pciInput1 = (3 * sss).toString()

                                                        pciInput2 = (3 * sss + 1).toString()

                                                        pciInput3 = (3 * sss + 2).toString()

                                                    } else {

                                                        pciInput1 = ""

                                                        pciInput2 = ""

                                                        pciInput3 = ""

                                                    }

                                                } else {

                                                    pciInput1 = ""

                                                    pciInput2 = ""

                                                    pciInput3 = ""

                                                }

                                                

                                                showEnbNameDialog = true

                                            },

                                            enbNameUpdateTrigger = enbNameUpdateTrigger

                                        )



                                        // Solo la tabla de celdas vecinas tiene scroll

                                        Column(

                                            modifier = Modifier

                                                .weight(1f)

                                                .verticalScroll(rememberScrollState())

                                        ) {

                                            NSGCellTableOptimized(data)

                                            Spacer(modifier = Modifier.height(16.dp))

                                        }

                                    }

                                }

                            }



                            // Mostrar indicador de carga mientras se procesan los datos

                            AnimatedVisibility(

                                visible = isProcessingCells || (showInitialLoading && hasPermissions && hasLocationPermission && !isAirplaneModeOn && isLocationServiceEnabled),

                                enter = fadeIn(animationSpec = tween(150)),

                                exit = fadeOut(animationSpec = tween(150))

                            ) {

                                Box(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(32.dp),

                                    contentAlignment = Alignment.Center

                                ) {

                                    Column(

                                        horizontalAlignment = Alignment.CenterHorizontally,

                                        verticalArrangement = Arrangement.spacedBy(16.dp)

                                    ) {

                                        CircularProgressIndicator(

                                            modifier = Modifier.size(48.dp),

                                            color = MaterialTheme.colorScheme.primary,

                                            strokeWidth = 4.dp

                                        )

                                        Text(

                                            text = if (showInitialLoading) "Cargando información de red..." else "Procesando datos...",

                                            style = MaterialTheme.typography.bodyMedium,

                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),

                                            textAlign = TextAlign.Center

                                        )

                                    }

                                }

                            }



                            // Mensaje cuando no hay datos y no está cargando

                            if (!isCheckingPermissions && cellRowsState == null && !isProcessingCells && cellInfoList.isEmpty() && 

                                hasPermissions && hasLocationPermission && !isAirplaneModeOn && isLocationServiceEnabled) {

                                AnimatedVisibility(

                                    visible = true,

                                    enter = fadeIn(animationSpec = tween(200))

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

                            useNavForCategory = useNavForPlanesCategory,

                            onCategoryChange = { category ->

                                if (useNavForPlanesCategory) {

                                    if (category == null) {

                                        navController.popBackStack()

                                    } else {

                                        navController.navigate("planes_list?category=$category")

                                    }

                                } else {

                                    selectedPlanesCategory = category

                                }

                            }

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

        

        // Diálogo para editar nombre de eNB

        if (showEnbNameDialog) {

            AlertDialog(

                onDismissRequest = {

                    showEnbNameDialog = false

                    enbNameInput = ""

                    pciInput1 = ""

                    pciInput2 = ""

                    pciInput3 = ""

                },

                title = {

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {

                        Surface(

                            color = MaterialTheme.colorScheme.primaryContainer,

                            shape = RoundedCornerShape(8.dp)

                        ) {

                            Text(

                                text = "eNB $currentEnbId",

                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),

                                style = MaterialTheme.typography.labelMedium,

                                fontWeight = FontWeight.SemiBold,

                                color = MaterialTheme.colorScheme.onPrimaryContainer

                            )

                        }

                    }

                },

                text = {

                    Column(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalAlignment = Alignment.CenterHorizontally

                    ) {



                        

                        Text(

                            text = "Nombre personalizado",

                            style = MaterialTheme.typography.titleSmall,

                            color = MaterialTheme.colorScheme.onSurface,

                            modifier = Modifier.padding(bottom = 8.dp)

                        )

                        OutlinedTextField(

                            value = enbNameInput,

                            onValueChange = { enbNameInput = it },

                            placeholder = { 

                                Text(

                                    "Ej: Casa, Oficina...",

                                    style = MaterialTheme.typography.bodySmall,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant

                                ) 

                            },

                            singleLine = true,

                            modifier = Modifier.fillMaxWidth(),

                            textStyle = MaterialTheme.typography.bodyMedium,

                            trailingIcon = {

                                if (enbNameInput.isNotBlank()) {

                                    IconButton(onClick = { enbNameInput = "" }) {

                                        Icon(

                                            imageVector = Icons.Default.Clear,

                                            contentDescription = "Limpiar",

                                            tint = MaterialTheme.colorScheme.error

                                        )

                                    }

                                }

                            }

                        )

                        

                        Text(

                            text = "PCIs asociados (Auto-calculados)",

                            style = MaterialTheme.typography.labelMedium,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).align(Alignment.Start)

                        )

                        

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                            OutlinedTextField(

                                value = pciInput1,

                                onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) pciInput1 = it },

                                label = { Text("PCI 1") },

                                modifier = Modifier.weight(1f),

                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                                singleLine = true,

                                shape = RoundedCornerShape(12.dp)

                            )

                            OutlinedTextField(

                                value = pciInput2,

                                onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) pciInput2 = it },

                                label = { Text("PCI 2") },

                                modifier = Modifier.weight(1f),

                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                                singleLine = true,

                                shape = RoundedCornerShape(12.dp)

                            )

                            OutlinedTextField(

                                value = pciInput3,

                                onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) pciInput3 = it },

                                label = { Text("PCI 3") },

                                modifier = Modifier.weight(1f),

                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                                singleLine = true,

                                shape = RoundedCornerShape(12.dp)

                            )

                        }

                    }

                },

                confirmButton = {

                    TextButton(

                        onClick = {

                            if (enbNameInput.isNotBlank()) {

                                val pcis = listOf(pciInput1, pciInput2, pciInput3)

                                    .filter { it.isNotBlank() }

                                    .joinToString(",")

                                EnbNameManager.saveEnbName(context, currentEnbId, enbNameInput.trim(), pcis)

                                enbNameUpdateTrigger++ // Forzar actualización del nombre del eNB

                                updateTrigger.value++ // Forzar actualización general de la UI

                            }

                            showEnbNameDialog = false

                            enbNameInput = ""

                            pciInput1 = ""

                            pciInput2 = ""

                            pciInput3 = ""

                        },

                        modifier = Modifier.padding(end = 8.dp)

                    ) {

                        Text(

                            "Guardar",

                            style = MaterialTheme.typography.labelMedium,

                            fontWeight = FontWeight.Medium

                        )

                    }

                },

                dismissButton = {

                    TextButton(

                        onClick = {

                            showEnbNameDialog = false

                            enbNameInput = ""

                            pciInput1 = ""

                            pciInput2 = ""

                            pciInput3 = ""

                        }

                    ) {

                        Text(

                            "Cancelar",

                            style = MaterialTheme.typography.labelMedium,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }

                },

                modifier = Modifier.padding(16.dp)

            )

        }

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }
        
        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = { 
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
            )
        }

    }

}







@Composable

internal fun NSGMainPanelOptimized(

    data: Triple<String, String, List<CellRow>>,

    onEnbClick: (String) -> Unit = {},

    enbNameUpdateTrigger: Int = 0

) {

    val (tech, badgeText, rows) = data

    val mainCell = rows.firstOrNull { it.isPrimary } ?: return

    val context = LocalContext.current

    

    // Obtener el nombre personalizado del eNB si existe

    val enbId = mainCell.eNB

    val customEnbName = remember(enbId, enbNameUpdateTrigger) {

        if (enbId != null && enbId != "-") {

            EnbNameManager.getEnbName(context, enbId)

        } else null

    }

    val displayName = customEnbName ?: "Celda Principal"



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

                Row(

                    verticalAlignment = Alignment.CenterVertically,

                    modifier = Modifier.weight(1f)

                ) {

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

                                "WCDMA", "HSPA", "HSPA+" -> Icons.Default.SignalCellularAlt

                                "GSM", "GPRS", "EDGE" -> Icons.Default.SignalCellularAlt

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

                            text = displayName,

                            style = MaterialTheme.typography.labelSmall,

                            color = MaterialTheme.colorScheme.primary,

                            maxLines = 1,

                            overflow = TextOverflow.Ellipsis

                        )

                    }

                }

                

                Spacer(modifier = Modifier.width(8.dp))





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
                    val lcid = mainCell.ciInt?.let { v -> (v % 256).toString() } ?: "-"

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NSGParameterItem(
                                "eNB | CID", 
                                if (eNB != "-" && lcid != "-") "$eNB | $lcid" else eNB, 
                                Modifier.weight(1f),
                                isEnbClickable = true,
                                onEnbClick = { onEnbClick(eNB) }
                            )

                            NSGParameterItem("EARFCN", mainCell.earfcn, Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)

                            NSGParameterItem("PCI", mainCell.pci, Modifier.weight(1f), horizontalAlignment = Alignment.End)

                        }

                        Row(modifier = Modifier.fillMaxWidth()) {

                            NSGParameterItem("TAC", mainCell.tac ?: "-", Modifier.weight(1f), horizontalAlignment = Alignment.Start)

                            NSGParameterItem("Band | Frec", "${mainCell.band} | ${getLteFrequency(mainCell.earfcn.toIntOrNull() ?: 0)}", Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)

                            NSGParameterItem("TA", mainCell.ta ?: "-", Modifier.weight(1f), horizontalAlignment = Alignment.End)

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

                "WCDMA", "HSPA", "HSPA+" -> {

                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

                        Row(modifier = Modifier.fillMaxWidth()) {

                            NSGParameterItem("Banda", mainCell.band, Modifier.weight(1f), horizontalAlignment = Alignment.Start)

                            NSGParameterItem("Frecuencia", getWcdmaFrequency(mainCell.earfcn.toIntOrNull() ?: 0), Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)

                            NSGParameterItem("UARFCN", mainCell.earfcn, Modifier.weight(1f), horizontalAlignment = Alignment.End)

                        }

                        Row(modifier = Modifier.fillMaxWidth()) {

                            NSGParameterItem("CI", mainCell.ciInt?.toString() ?: "-", Modifier.weight(1f), horizontalAlignment = Alignment.Start)

                            NSGParameterItem("LAC", mainCell.tac ?: "-", Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)

                            NSGParameterItem("PSC", mainCell.pci, Modifier.weight(1f), horizontalAlignment = Alignment.End)

                        }

                        // Extraer RNC y CID del CI completo

                        mainCell.ciInt?.let { ciValue ->

                            val rnc = ciValue shr 16

                            val cid = ciValue and 0xFFFF

                            Row(modifier = Modifier.fillMaxWidth()) {

                                NSGParameterItem("CID", cid.toString(), Modifier.weight(1f), horizontalAlignment = Alignment.Start)

                                NSGParameterItem("RNC", rnc.toString(), Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally)

                                NSGParameterItem("", "", Modifier.weight(1f), horizontalAlignment = Alignment.End) // Espacio vacío para balance

                            }

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

                "GSM", "GPRS", "EDGE" -> {

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

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }
        
        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = { 
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
            )
        }

    }

}



@Composable

fun NSGParameterItem(

    label: String, 

    value: String, 

    modifier: Modifier = Modifier,

    horizontalAlignment: Alignment.Horizontal = Alignment.Start,

    isEnbClickable: Boolean = false,

    onEnbClick: () -> Unit = {}

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

        if (isEnbClickable) {

            Text(

                text = value,

                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .clickable { onEnbClick() },

                style = MaterialTheme.typography.bodyMedium,

                fontWeight = FontWeight.SemiBold,

                color = MaterialTheme.colorScheme.onPrimaryContainer

            )

        } else {

            Text(

                text = value,

                style = MaterialTheme.typography.bodyMedium,

                fontWeight = FontWeight.SemiBold,

                color = MaterialTheme.colorScheme.onSurface

            )

        }

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }
        
        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = { 
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
            )
        }

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

                .clip(RoundedCornerShape(8.dp)),

            contentAlignment = Alignment.Center

        ) {

            Box(

                modifier = Modifier

                    .fillMaxHeight()

                    .fillMaxWidth(percent)

                    .background(color ?: Color.Gray)

                    .align(Alignment.CenterStart)

            )

            Text(

                text = value ?: "-",

                style = TextStyle(

                    fontSize = 11.sp,

                    fontWeight = FontWeight.Bold,

                    color = if (percent > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,

                    textAlign = TextAlign.Center,

                    platformStyle = PlatformTextStyle(includeFontPadding = false),

                    lineHeightStyle = LineHeightStyle(

                        alignment = LineHeightStyle.Alignment.Center,

                        trim = LineHeightStyle.Trim.Both

                    ),

                    lineHeight = 11.sp

                ),

                modifier = Modifier

                    .align(Alignment.Center)

                    .offset(y = (-1).dp)

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

                .clip(RoundedCornerShape(6.dp)),

            contentAlignment = Alignment.Center

        ) {

            Box(

                modifier = Modifier

                    .fillMaxHeight()

                    .fillMaxWidth(percent)

                    .background(color ?: Color.Gray)

                    .align(Alignment.CenterStart)

            )

            Text(

                text = value ?: "-",

                style = TextStyle(

                    fontSize = 10.sp,

                    fontWeight = FontWeight.Bold,

                    color = if (percent > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,

                    textAlign = TextAlign.Center,

                    platformStyle = PlatformTextStyle(includeFontPadding = false),

                    lineHeightStyle = LineHeightStyle(

                        alignment = LineHeightStyle.Alignment.Center,

                        trim = LineHeightStyle.Trim.Both

                    ),

                    lineHeight = 10.sp

                ),

                modifier = Modifier

                    .align(Alignment.Center)

                    .offset(y = (-1).dp)

            )

        }

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }
        
        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = { 
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
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

        "WCDMA", "HSPA", "HSPA+" -> listOf("UARFCN", "PSC", "RSCP", "RSSI")

        "GSM", "GPRS", "EDGE" -> listOf("ARFCN", "CID", "RSSI")

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

                val currentContext = LocalContext.current

                val prefs = remember { currentContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

                val showCustomNamesPref = remember { prefs.getBoolean("show_custom_enb_names_neighboring", false) }

                val pciToNameMap = remember(data) { EnbNameManager.getPciToNameMap(currentContext) }

                

                rows.forEachIndexed { rowIndex, row ->

                    Row(

                        modifier = Modifier

                            .fillMaxWidth()

                            .background(Color.Transparent)

                            .padding(horizontal = 12.dp, vertical = 6.dp), 

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        when (tech) {

                            "LTE" -> {

                                val customName = if (showCustomNamesPref) pciToNameMap[row.pci] else null

                                

                                // Bloque Banda (Columna 1) - Reducido para dar más espacio

                                Column(

                                    modifier = Modifier.weight(0.7f),

                                    horizontalAlignment = Alignment.CenterHorizontally,

                                    verticalArrangement = Arrangement.Center

                                ) {

                                    // Espaciador constante para mantener la altura homogénea

                                    Spacer(modifier = Modifier.height(10.dp))

                                    

                                    Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {

                                        if (row.isPrimary || row.isAggregated) {

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

                                        } else {

                                            NSGCellDataText(row.band)

                                        }

                                    }

                                }



                                // Bloque EARFCN y PCI con Nombre (Columnas 2 y 3) - Expandido

                                Column(

                                    modifier = Modifier.weight(2.3f),

                                    horizontalAlignment = Alignment.CenterHorizontally,

                                    verticalArrangement = Arrangement.spacedBy((-2).dp)

                                ) {

                                    if (customName != null) {

                                        Text(

                                            text = customName,

                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),

                                            color = MaterialTheme.colorScheme.primary,

                                            fontWeight = FontWeight.Bold,

                                            maxLines = 1,

                                            overflow = TextOverflow.Ellipsis,

                                            textAlign = TextAlign.Center,

                                            modifier = Modifier.fillMaxWidth()

                                        )

                                    } else {

                                        // Placeholder invisible para forzar la misma altura en todas las filas

                                        Text(

                                            "",

                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),

                                            maxLines = 1

                                        )

                                    }

                                    

                                    Row(

                                        modifier = Modifier.fillMaxWidth(),

                                        verticalAlignment = Alignment.CenterVertically

                                    ) {

                                        NSGCellDataText(row.earfcn, Modifier.weight(1f))

                                        NSGCellDataText(row.pci, Modifier.weight(1f))

                                    }

                                }



                                // Bloque Calidad LTE (Columnas 4 y 5)

                                Column(

                                    modifier = Modifier.weight(1f),

                                    horizontalAlignment = Alignment.CenterHorizontally

                                ) {

                                    Text("", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))

                                    NSGQualityMiniBlockCompact(row.rsrp, row.colorRsrp, "RSRP")

                                }

                                Column(

                                    modifier = Modifier.weight(1f),

                                    horizontalAlignment = Alignment.CenterHorizontally

                                ) {

                                    Text("", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))

                                    NSGQualityMiniBlockCompact(row.rsrq, row.colorRsrq, "RSRQ")

                                }

                            }

                            "WCDMA", "HSPA", "HSPA+" -> {

                                val customName = if (showCustomNamesPref) pciToNameMap[row.pci] else null

                                

                                // Estructura de 4 columnas: UARFCN, PSC, RSCP, RSSI

                                Row(modifier = Modifier.fillMaxWidth()) {

                                    // Columna 1: UARFCN

                                    Column(

                                        modifier = Modifier.weight(1f),

                                        horizontalAlignment = Alignment.CenterHorizontally

                                    ) {

                                        NSGCellDataText(row.earfcn)

                                    }

                                    

                                    // Columna 2: PSC

                                    Column(

                                        modifier = Modifier.weight(1f),

                                        horizontalAlignment = Alignment.CenterHorizontally

                                    ) {

                                        if (customName != null) {

                                            Text(

                                                text = customName,

                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),

                                                color = MaterialTheme.colorScheme.primary,

                                                fontWeight = FontWeight.Bold,

                                                maxLines = 1,

                                                overflow = TextOverflow.Ellipsis,

                                                textAlign = TextAlign.Center,

                                                modifier = Modifier.fillMaxWidth()

                                            )

                                        } else {

                                            NSGCellDataText(row.pci)

                                        }

                                    }

                                    

                                    // Columna 3: RSCP

                                    Column(

                                        modifier = Modifier.weight(1f),

                                        horizontalAlignment = Alignment.CenterHorizontally

                                    ) {

                                        NSGQualityMiniBlockCompact(row.rsrp, row.colorRsrp, "RSCP")

                                    }

                                    

                                    // Columna 4: RSSI

                                    Column(

                                        modifier = Modifier.weight(1f),

                                        horizontalAlignment = Alignment.CenterHorizontally

                                    ) {

                                        NSGQualityMiniBlockCompact(row.rssi, row.colorRssi, "RSSI")

                                    }

                                }

                            }

                            "GSM", "GPRS", "EDGE" -> {

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

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }
        
        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = { 
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
            )
        }

    }

}





@Composable

fun NSGCellDataTextPci(text: String, customName: String?, modifier: Modifier = Modifier) {

    if (customName != null) {

        Column(

            modifier = modifier,

            horizontalAlignment = Alignment.CenterHorizontally,

            verticalArrangement = Arrangement.Center

        ) {

            Text(

                text = customName,

                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),

                color = MaterialTheme.colorScheme.primary,

                maxLines = 1,

                fontWeight = FontWeight.Bold,

                textAlign = TextAlign.Center

            )

            Text(

                text = text,

                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),

                color = MaterialTheme.colorScheme.onSurface,

                textAlign = TextAlign.Center,

                fontWeight = FontWeight.Medium,

                maxLines = 1

            )

        }

    } else {

        NSGCellDataText(text, modifier)

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

    useNavForCategory: Boolean = false,

    onCategoryChange: (String?) -> Unit

) {

    val callPhonePermission = Manifest.permission.CALL_PHONE

    val hasCallPermission = ContextCompat.checkSelfPermission(context, callPhonePermission) == PackageManager.PERMISSION_GRANTED

    var pendingUssd by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

        if (isGranted) {

            pendingUssd?.let { ussd ->

                makeUssdCall(context, ussd, selectedSubscription)

                pendingUssd = null

            }

        } else {

            pendingUssd = null

        }

    }

    // Estados para el diálogo de confirmación de compra
    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf<PlanItem?>(null) }



    val planesDatos = listOf(

        PlanItem("2GB", "+15MIN+20SMS", "120 CUP", "*133*1*4*2*1#"),

        PlanItem("4GB", "+35MIN+40SMS", "240 CUP", "*133*1*4*3*1#"),

        PlanItem("6GB", "+60MIN+70SMS", "360 CUP", "*133*1*4*4*1#"),

        PlanItem("4.5GB", null, "240 CUP", "*133*1*4*1*1#"),

        PlanItem("ToDus 1GB", null, "25 CUP", "*133*1*2*1#"),

        PlanItem("Plan Diario 200MB", null, "25 CUP", "*133*1*3*1#")

    )



    val planesVoz = listOf(

        PlanItem("5 MIN", null, "$37.5 CUP", "*133*3*1*1#", icono = Icons.Default.Phone),

        PlanItem("10 MIN", null, "$72.5 CUP", "*133*3*2*1#", icono = Icons.Default.Phone),

        PlanItem("15 MIN", null, "$105 CUP", "*133*3*3*1#", icono = Icons.Default.Phone),

        PlanItem("25 MIN", null, "$162.5 CUP", "*133*3*4*1#", icono = Icons.Default.Phone),

        PlanItem("40 MIN", null, "$250 CUP", "*133*3*5*1#", icono = Icons.Default.Phone)

    )



    val planesSms = listOf(

        PlanItem("20 SMS", null, "$15 CUP", "*133*2*1*1#", icono = Icons.Default.Email),

        PlanItem("50 SMS", null, "$30 CUP", "*133*2*2*1#", icono = Icons.Default.Email),

        PlanItem("90 SMS", null, "$50 CUP", "*133*2*3*1#", icono = Icons.Default.Email),

        PlanItem("120 SMS", null, "$60 CUP", "*133*2*4*1#", icono = Icons.Default.Email)

    )



    // Launcher for SMS permission

    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

        if (isGranted) {

            val subId = selectedSubscription?.subscriptionId

            sendDirectSms(context, subId, "2266", "LTE")

        }

    }



    if (!useNavForCategory) {

        // Manejo del botón atrás de Android

        BackHandler(enabled = selectedCategory != null) {

            onCategoryChange(null)

        }

    }



    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(16.dp)

    ) {

        // Navegación de categorías

        val content: @Composable (String?) -> Unit = { category ->

            if (category == null) {

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

                val activePlanes = when (category) {

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

                                        VibrationManager.vibrate(context)

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

                                            // Mostrar diálogo de confirmación para comprar plan
                                            selectedPlan = plan
                                            showConfirmDialog = true

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



        if (useNavForCategory) {

            content(selectedCategory)

        } else {

            AnimatedContent(

                targetState = selectedCategory,

                transitionSpec = {

                    if (targetState != null) {

                        (slideInHorizontally(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)))

                            .togetherWith(slideOutHorizontally(animationSpec = tween(180)) { -it / 3 } + fadeOut(animationSpec = tween(180)))

                    } else {

                        (slideInHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeIn(animationSpec = tween(200)))

                            .togetherWith(slideOutHorizontally(animationSpec = tween(180)) { it } + fadeOut(animationSpec = tween(180)))

                    }

                },

                label = "planes_category_transition"

            ) { category ->

                content(category)

            }

        }

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }

        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }

        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = {
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
            )
        }

        // Diálogo de confirmación para comprar planes
        if (showConfirmDialog && selectedPlan != null) {
            Dialog(
                onDismissRequest = {
                    showConfirmDialog = false
                    selectedPlan = null
                },
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
                        // Icono
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Título
                        Text(
                            text = "CONFIRMAR COMPRA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Texto del plan
                        val plan = selectedPlan
                        val planDetails = if (!plan?.subtitle.isNullOrEmpty()) {
                            "${plan?.title} ${plan?.subtitle}"
                        } else {
                            plan?.title ?: ""
                        }
                        Text(
                            text = "Confirme que desea comprar el plan $planDetails por ${plan?.price ?: ""}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Botones en fila
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showConfirmDialog = false
                                    selectedPlan = null
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Cancelar",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = {
                                    VibrationManager.vibrate(context)
                                    showConfirmDialog = false
                                    selectedPlan?.let { plan ->
                                        // Obtener el índice de la SIM seleccionada para dual SIM
                                        val simIndex = selectedSubscription?.let { subscription ->
                                            val subscriptionManager = context.getSystemService(android.telephony.SubscriptionManager::class.java)
                                            val activeSubscriptions = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
                                            activeSubscriptions.indexOf(subscription)
                                        } ?: -1

                                        UssdManager.sendUssdRequest(context, plan.ussd, simIndex)
                                    }
                                    selectedPlan = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Aceptar",
                                    fontWeight = FontWeight.SemiBold
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

    val context = LocalContext.current

    val interactionSource = remember { MutableInteractionSource() }

    val isPressed by interactionSource.collectIsPressedAsState()



    val scale by animateFloatAsState(

        targetValue = if (isPressed) 0.98f else 1f,

        animationSpec = tween(120, easing = EaseInOut),

        label = "scale"

    )



    val elevation by animateDpAsState(

        targetValue = if (isPressed) 1.dp else 2.dp,

        animationSpec = tween(80),

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

                onClick = {

                     VibrationManager.vibrate(context)

                     onClick()

                }

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

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }
        
        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = { 
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
            )
        }

    }

}



@Composable

fun ConsultaSection(context: Context, selectedSubscription: SubscriptionInfo?) {

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

                                VibrationManager.vibrate(context)
                                
                                // Obtener el índice de la SIM seleccionada para dual SIM
                                val simIndex = selectedSubscription?.let { subscription ->
                                    val subscriptionManager = context.getSystemService(android.telephony.SubscriptionManager::class.java)
                                    val activeSubscriptions = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
                                    activeSubscriptions.indexOf(subscription)
                                } ?: -1
                                
                                UssdManager.sendUssdRequest(context, consulta.ussd, simIndex)

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



    var pendingUssd by remember { mutableStateOf<String?>(null) }



    // Launcher para solicitar permiso de llamada

    val permissionCallLauncher = rememberLauncherForActivityResult(

        contract = ActivityResultContracts.RequestPermission()

    ) { granted ->

        if (granted) {

            pendingUssd?.let { ussd ->

                makeUssdCall(context, ussd, selectedSubscription)

                pendingUssd = null

            }

        } else {

            pendingUssd = null

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

            // Sección de Número receptor

            OutlinedTextField(

                value = destinatario,

                onValueChange = { newValue ->

                    if (newValue.length <= 8 && newValue.all { it.isDigit() }) {

                        destinatario = newValue

                        numeroError = false

                    }

                },

                modifier = Modifier.fillMaxWidth(),

                label = { Text("Número receptor") },

                leadingIcon = {

                    Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)

                },

                trailingIcon = {

                    IconButton(

                        onClick = {

                            VibrationManager.vibrate(context)

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

                    focusedContainerColor = MaterialTheme.colorScheme.surface,

                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,

                    errorContainerColor = MaterialTheme.colorScheme.surface

                ),

                supportingText = {

                    if (numeroError) {

                        Text("Ingrese un número válido", color = MaterialTheme.colorScheme.error)

                    }

                }

            )



            // Campo PIN

            OutlinedTextField(

                value = contraseña,

                onValueChange = {

                    if (it.length <= 4 && it.all { it.isDigit() }) {

                        contraseña = it

                        pinError = false

                    }

                },

                modifier = Modifier.fillMaxWidth(),

                label = { Text("PIN") },

                leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },

                visualTransformation = PasswordVisualTransformation(),

                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),

                singleLine = true,

                isError = pinError,

                shape = RoundedCornerShape(16.dp),

                colors = OutlinedTextFieldDefaults.colors(

                    focusedContainerColor = MaterialTheme.colorScheme.surface,

                    unfocusedContainerColor = MaterialTheme.colorScheme.surface

                )

            )



            // Campo Importe en CUP

            OutlinedTextField(

                value = monto,

                onValueChange = {

                    if (it.all { char -> char.isDigit() }) {

                        monto = it

                        montoError = false

                    }

                },

                modifier = Modifier.fillMaxWidth(),

                label = { Text("Importe en CUP") },

                leadingIcon = { Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp)) },

                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                singleLine = true,

                isError = montoError,

                shape = RoundedCornerShape(16.dp),

                colors = OutlinedTextFieldDefaults.colors(

                    focusedContainerColor = MaterialTheme.colorScheme.surface,

                    unfocusedContainerColor = MaterialTheme.colorScheme.surface

                )

            )



            Spacer(modifier = Modifier.height(8.dp))



            // Botones Cancelar y Continuar

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.spacedBy(12.dp)

            ) {

                // Botón Cancelar

                OutlinedButton(

                    onClick = {

                        destinatario = ""

                        contraseña = ""

                        monto = ""

                        numeroError = false

                        pinError = false

                        montoError = false

                    },

                    modifier = Modifier

                        .weight(1f)

                        .height(56.dp),

                    shape = RoundedCornerShape(16.dp),

                    colors = ButtonDefaults.outlinedButtonColors(

                        contentColor = MaterialTheme.colorScheme.onSurface

                    )

                ) {

                    Text(

                        "Cancelar",

                        style = MaterialTheme.typography.titleMedium,

                        fontWeight = FontWeight.SemiBold

                    )

                }



                // Botón Continuar

                Button(

                    onClick = {

                        VibrationManager.vibrate(context)

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

                            pendingUssd = ussd

                            permissionCallLauncher.launch(Manifest.permission.CALL_PHONE)

                        }

                    },

                    modifier = Modifier

                        .weight(1f)

                        .height(56.dp)

                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.primary),

                    shape = RoundedCornerShape(16.dp),

                    colors = ButtonDefaults.buttonColors(

                        containerColor = MaterialTheme.colorScheme.primary

                    ),

                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)

                ) {

                    Text(

                        "Continuar",

                        style = MaterialTheme.typography.titleMedium,

                        fontWeight = FontWeight.ExtraBold

                    )

                }

            }

        }

        // Diálogo para mostrar respuestas USSD global
        val ussdResponse by UssdManager.ussdResponse.collectAsState()
        val isLoading by UssdManager.isLoading.collectAsState()
        var showGlobalUssdDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(ussdResponse, isLoading) {
            if (ussdResponse != null || isLoading) {
                showGlobalUssdDialog = true
            }
        }
        
        if (showGlobalUssdDialog) {
            UssdResponseDialog(
                onDismiss = { 
                    showGlobalUssdDialog = false
                    UssdManager.clearResponse()
                }
            )
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
    displayInfo: Any?, // Cambiado de TelephonyDisplayInfo? para evitar crash en API < 30
    physicalConfigList: List<Any>, // Cambiado de List<PhysicalChannelConfig>

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

            

            // MÉTODO (NetMonster/NSG style):

            // 1. Usar DisplayInfo para detección instantánea del estado lógico de CA (LTE+)

            val isDisplayCa = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayInfo != null) {
                try {
                    val di = displayInfo as android.telephony.TelephonyDisplayInfo
                    di.overrideNetworkType == 1 || // OVERRIDE_NETWORK_TYPE_LTE_CA
                    di.overrideNetworkType == 2    // OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
                } catch (e: Exception) {
                    false
                }
            } else false



            // 2. Usar ServiceState para contar portadoras físicas

            val bandwidths = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceState != null) {

                serviceState.cellBandwidths

            } else intArrayOf()



            // Lógica de Badge con Jerarquía: PhysicalChannelConfig > Bandwidths

            val bwComponents = mutableListOf<BwComponent>()

            var bwTotalStr: String? = null



            // FILTRADO MEJORADO: Solo contar PhysicalChannelConfigs que estén ACTIVOS
            val activePhysicalConfigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                physicalConfigList.filter { configObj ->
                    val config = configObj as? android.telephony.PhysicalChannelConfig ?: return@filter false
                    
                    val hasValidBand = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val band = config.band
                            band in 1..88
                        } else false
                    } catch (e: Exception) { false }

                    val hasValidPci = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pci = config.physicalCellId
                            pci >= 0 && pci < 504
                        } else false
                    } catch (e: Exception) { false }

                    val hasValidEarfcn = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val earfcn = config.downlinkChannelNumber
                            earfcn > 0 && earfcn != Int.MAX_VALUE
                        } else false
                    } catch (e: Exception) { false }

                    val hasValidData = hasValidBand || hasValidPci || hasValidEarfcn

                    // Verificar estado de conexión (Android 12+)
                    val isActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            // CONNECTION_STATUS_PRIMARY_SERVING = 1
                            // CONNECTION_STATUS_SECONDARY_SERVING = 2
                            config.connectionStatus == 1 || config.connectionStatus == 2
                        } catch (e: Exception) { true }
                    } else true
                    
                    hasValidData && isActive
                }
            } else emptyList()



            // Calculamos CC count con validación apropiada

            val ccCount = when {

                !isDataEnabled -> 1

                // Prioridad 1: DisplayInfo indica CA Y tenemos configs activos

                isDisplayCa && activePhysicalConfigs.size > 1 -> activePhysicalConfigs.size

                // Prioridad 2: DisplayInfo indica CA Y tenemos bandwidths

                isDisplayCa && bandwidths.size > 1 -> bandwidths.size

                // Prioridad 3: DisplayInfo indica CA pero sin detalles, asumir 2CC

                isDisplayCa -> 2

                // Prioridad 4: NO hay indicación de CA en DisplayInfo

                !isDisplayCa && activePhysicalConfigs.isEmpty() -> 1

                !isDisplayCa && bandwidths.isEmpty() -> 1

                // Si DisplayInfo no indica CA, no mostrar CA aunque haya múltiples configs

                // (podrían ser datos obsoletos)

                else -> 1

            }

            if (ccCount > 1) {

                badgeText = "${ccCount}CC"

            }



// Cálculo de Ancho de Banda (Estilo Pro) - EMPAREJAMIENTO CORRECTO

            var totalBandwidthKhz = 0



            // Paso 1: Crear mapa de celdas LTE con información completa

            data class CellInfo(val earfcn: Int, val pci: Int, val band: Int, val isRegistered: Boolean)

            val cellsMap = lteCells.mapNotNull { cell ->

                val earfcn = cell.cellIdentity.earfcn

                val pci = cell.cellIdentity.pci

                if (earfcn > 0 && earfcn != Int.MAX_VALUE && pci >= 0 && pci != Int.MAX_VALUE) {

                    val bandStr = getLteBand(earfcn)

                    val bandNum = bandStr.replace("Band ", "").toIntOrNull() ?: 0

                    if (bandNum in 1..88) {

                        CellInfo(earfcn, pci, bandNum, cell.isRegistered)

                    } else null

                } else null

            }



            // Paso 2: Crear lista de componentes BW con emparejamiento PCI/EARFCN

            data class BwCandidate(val band: Int, val bwMhz: Int, val pci: Int?, val earfcn: Int?, val index: Int)

            val bwCandidates = mutableListOf<BwCandidate>()



            if (activePhysicalConfigs.isNotEmpty()) {

                val shouldShowExtendedInfo = badgeText != "LTE"
                val configsToProcess = if (isDataEnabled && shouldShowExtendedInfo) activePhysicalConfigs else activePhysicalConfigs.take(1)



                configsToProcess.forEachIndexed { index, configObj ->
                    val config = configObj as? android.telephony.PhysicalChannelConfig ?: return@forEachIndexed
                    
                    var bwKhz = 0
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                           bwKhz = config.cellBandwidthDownlinkKhz
                        }
                    } catch (e: Exception) { }

                    if (bwKhz > 0 && bwKhz != Int.MAX_VALUE) {

                        val bwMhz = bwKhz / 1000

                        var bandNum = 0

                        var pciFromConfig: Int? = null

                        var earfcnFromConfig: Int? = null



                        // Extraer PCI del PhysicalChannelConfig (si está disponible)

                        try {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                                val pci = config.physicalCellId

                                pciFromConfig = if (pci != Int.MAX_VALUE && pci >= 0) pci else null

                            }

                        } catch (e: Exception) {

                            pciFromConfig = null

                        }



// Extraer EARFCN del PhysicalChannelConfig

                        try {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                                val channelNum = config.downlinkChannelNumber

                                earfcnFromConfig = if (channelNum > 0 && channelNum != Int.MAX_VALUE) channelNum else null

                            }

                        } catch (e: Exception) {

                            earfcnFromConfig = null

                        }



                        // MÉTODO 1: config.band directo

                        try {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                                if (config.band in 1..88) {

                                    bandNum = config.band

                                }

                            }

                        } catch (e: Exception) { }



                        // MÉTODO 2: Buscar por PCI coincidente

                        if (bandNum == 0 && pciFromConfig != null) {

                            val matchingCell = cellsMap.firstOrNull { it.pci == pciFromConfig }

                            if (matchingCell != null) {

                                bandNum = matchingCell.band

                            }

                        }



                        // MÉTODO 3: Buscar por EARFCN coincidente

                        if (bandNum == 0 && earfcnFromConfig != null) {

                            val matchingCell = cellsMap.firstOrNull { it.earfcn == earfcnFromConfig }

                            if (matchingCell != null) {

                                bandNum = matchingCell.band

                            } else {

                                // Si no hay celda con ese EARFCN, calcular banda del EARFCN

                                val bandStr = getLteBand(earfcnFromConfig)

                                val parsedBand = bandStr.replace("Band ", "").toIntOrNull() ?: 0

                                if (parsedBand in 1..88) {

                                    bandNum = parsedBand

                                }

                            }

                        }



                        // MÉTODO 4: Reflexión para campos ocultos

                        if (bandNum == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                            try {

                                val bandMethod = config.javaClass.getDeclaredMethod("getBand")

                                bandMethod.isAccessible = true

                                val hiddenBand = bandMethod.invoke(config) as? Int

                                if (hiddenBand != null && hiddenBand in 1..88) {

                                    bandNum = hiddenBand

                                }

                            } catch (e: Exception) {

                                // Ignorar

                            }

                        }



                        // MÉTODO 5: Parsing del toString()

                        if (bandNum == 0) {

                            try {

                                val configStr = config.toString()



                                // Buscar banda

                                val bandRegex = """(?:band|mBand|Band)\s*[:=]\s*(\d+)""".toRegex(RegexOption.IGNORE_CASE)

                                val match = bandRegex.find(configStr)

                                if (match != null) {

                                    val parsedBand = match.groupValues[1].toIntOrNull() ?: 0

                                    if (parsedBand in 1..88) {

                                        bandNum = parsedBand

                                    }

                                }



                                // Buscar PCI si no lo tenemos

                                if (pciFromConfig == null) {

                                    val pciRegex = """(?:pci|physicalCellId|cellId)\s*[:=]\s*(\d+)""".toRegex(RegexOption.IGNORE_CASE)

                                    val pciMatch = pciRegex.find(configStr)

                                    if (pciMatch != null) {

                                        val parsedPci = pciMatch.groupValues[1].toIntOrNull()

                                        if (parsedPci != null && parsedPci >= 0 && parsedPci < 504) {

                                            pciFromConfig = parsedPci

                                            // Reintentar buscar por PCI

                                            val matchingCell = cellsMap.firstOrNull { it.pci == parsedPci }

                                            if (matchingCell != null && bandNum == 0) {

                                                bandNum = matchingCell.band

                                            }

                                        }

                                    }

                                }



                                // Buscar EARFCN si no lo tenemos

                                if (earfcnFromConfig == null && bandNum == 0) {

                                    val earfcnRegex = """(?:earfcn|channel|arfcn)\s*[:=]\s*(\d+)""".toRegex(RegexOption.IGNORE_CASE)

                                    val earfcnMatch = earfcnRegex.find(configStr)

                                    if (earfcnMatch != null) {

                                        val parsedEarfcn = earfcnMatch.groupValues[1].toIntOrNull()

                                        if (parsedEarfcn != null && parsedEarfcn > 0) {

                                            earfcnFromConfig = parsedEarfcn

                                            val bandStr = getLteBand(parsedEarfcn)

                                            val parsedBand = bandStr.replace("Band ", "").toIntOrNull() ?: 0

                                            if (parsedBand in 1..88) {

                                                bandNum = parsedBand

                                            }

                                        }

                                    }

                                }

                            } catch (e: Exception) {

                                // Ignorar

                            }

                        }



                        // Agregar candidato (incluso si bandNum = 0, lo resolveremos después)

                        bwCandidates.add(BwCandidate(bandNum, bwMhz, pciFromConfig, earfcnFromConfig, index))

                    }

                }

            } else if (bandwidths.isNotEmpty()) {

                // Fallback para Android antiguo
                val shouldShowExtendedInfo = badgeText != "LTE"
                val bwsToProcess = if (isDataEnabled && shouldShowExtendedInfo) bandwidths.toList() else bandwidths.take(1)



                bwsToProcess.forEachIndexed { index, bwKhz ->

                    if (bwKhz > 0 && bwKhz != Int.MAX_VALUE) {

                        val bwMhz = bwKhz / 1000

                        val bandNum = if (index < cellsMap.size) cellsMap[index].band else 0

                        bwCandidates.add(BwCandidate(bandNum, bwMhz, null, null, index))

                    }

                }

            }



// Paso 3: Asignar bandas faltantes usando celdas disponibles no usadas

            val usedBands = bwCandidates.filter { it.band > 0 }.map { it.band }.toMutableSet()

            val availableCells = cellsMap.filter { it.band !in usedBands }.sortedByDescending { it.isRegistered }



            bwCandidates.forEachIndexed { idx, candidate ->

                if (candidate.band == 0 && availableCells.isNotEmpty()) {

                    // Asignar la primera celda disponible

                    val cellToUse = availableCells.firstOrNull()

                    if (cellToUse != null) {

                        bwCandidates[idx] = candidate.copy(band = cellToUse.band)

                        usedBands.add(cellToUse.band)

                        availableCells.toMutableList().remove(cellToUse)

                    }

                }

            }



// Paso 4: Construir componentes BW finales

            bwCandidates.forEach { candidate ->

                val bandName = if (candidate.band > 0) "B${candidate.band}" else "Bx"

                bwComponents.add(BwComponent(bandName, "${candidate.bwMhz}MHz"))

                totalBandwidthKhz += candidate.bwMhz * 1000



                // Debug log

                if (bandName == "Bx") {

                    println("⚠️ BW[${candidate.index}]: No detectado (${candidate.bwMhz}MHz)")

                    println("   - PCI: ${candidate.pci}")

                    println("   - EARFCN: ${candidate.earfcn}")

                    println("   - Celdas disponibles: ${cellsMap.size}")

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



                // SNR Extraction Logic - Enhanced for Xiaomi & Custom ROMs (and Samsung A20 dummy 0 fix)

                var snrRaw = if (ss.rssnr != Int.MAX_VALUE) ss.rssnr else null



                // Si es 0 o null, intentamos fallbacks ya que en muchos dispositivos (como Samsung A20) 

                // el módem reporta 0 cuando el dato no está disponible realmente.

                if (snrRaw == null || snrRaw == 0) {

                    val ssStr = ss.toString()

                    val snrRegex = "(?:rssnr|snr)[:=]\\s*([+-]?\\d+)".toRegex(RegexOption.IGNORE_CASE)

                    val snrFromStr = snrRegex.find(ssStr)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it != Int.MAX_VALUE }

                    

                    // Si encontramos un valor no nulo en el string, y el actual es nulo o 0, priorizamos el del string

                    // a menos que el del string también sea 0 (en cuyo caso seguimos buscando).

                    if (snrFromStr != null && (snrRaw == null || (snrRaw == 0 && snrFromStr != 0))) {

                        snrRaw = snrFromStr

                    }

                }



                // Fallback 1: Otro intento con nombres de métodos de reflexión

                if (snrRaw == null || snrRaw == 0) {

                    try {

                        val method = try {

                            ss.javaClass.getMethod("getSnr")

                        } catch (e: Exception) {

                            try {

                                ss.javaClass.getMethod("getLteSnr")

                            } catch (e2: Exception) {

                                null

                            }

                        }

                        if (method != null) {

                            val v = method.invoke(ss) as Int

                            if (v != Int.MAX_VALUE && (snrRaw == null || v != 0)) snrRaw = v

                        }

                    } catch (e: Exception) { }

                }



                // Fallback: Global Signal Strength (Critical for some Xiaomi/Custom ROMs/Samsung)

                if ((snrRaw == null || (it.isRegistered && snrRaw == 0)) && signalStrength != null) {

                    try {

                        // Encontrar componente LTE en señal global

                        val globalLte = signalStrength.getCellSignalStrengths(CellSignalStrengthLte::class.java).firstOrNull()

                        

                        if (globalLte != null) {

                            val gSnr = globalLte.rssnr

                            if (gSnr != Int.MAX_VALUE && (snrRaw == null || (snrRaw == 0 && gSnr != 0))) {

                                snrRaw = gSnr

                            } else {

                                // Parsing del string del componente global

                                val globalStr = globalLte.toString()

                                val snrRegex = "(?:rssnr|snr)[:=]\\s*([+-]?\\d+)".toRegex(RegexOption.IGNORE_CASE)

                                val gSnrStr = snrRegex.find(globalStr)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it != Int.MAX_VALUE }

                                if (gSnrStr != null && (snrRaw == null || (snrRaw == 0 && gSnrStr != 0))) {

                                    snrRaw = gSnrStr

                                }

                            }

                        }

                        

                        // Último recurso: Buscar en el string bruto de TODO el objeto SignalStrength

                        if (snrRaw == null || snrRaw == 0) {

                             val fullStr = signalStrength.toString()

                             val snrRegex = "(?:rssnr|snr)[:=]\\s*([+-]?\\d+)".toRegex(RegexOption.IGNORE_CASE)

                             val fSnrStr = snrRegex.find(fullStr)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it != Int.MAX_VALUE }

                             if (fSnrStr != null && (snrRaw == null || (snrRaw == 0 && fSnrStr != 0))) {

                                 snrRaw = fSnrStr

                             }

                        }

                    } catch(e: Exception) { }

                }



                // Si después de todos los fallos sigue siendo 0 y el valor bruto de la API era null/max,

                // entonces preferimos no mostrar nada (null -> "-") en lugar de un 0 sospechoso.

                // Excepto si el valor bruto era 0 realmente y no encontramos nada mejor.

                if (snrRaw != null && snrRaw > 100 && snrRaw != Int.MAX_VALUE) {

                    snrRaw /= 10

                }



                // RSSI Extraction Logic - LTE (Samsung often reports 0 or dummy values)

                var rssiRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    ss.rssi.takeIf { it != Int.MAX_VALUE && it != 0 }

                } else null



                if (rssiRaw == null) {

                    // Fallback a toString

                    val ssStr = ss.toString()

                    val rssiRegex = "(?:rssi)[:=]\\s*([+-]?\\d+)".toRegex(RegexOption.IGNORE_CASE)

                    rssiRaw = rssiRegex.find(ssStr)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it != Int.MAX_VALUE && it != 0 }

                }



                if (rssiRaw == null && it.isRegistered && signalStrength != null) {

                    try {

                        val fullStr = signalStrength.toString()

                        val rssiRegex = "(?:rssi)[:=]\\s*([+-]?\\d+)".toRegex(RegexOption.IGNORE_CASE)

                        rssiRaw = rssiRegex.find(fullStr)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it != Int.MAX_VALUE && it != 0 }

                    } catch(e: Exception) {}

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

                    rssi = rssiRaw?.let { v -> "$v dBm" },

                    colorRsrp = getParamColor("RSRP", ss.rsrp),

                    colorRsrq = getParamColor("RSRQ", ss.rsrq),

                    colorSnr = snrRaw?.let { getParamColor("SNR", it) } ?: Color.Gray,

                    colorRssi = rssiRaw?.let { getParamColor("RSSI", it) } ?: Color.Gray,

                    isAggregated = false,

                    ta = if (isStale && taStr == null) "-" else taStr,

                    bwList = if (it.isRegistered && bwComponents.isNotEmpty()) bwComponents else emptyList(),

                    bwTotal = if (it.isRegistered) bwTotalStr else null

                )

            }.toMutableList()



            // Post-proceso para marcar las celdas CA (solo si hay evidencia de agregación activa y datos activados)
            if (isDataEnabled && badgeText != "LTE") {

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

            val networkType = get3GNetworkType(displayInfo)

            Triple(networkType, "", wcdmaCells.map { cellInfoItem ->

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



                // Fallback 2: Parsing del toString() de la celda

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

            val networkType = get2GNetworkType(displayInfo)

            Triple(networkType, "", gsmCells.map {

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



private fun get3GNetworkType(displayInfo: Any?): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayInfo != null) {
        val di = displayInfo as? android.telephony.TelephonyDisplayInfo ?: return "WCDMA"
        // Check override network types first (for LTE/5G)
        when (di.overrideNetworkType) {
            3 -> return "5G NSA" // OVERRIDE_NETWORK_TYPE_NR_NSA
            4 -> return "5G+" // OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
            1 -> return "LTE+" // OVERRIDE_NETWORK_TYPE_LTE_CA
            2 -> return "LTE Pro" // OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
        }
        
        // For 3G networks, use the network type to determine specific technology
        return when (di.networkType) {
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_UMTS -> "WCDMA"
            else -> "WCDMA"
        }
    }
    return "WCDMA"
}



private fun get2GNetworkType(displayInfo: Any?): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayInfo != null) {
        val di = displayInfo as? android.telephony.TelephonyDisplayInfo ?: return "GPRS"
        // Check override network types first (for LTE/5G)
        when (di.overrideNetworkType) {
            3 -> return "5G NSA" // OVERRIDE_NETWORK_TYPE_NR_NSA
            4 -> return "5G+" // OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
            1 -> return "LTE+" // OVERRIDE_NETWORK_TYPE_LTE_CA
            2 -> return "LTE Pro" // OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
        }
        
        // For 2G networks, use the network type to determine specific technology
        return when (di.networkType) {
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
            else -> "GSM"
        }
    }
    return "GSM"
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

