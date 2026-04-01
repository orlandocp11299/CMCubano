package cu.cuban.cmcubano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import cu.cuban.cmcubano.DataPlanWidgetProvider
import cu.cuban.cmcubano.DataUsageWidgetProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.core.view.WindowCompat
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext
import android.content.SharedPreferences
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.navigation.NavType
import androidx.navigation.navArgument
import cu.cuban.cmcubano.screens.WelcomeDialog
import cu.cuban.cmcubano.screens.PermissionsDialog
import cu.cuban.cmcubano.screens.*
import cu.cuban.cmcubano.ui.theme.CMCubanoTheme
import cu.cuban.cmcubano.models.AppUpdate
import cu.cuban.cmcubano.utils.SupabaseManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Todos los permisos fueron concedidos
            println("Todos los permisos concedidos")
        } else {
            // Algunos permisos fueron denegados
            println("Algunos permisos fueron denegados")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Solicitar permisos necesarios (Movido a LaunchedEffect en setContent para no bloquear el diálogo de bienvenida)
        // requestRequiredPermissions()
        
        // Verificar si se debe iniciar el monitor de red
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("monitor_network_notification", false)) {
            val netIntent = android.content.Intent(this, cu.cuban.cmcubano.NotificationService::class.java).apply {
                action = cu.cuban.cmcubano.NotificationService.ACTION_START_MONITOR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(netIntent)
            } else {
                startService(netIntent)
            }
        }

        // Verificar si se debe iniciar el monitor de datos
        val dataPrefs = getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
        if (dataPrefs.getBoolean("data_usage_live_notification", false)) {
            val dataIntent = android.content.Intent(this, cu.cuban.cmcubano.NotificationService::class.java).apply {
                action = cu.cuban.cmcubano.NotificationService.ACTION_START_DATA_MONITOR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(dataIntent)
            } else {
                startService(dataIntent)
            }
        }
        
        // Verificar si se debe iniciar la burbuja flotante
        if (dataPrefs.getBoolean("floating_bubble_enabled", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    val bubbleIntent = android.content.Intent(this, cu.cuban.cmcubano.services.FloatingBubbleService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(bubbleIntent)
                    } else {
                        startService(bubbleIntent)
                    }
                }
            } else {
                val bubbleIntent = android.content.Intent(this, cu.cuban.cmcubano.services.FloatingBubbleService::class.java)
                startService(bubbleIntent)
            }
        }
        
        // Determinar destino inicial
        val navigateTo = intent?.getStringExtra("navigate_to")
        val startDest = "inicio" // Siempre iniciar en inicio para mantener pila de navegación
        val navigateToDest = when (navigateTo) {
            "utiles_screen" -> "red_info"
            "control_datos" -> "control_datos"
            "control_datos_settings" -> "control_datos_settings"
            "firewall" -> "firewall"
            else -> null
        }

        setContent {
            val context = LocalContext.current
            val activity = remember { this }
            // val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) } // Ya obtenido arriba, pero needed for composable scope if reactive?
            // Better to re-get or just use the one we have? standard practice is re-remember inside composable.
            val prefsState = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
            
            var themeMode by remember { mutableStateOf(prefsState.getInt("theme_mode", 0)) }
            
            // Estado para el mensaje de bienvenida
            var showWelcome by rememberSaveable {
                mutableStateOf(prefsState.getBoolean("show_welcome_screen", true))
            }

            // Estado para el diálogo de permisos (solo se muestra 1 vez)
            var showPermissions by rememberSaveable {
                mutableStateOf(prefsState.getBoolean("show_permissions_screen", true))
            }

            // Estado para actualización
            var appUpdate by remember { mutableStateOf<AppUpdate?>(null) }

            // Comprobar actualizaciones al iniciar
            LaunchedEffect(Unit) {
                try {
                    val latestUpdate = withContext(Dispatchers.IO) {
                        SupabaseManager.client.from("app_updates")
                            .select {
                                order("version_code", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                                limit(1)
                            }.decodeSingleOrNull<AppUpdate>()
                    }

                    if (latestUpdate != null && latestUpdate.versionCode > BuildConfig.VERSION_CODE) {
                        appUpdate = latestUpdate
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Efecto para mostrar el diálogo de permisos después del mensaje de bienvenida
            LaunchedEffect(showWelcome) {
                if (!showWelcome && showPermissions) {
                    // No hacer nada aquí, el diálogo se mostrará en el UI
                }
            }

            DisposableEffect(prefsState) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "theme_mode") {
                        themeMode = sharedPreferences.getInt("theme_mode", 0)
                    }
                }
                prefsState.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefsState.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when(themeMode) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }
            CMCubanoTheme(darkTheme = useDarkTheme) {
                val view = LocalView.current
                val systemBarColor = MaterialTheme.colorScheme.background

                DisposableEffect(useDarkTheme) {
                    window.statusBarColor = systemBarColor.toArgb()
                    window.navigationBarColor = systemBarColor.toArgb()
                    
                    val windowInsetsController = WindowCompat.getInsetsController(window, view)
                    windowInsetsController?.isAppearanceLightStatusBars = !useDarkTheme
                    windowInsetsController?.isAppearanceLightNavigationBars = !useDarkTheme
                    
                    onDispose {}
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        MainScreen(startDestination = startDest, navigateToDest = navigateToDest)

                        if (showWelcome) {
                            WelcomeDialog(
                                onDismissRequest = {
                                    showWelcome = false
                                },
                                onDisableWelcome = {
                                    prefsState.edit().putBoolean("show_welcome_screen", false).apply()
                                }
                            )
                        }

                        // Diálogo de permisos (solo si no se muestra bienvenida)
                        if (!showWelcome && showPermissions) {
                            PermissionsDialog(
                                onGrantPermissions = {
                                    // Marcar que ya se mostró el diálogo
                                    prefsState.edit().putBoolean("show_permissions_screen", false).apply()
                                    showPermissions = false
                                    // Solicitar permisos
                                    requestRequiredPermissions()
                                }
                            )
                        }

                        // Diálogo de actualización
                        appUpdate?.let { update ->
                            UpdateDialog(
                                update = update,
                                onDismissRequest = { appUpdate = null }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Actualizar widgets al entrar en la app
        try {
            val refreshPlan = Intent(this, DataPlanWidgetProvider::class.java).apply {
                action = "cu.cuban.cmcubano.DATA_PLAN_REFRESH"
            }
            sendBroadcast(refreshPlan)

            val refreshUsage = Intent(this, DataUsageWidgetProvider::class.java).apply {
                action = "cu.cuban.cmcubano.DATA_USAGE_REFRESH"
            }
            sendBroadcast(refreshUsage)
        } catch (e: Exception) {
            // Ignorar errores al refrescar widgets
        }
    }
    
    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.PACKAGE_USAGE_STATS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Filtrar permisos que no están concedidos
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            println("Todos los permisos ya están concedidos")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(startDestination: String = "inicio", navigateToDest: String? = null) {
    val navController = rememberNavController()

    // Navegar al destino específico si viene de notificación
    LaunchedEffect(navigateToDest) {
        navigateToDest?.let {
            navController.navigate(it)
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(200)
                ) + fadeIn(animationSpec = tween(200))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = tween(180)
                ) + fadeOut(animationSpec = tween(180))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(200)
                ) + fadeIn(animationSpec = tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(180)
                ) + fadeOut(animationSpec = tween(180))
            }
        ) {
            composable("inicio") { InicioScreen(navController) }
            composable("noticias") { NoticiasScreen(navController) }
            composable("red_info") { UtilesScreen(navController, initialTab = 0, showTabs = false, standaloneTitle = "Información de Red") }
            composable("planes_menu") { PlanesMenuScreen(navController) }
            composable(
                route = "planes_list?category={category}",
                arguments = listOf(
                    navArgument("category") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category")
                UtilesScreen(
                    navController,
                    initialTab = 2,
                    showTabs = false,
                    standaloneTitle = "Planes",
                    initialPlanesCategory = category,
                    useNavForPlanesCategory = true
                )
            }
            composable("consultas_list") { UtilesScreen(navController, initialTab = 3, showTabs = false, standaloneTitle = "Consultas") }
            composable("atencion_cliente_list") { AtencionClienteScreen(navController) }
            composable("transferencia_list") { UtilesScreen(navController, initialTab = 4, showTabs = false, standaloneTitle = "Transferencia") }
            composable("recordatorio_screen") { RecordatorioScreen(navController) }
            composable("utiles") { UtilesScreen(navController) }
            composable("datos") { DatosScreen(navController) }
            composable(
                route = "datos_content?section={section}",
                arguments = listOf(
                    navArgument("section") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "conceptos"
                    }
                )
            ) { backStackEntry ->
                val section = backStackEntry.arguments?.getString("section")
                DatosContentScreen(navController, section ?: "conceptos")
            }
            composable("mapa") { MapaScreen(navController) }
            composable("mas") { AjustesScreen(navController) }
            composable("configuracion_app") { AppSettingsScreen(navController) }
            composable("speedtest") { SpeedTestScreen(navController) }
            composable("speedtest_history") { SpeedTestHistoryScreen(navController) }
            composable("control_datos") { ControlDatosScreen(navController) }
            composable("control_datos_settings") { ControlDatosSettingsScreen(navController) }
            composable("firewall") { FirewallScreen(navController) }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    CMCubanoTheme {
        MainScreen()
    }
}

// La función InicioScreen ha sido movida a screens/InicioScreen.kt