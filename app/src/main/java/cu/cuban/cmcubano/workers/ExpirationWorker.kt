package cu.cuban.cmcubano.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cu.cuban.cmcubano.utils.NotificationScheduler
import cu.cuban.cmcubano.NotificationReceiver
import android.content.Intent

class ExpirationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("ExpirationWorker", "Ejecutando chequeo de expiración programado...")
        
        // Disparar el chequeo a través del receiver existente para mantener consistencia
        val intent = Intent(applicationContext, NotificationReceiver::class.java).apply {
            action = "ACTION_DAILY_CHECK"
        }
        applicationContext.sendBroadcast(intent)
        
        return Result.success()
    }
}
