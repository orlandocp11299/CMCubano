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
import cu.cuban.cmcubano.utils.NotificationScheduler


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

            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (currentHour < 9 || currentHour >= 22) {
                Log.d(TAG, "Chequeo diario postergado: Horario restringido ($currentHour:00). Se procesará en la ventana de 9:00 a 22:00.")
                // Asegurar que el próximo chequeo esté programado para las 9 AM
                NotificationScheduler.scheduleDailyCheck(context)
                return
            }

            val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
            val startTime = prefs.getLong("hidden_start_date", 0L)

            
            if (startTime > 0) {
                val now = System.currentTimeMillis()
                
                // Usar Calendar para calcular la diferencia de días exactos (ignorando la hora)
                val startCal = java.util.Calendar.getInstance().apply { timeInMillis = startTime }
                val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
                
                startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                startCal.set(java.util.Calendar.MINUTE, 0)
                startCal.set(java.util.Calendar.SECOND, 0)
                startCal.set(java.util.Calendar.MILLISECOND, 0)
                
                nowCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                nowCal.set(java.util.Calendar.MINUTE, 0)
                nowCal.set(java.util.Calendar.SECOND, 0)
                nowCal.set(java.util.Calendar.MILLISECOND, 0)
                
                val diffDays = ((nowCal.timeInMillis - startCal.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
                val lastNotifiedDay = prefs.getInt("last_notified_day", -1)
                
                // Actualizar el campo oculto para reflejar la realidad
                prefs.edit().putInt("hidden_days_passed", diffDays).apply()
                
                Log.d(TAG, "Chequeo. Inicio: ${startCal.time}, Ahora: ${nowCal.time}, DíasPasados: $diffDays, ÚltimoNotificado: $lastNotifiedDay")
                
                // Lógica de notificación:
                // Si han pasado 34 días, estamos en el día 35 del plan -> Notificar "Vence mañana"
                if (diffDays == 34 && lastNotifiedDay < 34) {
                    showNotification(
                        context, 
                        "ALERTA", 
                        "Su paquete de datos vence mañana", 
                        1001
                    )
                    prefs.edit().putInt("last_notified_day", 34).apply()
                    Log.d(TAG, "Notificación enviada: Vence mañana (Día 35)")
                } 
                // Si han pasado 35 o más días, estamos en el día 36 del plan o superior -> Notificar "Vence hoy"
                else if (diffDays >= 35 && lastNotifiedDay < 35) {
                    showNotification(
                        context, 
                        "ALERTA", 
                        "Su paquete de datos vence hoy , compre uno nuevo para conservar sus recursos.", 
                        1002
                    )
                    prefs.edit().putInt("last_notified_day", 35).apply()
                    Log.d(TAG, "Notificación enviada: Vence hoy (Día 36+)")
                }

                // Seguir programando el chequeo si aún no hemos llegado al día de vencimiento hoy
                if (diffDays < 35) {
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
        NotificationScheduler.scheduleDailyCheck(context)
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
