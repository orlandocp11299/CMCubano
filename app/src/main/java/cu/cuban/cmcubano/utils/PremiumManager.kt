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

    private fun generateActivationCode(reference: String): String {
        val key = cu.cuban.cmcubano.BuildConfig.ACTIVATION_SECRET_KEY
        val input = reference + key
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    fun validateAndActivate(context: Context, code: String): Boolean {
        val reference = getDeviceReference(context)
        val expected = generateActivationCode(reference)
        
        if (code.equals(expected, ignoreCase = true)) {
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
        return generateActivationCode(reference)
    }
}

