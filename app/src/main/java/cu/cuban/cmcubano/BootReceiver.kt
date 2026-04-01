package cu.cuban.cmcubano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cu.cuban.cmcubano.utils.PremiumManager
import cu.cuban.cmcubano.utils.NotificationScheduler
import cu.cuban.cmcubano.NotificationReceiver


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

            // Verificar y reprogramar alarmas de Recarga 360
            val recharge360Prefs = context.getSharedPreferences("recharge360_prefs", Context.MODE_PRIVATE)
            val isRecharge360Enabled = recharge360Prefs.getBoolean("recharge360_enabled", false)

            if (isRecharge360Enabled) {
                Log.d("BootReceiver", "Recarga 360 activo. Verificando y reprogramando alarmas...")

                // Ejecutar verificación inmediata de Recarga 360
                val checkIntent = Intent(context, NotificationReceiver::class.java)
                checkIntent.action = "ACTION_RECHARGE_360_CHECK"
                context.sendBroadcast(checkIntent)
            }

            // Iniciar Monitor de Red si está activo
            val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (appPrefs.getBoolean("monitor_network_notification", false)) {
                val netIntent = Intent(context, NotificationService::class.java).apply {
                    this.action = NotificationService.ACTION_START_MONITOR
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(netIntent)
                } else {
                    context.startService(netIntent)
                }
            }

            // Iniciar Monitor de Datos si está activo
            val dataPrefs = context.getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
            if (dataPrefs.getBoolean("data_usage_live_notification", false)) {
                val dataIntent = Intent(context, NotificationService::class.java).apply {
                    this.action = NotificationService.ACTION_START_DATA_MONITOR
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(dataIntent)
                } else {
                    context.startService(dataIntent)
                }
            }
        }
    }
    
}
