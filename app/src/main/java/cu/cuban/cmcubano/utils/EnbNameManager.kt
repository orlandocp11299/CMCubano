package cu.cuban.cmcubano.utils

import android.content.Context
import android.content.SharedPreferences

object EnbNameManager {
    private const val PREFS_NAME = "enb_names_prefs"
    private const val ENB_NAME_PREFIX = "enb_name_"
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveEnbName(context: Context, enbId: String, name: String) {
        val prefs = getPreferences(context)
        prefs.edit()
            .putString("${ENB_NAME_PREFIX}$enbId", name)
            .apply()
    }
    
    fun getEnbName(context: Context, enbId: String): String? {
        val prefs = getPreferences(context)
        return prefs.getString("${ENB_NAME_PREFIX}$enbId", null)
    }
    
    fun getAllEnbNames(context: Context): Map<String, String> {
        val prefs = getPreferences(context)
        val enbNames = mutableMapOf<String, String>()
        
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(ENB_NAME_PREFIX) && value is String) {
                val enbId = key.removePrefix(ENB_NAME_PREFIX)
                enbNames[enbId] = value
            }
        }
        
        return enbNames
    }
    
    fun removeEnbName(context: Context, enbId: String) {
        val prefs = getPreferences(context)
        prefs.edit()
            .remove("${ENB_NAME_PREFIX}$enbId")
            .apply()
    }
    
    fun clearAllEnbNames(context: Context) {
        val prefs = getPreferences(context)
        prefs.edit().clear().apply()
    }
}
