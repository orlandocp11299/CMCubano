package cu.cuban.cmcubano

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.view.View
import cu.cuban.cmcubano.utils.DataUsageManager
import cu.cuban.cmcubano.utils.PremiumManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DataUsageWidgetProvider : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        private const val ACTION_REFRESH = "cu.cuban.cmcubano.DATA_USAGE_REFRESH"
        private const val ACTION_NEXT_PERIOD = "cu.cuban.cmcubano.DATA_USAGE_NEXT"
        private const val ACTION_PREV_PERIOD = "cu.cuban.cmcubano.DATA_USAGE_PREV"
        
        private const val PREFS_NAME = "data_usage_widget_prefs"
        private const val KEY_PERIOD_INDEX = "period_index"
        private const val KEY_IS_UPDATING = "is_updating"
        private val PERIODS = listOf("Hoy", "Ayer", "Semana", "Mes")
        private const val TAG = "DataUsageWidget"
    }

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
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var currentIndex = prefs.getInt(KEY_PERIOD_INDEX, 0)

                when (intent.action) {
                    ACTION_REFRESH -> {
                        Log.d(TAG, "Manual refresh requested")
                        setUpdatingState(context, true)
                        
                        // Pequeño delay para la animación
                        delay(800)
                        
                        updateAllWidgets(context)
                        setUpdatingState(context, false)
                    }
                    ACTION_NEXT_PERIOD -> {
                        currentIndex = (currentIndex + 1) % PERIODS.size
                        prefs.edit().putInt(KEY_PERIOD_INDEX, currentIndex).apply()
                        updateAllWidgets(context)
                    }
                    ACTION_PREV_PERIOD -> {
                        currentIndex = if (currentIndex > 0) currentIndex - 1 else PERIODS.size - 1
                        prefs.edit().putInt(KEY_PERIOD_INDEX, currentIndex).apply()
                        updateAllWidgets(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
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
        val componentName = ComponentName(context, DataUsageWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (appWidgetId in appWidgetIds) {
            updateWidgetData(context, appWidgetManager, appWidgetId)
        }
    }

    private suspend fun updateWidgetData(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val periodIndex = prefs.getInt(KEY_PERIOD_INDEX, 0)
            val isUpdating = prefs.getBoolean(KEY_IS_UPDATING, false)
            val periodName = PERIODS[periodIndex]

            val dataUsageManager = DataUsageManager(context)
            val range = calculateTimeRange(periodName)
            
            val mobile = dataUsageManager.getMobileDataUsage(range.first, range.second)
            val wifi = dataUsageManager.getWifiDataUsage(range.first, range.second)
            
            withContext(Dispatchers.Main) {
                val views = RemoteViews(context.packageName, R.layout.data_usage_widget)
                
                views.setTextViewText(R.id.tv_current_period, periodName.uppercase())
                
                // Formatear y separar Móvil
                val mobileFormatted = formatBytes(mobile).split(" ")
                views.setTextViewText(R.id.tv_widget_mobile_val, mobileFormatted[0])
                views.setTextViewText(R.id.tv_widget_mobile_unit, mobileFormatted[1])
                
                // Formatear y separar WiFi
                val wifiFormatted = formatBytes(wifi).split(" ")
                views.setTextViewText(R.id.tv_widget_wifi_val, wifiFormatted[0])
                views.setTextViewText(R.id.tv_widget_wifi_unit, wifiFormatted[1])
                
                // Formatear y separar Total
                val totalFormatted = formatBytes(mobile + wifi).split(" ")
                views.setTextViewText(R.id.tv_widget_total_val, totalFormatted[0])
                views.setTextViewText(R.id.tv_widget_total_unit, totalFormatted[1])
                
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                views.setTextViewText(R.id.tv_widget_last_update, "Última act: $time")

                // Lógica de Bloqueo Premium
                val isPremium = PremiumManager.isPremium(context)
                val trialPrefs = context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE)
                val trialUsed = trialPrefs.getBoolean("control_datos_trial_used", false)
                val trialEndTime = trialPrefs.getLong("control_datos_trial_end_time", 0L)
                val isTrialActive = trialUsed && System.currentTimeMillis() < trialEndTime
                val isLocked = !isPremium && !isTrialActive

                if (isLocked) {
                    views.setViewVisibility(R.id.tv_premium_lock, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_content_container, View.GONE)
                } else {
                    views.setViewVisibility(R.id.tv_premium_lock, View.GONE)
                    views.setViewVisibility(R.id.widget_content_container, View.VISIBLE)
                }

                // Estado del botón de actualización (animación)
                if (isUpdating) {
                    views.setViewVisibility(R.id.tv_update_text, View.GONE)
                    views.setViewVisibility(R.id.progress_refresh, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.tv_update_text, View.VISIBLE)
                    views.setViewVisibility(R.id.progress_refresh, View.GONE)
                }

                // Setup PendingIntents
                setupIntents(context, views, isLocked)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget", e)
        }
    }

    private fun setupIntents(context: Context, views: RemoteViews, isLocked: Boolean) {
        val refreshIntent = Intent(context, DataUsageWidgetProvider::class.java).apply { action = ACTION_REFRESH }
        val nextIntent = Intent(context, DataUsageWidgetProvider::class.java).apply { action = ACTION_NEXT_PERIOD }
        val prevIntent = Intent(context, DataUsageWidgetProvider::class.java).apply { action = ACTION_PREV_PERIOD }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT

        views.setOnClickPendingIntent(R.id.btn_widget_refresh, PendingIntent.getBroadcast(context, 10, refreshIntent, flags))
        views.setOnClickPendingIntent(R.id.btn_next_period, PendingIntent.getBroadcast(context, 11, nextIntent, flags))
        views.setOnClickPendingIntent(R.id.btn_prev_period, PendingIntent.getBroadcast(context, 12, prevIntent, flags))
        
        // Clic en el cuerpo del widget abre la app
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            if (!isLocked) {
                putExtra("navigate_to", "control_datos")
            } else {
                putExtra("navigate_to", "inicio")
            }
        }
        val mainPendingIntent = PendingIntent.getActivity(context, 13, mainIntent, flags)
        views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
    }

    private fun calculateTimeRange(period: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        return when (period) {
            "Hoy" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            "Ayer" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(start, calendar.timeInMillis)
            }
            "Semana" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                if (calendar.timeInMillis > now) {
                    calendar.add(Calendar.WEEK_OF_YEAR, -1)
                }
                Pair(calendar.timeInMillis, now)
            }
            "Mes" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            else -> Pair(now - 86400000, now)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB")
        val unit = units[exp - 1]
        val value = bytes / Math.pow(1024.0, exp.toDouble())
        return String.format("%.1f %s", value, unit)
    }
}
