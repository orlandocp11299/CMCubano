package cu.cuban.cmcubano.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object NetworkUtils {

    fun openRadioInfo(context: Context) {
        val intents = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.RadioInfo"),
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$RadioInfoActivity"),
            Intent().setClassName("com.android.phone", "com.android.phone.settings.RadioInfo"),
            Intent().setClassName("com.samsung.android.app.telephonyui", "com.samsung.android.app.telephonyui.HiddenMenu"),
            Intent().setClassName("com.qualcomm.qti.networksetting", "com.qualcomm.qti.networksetting.MobileNetworkSettings"),
            Intent().setClassName("com.samsung.android.networksetting", "com.samsung.android.networksetting.MobileNetworkSettings"),
            Intent().setClassName("com.android.settings", "com.android.settings.BandMode"),
            Intent().setClassName("com.samsung.android.app.telephonyui", "com.samsung.android.app.telephonyui.netsettings.ui.NetSettingsActivity"),
            Intent().setClassName("com.samsung.android.app.telephonyui", "com.samsung.android.app.telephonyui.netsettings.ui.BandSelectionActivity"),
            Intent().setClassName("com.android.settings", "com.android.settings.TestingSettings"),
            Intent("android.intent.action.MAIN").setClassName("com.android.settings", "com.android.settings.TestingSettingsActivity"),
            Intent().setClassName("com.mediatek.engineermode", "com.mediatek.engineermode.EngineerMode"),
            Intent().setClassName("com.mediatek.engineermode", "com.mediatek.engineermode.Connectivity")
        )

        var started = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                started = true
                break
            } catch (e: Exception) {
                continue
            }
        }

        if (!started) {
            val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
            if (isSamsung) {
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:*#*#4636#*#*")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    Toast.makeText(context, "Samsung bloquea el acceso automático. Marque el código mostrado.", Toast.LENGTH_LONG).show()
                    return
                } catch (e: Exception) {
                }
            }

            try {
                context.startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                val message = if (isSamsung) "Su Samsung tiene este menú bloqueado. Intente usar una app como 'ForceLTE' o 'Alliance Shield'."
                else "No se pudo abrir el menú. Busque 'Información del dispositivo' en ajustes."
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir la configuración.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
