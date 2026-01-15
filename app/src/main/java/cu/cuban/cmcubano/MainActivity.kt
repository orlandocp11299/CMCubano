package cu.cuban.cmcubano

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
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
import cu.cuban.cmcubano.screens.*
import cu.cuban.cmcubano.ui.theme.CMCubanoTheme

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
        
        // Solicitar permisos necesarios
        requestRequiredPermissions()
        
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
            var amoledPref by remember { mutableStateOf(prefs.getBoolean("amoled_dark", false)) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "amoled_dark") {
                        amoledPref = sharedPreferences.getBoolean("amoled_dark", false)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            CMCubanoTheme(amoledDark = isSystemInDarkTheme() && amoledPref) {
                val view = LocalView.current
                val isDark = isSystemInDarkTheme()
                val systemBarColor = MaterialTheme.colorScheme.background

                DisposableEffect(isDark) {
                    window.statusBarColor = systemBarColor.toArgb()
                    window.navigationBarColor = systemBarColor.toArgb()
                    
                    val windowInsetsController = WindowCompat.getInsetsController(window, view)
                    windowInsetsController?.isAppearanceLightStatusBars = !isDark
                    windowInsetsController?.isAppearanceLightNavigationBars = !isDark
                    
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
                        MainScreen()
                        
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                        var showWelcome by remember { 
                            mutableStateOf<Boolean>(prefs.getBoolean("show_welcome", true)) 
                        }

                        if (showWelcome) {
                            WelcomeDialog(
                                onDismissRequest = { 
                                    // Just close for this session, but don't save "don't show" unless explicitly asked (handled inside dialog logic if passed)
                                    // Actually the dialog logic I wrote calls onDisableWelcome if checked when clicking Continue.
                                    // If they just dismiss (back button), we might want to respect that too? 
                                    // The dialog I wrote sends onDismissRequest when clicking Continue.
                                    showWelcome = false
                                },
                                onDisableWelcome = {
                                    prefs.edit().putBoolean("show_welcome", false).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun requestRequiredPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "inicio",
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
            composable("transferencia_list") { UtilesScreen(navController, initialTab = 4, showTabs = false, standaloneTitle = "Transferencia") }
            composable("utiles") { UtilesScreen(navController) }
            composable("datos") { DatosScreen(navController) }
            composable("mapa") { MapaScreen(navController) }
            composable("mas") { AjustesScreen(navController) }
            composable("speedtest") { SpeedTestScreen(navController) }
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