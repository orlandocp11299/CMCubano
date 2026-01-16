package cu.cuban.cmcubano.screens

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebSettings
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import cu.cuban.cmcubano.utils.AdBlockWebViewClient
import cu.cuban.cmcubano.utils.WebViewCacheManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Usar el WebView cacheado para mejor rendimiento
    val cachedWebView = remember { WebViewCacheManager.getOrCreateWebView(context) }
    
    // Manejar el ciclo de vida del WebView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> cachedWebView.onResume()
                Lifecycle.Event.ON_PAUSE -> cachedWebView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Mapa de Radiobases",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Recargar la página web
                            cachedWebView.reload()
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar mapa",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        AndroidView(
            factory = { cachedWebView },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            update = { webView ->
                // Solo cargar la URL si no está cargada o es diferente
                if (webView.url == null || !webView.url!!.contains("cellmapper.net")) {
                    webView.loadUrl("https://www.cellmapper.net/map?MCC=368&MNC=1&type=LTE&latitude=23.113592&longitude=-82.366592&zoom=7&showTowers=true&showCoverage=true&clusterEnabled=true&tilesEnabled=true&showOrphans=false&showNoFrequencyOnly=false&showFrequencyOnly=false&showBandwidthOnly=false&DateFilterType=Last&showHex=false&showVerifiedOnly=false&showUnverifiedOnly=false&showLTECAOnly=false&showENDCOnly=false&showBand=0&showSectorColours=true")
                }
            }
        )
    }
}