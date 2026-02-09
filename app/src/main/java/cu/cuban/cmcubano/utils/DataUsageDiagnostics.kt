package cu.cuban.cmcubano.utils

import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utilidades para diagnóstico del monitoreo de datos
 */
class DataUsageDiagnostics(private val context: Context) {
    
    companion object {
        private const val TAG = "DataUsageDiagnostics"
    }
    
    /**
     * Verifica el estado del permiso PACKAGE_USAGE_STATS
     */
    fun checkUsageStatsPermission(): Boolean {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando permiso de estadísticas", e)
            false
        }
    }
    
    /**
     * Obtiene información básica de TrafficStats
     */
    fun getTrafficStatsInfo(): TrafficStatsInfo {
        return try {
            val totalRx = TrafficStats.getTotalRxBytes()
            val totalTx = TrafficStats.getTotalTxBytes()
            val mobileRx = TrafficStats.getMobileRxBytes()
            val mobileTx = TrafficStats.getMobileTxBytes()
            val uidRx = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            val uidTx = TrafficStats.getUidTxBytes(android.os.Process.myUid())
            
            TrafficStatsInfo(
                totalRx = totalRx,
                totalTx = totalTx,
                mobileRx = mobileRx,
                mobileTx = mobileTx,
                uidRx = uidRx,
                uidTx = uidTx,
                isAvailable = totalRx != TrafficStats.UNSUPPORTED.toLong() || totalTx != TrafficStats.UNSUPPORTED.toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo TrafficStats", e)
            TrafficStatsInfo(isAvailable = false)
        }
    }
    
    /**
     * Verifica la disponibilidad de NetworkStatsManager
     */
    fun checkNetworkStatsManagerAvailability(): NetworkStatsInfo {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return NetworkStatsInfo(
                    isAvailable = false,
                    reason = "Android versión ${Build.VERSION.RELEASE} (< 6.0) no soporta NetworkStatsManager"
                )
            }
            
            val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? android.app.usage.NetworkStatsManager
            if (networkStatsManager == null) {
                return NetworkStatsInfo(
                    isAvailable = false,
                    reason = "NetworkStatsManager no disponible en este dispositivo"
                )
            }
            
            // Intentar una consulta de prueba
            val testEndTime = System.currentTimeMillis()
            val testStartTime = testEndTime - 60000 // 1 minuto atrás
            
            val mobileStats = networkStatsManager.querySummary(
                android.net.ConnectivityManager.TYPE_MOBILE,
                null,
                testStartTime,
                testEndTime
            )
            
            mobileStats.close()
            
            NetworkStatsInfo(
                isAvailable = true,
                reason = "NetworkStatsManager disponible y funcional"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando NetworkStatsManager", e)
            NetworkStatsInfo(
                isAvailable = false,
                reason = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Obtiene información del operador
     */
    fun getCarrierInfo(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager == null) {
                return "TelephonyManager no disponible"
            }
            
            val networkOperator = telephonyManager.networkOperator
            val networkOperatorName = telephonyManager.networkOperatorName
            val simOperatorName = telephonyManager.simOperatorName
            val networkType = telephonyManager.networkType
            val networkTypeName = when (networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
                TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> "Desconocido ($networkType)"
            }
            
            """
            Operador: $networkOperatorName
            SIM: $simOperatorName
            Código: $networkOperator
            Tipo Red: $networkTypeName
            """.trimIndent()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo información del operador", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Genera un reporte completo del estado del monitoreo
     */
    fun generateDiagnosticReport(): DiagnosticReport {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val hasPermission = checkUsageStatsPermission()
        val trafficStats = getTrafficStatsInfo()
        val networkStats = checkNetworkStatsManagerAvailability()
        val carrierInfo = getCarrierInfo()
        
        val recommendations = mutableListOf<String>()
        
        if (!hasPermission) {
            recommendations.add("❌ Conceder permiso PACKAGE_USAGE_STATS")
        } else {
            recommendations.add("✅ Permiso PACKAGE_USAGE_STATS concedido")
        }
        
        if (!trafficStats.isAvailable) {
            recommendations.add("❌ TrafficStats no disponible o no funciona")
        } else {
            recommendations.add("✅ TrafficStats disponible")
            if (trafficStats.totalRx < 0 || trafficStats.totalTx < 0) {
                recommendations.add("⚠️ TrafficStats devuelve valores inválidos")
            }
        }
        
        if (!networkStats.isAvailable) {
            recommendations.add("❌ NetworkStatsManager no disponible: ${networkStats.reason}")
        } else {
            recommendations.add("✅ NetworkStatsManager disponible")
        }
        
        return DiagnosticReport(
            timestamp = timestamp,
            hasUsageStatsPermission = hasPermission,
            trafficStatsInfo = trafficStats,
            networkStatsInfo = networkStats,
            carrierInfo = carrierInfo,
            recommendations = recommendations,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }
    
    /**
     * Registra el reporte de diagnóstico en log
     */
    fun logDiagnosticReport() {
        val report = generateDiagnosticReport()
        Log.i(TAG, """
            === REPORTE DE DIAGNÓSTICO ===
            Timestamp: ${report.timestamp}
            Android: ${report.androidVersion}
            
            Permisos:
            ${if (report.hasUsageStatsPermission) "✅" else "❌"} PACKAGE_USAGE_STATS
            
            TrafficStats:
            Disponible: ${if (report.trafficStatsInfo.isAvailable) "✅" else "❌"}
            Total RX: ${formatBytes(report.trafficStatsInfo.totalRx)}
            Total TX: ${formatBytes(report.trafficStatsInfo.totalTx)}
            Móvil RX: ${formatBytes(report.trafficStatsInfo.mobileRx)}
            Móvil TX: ${formatBytes(report.trafficStatsInfo.mobileTx)}
            
            NetworkStatsManager:
            Disponible: ${if (report.networkStatsInfo.isAvailable) "✅" else "❌"}
            Razón: ${report.networkStatsInfo.reason}
            
            Operador:
            ${report.carrierInfo}
            
            Recomendaciones:
            ${report.recommendations.joinToString("\n")}
            ==============================
        """.trimIndent())
    }
    
    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "No disponible"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}

/**
 * Información de TrafficStats
 */
data class TrafficStatsInfo(
    val totalRx: Long = -1L,
    val totalTx: Long = -1L,
    val mobileRx: Long = -1L,
    val mobileTx: Long = -1L,
    val uidRx: Long = -1L,
    val uidTx: Long = -1L,
    val isAvailable: Boolean = false
)

/**
 * Información de NetworkStatsManager
 */
data class NetworkStatsInfo(
    val isAvailable: Boolean,
    val reason: String = "Disponible"
)

/**
 * Reporte completo de diagnóstico
 */
data class DiagnosticReport(
    val timestamp: String,
    val hasUsageStatsPermission: Boolean,
    val trafficStatsInfo: TrafficStatsInfo,
    val networkStatsInfo: NetworkStatsInfo,
    val carrierInfo: String,
    val recommendations: List<String>,
    val androidVersion: String
)
