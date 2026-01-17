package cu.cuban.cmcubano

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cu.cuban.cmcubano.utils.PremiumManager


class NotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationReceiver"
        private const val CHANNEL_ID = "REMINDER_CHANNEL"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Broadcast recibido: ${intent.action}")

        if (intent.action == "ACTION_DAILY_CHECK") {
            if (!PremiumManager.isPremium(context)) {
                Log.d(TAG, "Chequeo diario ignorado: El usuario no es Premium")
                return
            }
            val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
            val startTime = prefs.getLong("hidden_start_date", 0L)

            
            if (startTime > 0) {
                val now = System.currentTimeMillis()
                val diff = now - startTime
                // Calcular días pasados basándose en la diferencia de tiempo real
                val daysPassed = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
                
                // Actualizar el campo oculto para reflejar la realidad
                prefs.edit().putInt("hidden_days_passed", daysPassed.toInt()).apply()
                
                Log.d(TAG, "Chequeo diario. Inicio: ${java.util.Date(startTime)}, Ahora: ${java.util.Date(now)}, Dias pasados: $daysPassed")
                
                if (daysPassed >= 34) {
                    showNotification(
                        context, 
                        "ALERTA", 
                        "Su paquete de datos vence mañana , este es el último recordatorio.", 
                        1001
                    )
                } else {
                     // Reprogramar para mañana a las 9 AM si aún no ha llegado el día
                     scheduleNextCheck(context)
                }
            }
        }
        
        if (intent.action == "SHOW_EXPIRATION_ALERT") {
            val title = intent.getStringExtra("title") ?: "ALERTA"
            val message = intent.getStringExtra("message") ?: "Tu Plan de Datos vence mañana."
            val notificationId = intent.getIntExtra("notification_id", 0)
            
            Log.d(TAG, "Mostrando notificación - Título: $title, Mensaje: $message, ID: $notificationId")
            
            showNotification(context, title, message, notificationId)
        }
    }
    
    private fun scheduleNextCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "ACTION_DAILY_CHECK"
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 
            1001, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, 1) // Mañana
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                     alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                     alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                 alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al reprogramar alarma: ${e.message}")
        }
    }
    
    private fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
        Log.d(TAG, "Creando canal de notificación")
        createNotificationChannel(context)
        
        // Verificar permisos de notificación
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.areNotificationsEnabled()) {
            Log.d(TAG, "Permisos de notificación concedidos")
            
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            
            try {
                notificationManager.notify(notificationId, builder.build())
                Log.d(TAG, "Notificación enviada exitosamente")
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de seguridad al mostrar notificación: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error general al mostrar notificación: ${e.message}")
            }
        } else {
            Log.w(TAG, "Permisos de notificación no concedidos")
        }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de ETECSA"
            val descriptionText = "Notificaciones de vencimiento de planes de datos"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Verificar si el canal ya existe
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                Log.d(TAG, "Creando nuevo canal de notificación")
                notificationManager.createNotificationChannel(channel)
            } else {
                Log.d(TAG, "Canal de notificación ya existe")
            }
        }
    }
}
