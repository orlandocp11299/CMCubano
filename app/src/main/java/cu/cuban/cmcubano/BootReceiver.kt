package cu.cuban.cmcubano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cu.cuban.cmcubano.utils.PremiumManager
import cu.cuban.cmcubano.utils.NotificationScheduler


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == "android.intent.action.TIME_SET" ||
            action == "android.intent.action.TIMEZONE_CHANGED") {
            Log.d("BootReceiver", "Evento detectado ($action). Verificando recordatorios...")
            
            val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
            val isPremium = PremiumManager.isPremium(context)
            val isEnabled = prefs.getBoolean("recordatorio_enabled", isPremium)
            val startTime = prefs.getLong("hidden_start_date", 0L)
            
            if (isEnabled && startTime > 0 && isPremium) {
                Log.d("BootReceiver", "Recordatorio activo. Activando chequeo inmediato y reprogramando...")
                
                // 1. Ejecutar un chequeo inmediato por si el teléfono estuvo apagado a las 9 AM
                NotificationScheduler.triggerImmediateCheck(context)
                
                // 2. Reprogramar la alarma para los siguientes días
                NotificationScheduler.scheduleDailyCheck(context)
            }
        }
    }
    
}
