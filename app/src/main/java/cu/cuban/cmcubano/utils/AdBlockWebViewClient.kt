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

    private val jsInject = """
        javascript:(function() {
            // 1. Inyectar CSS para ocultar anuncios
            var style = document.createElement('style');
            style.type = 'text/css';
            style.innerHTML = `
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

            // 2. Función para auto-aceptar cookies y términos
            function autoAccept() {
                // Botón "Got it!" (Cookies)
                var cookiesBtn = document.querySelector('.cc-btn.cc-dismiss');
                if (cookiesBtn && cookiesBtn.offsetParent !== null) {
                    cookiesBtn.click();
                    console.log('Cookies accepted');
                }

                // Botón "I Agree" (Términos)
                var buttons = document.querySelectorAll('button.btn-primary, .btn.btn-primary');
                for (var i = 0; i < buttons.length; i++) {
                    if (buttons[i].textContent.trim().toLowerCase() === 'i agree' && buttons[i].offsetParent !== null) {
                        buttons[i].click();
                        console.log('Terms accepted');
                        break;
                    }
                }
            }

            // Ejecutar inmediatamente y luego periódicamente por si cargan lento
            autoAccept();
            var interval = setInterval(autoAccept, 1000);
            
            // Detener el intervalo después de 15 segundos para no consumir recursos
            setTimeout(function() {
                clearInterval(interval);
            }, 15000);
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
        view.evaluateJavascript(jsInject, null)
    }
} 