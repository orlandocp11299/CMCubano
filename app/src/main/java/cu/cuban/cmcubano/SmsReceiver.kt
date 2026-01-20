package cu.cuban.cmcubano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.SmsMessage
import android.util.Log
import android.os.Build
import cu.cuban.cmcubano.utils.PremiumManager
import cu.cuban.cmcubano.utils.NotificationScheduler
import java.util.*
import kotlin.text.RegexOption


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
                val format = bundle.getString("format")
                if (pdus != null) {
                    for (pdu in pdus) {
                        val smsMessage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && format != null) {
                            SmsMessage.createFromPdu(pdu as ByteArray, format)
                        } else {
                            SmsMessage.createFromPdu(pdu as ByteArray)
                        }
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
                                    .putInt("last_notified_day", -1) // Reset notifications logic
                                    .apply()
                                
                                Log.d(TAG, "Contador reiniciado a 0. Fecha guardada.")
                                
                                // 2. Programar chequeo diario a las 9 AM
                                NotificationScheduler.scheduleDailyCheck(context)
                            }
                        }
                    }
                }
            }
        }
    }
}
