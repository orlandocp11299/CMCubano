package cu.cuban.cmcubano

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthLte
import android.telephony.PhoneStateListener
import android.telephony.PhysicalChannelConfig
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.gms.location.*
import cu.cuban.cmcubano.screens.UtilesScreen
import cu.cuban.cmcubano.utils.EnbNameManager
import cu.cuban.cmcubano.MainActivity
import kotlinx.coroutines.*
import android.os.Looper
import cu.cuban.cmcubano.utils.DataUsageManager
import cu.cuban.cmcubano.utils.DataUsageDiagnostics
import cu.cuban.cmcubano.utils.AppDataUsage
import java.text.SimpleDateFormat
import java.util.*

class NotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var telephonyManager: TelephonyManager? = null
    private var notificationManager: NotificationManager? = null
    private var isDestroyed = false
    
    // Telephony Listeners
    private var telephonyCallback: TelephonyCallback? = null // For API 31+
    private var phoneStateListener: PhoneStateListener? = null // For API < 31

    // State
    private var currentCellInfoList: List<CellInfo> = emptyList()
    private var currentServiceState: ServiceState? = null
    private var currentDisplayInfo: TelephonyDisplayInfo? = null
    private var currentPhysicalConfigList: List<PhysicalChannelConfig> = emptyList()
    private var monitoringJob: Job? = null
    private var dataUsageJob: Job? = null
    
    // Estado para restaurar tras alertas
    private var wasMonitoringActive = false
    private var wasDataMonitoringActive = false
    
    private var dataUsageManager: DataUsageManager? = null
    private var currentDataPeriod: String = "Hoy"
    
    private var locationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "NETWORK_MONITOR_CHANNEL"
        private const val NOTIFICATION_ID = 12345
        
        const val ACTION_START_MONITOR = "cu.cuban.cmcubano.ACTION_START_MONITOR"
        const val ACTION_STOP_MONITOR = "cu.cuban.cmcubano.ACTION_STOP_MONITOR"
        const val ACTION_START_DATA_MONITOR = "cu.cuban.cmcubano.ACTION_START_DATA_MONITOR"
        const val ACTION_STOP_DATA_MONITOR = "cu.cuban.cmcubano.ACTION_STOP_DATA_MONITOR"
        const val ACTION_CHANGE_DATA_PERIOD = "cu.cuban.cmcubano.ACTION_CHANGE_DATA_PERIOD"
        const val ACTION_TOGGLE_REMINDER = "TOGGLE_REMINDER" // Legacy
        
        const val ACTION_SUSPEND_FOR_ALERT = "cu.cuban.cmcubano.ACTION_SUSPEND_FOR_ALERT"
        const val ACTION_RESUME_FROM_ALERT = "cu.cuban.cmcubano.ACTION_RESUME_FROM_ALERT"
        
        private const val DATA_USAGE_NOTIFICATION_ID = 54321
        private const val DATA_USAGE_CHANNEL_ID = "DATA_USAGE_CHANNEL"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationService Created")
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        dataUsageManager = DataUsageManager(this)
        createNotificationChannel()
        createDataUsageChannel()
        isDestroyed = false
    }

    override fun onDestroy() {
        Log.d(TAG, "NotificationService Destroyed")
        isDestroyed = true
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_MONITOR -> {
                startMonitoring()
            }
            ACTION_STOP_MONITOR -> {
                stopMonitoring()
            }
            ACTION_START_DATA_MONITOR -> {
                startDataUsageMonitoring()
            }
            ACTION_STOP_DATA_MONITOR -> {
                stopDataUsageMonitoring()
            }
            ACTION_CHANGE_DATA_PERIOD -> {
                val newPeriod = intent.getStringExtra("period") ?: "Hoy"
                currentDataPeriod = newPeriod
                serviceScope.launch { updateDataUsageNotification() }
            }
            // Preserve legacy functionality for simple reminders if needed, 
            // though strict separation is better. We'll handle legacy internally if called.
             ACTION_TOGGLE_REMINDER -> {
                 // Legacy reminder toggle logic here if needed, or simply ignore
             }
             ACTION_SUSPEND_FOR_ALERT -> {
                 suspendNotifications()
             }
             ACTION_RESUME_FROM_ALERT -> {
                 resumeNotifications()
             }
        }
        return START_STICKY
    }
    
    @SuppressLint("MissingPermission")
    private fun startMonitoring() {
        Log.d(TAG, "Starting Network Monitoring")
        
        // 1. Create initial notification to promote to Foreground
        val notification = buildNotification(isInitializing = true)
        
        // 2. Start Foreground
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) 
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
             // Fallback if generic (Manifest might need update for specific type)
             try {
                startForeground(NOTIFICATION_ID, notification) 
             } catch(e: Exception) {
                 Log.e(TAG, "Failed to start foreground: ${e.message}")
             }
        }

        // 3. Register Listeners
        registerTelephonyListeners()
        
        // 4. Force Initial Data Fetch & Start Periodic Polling
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Refresh ServiceState and DisplayInfo (Best effort)
                    telephonyManager?.let { tm ->
                        currentServiceState = tm.serviceState
                        // Note: DisplayInfo relies mostly on listener, hard to poll directly without hidden APIs
                    }
                    
                    // Refresh Cell Info
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        telephonyManager?.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                currentCellInfoList = cellInfo
                                updateNotification()
                            }
                            override fun onError(errorCode: Int, detail: Throwable?) {
                                Log.w(TAG, "CellInfo Update Limit/Error: $errorCode")
                            }
                        })
                    } else {
                        @Suppress("DEPRECATION")
                        currentCellInfoList = telephonyManager?.allCellInfo ?: emptyList()
                        updateNotification()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop: ${e.message}")
                }
                
                delay(2500)
            }
        }
        
        // 5. Modem "Wake-up" trick (Location)
        startLocationTrigger()
    }
    
    private fun startLocationTrigger() {
        if (locationClient == null) {
            locationClient = LocationServices.getFusedLocationProviderClient(this)
        }
        
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000)
            .build()
            
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                // Just keeping the modem active
            }
        }
        
        try {
            locationClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing for modem trigger")
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping Network Monitoring")
        try {
            monitoringJob?.cancel()
            monitoringJob = null
            locationCallback?.let { locationClient?.removeLocationUpdates(it) }
            unregisterTelephonyListeners()
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            // Si no queda nada activo, parar el servicio
            if (dataUsageJob == null) {
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring: ${e.message}")
        }
    }

    private fun startDataUsageMonitoring() {
        Log.d(TAG, "Starting Data Usage Monitoring")
        val notification = buildDataUsageNotification("Cargando...")
        
        // Si ya está en foreground por el monitor de red, solo enviamos la notificación
        // Si no, iniciamos en foreground
        if (monitoringJob == null) {
            try {
                startForeground(DATA_USAGE_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar foreground para datos: ${e.message}")
            }
        } else {
            notificationManager?.notify(DATA_USAGE_NOTIFICATION_ID, notification)
        }

        dataUsageJob?.cancel()
        dataUsageJob = serviceScope.launch {
            // Actualizar inmediatamente la primera vez
            val prefs = getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
            currentDataPeriod = prefs.getString("live_notification_period", "Hoy") ?: "Hoy"
            updateDataUsageNotification()
            
            while (isActive) {
                delay(15000) // Actualizar cada 15 segundos
                if (isActive) {
                    val p = getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
                    currentDataPeriod = p.getString("live_notification_period", "Hoy") ?: "Hoy"
                    updateDataUsageNotification()
                }
            }
        }
    }

    private fun stopDataUsageMonitoring() {
        Log.d(TAG, "Stopping Data Usage Monitoring")
        dataUsageJob?.cancel()
        dataUsageJob = null
        notificationManager?.cancel(DATA_USAGE_NOTIFICATION_ID)
        
        // Si no queda nada activo, parar el servicio
        if (monitoringJob == null) {
            stopSelf()
        }
    }

    private fun suspendNotifications() {
        Log.d(TAG, "Suspending notifications for alert")
        wasMonitoringActive = monitoringJob != null
        wasDataMonitoringActive = dataUsageJob != null
        
        if (wasMonitoringActive) stopMonitoring()
        if (wasDataMonitoringActive) stopDataUsageMonitoring()
    }

    private fun resumeNotifications() {
        Log.d(TAG, "Resuming notifications after alert: net=$wasMonitoringActive, data=$wasDataMonitoringActive")
        if (wasMonitoringActive) {
            startMonitoring()
            wasMonitoringActive = false
        }
        if (wasDataMonitoringActive) {
            startDataUsageMonitoring()
            wasDataMonitoringActive = false
        }
    }

    private suspend fun updateDataUsageNotification() {
        if (isDestroyed) return
        try {
            val content = if (currentDataPeriod == "Plan") {
                val prefs = getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
                val pLimitStr = prefs.getString("data_plan_limit", "") ?: ""
                val pUnit = prefs.getString("data_plan_unit", "GB") ?: "GB"
                val pConsumedStr = prefs.getString("data_plan_consumed", "0") ?: "0"
                val pConsumed = pConsumedStr.toDoubleOrNull() ?: 0.0
                val pConsumedInMb = if (pUnit == "GB") pConsumed * 1024.0 else pConsumed
                val pLastUpdate = prefs.getLong("data_plan_last_update_time", 0L)
                
                val limitVal = pLimitStr.toDoubleOrNull() ?: 0.0
                val limitMb = if (pUnit == "GB") limitVal * 1024.0 else limitVal
                
                val deltaUsageBytes = dataUsageManager?.getMobileDataUsage(pLastUpdate, System.currentTimeMillis()) ?: 0L
                val deltaUsageMb = deltaUsageBytes / (1024.0 * 1024.0)
                val calculatedRealTimeMb = pConsumedInMb + deltaUsageMb
                val remainingMb = (limitMb - calculatedRealTimeMb).coerceAtLeast(0.0)
                
                val qStr = if (pUnit == "GB") String.format(java.util.Locale.US, "%.2f GB", limitMb / 1024.0) 
                           else String.format(java.util.Locale.US, "%.0f MB", limitMb)
                
                val cStr = if (pUnit == "GB") String.format(java.util.Locale.US, "%.3f GB", calculatedRealTimeMb / 1024.0) 
                           else String.format(java.util.Locale.US, "%.1f MB", calculatedRealTimeMb)
                           
                val rStr = if (pUnit == "GB") String.format(java.util.Locale.US, "%.3f GB", remainingMb / 1024.0) 
                           else String.format(java.util.Locale.US, "%.1f MB", remainingMb)
                
                "Plan: $qStr | Usado: $cStr\nRestante: $rStr"
            } else {
                val (startTime, endTime) = calculateTimeRange(currentDataPeriod)
                val mobile = dataUsageManager?.getMobileDataUsage(startTime, endTime) ?: 0L
                val wifi = dataUsageManager?.getWifiDataUsage(startTime, endTime) ?: 0L
                val total = mobile + wifi
                "Móvil: ${formatBytes(mobile)} | WiFi: ${formatBytes(wifi)}\nTotal: ${formatBytes(total)}"
            }
            
            if (!isDestroyed) {
                val notification = buildDataUsageNotification(content)
                notificationManager?.notify(DATA_USAGE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando notificación de datos", e)
        }
    }

    private fun buildDataUsageNotification(content: String): Notification {
        val title = "Uso de Datos ($currentDataPeriod)"
        
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "control_datos")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 10, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exitIntent = Intent(this, NotificationService::class.java).apply {
            action = ACTION_STOP_DATA_MONITOR
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 2, exitIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.outline_app_badging_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSortKey("A_servicios_2_datos")
            .setPriority(NotificationCompat.PRIORITY_MIN) // Mínima importancia para servicios de fondo
            .setWhen(0) // Tiempo cero para que nunca se considere "nueva"
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Salir", exitPendingIntent)

        return builder.build()
    }

    private fun registerTelephonyListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.CellInfoListener,
                TelephonyCallback.ServiceStateListener,
                TelephonyCallback.DisplayInfoListener,
                TelephonyCallback.PhysicalChannelConfigListener {
                
                override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
                    currentCellInfoList = cellInfo
                    updateNotification()
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    currentServiceState = serviceState
                    updateNotification()
                }

                override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                    currentDisplayInfo = displayInfo
                    updateNotification()
                }

                override fun onPhysicalChannelConfigChanged(configs: List<PhysicalChannelConfig>) {
                    currentPhysicalConfigList = configs
                    updateNotification()
                }
            }
            telephonyCallback = callback
            try {
                telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for TelephonyCallback")
            }
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCellInfoChanged(cellInfo: List<CellInfo>?) {
                    currentCellInfoList = cellInfo ?: emptyList()
                    updateNotification()
                }

                @Deprecated("Deprecated in Java")
                override fun onServiceStateChanged(serviceState: ServiceState?) {
                    currentServiceState = serviceState
                    updateNotification()
                }

                @Deprecated("Deprecated in Java")
                override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                    currentDisplayInfo = displayInfo
                    updateNotification()
                }
            }
            phoneStateListener = listener
            try {
                telephonyManager?.listen(listener, 
                    PhoneStateListener.LISTEN_CELL_INFO or 
                    PhoneStateListener.LISTEN_SERVICE_STATE or 
                    PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for PhoneStateListener")
            }
        }
    }

    private fun unregisterTelephonyListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { 
                telephonyManager?.unregisterTelephonyCallback(it) 
            }
        } else {
            phoneStateListener?.let { 
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) 
            }
        }
    }

    // --- LOGIC ---
    
    private fun updateNotification() {
        serviceScope.launch {
            val notification = buildNotification(isInitializing = false)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(isInitializing: Boolean): Notification {
        // Default Intent: Open App
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "utiles_screen") // Tell MainActivity where to go
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action: Exit
        val exitIntent = Intent(this, NotificationService::class.java).apply {
            action = ACTION_STOP_MONITOR
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // --- Determine Network State ---
        var tech = "Red"
        var freq = "-"
        var details = ""
        var isLte = false
        var activeCCs = 0
        var netIcon = R.drawable.outline_android_cell_4_bar_off_24 // Por defecto para sin servicio

        if (!isInitializing) {
            val primaryCell = currentCellInfoList.firstOrNull { it.isRegistered }
            if (primaryCell != null) {
                when (primaryCell) {
                    is CellInfoLte -> {
                        isLte = true
                        tech = "LTE"
                        
                        // 1. Check DisplayInfo for CA status (Logical)
                        val isDisplayCa = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && currentDisplayInfo != null) {
                             currentDisplayInfo?.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA ||
                             currentDisplayInfo?.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO ||
                             currentDisplayInfo?.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                             currentDisplayInfo?.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                        } else false

                        // 2. Count via PhysicalChannelConfig (Physical)
                        if (currentPhysicalConfigList.isNotEmpty()) {
                            activeCCs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                currentPhysicalConfigList.count { 
                                    (it.connectionStatus == 1 || it.connectionStatus == 2) && it.band > 0
                                }
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                currentPhysicalConfigList.count { it.band > 0 }
                            } else 0
                        } else {
                            activeCCs = 0
                        }
                        
                        // 3. Check ServiceState bandwidths (Reliable alternative)
                        val bws = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) currentServiceState?.cellBandwidths else null
                        if (bws != null && bws.size > 1) {
                            if (bws.size > activeCCs) activeCCs = bws.size
                        }
                        
                        // Verificar si los datos móviles están activos
                        val isDataEnabled = telephonyManager?.isDataEnabled ?: true
                        
                        // Determine if carrier aggregation is active (solo si datos están activos)
                        val hasCarrierAggregation = isDataEnabled && (activeCCs > 1 || isDisplayCa)
                        
                        val earfcn = primaryCell.cellIdentity.earfcn
                        freq = getLteFrequency(earfcn)
                        
                        val enb = primaryCell.cellIdentity.ci shr 8
                        val pci = primaryCell.cellIdentity.pci
                        val rsrp = primaryCell.cellSignalStrength.rsrp
                        val rsrq = primaryCell.cellSignalStrength.rsrq
                        val rssnr = getEnhancedSnr(primaryCell.cellSignalStrength)
                        
                        // Obtener nombre personalizado del eNB si existe
                        val enbIdStr = enb.toString()
                        val customEnbName = EnbNameManager.getEnbName(this, enbIdStr)
                        
                        // Construir tecnología con nombre personalizado si existe
                        val techWithCustomName = if (!customEnbName.isNullOrEmpty()) {
                            // Usar la banda de frecuencia cuando hay nombre personalizado
                            val band = getLteBand(earfcn)
                            // Agregar + si hay agregación de portadoras y datos están activos
                            if (hasCarrierAggregation) {
                                netIcon = R.drawable.outline_4g_mobiledata_badge_24
                                "$band+ $customEnbName"
                            } else {
                                netIcon = R.drawable.outline_4g_mobiledata_badge_24
                                "$band $customEnbName"
                            }
                        } else {
                        // Para display normal: siempre LTE o LTE+, nunca LTE-A
                        if (hasCarrierAggregation) {
                            netIcon = R.drawable.outline_4g_mobiledata_badge_24
                            "LTE+"
                        } else {
                            netIcon = R.drawable.outline_4g_mobiledata_badge_24
                            "LTE"
                        }
                        }
                        
                        // Actualizar tech para usar en el título
                        tech = techWithCustomName
                        
                        // Si hay nombre personalizado, no mostrar frecuencia en el título
                        if (!customEnbName.isNullOrEmpty()) {
                            freq = ""
                        }
                        
                        details = "$enb  |  $pci  |  RSRP: $rsrp dBm\nRSRQ: $rsrq dB  |  SNR: ${rssnr ?: "-"} dB"
                    }

                    is CellInfoWcdma -> {
                        // 3G Specifics
                        tech = getNetworkTypeName(telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN)
                        if (tech == "UNKNOWN" || tech == "LTE" || tech == "IWLAN") tech = "3G" // Fallback
                        
                        val uarfcn = primaryCell.cellIdentity.uarfcn
                        freq = getWcdmaFrequency(uarfcn)
                        
                        val cidComplete = primaryCell.cellIdentity.cid // CI completo (RNC + CID)
                        val cid = cidComplete and 0xFFFF // Extraer CID real (16 bits bajos)
                        // 1. RSCP Extraction (Robust)
                        val ss = primaryCell.cellSignalStrength
                        var rscpVal = Int.MAX_VALUE
                        
                        // Try Reflection getRscp
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val rscpMethod = try {
                                    ss.javaClass.getMethod("getRscp")
                                } catch (e: NoSuchMethodException) {
                                    ss.javaClass.getDeclaredMethod("getRscp").apply { isAccessible = true }
                                }
                                val v = rscpMethod.invoke(ss) as Int
                                if (v != Int.MAX_VALUE && v != -24) rscpVal = v
                            }
                        } catch (e: Exception) { }

                        // Fallback: ASU formula
                        if (rscpVal == Int.MAX_VALUE) {
                            val asu = ss.asuLevel
                            if (asu in 1..31) {
                                rscpVal = asu - 115
                            }
                        }

                        // Fallback: DBm Standard (Last Resort)
                        if (rscpVal == Int.MAX_VALUE) {
                             // Try ToString Parsing (Samsung specific fix from UtilesScreen)
                            try {
                                val cellString = primaryCell.toString()
                                val rscpRegex = "rscp=(-?\\d+)".toRegex()
                                val match = rscpRegex.find(cellString)
                                if (match != null) {
                                    val valorExtraido = match.groupValues[1].toInt()
                                    if (valorExtraido != Int.MAX_VALUE && valorExtraido < -24) {
                                        rscpVal = valorExtraido
                                    }
                                }
                            } catch (e: Exception) { }
                        }
                        
                        if (rscpVal == Int.MAX_VALUE) {
                            rscpVal = ss.dbm
                        }
                        
                        netIcon = R.drawable.outline_3g_mobiledata_badge_24

                        // 2. RSSI Extraction (Robust)
                        var rssiVal = Int.MAX_VALUE
                        try {
                             val rssiMethod = try {
                                ss.javaClass.getMethod("getRssi")
                            } catch (e: NoSuchMethodException) {
                                ss.javaClass.getDeclaredMethod("getRssi").apply { isAccessible = true }
                            }
                            val v = rssiMethod.invoke(ss) as Int
                            if (v != Int.MAX_VALUE && v != 0) rssiVal = v
                        } catch (e: Exception) { 
                            // Fallback based on Level (Approximate)
                             val level = ss.level
                             rssiVal = when (level) {
                                4 -> -51
                                3 -> -75
                                2 -> -91
                                1 -> -105
                                else -> Int.MAX_VALUE
                             }
                        }
                        
                        // Final Safety Check for Display
                        val rscpDisplay = if (rscpVal != Int.MAX_VALUE && rscpVal < -20) "$rscpVal dBm" else "-"
                        val rssiDisplay = if (rssiVal != Int.MAX_VALUE && rssiVal < -20) "$rssiVal dBm" else "-"

                        details = "CID: $cid  |  UARFCN: $uarfcn\nRSSI: $rssiDisplay"
                    }
                    is CellInfoGsm -> {
                        // 2G Specifics
                        tech = getNetworkTypeName(telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN)
                        if (tech == "UNKNOWN") tech = "2G"

                        val arfcn = primaryCell.cellIdentity.arfcn
                        freq = getGsmFrequency(arfcn)
                        
                        val cid = primaryCell.cellIdentity.cid
                        val rssi = primaryCell.cellSignalStrength.dbm // RSSI for GSM
                        
                        netIcon = R.drawable.outline_2g_mobiledata_badge_24
                        
                        details = "CID: $cid  |  ARFCN: $arfcn\nRSSI: $rssi dBm"
                    }
                    else -> {}
                }
            } else {
                 tech = "Sin Serv"
            }
        } else {
            tech = "Sin Servicio"
        }

        val title = "$tech  $freq"
        val text = if (details.isNotEmpty()) details else "Sin datos de la red"
        
        // --- Build Notification ---
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(netIcon)
            .setSortKey("A_servicios_1_red")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setWhen(0)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Salir", exitPendingIntent)
            
        if (details.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(details))
        }
        
        return builder.build()
    }
    
    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "UNKNOWN"
        }
    }

    private fun createIconBitmap(text: String): Bitmap {
        val width = 256
        val height = 256
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        
        // Clear background (Transparent)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.FILL_AND_STROKE
        
        // Draw Centered Text - Same size for all 2-3 character texts
        paint.textSize = when {
            text.length <= 3 -> 160f  // For "4G", "3G", "2G"
            else -> 140f             // For longer text
        }
        
        // Calculate vertical center
        val fontMetrics = paint.fontMetrics
        val baseline = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        
        canvas.drawText(text, width / 2f, baseline, paint)
        
        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Monitor de Red"
            val descriptionText = "Muestra información de la red en tiempo real"
            val importance = NotificationManager.IMPORTANCE_LOW // Low so it doesn't make sound/pop up constantly
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createDataUsageChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Control de Datos"
            val descriptionText = "Muestra el consumo de datos móviles y WiFi"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(DATA_USAGE_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    // --- Time Range Calculation (Copied from ControlDatosScreen) ---
    private fun calculateTimeRange(period: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        return when (period) {
            "Hoy" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            "Ayer" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(startTime, calendar.timeInMillis)
            }
            "Semana" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                if (calendar.timeInMillis > now) {
                    calendar.add(Calendar.WEEK_OF_YEAR, -1)
                }
                
                val startTime = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(startTime, calendar.timeInMillis)
            }
            "Mes" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(startTime, calendar.timeInMillis)
            }
            else -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                Pair(calendar.timeInMillis, now)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
    
    
    // --- Enhanced SNR Extraction Logic ---
    private fun getEnhancedSnr(ss: CellSignalStrengthLte): Int? {
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
                    ss::class.java.getMethod("getSnr")
                } catch (e: Exception) {
                    try {
                        ss::class.java.getMethod("getLteSnr")
                    } catch (e2: Exception) {
                        null
                    }
                }
                if (method != null) {
                    val v = method.invoke(ss) as? Int
                    if (v != null && v != Int.MAX_VALUE && (snrRaw == null || v != 0)) snrRaw = v
                }
            } catch (e: Exception) { }
        }

        // Fallback: Global Signal Strength (Critical for some Xiaomi/Custom ROMs/Samsung)
        if ((snrRaw == null || snrRaw == 0)) {
            try {
                // Encontrar componente LTE en señal global
                val signalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try { telephonyManager?.signalStrength } catch (e: Exception) { null }
                } else null
                
                if (signalStrength != null) {
                    @Suppress("UNCHECKED_CAST")
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
                }
            } catch(e: Exception) { }
        }

        // Si después de todos los fallos sigue siendo 0 y el valor bruto de la API era null/max,
        // entonces preferimos no mostrar nada (null -> "-") en lugar de un 0 sospechoso.
        // Excepto si el valor bruto era 0 realmente y no encontramos nada mejor.
        if (snrRaw != null && snrRaw > 100 && snrRaw != Int.MAX_VALUE) {
            snrRaw = snrRaw / 10
        }

        return snrRaw?.takeIf { it != 0 && it != Int.MAX_VALUE }
    }
    
    // --- Helpers Copied from UtilesScreen ---
    private fun getLteBand(earfcn: Int): String {
        return when {
            earfcn in 0..599 -> "B1"
            earfcn in 600..1199 -> "B2"
            earfcn in 1200..1949 -> "B3"
            earfcn in 1950..2399 -> "B4"
            earfcn in 2400..2649 -> "B5"
            earfcn in 2750..3449 -> "B7"
            earfcn in 3450..3799 -> "B8"
            earfcn in 4150..4759 -> "B20"
            earfcn in 5000..5179 -> "B26"
            earfcn in 5180..5279 -> "B18"
            earfcn in 5280..5379 -> "B19"
            earfcn in 5380..5579 -> "B21"
            earfcn in 5700..5879 -> "B14"
            earfcn in 5880..5989 -> "B12"
            earfcn in 5990..6149 -> "B17"
            earfcn in 6150..6449 -> "B13"
            earfcn in 6450..6599 -> "B25"
            earfcn in 6600..7399 -> "B29"
            earfcn in 7400..7599 -> "B30"
            earfcn in 7700..8039 -> "B32"
            earfcn in 8040..8689 -> "B34"
            earfcn in 8690..9039 -> "B35"
            earfcn in 9040..9289 -> "B36"
            earfcn in 9210..9659 -> "B71"
            earfcn in 9660..9899 -> "B41"
            earfcn in 27210..27659 -> "B72"
            earfcn in 27660..28179 -> "B73"
            earfcn in 28180..29119 -> "B74"
            earfcn in 29120..30159 -> "B75"
            earfcn in 30160..31199 -> "B76"
            earfcn in 31200..32099 -> "B85"
            earfcn in 37750..38249 -> "B38"
            earfcn in 38250..38649 -> "B39"
            earfcn in 38650..39649 -> "B40"
            earfcn in 39650..41589 -> "B42"
            earfcn in 41590..43589 -> "B43"
            earfcn in 43590..45589 -> "B46"
            earfcn in 45590..46589 -> "B48"
            earfcn in 46590..46889 -> "B66"
            earfcn in 46890..47589 -> "B67"
            earfcn in 47590..49189 -> "B68"
            earfcn in 49190..50499 -> "B70"
            else -> "B${earfcn}"
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
    
    private fun getWcdmaFrequency(uarfcn: Int): String {
        return when {
            uarfcn in 10562..10838 -> "2100 MHz"
            uarfcn in 2937..3088 -> "900 MHz"
            else -> "-"
        }
    }
    
    private fun getGsmFrequency(arfcn: Int): String {
        return when {
            arfcn in 1..124 -> "900 MHz"
            arfcn in 512..885 -> "1800 MHz"
            else -> "-"
        }
    }
}
