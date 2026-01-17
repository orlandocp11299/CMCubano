package cu.cuban.cmcubano.utils

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup

class CachedWebViewClient : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: android.webkit.WebResourceRequest
    ): android.webkit.WebResourceResponse? {
        // Dejar que AdBlockWebViewClient maneje los anuncios primero
        return super.shouldInterceptRequest(view, request)
    }
}

object WebViewCacheManager {
    private var cachedWebView: WebView? = null
    
    fun getOrCreateWebView(context: Context): WebView {
        return cachedWebView ?: createOptimizedWebView(context).also { 
            cachedWebView = it 
        }
    }
    
    fun releaseWebView() {
        cachedWebView?.apply {
            stopLoading()
            onPause()
            destroyDrawingCache()
            clearHistory()
        }
        cachedWebView = null
    }
    
    private fun createOptimizedWebView(context: Context): WebView {
        return WebView(context).apply {
            webViewClient = AdBlockWebViewClient()
            
            settings.apply {
                // Configuraciones básicas
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // Configuraciones de visualización
                loadWithOverviewMode = false
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                setGeolocationEnabled(true)
                textZoom = 100
                
                // Optimizaciones de caché avanzadas
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                
                // Caché de recursos y rendimiento
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE)
                allowFileAccess = true
                allowContentAccess = true
                
                // Optimización de carga
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                
                // User agent optimizado
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                
                // Habilitar caché de DOM y base de datos
                databaseEnabled = true
                setDomStorageEnabled(true)
            }
            
            // Configuración de layout
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Eliminar barras de scroll para mejor UX
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            
            // Habilitar hardware acceleration
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }
}
