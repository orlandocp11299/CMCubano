package cu.cuban.cmcubano.utils

import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import java.io.ByteArrayInputStream

class AdBlockWebViewClient : WebViewClient() {
    private val adServersList = listOf(
        "google-analytics.com",
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adnxs.com",
        "facebook.com",
        "scorecardresearch.com",
        "advertising.com",
        "ads.",
        "adserver.",
        "adnetwork.",
        "ad.doubleclick.net",
        "pagead",
        "googleads",
        "analytics",
        "tracking",
        "banner",
        "advertisement"
    )

    private val cssInject = """
        javascript:(function() {
            // Crear y agregar estilos CSS para ocultar anuncios
            var style = document.createElement('style');
            style.type = 'text/css';
            style.innerHTML = `
                /* Ocultar elementos de marketing y publicidad */
                div[class*="ad-"],
                div[id*="ad-"],
                iframe[src*="ads"],
                iframe[id*="ads"],
                [class*="adsbygoogle"],
                [id*="adsbygoogle"] {
                    display: none !important;
                    opacity: 0 !important;
                    visibility: hidden !important;
                    height: 0 !important;
                    width: 0 !important;
                    position: absolute !important;
                    top: -9999px !important;
                    left: -9999px !important;
                    z-index: -999 !important;
                    pointer-events: none !important;
                }
            `;
            document.head.appendChild(style);
        })()
    """

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (adServersList.any { url.contains(it, ignoreCase = true) }) {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream("".toByteArray())
            )
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Inyectar CSS y JavaScript cuando la página termine de cargar
        view.evaluateJavascript(cssInject, null)
    }
} 