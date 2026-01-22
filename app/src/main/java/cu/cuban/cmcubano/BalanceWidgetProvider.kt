package cu.cuban.cmcubano

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class BalanceWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_UPDATE_BALANCE = "cu.cuban.cmcubano.ACTION_UPDATE_BALANCE"
        private const val ACTION_SELECT_SIM = "cu.cuban.cmcubano.ACTION_SELECT_SIM"
        private const val ACTION_SHOW_AIRPLANE_MESSAGE = "cu.cuban.cmcubano.ACTION_SHOW_AIRPLANE_MESSAGE"
        private const val EXTRA_SIM_INDEX = "extra_sim_index"
        private const val PREFS_NAME = "balance_widget_prefs"
        private const val KEY_SALDO = "saldo"
        private const val KEY_DATOS = "datos"
        private const val KEY_VOZ = "voz"
        private const val KEY_SMS = "sms"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_SELECTED_SIM = "selected_sim"
        private const val KEY_CALL_STATE = "last_call_state"
        private const val KEY_IS_UPDATING = "is_updating"
        private const val KEY_SIM2_AVAILABLE = "sim2_available"
        private const val TAG = "BalanceWidget"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Check SIM availability on widget updates
        checkSimAvailability(context)
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE_BALANCE -> {
                Log.d(TAG, "Update requested from widget")
                if (isAirplaneModeOn(context)) {
                    showAirplaneModeMessage(context)
                } else {
                    setUpdatingState(context, true)
                    requestUssd(context, silent = false)
                }
            }
            ACTION_SHOW_AIRPLANE_MESSAGE -> {
                showAirplaneModeMessage(context)
            }
            ACTION_SELECT_SIM -> {
                val simIndex = intent.getIntExtra(EXTRA_SIM_INDEX, 0)
                Log.d(TAG, "SIM selection changed to: $simIndex")
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_SELECTED_SIM, simIndex).apply()
                
                Log.d(TAG, "Saved selected SIM: $simIndex")
                
                // Refresh UI immediately
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, BalanceWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                Log.d(TAG, "Updating ${appWidgetIds.size} widgets")
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
                
                // Trigger actual USSD request for the new SIM
                if (isAirplaneModeOn(context)) {
                    showAirplaneModeMessage(context)
                } else {
                    setUpdatingState(context, true)
                    requestUssd(context, silent = false)
                }
            }
            "android.intent.action.PHONE_STATE" -> {
                val stateString = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d(TAG, "Phone State Change: $stateString")
                
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastSavedState = prefs.getString(KEY_CALL_STATE, TelephonyManager.EXTRA_STATE_IDLE)
                
                // If it was OFFHOOK and now it's IDLE, the call just ended
                if (lastSavedState == TelephonyManager.EXTRA_STATE_OFFHOOK && 
                    stateString == TelephonyManager.EXTRA_STATE_IDLE) {
                    Log.d(TAG, "Call ended detected, triggering silent update")
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestUssd(context, silent = true)
                    }, 3000)
                }
                
                // Save current state for next time
                if (stateString != null) {
                    prefs.edit().putString(KEY_CALL_STATE, stateString).apply()
                }
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saldo = prefs.getString(KEY_SALDO, "---")
        val datos = prefs.getString(KEY_DATOS, "---")
        val voz = prefs.getString(KEY_VOZ, "---")
        val sms = prefs.getString(KEY_SMS, "---")
        val lastUpdate = prefs.getString(KEY_LAST_UPDATE, "Nunca")
        val selectedSim = prefs.getInt(KEY_SELECTED_SIM, 0)
        val isUpdating = prefs.getBoolean(KEY_IS_UPDATING, false)
        val sim2Available = prefs.getBoolean(KEY_SIM2_AVAILABLE, true)

        val views = RemoteViews(context.packageName, R.layout.balance_widget)
        views.setTextViewText(R.id.tv_saldo, saldo)
        views.setTextViewText(R.id.tv_datos, datos)
        views.setTextViewText(R.id.tv_voz, voz)
        views.setTextViewText(R.id.tv_sms, sms)
        views.setTextViewText(R.id.tv_last_update, "Última act: $lastUpdate")

        // SIM Selector UI
        val colorWhite = context.resources.getColor(R.color.white)
        val colorSecondary = context.resources.getColor(R.color.widget_text_secondary)
        
        views.setTextColor(R.id.btn_sim1, if (selectedSim == 0) colorWhite else colorSecondary)
        views.setTextColor(R.id.btn_sim2, if (selectedSim == 1 && sim2Available) colorWhite else colorSecondary)
        
        views.setInt(R.id.btn_sim1, "setBackgroundResource", if (selectedSim == 0) R.drawable.bg_sim_active else 0)
        views.setInt(R.id.btn_sim2, "setBackgroundResource", if (selectedSim == 1 && sim2Available) R.drawable.bg_sim_active else 0)
        
        // Handle SIM2 availability
        if (!sim2Available) {
            views.setViewVisibility(R.id.btn_sim2, View.GONE)
        } else {
            views.setViewVisibility(R.id.btn_sim2, View.VISIBLE)
            views.setTextViewText(R.id.btn_sim2, "SIM 2")
        }

        // Update button state based on updating status
        if (isUpdating) {
            views.setViewVisibility(R.id.tv_update_text, View.GONE)
            views.setViewVisibility(R.id.progress_refresh, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tv_update_text, View.VISIBLE)
            views.setViewVisibility(R.id.progress_refresh, View.GONE)
        }

        // Intent for the Update button
        val updateIntent = Intent(context, BalanceWidgetProvider::class.java).apply {
            action = ACTION_UPDATE_BALANCE
        }
        val pendingUpdateIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.btn_update, pendingUpdateIntent)

        // Intents for SIM selection
        val sim1Intent = Intent(context, BalanceWidgetProvider::class.java).apply {
            action = ACTION_SELECT_SIM
            putExtra(EXTRA_SIM_INDEX, 0)
        }
        val pendingSim1Intent = PendingIntent.getBroadcast(
            context, 1, sim1Intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.btn_sim1, pendingSim1Intent)

        val sim2Intent = Intent(context, BalanceWidgetProvider::class.java).apply {
            action = ACTION_SELECT_SIM
            putExtra(EXTRA_SIM_INDEX, 1)
        }
        val pendingSim2Intent = PendingIntent.getBroadcast(
            context, 2, sim2Intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.btn_sim2, pendingSim2Intent)

        // Ensure the parent container also handles clicks and is properly linked
        if (sim2Available) {
            views.setOnClickPendingIntent(R.id.sim_selector_container, if (selectedSim == 0) pendingSim2Intent else pendingSim1Intent)
        } else {
            views.setOnClickPendingIntent(R.id.sim_selector_container, pendingSim1Intent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun requestUssd(context: Context, silent: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val selectedSim = prefs.getInt(KEY_SELECTED_SIM, 0)
                
                var telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                
                // Try to use selected SIM
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                    try {
                        val subInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(selectedSim)
                        if (subInfo != null) {
                            telephonyManager = telephonyManager.createForSubscriptionId(subInfo.subscriptionId)
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "No permission to read subscription info", e)
                    }
                }

                if (!silent) {
                    // Toast.makeText(context, "Obteniendo Info SIM ${selectedSim + 1}", Toast.LENGTH_SHORT).show()
                }
                
                telephonyManager.sendUssdRequest("*222#", object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(telephonyManager: TelephonyManager?, request: String?, response: CharSequence?) {
                        Log.d(TAG, "USSD Response: $response")
                        setUpdatingState(context, false)
                        processResponse(context, response.toString(), silent)
                    }

                    override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager?, request: String?, failureCode: Int) {
                        Log.e(TAG, "USSD Failed: $failureCode")
                        setUpdatingState(context, false)
                        if (!silent) {
                            Handler(Looper.getMainLooper()).post {
                                // Toast.makeText(context, "Error en consulta: $failureCode", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: SecurityException) {
                setUpdatingState(context, false)
                if (!silent) {
                    // Toast.makeText(context, "Permisos insuficientes para USSD", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setUpdatingState(context, false)
                Log.e(TAG, "Exception in USSD", e)
            }
        } else {
            // Fallback for older versions - NOT silent
            if (!silent) {
                val ussdCode = "*222#"
                val encodedHash = Uri.encode("#")
                val dialIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$ussdCode$encodedHash"))
                dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(dialIntent)
            }
        }
    }

    private fun processResponse(context: Context, response: String, silent: Boolean) {
        // Simple ETECSA parser
        // Example: "Saldo:10.00 CUP, expira: 12/05/2024. Datos: 1.5 GB LTE + 200 MB 3G. Voz: 15 min. SMS: 20."
        
        val saldo = findValue(response, "Saldo:?\\s*([\\d.]+)") ?: "---"
        val datos = findValue(response, "Datos:?\\s*([\\d.\\s]+[GM]B)") ?: "---"
        val sms = findValue(response, "SMS:?\\s*(\\d+)") ?: "---"
        val voz = findValue(response, "Voz:?\\s*([\\d:]+)") ?: "---"

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val lastUpdate = timeFormatter.format(Date())

        // Save to prefs
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_SALDO, saldo)
            putString(KEY_DATOS, datos)
            putString(KEY_VOZ, voz)
            putString(KEY_SMS, sms)
            putString(KEY_LAST_UPDATE, lastUpdate)
            apply()
        }

        // Update all widgets
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, BalanceWidgetProvider::class.java)
        onUpdate(context, appWidgetManager, appWidgetManager.getAppWidgetIds(componentName))
        
        // if (!silent) {
        //     Toast.makeText(context, "Información actualizada", Toast.LENGTH_SHORT).show()
        // }
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } else {
            @Suppress("DEPRECATION")
            Settings.System.getInt(context.contentResolver, Settings.System.AIRPLANE_MODE_ON, 0) != 0
        }
    }

    private fun showAirplaneModeMessage(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Por favor desactive el modo avión", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkSimAvailability(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                val simCount = activeSubscriptions?.size ?: 0
                
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val sim2Available = simCount >= 2
                
                Log.d(TAG, "Detected $simCount SIM(s), SIM2 available: $sim2Available")
                
                // Update SIM2 availability in preferences
                prefs.edit().putBoolean(KEY_SIM2_AVAILABLE, sim2Available).apply()
                
                // If SIM2 was selected but is not available, fallback to SIM1
                val selectedSim = prefs.getInt(KEY_SELECTED_SIM, 0)
                if (selectedSim == 1 && !sim2Available) {
                    prefs.edit().putInt(KEY_SELECTED_SIM, 0).apply()
                    Log.d(TAG, "Fallback to SIM1 since SIM2 is not available")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "No permission to check subscription info", e)
                // Assume SIM2 is available if we can't check
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SIM2_AVAILABLE, true).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking SIM availability", e)
                // Assume SIM2 is available on error
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SIM2_AVAILABLE, true).apply()
            }
        } else {
            // For older versions, assume SIM2 is available
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SIM2_AVAILABLE, true).apply()
        }
    }

    private fun setUpdatingState(context: Context, isUpdating: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_UPDATING, isUpdating)
            .apply()
        
        // Update all widgets to reflect the new state
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, BalanceWidgetProvider::class.java)
        onUpdate(context, appWidgetManager, appWidgetManager.getAppWidgetIds(componentName))
    }

    private fun findValue(text: String, regex: String): String? {
        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }
}
