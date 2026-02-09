package cu.cuban.cmcubano

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import cu.cuban.cmcubano.utils.DataUsageManager
import cu.cuban.cmcubano.utils.PremiumManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DataPlanWidgetProvider : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidgetData(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == "cu.cuban.cmcubano.DATA_PLAN_REFRESH") {
            Log.d(TAG, "Refresh action received")
            
            scope.launch {
                try {
                    // 1. Mostrar estado cargando
                    setUpdatingState(context, true)
                    
                    // 2. Simular un pequeño delay para la animación
                    delay(800)
                    
                    updateAllWidgets(context)
                    
                    // 3. Quitar estado cargando
                    setUpdatingState(context, false)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            pendingResult.finish()
        }
    }

    private suspend fun setUpdatingState(context: Context, isUpdating: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_UPDATING, isUpdating)
            .apply()
        
        updateAllWidgets(context)
    }

    private suspend fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, DataPlanWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (appWidgetId in appWidgetIds) {
            updateWidgetData(context, appWidgetManager, appWidgetId)
        }
    }

    private suspend fun updateWidgetData(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val widgetPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isUpdating = widgetPrefs.getBoolean(KEY_IS_UPDATING, false)
        
        try {
            val views = RemoteViews(context.packageName, R.layout.data_plan_widget)
            
            // Premium check
            val isPremium = PremiumManager.isPremium(context)
            val trialPrefs = context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE)
            val trialUsed = trialPrefs.getBoolean("control_datos_trial_used", false)
            val trialEndTime = trialPrefs.getLong("control_datos_trial_end_time", 0L)
            val isTrialActive = trialUsed && System.currentTimeMillis() < trialEndTime
            val isLocked = !isPremium && !isTrialActive

            if (isLocked) {
                views.setViewVisibility(R.id.tv_premium_lock, View.VISIBLE)
                views.setViewVisibility(R.id.widget_content_container, View.GONE)
                views.setViewVisibility(R.id.layout_not_configured, View.GONE)
                
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("navigate_to", "inicio")
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 20, intent, flags))
            } else {
                views.setViewVisibility(R.id.tv_premium_lock, View.GONE)
                
                val prefs = context.getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
                val limitStr = prefs.getString("data_plan_limit", "") ?: ""
                val unit = prefs.getString("data_plan_unit", "GB") ?: "GB"
                
                if (limitStr.isEmpty()) {
                    views.setViewVisibility(R.id.widget_content_container, View.GONE)
                    views.setViewVisibility(R.id.layout_not_configured, View.VISIBLE)
                    
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("navigate_to", "control_datos_settings")
                    }
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                    views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 21, intent, flags))
                } else {
                    views.setViewVisibility(R.id.widget_content_container, View.VISIBLE)
                    views.setViewVisibility(R.id.layout_not_configured, View.GONE)
                    
                    val limit = limitStr.toDoubleOrNull() ?: 0.0
                    
                    // Cálculo de consumo en tiempo real (Idéntico a ControlDatosScreen)
                    val pConsumedStr = prefs.getString("data_plan_consumed", "0") ?: "0"
                    val pConsumedBase = pConsumedStr.toDoubleOrNull() ?: 0.0
                    val pConsumedBaseMb = if (unit == "GB") pConsumedBase * 1024.0 else pConsumedBase
                    val pLastUpdate = prefs.getLong("data_plan_last_update_time", 0L)
                    
                    val dataUsageManager = DataUsageManager(context)
                    val deltaUsageBytes = dataUsageManager.getMobileDataUsage(pLastUpdate, System.currentTimeMillis())
                    val deltaUsageMb = deltaUsageBytes / (1024.0 * 1024.0)
                    val consumedMb = pConsumedBaseMb + deltaUsageMb
                    
                    val limitMb = if (unit == "GB") limit * 1024.0 else limit
                    
                    val progress = if (limitMb > 0) (consumedMb / limitMb * 100).toInt().coerceIn(0, 100) else 0
                    val remainingMb = (limitMb - consumedMb).coerceAtLeast(0.0)

                    views.setTextViewText(R.id.tv_plan_limit, "Cuota total: $limit $unit")
                    
                    if (unit == "GB") {
                        views.setTextViewText(R.id.tv_plan_consumed, String.format(Locale.US, "%.3f GB", consumedMb / 1024.0))
                        views.setTextViewText(R.id.tv_plan_remaining, String.format(Locale.US, "Restante: %.3f GB", remainingMb / 1024.0))
                    } else {
                        views.setTextViewText(R.id.tv_plan_consumed, String.format(Locale.US, "%.0f MB", consumedMb))
                        views.setTextViewText(R.id.tv_plan_remaining, String.format(Locale.US, "Restante: %.0f MB", remainingMb))
                    }
                    
                    // Color logic - Orange at 80%, Red at 90%
                    val accentColor: Int
                    val visibleBarId: Int
                    val hiddenBarIds = mutableListOf<Int>()
                    
                    when {
                        progress >= 90 -> {
                            accentColor = 0xFFF44336.toInt()
                            visibleBarId = R.id.progress_red
                            hiddenBarIds.add(R.id.progress_blue)
                            hiddenBarIds.add(R.id.progress_orange)
                        }
                        progress >= 80 -> {
                            accentColor = 0xFFFF9800.toInt()
                            visibleBarId = R.id.progress_orange
                            hiddenBarIds.add(R.id.progress_blue)
                            hiddenBarIds.add(R.id.progress_red)
                        }
                        else -> {
                            accentColor = 0xFF2196F3.toInt()
                            visibleBarId = R.id.progress_blue
                            hiddenBarIds.add(R.id.progress_orange)
                            hiddenBarIds.add(R.id.progress_red)
                        }
                    }
                    
                    views.setTextColor(R.id.tv_plan_title, accentColor)
                    
                    // Actualizar barra de progreso (mostrar la correcta, ocultar el resto)
                    views.setViewVisibility(visibleBarId, View.VISIBLE)
                    views.setProgressBar(visibleBarId, 100, progress, false)
                    
                    for (id in hiddenBarIds) {
                        views.setViewVisibility(id, View.GONE)
                    }
                    
                    views.setTextViewText(R.id.tv_plan_percent, "$progress%")
                    
                    // Estado del botón de actualización (animación)
                    if (isUpdating) {
                        views.setViewVisibility(R.id.tv_update_text, View.GONE)
                        views.setViewVisibility(R.id.progress_refresh, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.tv_update_text, View.VISIBLE)
                        views.setViewVisibility(R.id.progress_refresh, View.GONE)
                    }

                    // Setup update button
                    val refreshIntent = Intent(context, DataPlanWidgetProvider::class.java).apply { 
                        action = "cu.cuban.cmcubano.DATA_PLAN_REFRESH" 
                    }
                    val refreshPending = PendingIntent.getBroadcast(
                        context, 
                        23, 
                        refreshIntent, 
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPending)
                    
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    views.setTextViewText(R.id.tv_widget_last_update, "Última act: $time")

                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("navigate_to", "control_datos")
                    }
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                    views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 22, intent, flags))
                }
            }
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating data plan widget", e)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "cu.cuban.cmcubano.DATA_PLAN_REFRESH"
        private const val TAG = "DataPlanWidget"
        private const val PREFS_NAME = "data_plan_widget_prefs"
        private const val KEY_IS_UPDATING = "is_updating"
    }
}
