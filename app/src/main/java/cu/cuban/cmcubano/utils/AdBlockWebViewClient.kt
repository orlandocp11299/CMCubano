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
            function removeAdsAndHandleCookies() {
                // Crear y agregar estilos CSS
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = `
                    /* Ocultar elementos de marketing y publicidad */
                    div[class*="ad-"],
                    div[id*="ad-"],
                    iframe[src*="ads"],
                    iframe[id*="ads"],
                    [class*="adsbygoogle"],
                    [id*="adsbygoogle"],
                    /* Ocultar diálogos de cookies y términos */
                    .modal,
                    .modal-dialog,
                    .modal-content,
                    .cookie-banner,
                    .cookie-notice,
                    .consent-dialog,
                    .welcome-dialog,
                    [role="dialog"],
                    .cookie-consent,
                    .gdpr-banner,
                    .privacy-notice {
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

                // Función para manejar cookies y diálogos
                function handleElements() {
                    // Buscar y hacer clic en botones de aceptar cookies
                    const acceptButtons = document.querySelectorAll(
                        'button[class*="accept"], button[class*="agree"], button[class*="consent"], ' +
                        'button[id*="accept"], button[id*="agree"], button[id*="consent"], ' +
                        'button[onclick*="accept"], button[onclick*="agree"], button[onclick*="consent"], ' +
                        'a[class*="accept"], a[class*="agree"], a[class*="consent"], ' +
                        'input[type="button"][value*="Accept"], input[type="button"][value*="Agree"], ' +
                        'input[type="submit"][value*="Accept"], input[type="submit"][value*="Agree"]'
                    );
                    
                    acceptButtons.forEach(function(button) {
                        const buttonText = button.textContent.toLowerCase();
                        if (buttonText.includes('accept') || 
                            buttonText.includes('agree') || 
                            buttonText.includes('consent') ||
                            buttonText.includes('aceptar') ||
                            buttonText.includes('estoy de acuerdo') ||
                            buttonText.includes('i agree')) {
                            console.log('Clicking accept button:', button);
                            button.click();
                        }
                    });

                    // Buscar y eliminar diálogos específicos
                    const elements = document.querySelectorAll('div[role="dialog"], .modal-dialog, .modal, .cookie-banner, .welcome-dialog');
                    elements.forEach(function(element) {
                        const text = element.textContent.toLowerCase();
                        
                        // Eliminar diálogos de cookies
                        if (text.includes('cookie') || 
                            text.includes('consent') || 
                            text.includes('privacy') ||
                            text.includes('gdpr') ||
                            text.includes('welcome to cellmapper') ||
                            text.includes('agree') ||
                            text.includes('terms of use') ||
                            text.includes('privacy policy')) {
                            console.log('Removing dialog:', element);
                            element.remove();
                        }
                        
                        // Eliminar diálogos de marketing (código existente)
                        if (text.includes('marketing') && 
                            !text.includes('tower') && 
                            !text.includes('cell') && 
                            !text.includes('band') && 
                            !text.includes('signal')) {
                            console.log('Removing marketing dialog:', element);
                            element.remove();
                        }
                    });

                    // Eliminar específicamente el diálogo de CellMapper
                    const cellMapperDialog = document.querySelector('div.modal-dialog div.modal-content:only-child');
                    if (cellMapperDialog && (
                        cellMapperDialog.textContent.toLowerCase().includes('welcome to cellmapper') ||
                        cellMapperDialog.textContent.toLowerCase().includes('agree') ||
                        cellMapperDialog.textContent.toLowerCase().includes('cookie')
                    )) {
                        console.log('Removing CellMapper dialog:', cellMapperDialog);
                        const parentModal = cellMapperDialog.closest('.modal');
                        if (parentModal) {
                            parentModal.remove();
                        }
                    }
                }

                // Ejecutar inmediatamente
                handleElements();

                // Reintentar varias veces para capturar diálogos que aparecen después
                setTimeout(handleElements, 500);
                setTimeout(handleElements, 1000);
                setTimeout(handleElements, 2000);
                setTimeout(handleElements, 3000);

                // Configurar un observador para elementos dinámicos
                var observer = new MutationObserver(function(mutations) {
                    handleElements();
                });

                // Observar cambios en el DOM
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
            }

            // Ejecutar cuando el DOM esté listo
            if (document.readyState === "loading") {
                document.addEventListener('DOMContentLoaded', removeAdsAndHandleCookies);
            } else {
                removeAdsAndHandleCookies();
            }
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