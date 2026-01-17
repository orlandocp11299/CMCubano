package cu.cuban.cmcubano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.SmsMessage
import android.util.Log
import cu.cuban.cmcubano.utils.PremiumManager
import java.util.*


class SmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            Log.d(TAG, "SMS recibido")
            
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<*>?
                if (pdus != null) {
                    for (pdu in pdus) {
                        val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                        val messageBody = smsMessage.messageBody
                        val sender = smsMessage.originatingAddress
                        
                        // Verificar si es un mensaje de ETECSA con los encabezados específicos
                        val hasPlanNacionalHeader = messageBody.contains("ETECSA Plan Nacional")
                        val hasRecargaHeader = messageBody.contains("ETECSA Recarga Internacional")
                        // Regex para detectar xGB o xMB (ej: 1GB, 200MB, 5 GB)
                        val hasDataInfo = messageBody.contains(Regex("\\d+\\s*(GB|MB)", RegexOption.IGNORE_CASE))
                        
                        val shouldProcess = (hasPlanNacionalHeader && hasDataInfo) || hasRecargaHeader

                        if (shouldProcess) {
                            
                            Log.d(TAG, "Mensaje de ETECSA detectado: $messageBody")
                            
                            // Verificar si el recordatorio está activado y si el usuario es Premium
                            val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
                            val isRecordatorioEnabled = prefs.getBoolean("recordatorio_enabled", false)
                            val isPremium = PremiumManager.isPremium(context)
                            
                            if (isRecordatorioEnabled && isPremium) {

                                // 1. Guardar fecha de recepción (campo oculto 1)
                                val timestamp = System.currentTimeMillis()
                                prefs.edit()
                                    .putLong("hidden_start_date", timestamp)
                                    .putInt("hidden_days_passed", 0) // Empezando de cero
                                    .apply()
                                
                                Log.d(TAG, "Contador reiniciado a 0. Fecha guardada.")
                                
                                // 2. Programar chequeo diario a las 9 AM
                                scheduleDailyCheck(context)
                            }
                        }
                    }
                }
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
        
        // Calcular próxima 9:00 AM
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Si ya pasaron las 9 AM de hoy, programar para mañana
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
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
            Log.d(TAG, "Chequeo diario programado para: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al programar alarma: ${e.message}")
        }
    }
}
