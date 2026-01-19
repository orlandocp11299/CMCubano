package cu.cuban.cmcubano.utils

import android.content.Context
import android.provider.Settings

object PremiumManager {
    private const val PREFS_NAME = "app_prefs"
    private const val IS_PREMIUM = "is_premium"
    private const val PREMIUM_CODE = "premium_code"

    // Clave pública RSA para verificar la firma
    private const val PUBLIC_KEY = 
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0L+cKeapsA5cQj3RRe0A" +
        "xw4Z1Vxr56RyH7CzgbM0OnKJv0ffmehqIKI+vMz35XGlM3NCYAczNgCx9IOULcSk" +
        "UG7gDAfU3ojFk75OGowkVMEVWgs5FMO3CqBuYpyTeY5gZBceiH/dXBcl3RqQcv/8" +
        "C+zE5rglIaXWFKGIV5rgQsIfnwkplwgElEeKs6DdiylVuTf6K35Mi2Ey+TUQqki6" +
        "VLQY63/Kd7g+aDyr28bipz5Dr5R+Mt+YnkcwidpW9QEJm9j03cYXDfDORpnAO+Bb" +
        "CtmTtGHOpW8LimpTVTMHLQh8PDw0R/JLlxM8EVd6t1cfQZkk8Ll08C2GVpFcCGRF" +
        "qQIDAQAB"

    fun isPremium(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(IS_PREMIUM, false)
    }

    fun getDeviceReference(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "0000000000000000"
        // Generate a stable 8-digit numeric reference from the ANDROID_ID
        val hashCode = Math.abs(androidId.hashCode()).toLong()
        return hashCode.toString().padStart(8, '0').takeLast(8)
    }

    fun validateAndActivate(context: Context, signatureCode: String): Boolean {
        if (signatureCode.isBlank()) return false
        val reference = getDeviceReference(context)
        
        // Verificar la firma RSA
        if (verifySignature(reference, signatureCode)) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(IS_PREMIUM, true)
                .putString(PREMIUM_CODE, signatureCode)
                .apply()
            return true
        }
        return false
    }

    private fun verifySignature(data: String, signatureStr: String): Boolean {
        try {
            // 1. Limpiar la firma de posibles headers y espacios
            val cleanSignature = signatureStr
                .replace("-----BEGIN SIGNATURE-----", "")
                .replace("-----END SIGNATURE-----", "")
                .replace("-----BEGIN RSA SIGNATURE-----", "")
                .replace("-----END RSA SIGNATURE-----", "")
                .replace("\\s".toRegex(), "")

            // 2. Preparar la llave pública
            val publicKeyBytes = android.util.Base64.decode(PUBLIC_KEY, android.util.Base64.DEFAULT)
            val keySpec = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            
            // 3. Obtener posibles bytes de firma (Base64 y Hex)
            val possibleSigBytes = mutableListOf<ByteArray>()
            
            // Intentar Base64
            try {
                possibleSigBytes.add(android.util.Base64.decode(cleanSignature, android.util.Base64.DEFAULT))
            } catch (e: Exception) {}
            
            // Intentar Hex (si parece hex)
            if (cleanSignature.all { it in "0123456789abcdefABCDEF" } && cleanSignature.length % 2 == 0) {
                try {
                    val hexBytes = cleanSignature.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    possibleSigBytes.add(hexBytes)
                } catch (e: Exception) {}
            }

            if (possibleSigBytes.isEmpty()) return false

            // 4. Probar combinaciones de algoritmos y variantes de datos
            val algos = listOf("SHA256withRSA", "SHA1withRSA")
            val dataVariants = listOf(data, data + "\n", data + "\r\n")

            for (sigBytes in possibleSigBytes) {
                for (algo in algos) {
                    val signature = java.security.Signature.getInstance(algo)
                    for (variant in dataVariants) {
                        try {
                            signature.initVerify(publicKey)
                            signature.update(variant.toByteArray(Charsets.UTF_8))
                            if (signature.verify(sigBytes)) return true
                        } catch (e: Exception) {
                            // Continuar con la siguiente combinación
                        }
                    }
                }
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }
}

