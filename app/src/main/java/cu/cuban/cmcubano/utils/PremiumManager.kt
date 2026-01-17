package cu.cuban.cmcubano.utils

import android.content.Context
import android.provider.Settings

object PremiumManager {
    private const val PREFS_NAME = "app_prefs"
    private const val IS_PREMIUM = "is_premium"
    private const val PREMIUM_CODE = "premium_code"

    fun isPremium(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(IS_PREMIUM, false)
    }

    fun getDeviceReference(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "0000000000000000"
        // Generate a stable 8-digit numeric reference from the ANDROID_ID
        // We use the absolute value of the hash code to ensure it's numeric and stable
        val hashCode = Math.abs(androidId.hashCode()).toLong()
        return hashCode.toString().padStart(8, '0').takeLast(8)
    }

    fun validateAndActivate(context: Context, code: String): Boolean {
        val reference = getDeviceReference(context)
        val referenceNum = reference.toLongOrNull() ?: 0L
        
        // Formula: (Reference * 19223) / 32291
        val expected = (referenceNum * 19223) / 32291
        
        if (code == expected.toString()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(IS_PREMIUM, true)
                .putString(PREMIUM_CODE, code)
                .apply()
            return true
        }
        return false
    }

    fun getExpectedCode(reference: String): String {
        val referenceNum = reference.toLongOrNull() ?: 0L
        return ((referenceNum * 19223) / 32291).toString()
    }
}

