package cu.cuban.cmcubano.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import cu.cuban.cmcubano.NotificationReceiver
import cu.cuban.cmcubano.workers.ExpirationWorker
import java.util.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val TAG = "NotificationScheduler"
    private const val REQUEST_CODE = 1001
    private const val WORK_NAME = "expiration_check_work"

    fun scheduleDailyCheck(context: Context) {
        // 1. Programar con AlarmManager (Alta precisión si es posible)
        scheduleAlarm(context)
        
        // 2. Programar con WorkManager (Alta persistencia ante reinicios y ahorro de batería)
        scheduleWork(context)
    }

    private fun scheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "ACTION_DAILY_CHECK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
            Log.d(TAG, "Alarma (AlarmManager) programada para: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al programar AlarmManager: ${e.message}")
        }
    }

    private fun scheduleWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        // Creamos una tarea periódica cada 12 horas para asegurar que el chequeo ocurra
        // aunque el AlarmManager falle o el sistema mate la app.
        val workRequest = PeriodicWorkRequestBuilder<ExpirationWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d(TAG, "Tarea (WorkManager) encolada (Cada 12h)")
    }

    fun triggerImmediateCheck(context: Context) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "ACTION_DAILY_CHECK"
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Chequeo inmediato disparado")
    }

    fun cancelAll(context: Context) {
        // Cancelar Alarma
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }

        // Cancelar Work
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "Todos los chequeos cancelados")
    }
}
