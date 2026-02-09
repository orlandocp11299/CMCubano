package cu.cuban.cmcubano.utils

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class DataUsageManager(private val context: Context) {
    
    private val networkStatsManager: NetworkStatsManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        } else {
            null
        }
    }
    
    private val packageManager: PackageManager by lazy {
        context.packageManager
    }
    
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    
    companion object {
        private const val TAG = "DataUsageManager"
    }
    
    /**
     * Obtiene el uso de datos móviles en un período de tiempo
     */
    suspend fun getMobileDataUsage(startTime: Long, endTime: Long): Long = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return@withContext getMobileDataUsageNew(startTime, endTime)
            } else {
                return@withContext getMobileDataUsageLegacy(startTime, endTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener uso de datos móviles", e)
            // Fallback a TrafficStats
            return@withContext try {
                val totalRx = TrafficStats.getTotalRxBytes()
                val totalTx = TrafficStats.getTotalTxBytes()
                val mobileRx = TrafficStats.getMobileRxBytes()
                val mobileTx = TrafficStats.getMobileTxBytes()
                
                // Si TrafficStats devuelve -1, usar 0
                val validMobileRx = if (mobileRx >= 0) mobileRx else 0L
                val validMobileTx = if (mobileTx >= 0) mobileTx else 0L
                
                val result = validMobileRx + validMobileTx
                Log.d(TAG, "Fallback TrafficStats - Datos móviles: ${formatBytes(result)}")
                result
            } catch (ex: Exception) {
                Log.e(TAG, "Error en fallback TrafficStats", ex)
                0L
            }
        }
    }
    
    /**
     * Método para Android 6.0+ usando NetworkStatsManager
     */
    private suspend fun getMobileDataUsageNew(startTime: Long, endTime: Long): Long = withContext(Dispatchers.IO) {
        val networkStatsManager = networkStatsManager ?: return@withContext 0L
        
        try {
            // Intentar obtener subscriber ID
            val subscriberId = getSubscriberId()
            
            // Si no hay subscriber ID, intentar obtener todas las redes móviles
            val networkStats = if (subscriberId.isNullOrEmpty()) {
                // Usar null para subscriberId para obtener todas las redes móviles
                networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    null,
                    startTime,
                    endTime
                )
            } else {
                networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    subscriberId,
                    startTime,
                    endTime
                )
            }
            
            var totalBytes = 0L
            val bucket = NetworkStats.Bucket()
            
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                // Solo contar datos móviles (no roaming si es posible)
                if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND || 
                    bucket.state == NetworkStats.Bucket.STATE_DEFAULT ||
                    bucket.state == NetworkStats.Bucket.STATE_ALL) {
                    totalBytes += bucket.rxBytes + bucket.txBytes
                }
            }
            
            networkStats.close()
            
            Log.d(TAG, "Uso de datos móviles (API 23+): ${formatBytes(totalBytes)}")
            totalBytes
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en NetworkStatsManager para móviles", e)
            // Fallback a legacy
            return@withContext getMobileDataUsageLegacy(startTime, endTime)
        }
    }
    
    /**
     * Obtiene el uso de datos WiFi en un período de tiempo
     */
    suspend fun getWifiDataUsage(startTime: Long, endTime: Long): Long = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return@withContext getWifiDataUsageNew(startTime, endTime)
            } else {
                return@withContext getWifiDataUsageLegacy(startTime, endTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener uso de datos WiFi", e)
            // Fallback a TrafficStats
            return@withContext try {
                val totalRx = TrafficStats.getTotalRxBytes()
                val totalTx = TrafficStats.getTotalTxBytes()
                val mobileRx = TrafficStats.getMobileRxBytes()
                val mobileTx = TrafficStats.getMobileTxBytes()
                
                // Si TrafficStats devuelve -1, usar 0
                val validTotalRx = if (totalRx >= 0) totalRx else 0L
                val validTotalTx = if (totalTx >= 0) totalTx else 0L
                val validMobileRx = if (mobileRx >= 0) mobileRx else 0L
                val validMobileTx = if (mobileTx >= 0) mobileTx else 0L
                
                // WiFi = Total - Móviles
                val wifiRx = (validTotalRx - validMobileRx).coerceAtLeast(0L)
                val wifiTx = (validTotalTx - validMobileTx).coerceAtLeast(0L)
                
                wifiRx + wifiTx
            } catch (ex: Exception) {
                Log.e(TAG, "Error en fallback TrafficStats para WiFi", ex)
                0L
            }
        }
    }
    
    /**
     * Método para Android 6.0+ usando NetworkStatsManager para WiFi
     */
    private suspend fun getWifiDataUsageNew(startTime: Long, endTime: Long): Long = withContext(Dispatchers.IO) {
        val networkStatsManager = networkStatsManager ?: return@withContext 0L
        
        try {
            // Obtener estadísticas de red para WiFi
            val networkStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime
            )
            
            var totalBytes = 0L
            val bucket = NetworkStats.Bucket()
            
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                // Solo contar datos WiFi
                if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND || 
                    bucket.state == NetworkStats.Bucket.STATE_DEFAULT ||
                    bucket.state == NetworkStats.Bucket.STATE_ALL) {
                    totalBytes += bucket.rxBytes + bucket.txBytes
                }
            }
            
            networkStats.close()
            
            Log.d(TAG, "Uso de datos WiFi (API 23+): ${formatBytes(totalBytes)}")
            totalBytes
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en NetworkStatsManager para WiFi", e)
            // Fallback a legacy
            return@withContext getWifiDataUsageLegacy(startTime, endTime)
        }
    }
    
    /**
     * Obtiene el uso de datos por aplicación (Optimizado y Robusto)
     */
    suspend fun getAppDataUsage(startTime: Long, endTime: Long): List<AppDataUsage> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return@withContext getAppDataUsageLegacy(startTime, endTime)
        }
        
        val networkStatsManager = networkStatsManager ?: return@withContext emptyList()
        val subscriberId = getSubscriberId()
        val appUsageList = mutableListOf<AppDataUsage>()

        try {
            // Mapa para acumular consumos: UID -> (MobileBytes, WifiBytes)
            val usageMap = mutableMapOf<Int, Pair<Long, Long>>()

            fun aggregate(type: Int, subId: String?, isMobile: Boolean) {
                try {
                    val stats = networkStatsManager.querySummary(type, subId, startTime, endTime)
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val uid = bucket.uid
                        val current = usageMap[uid] ?: Pair(0L, 0L)
                        
                        val totalBytes = bucket.rxBytes + bucket.txBytes
                        usageMap[uid] = if (isMobile) {
                            Pair(current.first + totalBytes, current.second)
                        } else {
                            Pair(current.first, current.second + totalBytes)
                        }
                    }
                    stats.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error consultando consumo tipo $type para apps", e)
                }
            }

            // Recolectar datos
            aggregate(ConnectivityManager.TYPE_MOBILE, subscriberId, true)
            aggregate(ConnectivityManager.TYPE_WIFI, null, false)

            // Procesar el mapa de UIDs
            for ((uid, usage) in usageMap) {
                val mobile = usage.first
                val wifi = usage.second
                
                // Solo mostrar si hay consumo real
                if (mobile + wifi > 0) {
                    val packageNames = packageManager.getPackagesForUid(uid)
                    val packageName = packageNames?.getOrNull(0) ?: "unknown.uid.$uid"
                    
                    val appName = try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        when (uid) {
                            1000 -> "Sistema Android"
                            1001 -> "Teléfono"
                            else -> "App (ID: $uid)"
                        }
                    }

                    appUsageList.add(AppDataUsage(
                        appName = appName,
                        packageName = packageName,
                        uid = uid,
                        mobileBytes = mobile,
                        wifiBytes = wifi,
                        foregroundBytes = mobile + wifi,
                        backgroundBytes = 0L,
                        packetsSent = 0L,
                        packetsReceived = 0L
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error general en getAppDataUsage", e)
        }
        
        appUsageList.sortByDescending { it.mobileBytes + it.wifiBytes }
        appUsageList
    }
    
    /**
     * Obtiene el uso de datos totales (móviles + WiFi)
     */
    suspend fun getTotalDataUsage(startTime: Long, endTime: Long): Long {
        val mobileUsage = getMobileDataUsage(startTime, endTime)
        val wifiUsage = getWifiDataUsage(startTime, endTime)
        return mobileUsage + wifiUsage
    }
    
    /**
     * Verifica si hay conexión a datos móviles activa
     */
    fun isMobileDataActive(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }
    
    /**
     * Verifica si hay conexión WiFi activa
     */
    fun isWifiActive(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }
    
    /**
     * Obtiene información del operador móvil
     */
    fun getCarrierInfo(): CarrierInfo {
        return CarrierInfo(
            name = telephonyManager.networkOperatorName ?: "Desconocido",
            mcc = try { telephonyManager.networkOperator?.substring(0, 3) } catch (e: Exception) { null },
            mnc = try { telephonyManager.networkOperator?.substring(3) } catch (e: Exception) { null },
            countryIso = telephonyManager.networkCountryIso ?: "",
            networkType = getNetworkTypeName()
        )
    }
    
    /**
     * Método legacy para obtener datos móviles en versiones antiguas
     */
    private fun getMobileDataUsageLegacy(startTime: Long, endTime: Long): Long {
        return try {
            // Usar TrafficStats como fallback para versiones antiguas
            val totalRx = android.net.TrafficStats.getTotalRxBytes()
            val totalTx = android.net.TrafficStats.getTotalTxBytes()
            val mobileRx = android.net.TrafficStats.getMobileRxBytes()
            val mobileTx = android.net.TrafficStats.getMobileTxBytes()
            
            // TrafficStats no proporciona datos por tiempo, solo acumulados
            // Esta es una aproximación simple
            (mobileRx + mobileTx).coerceAtLeast(0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error en método legacy para datos móviles", e)
            0L
        }
    }
    
    /**
     * Método legacy para obtener datos WiFi en versiones antiguas
     */
    private fun getWifiDataUsageLegacy(startTime: Long, endTime: Long): Long {
        return try {
            val totalRx = android.net.TrafficStats.getTotalRxBytes()
            val totalTx = android.net.TrafficStats.getTotalTxBytes()
            val mobileRx = android.net.TrafficStats.getMobileRxBytes()
            val mobileTx = android.net.TrafficStats.getMobileTxBytes()
            
            // WiFi = Total - Móviles
            val wifiRx = (totalRx - mobileRx).coerceAtLeast(0L)
            val wifiTx = (totalTx - mobileTx).coerceAtLeast(0L)
            
            wifiRx + wifiTx
        } catch (e: Exception) {
            Log.e(TAG, "Error en método legacy para datos WiFi", e)
            0L
        }
    }
    
    /**
     * Método legacy para obtener datos por aplicación en versiones antiguas
     */
    private fun getAppDataUsageLegacy(startTime: Long, endTime: Long): List<AppDataUsage> {
        val appUsageList = mutableListOf<AppDataUsage>()
        
        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            for (appInfo in installedApps) {
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                    continue
                }
                
                val uid = appInfo.uid
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName
                
                val rxBytes = android.net.TrafficStats.getUidRxBytes(uid) ?: 0L
                val txBytes = android.net.TrafficStats.getUidTxBytes(uid) ?: 0L
                val totalBytes = rxBytes + txBytes
                
                if (totalBytes > 0) {
                    val appDataUsage = AppDataUsage(
                        appName = appName,
                        packageName = packageName,
                        uid = uid,
                        mobileBytes = totalBytes * 0.6f.toLong(), // Estimación
                        wifiBytes = totalBytes * 0.4f.toLong(), // Estimación
                        foregroundBytes = totalBytes * 0.7f.toLong(),
                        backgroundBytes = totalBytes * 0.3f.toLong(),
                        packetsSent = (totalBytes / 1500).toLong(),
                        packetsReceived = (totalBytes / 1500).toLong()
                    )
                    
                    appUsageList.add(appDataUsage)
                }
            }
            
            appUsageList.sortByDescending { it.mobileBytes + it.wifiBytes }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en método legacy para datos por aplicación", e)
        }
        
        return appUsageList
    }
    
    /**
     * Obtiene el subscriber ID para datos móviles
     */
    private fun getSubscriberId(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // En Android 10+, necesitas permisos especiales para acceder al subscriber ID
                // Usamos un enfoque alternativo
                null
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.subscriberId
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener el subscriber ID", e)
            null
        }
    }
    
    /**
     * Obtiene el nombre del tipo de red
     */
    private fun getNetworkTypeName(): String {
        return when (telephonyManager.networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "Desconocido"
        }
    }
    
    /**
     * Formatea bytes a formato legible
     */
    private fun formatBytes(bytes: Long): String {
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
 * Clase de datos para información del operador
 */
data class CarrierInfo(
    val name: String,
    val mcc: String?,
    val mnc: String?,
    val countryIso: String,
    val networkType: String
)
