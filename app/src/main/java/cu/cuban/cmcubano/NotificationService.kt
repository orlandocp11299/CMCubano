package cu.cuban.cmcubano

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class NotificationService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "NotificationService"
        
        fun isReminderEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("recordatorio_enabled", false)
        }
        
        fun toggleReminder(context: Context, enabled: Boolean) {
            val intent = Intent(context, NotificationService::class.java).apply {
                action = "TOGGLE_REMINDER"
                putExtra("enabled", enabled)
            }
            context.startService(intent)
        }
        
        // Función de prueba para verificar el sistema de notificaciones
        fun testNotification(context: Context) {
            Log.d(TAG, "Iniciando prueba de notificación")
            
            val timestamp = System.currentTimeMillis()
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = "SHOW_EXPIRATION_ALERT"
                putExtra("notification_id", timestamp.toInt())
                putExtra("title", "ALERTA")
                putExtra("message", "Tu Plan de Datos vence mañana.")
            }
            
            // Programar notificación para 5 segundos después
            val notificationTime = timestamp + (5 * 1000L)
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                timestamp.toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExact(
                            android.app.AlarmManager.RTC_WAKEUP,
                            notificationTime,
                            pendingIntent
                        )
                        Log.d(TAG, "Prueba de alarma exacta programada para 5 segundos")
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            notificationTime,
                            pendingIntent
                        )
                        Log.d(TAG, "Prueba de alarma con setAndAllowWhileIdle programada para 5 segundos")
                    }
                } else {
                    alarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Prueba de alarma exacta programada para 5 segundos (versiones anteriores)")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de seguridad en prueba: ${e.message}")
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                )
                Log.d(TAG, "Prueba de alarma fallback programada para 5 segundos")
            }
        }
        
        // Función para verificar alarmas activas
        fun checkActiveAlarms(context: Context) {
            val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            val allKeys = prefs.all.keys
            
            Log.d(TAG, "Verificando alarmas activas...")
            allKeys.filter { it.endsWith("_timestamp") }.forEach { key ->
                val timestamp = prefs.getLong(key, 0L)
                val notificationTime = prefs.getLong("${key.replace("_timestamp", "_notification_time")}", 0L)
                
                if (timestamp > 0) {
                    Log.d(TAG, "Recordatorio: $key")
                    Log.d(TAG, "  Timestamp: $timestamp (${java.util.Date(timestamp)})")
                    Log.d(TAG, "  Notificación programada: $notificationTime (${java.util.Date(notificationTime)})")
                    Log.d(TAG, "  Tiempo restante: ${notificationTime - System.currentTimeMillis()} ms")
                }
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "TOGGLE_REMINDER" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                toggleReminder(this, enabled)
            }
            "TEST_NOTIFICATION" -> {
                testNotification(this)
            }
            "CHECK_ALARMS" -> {
                checkActiveAlarms(this)
            }
        }
        return START_STICKY
    }
    
    private fun toggleReminder(context: Context, enabled: Boolean) {
        Log.d(TAG, "Cambiando estado de recordatorio a: $enabled")
        
        val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("recordatorio_enabled", enabled).apply()
        
        if (enabled) {
            // Iniciar monitoreo de mensajes SMS
            startSmsMonitoring(context)
        } else {
            // Detener monitoreo y cancelar notificaciones programadas
            stopSmsMonitoring(context)
            cancelAllScheduledNotifications(context)
        }
    }
    
    private fun startSmsMonitoring(context: Context) {
        Log.d(TAG, "Iniciando monitoreo de SMS")
        // El monitoreo se maneja automáticamente a través del BroadcastReceiver
        // Solo necesitamos asegurarnos de que los permisos estén concedidos
    }
    
    private fun stopSmsMonitoring(context: Context) {
        Log.d(TAG, "Deteniendo monitoreo de SMS")
        // El BroadcastReceiver seguirá activo pero no procesará mensajes
        // ya que el estado estará desactivado en SharedPreferences
    }
    
    private fun cancelAllScheduledNotifications(context: Context) {
        Log.d(TAG, "Cancelando todas las notificaciones programadas")
        
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val allKeys = prefs.all.keys
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        
        allKeys.filter { it.endsWith("_timestamp") }.forEach { key ->
            val timestamp = prefs.getLong(key, 0L)
            if (timestamp > 0) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = "SHOW_EXPIRATION_ALERT"
                }
                
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    timestamp.toInt(),
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "Alarma cancelada: $key")
            }
        }
        
        // Limpiar recordatorios guardados
        prefs.edit().clear().apply()
        Log.d(TAG, "Recordatorios guardados limpiados")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
