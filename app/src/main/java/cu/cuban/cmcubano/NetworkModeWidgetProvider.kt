package cu.cuban.cmcubano

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import cu.cuban.cmcubano.utils.NetworkUtils
import cu.cuban.cmcubano.utils.VibrationManager

class NetworkModeWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_OPEN_NETWORK_MODE = "cu.cuban.cmcubano.ACTION_OPEN_NETWORK_MODE"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_OPEN_NETWORK_MODE) {
            VibrationManager.vibrate(context)
            NetworkUtils.openRadioInfo(context)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.network_mode_widget)

        val intent = Intent(context, NetworkModeWidgetProvider::class.java).apply {
            action = ACTION_OPEN_NETWORK_MODE
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            0, 
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
