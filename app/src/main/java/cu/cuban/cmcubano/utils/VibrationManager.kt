package cu.cuban.cmcubano.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context.VIBRATOR_SERVICE

object VibrationManager {
    // Vibration intensity levels
    const val INTENSITY_LOW = 0
    const val INTENSITY_MEDIUM = 1
    const val INTENSITY_HIGH = 2
    
    fun vibrate(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("vibration_enabled", false) 
        
        if (isEnabled) {
            val intensity = prefs.getInt("vibration_intensity", INTENSITY_LOW)
            val vibrator = context.getSystemService(VIBRATOR_SERVICE) as Vibrator
            
            // Define duration and amplitude based on intensity
            val (duration, amplitude) = when (intensity) {
                INTENSITY_LOW -> Pair(40L, 80)      // Baja: 40ms, amplitud 80
                INTENSITY_MEDIUM -> Pair(60L, 150)  // Media: 60ms, amplitud 150
                INTENSITY_HIGH -> Pair(80L, 255)    // Alta: 80ms, amplitud máxima
                else -> Pair(40L, 80)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }
}
