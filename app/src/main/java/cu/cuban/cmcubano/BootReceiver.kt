package cu.cuban.cmcubano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cu.cuban.cmcubano.utils.PremiumManager


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Teléfono reiniciado. Reprogramando alarmas...")
            
            val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("recordatorio_enabled", false)
            val startTime = prefs.getLong("hidden_start_date", 0L)
            val isPremium = PremiumManager.isPremium(context)
            
            if (isEnabled && startTime > 0 && isPremium) {

                // Usar la lógica de SmsReceiver para reprogramar el chequeo diario
                val smsReceiver = SmsReceiver()
                // Nota: He hecho pública la función scheduleDailyCheck o usado una instancia aquí
                // Para mantenerlo limpio, podemos simplemente invocar la lógica de programación
                scheduleDailyCheck(context)
            }
        }
    }
    
    private fun scheduleDailyCheck(context: Context) {
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
        }
        
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
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
            Log.d("BootReceiver", "Alarma reprogramada exitosamente para las 9 AM")
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error al reprogramar: ${e.message}")
        }
    }
}
