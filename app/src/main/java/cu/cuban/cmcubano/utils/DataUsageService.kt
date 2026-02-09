package cu.cuban.cmcubano.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import cu.cuban.cmcubano.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Servicio en segundo plano para monitoreo continuo del uso de datos
 */
class DataUsageService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dataUsageManager: DataUsageManager? = null
    private var dataLimitManager: DataLimitManager? = null
    private var monitoringJob: Job? = null
    private var isMonitoring = false
    
    companion object {
        const val ACTION_START_MONITOR = "cu.cuban.cmcubano.START_DATA_MONITOR"
        const val ACTION_STOP_MONITOR = "cu.cuban.cmcubano.STOP_DATA_MONITOR"
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "data_usage_monitor"
        
        // Intervalo de monitoreo en milisegundos (5 minutos)
        private const val MONITORING_INTERVAL = 5 * 60 * 1000L
        
        // Intervalo para recordatorios diarios (24 horas)
        private const val DAILY_REMINDER_INTERVAL = 24 * 60 * 60 * 1000L
    }
    
    override fun onCreate() {
        super.onCreate()
        dataUsageManager = DataUsageManager(this)
        dataLimitManager = DataLimitManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITOR -> startMonitoring()
            ACTION_STOP_MONITOR -> stopMonitoring()
            else -> startMonitoring()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
    }
    
    /**
     * Inicia el monitoreo del uso de datos
     */
    private fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        
        // Iniciar servicio como foreground
        startForeground(NOTIFICATION_ID, createMonitoringNotification())
        
        // Iniciar job de monitoreo
        monitoringJob = serviceScope.launch {
            var lastDailyReminder = 0L
            
            while (isMonitoring) {
                try {
                    // Verificar si el dispositivo está en modo de bajo consumo
                    if (!isPowerSaveMode()) {
                        val (startTime, endTime) = dataLimitManager?.getCurrentCycleDates() ?: Pair(0L, 0L)
                        val currentUsage = dataUsageManager?.getMobileDataUsage(startTime, endTime) ?: 0L
                        
                        // Verificar límites y generar alertas
                        dataLimitManager?.checkDataUsage(currentUsage)
                        
                        // Generar recordatorio diario si está habilitado
                        val currentTime = System.currentTimeMillis()
                        if (dataLimitManager?.isDailyReminderEnabled() == true && 
                            currentTime - lastDailyReminder >= DAILY_REMINDER_INTERVAL) {
                            dataLimitManager?.generateDailyReminder(currentUsage)
                            lastDailyReminder = currentTime
                        }
                        
                        // Actualizar notificación de monitoreo
                        updateMonitoringNotification(currentUsage)
                    }
                    
                    // Esperar hasta el próximo ciclo de monitoreo
                    delay(MONITORING_INTERVAL)
                    
                } catch (e: Exception) {
                    // En caso de error, esperar un tiempo más corto antes de reintentar
                    delay(60000) // 1 minuto
                }
            }
        }
    }
    
    /**
     * Detiene el monitoreo del uso de datos
     */
    private fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Crea el canal de notificación para el monitoreo
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor de Datos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo continuo del uso de datos móviles"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Crea la notificación de monitoreo
     */
    private fun createMonitoringNotification(usage: Long = 0L): Notification {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("navigate_to", "control_datos")
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitor de Datos Activo")
            .setContentText("Uso actual: ${formatBytes(usage)}")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    /**
     * Actualiza la notificación de monitoreo con el uso actual
     */
    private fun updateMonitoringNotification(usage: Long) {
        try {
            val notification = createMonitoringNotification(usage)
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Ignorar errores de notificación
        }
    }
    
    /**
     * Verifica si el dispositivo está en modo de bajo consumo
     */
    private fun isPowerSaveMode(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }
    
    /**
     * Envía una notificación de alerta sobre el uso de datos
     */
    private fun sendAlertNotification(alert: DataUsageAlert) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("navigate_to", "control_datos")
            
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Alerta de Datos")
                .setContentText(alert.message)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pendingIntent)
                .setPriority(getNotificationPriority(alert.severity))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(alert.id.hashCode(), notification)
            
        } catch (e: Exception) {
            // Ignorar errores de notificación
        }
    }
    
    /**
     * Obtiene la prioridad de notificación según la severidad
     */
    private fun getNotificationPriority(severity: AlertSeverity): Int {
        return when (severity) {
            AlertSeverity.LOW -> NotificationCompat.PRIORITY_DEFAULT
            AlertSeverity.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
            AlertSeverity.HIGH -> NotificationCompat.PRIORITY_HIGH
            AlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_MAX
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
 * Gestor para el servicio de monitoreo de datos
 */
class DataUsageMonitorManager(private val context: Context) {
    
    /**
     * Inicia el servicio de monitoreo
     */
    fun startMonitoring() {
        val intent = Intent(context, DataUsageService::class.java).apply {
            action = DataUsageService.ACTION_START_MONITOR
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    /**
     * Detiene el servicio de monitoreo
     */
    fun stopMonitoring() {
        val intent = Intent(context, DataUsageService::class.java).apply {
            action = DataUsageService.ACTION_STOP_MONITOR
        }
        context.startService(intent)
    }
    
    /**
     * Verifica si el servicio está en ejecución
     */
    fun isMonitoring(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        return runningServices.any { service ->
            service.service.className == DataUsageService::class.java.name
        }
    }
    
    /**
     * Programa el monitoreo automático si está habilitado
     */
    fun scheduleAutoMonitoring() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val autoMonitoringEnabled = prefs.getBoolean("auto_data_monitoring", false)
        
        if (autoMonitoringEnabled) {
            startMonitoring()
        } else {
            stopMonitoring()
        }
    }
    
    /**
     * Habilita o deshabilita el monitoreo automático
     */
    fun setAutoMonitoring(enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_data_monitoring", enabled).apply()
        
        if (enabled) {
            startMonitoring()
        } else {
            stopMonitoring()
        }
    }
}
