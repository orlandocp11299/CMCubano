package cu.cuban.cmcubano.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Gestor para configuración y monitoreo de límites de datos
 */
class DataLimitManager(private val context: Context) {
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(
        "data_limit_prefs",
        Context.MODE_PRIVATE
    )
    
    private val _dataLimit = MutableStateFlow<DataLimit>(getDefaultDataLimit())
    val dataLimit: StateFlow<DataLimit> = _dataLimit.asStateFlow()
    
    private val _alerts = MutableStateFlow<List<DataUsageAlert>>(emptyList())
    val alerts: StateFlow<List<DataUsageAlert>> = _alerts.asStateFlow()
    
    companion object {
        private const val KEY_LIMIT_BYTES = "limit_bytes"
        private const val KEY_CYCLE_START_DAY = "cycle_start_day"
        private const val KEY_WARNING_THRESHOLD = "warning_threshold"
        private const val KEY_CRITICAL_THRESHOLD = "critical_threshold"
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_DAILY_REMINDER_ENABLED = "daily_reminder_enabled"
        
        // Límite predeterminado: 5 GB
        private const val DEFAULT_LIMIT_BYTES = 5L * 1024 * 1024 * 1024
        private const val DEFAULT_CYCLE_START_DAY = 1
        private const val DEFAULT_WARNING_THRESHOLD = 0.8f
        private const val DEFAULT_CRITICAL_THRESHOLD = 0.95f
    }
    
    init {
        loadDataLimit()
        loadAlerts()
    }
    
    /**
     * Obtiene el límite de datos predeterminado
     */
    private fun getDefaultDataLimit(): DataLimit {
        return DataLimit(
            limitBytes = sharedPrefs.getLong(KEY_LIMIT_BYTES, DEFAULT_LIMIT_BYTES),
            cycleStartDay = sharedPrefs.getInt(KEY_CYCLE_START_DAY, DEFAULT_CYCLE_START_DAY),
            warningThreshold = sharedPrefs.getFloat(KEY_WARNING_THRESHOLD, DEFAULT_WARNING_THRESHOLD),
            criticalThreshold = sharedPrefs.getFloat(KEY_CRITICAL_THRESHOLD, DEFAULT_CRITICAL_THRESHOLD)
        )
    }
    
    /**
     * Carga la configuración del límite de datos
     */
    private fun loadDataLimit() {
        _dataLimit.value = getDefaultDataLimit()
    }
    
    /**
     * Guarda la configuración del límite de datos
     */
    fun saveDataLimit(dataLimit: DataLimit) {
        sharedPrefs.edit().apply {
            putLong(KEY_LIMIT_BYTES, dataLimit.limitBytes)
            putInt(KEY_CYCLE_START_DAY, dataLimit.cycleStartDay)
            putFloat(KEY_WARNING_THRESHOLD, dataLimit.warningThreshold)
            putFloat(KEY_CRITICAL_THRESHOLD, dataLimit.criticalThreshold)
            apply()
        }
        _dataLimit.value = dataLimit
    }
    
    /**
     * Establece el límite de datos en bytes
     */
    fun setDataLimit(limitBytes: Long) {
        val currentLimit = _dataLimit.value
        saveDataLimit(currentLimit.copy(limitBytes = limitBytes))
    }
    
    /**
     * Establece el día de inicio del ciclo de facturación
     */
    fun setCycleStartDay(day: Int) {
        val currentLimit = _dataLimit.value
        saveDataLimit(currentLimit.copy(cycleStartDay = day))
    }
    
    /**
     * Establece el umbral de advertencia
     */
    fun setWarningThreshold(threshold: Float) {
        val currentLimit = _dataLimit.value
        saveDataLimit(currentLimit.copy(warningThreshold = threshold))
    }
    
    /**
     * Establece el umbral crítico
     */
    fun setCriticalThreshold(threshold: Float) {
        val currentLimit = _dataLimit.value
        saveDataLimit(currentLimit.copy(criticalThreshold = threshold))
    }
    
    /**
     * Verifica si las alertas están habilitadas
     */
    fun areAlertsEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_ALERTS_ENABLED, true)
    }
    
    /**
     * Habilita o deshabilita las alertas
     */
    fun setAlertsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
    }
    
    /**
     * Verifica si los recordatorios diarios están habilitados
     */
    fun isDailyReminderEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, false)
    }
    
    /**
     * Habilita o deshabilita los recordatorios diarios
     */
    fun setDailyReminderEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_DAILY_REMINDER_ENABLED, enabled).apply()
    }
    
    /**
     * Obtiene las fechas de inicio y fin del ciclo actual
     */
    fun getCurrentCycleDates(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val cycleStartDay = _dataLimit.value.cycleStartDay
        
        // Fecha de inicio del ciclo
        val startCalendar = Calendar.getInstance()
        if (currentDay >= cycleStartDay) {
            startCalendar.set(Calendar.DAY_OF_MONTH, cycleStartDay)
        } else {
            startCalendar.add(Calendar.MONTH, -1)
            startCalendar.set(Calendar.DAY_OF_MONTH, cycleStartDay)
        }
        startCalendar.set(Calendar.HOUR_OF_DAY, 0)
        startCalendar.set(Calendar.MINUTE, 0)
        startCalendar.set(Calendar.SECOND, 0)
        startCalendar.set(Calendar.MILLISECOND, 0)
        
        // Fecha de fin del ciclo
        val endCalendar = Calendar.getInstance()
        endCalendar.timeInMillis = startCalendar.timeInMillis
        endCalendar.add(Calendar.MONTH, 1)
        endCalendar.add(Calendar.MILLISECOND, -1)
        
        return Pair(startCalendar.timeInMillis, endCalendar.timeInMillis)
    }
    
    /**
     * Verifica el uso de datos y genera alertas si es necesario
     */
    suspend fun checkDataUsage(currentUsage: Long) {
        if (!areAlertsEnabled()) return
        
        val limit = _dataLimit.value
        val currentAlerts = _alerts.value.toMutableList()
        
        // Verificar umbral crítico
        if (limit.isCriticalThresholdExceeded(currentUsage)) {
            val criticalAlert = DataUsageAlert(
                id = "critical_${System.currentTimeMillis()}",
                type = AlertType.CRITICAL,
                message = "¡Cuidado! Has alcanzado el ${String.format("%.1f", limit.getUsagePercentage(currentUsage))}% de tu límite de datos",
                timestamp = System.currentTimeMillis(),
                currentUsage = currentUsage,
                limit = limit
            )
            
            // Evitar alertas duplicadas
            if (!currentAlerts.any { it.type == AlertType.CRITICAL && !it.isRead }) {
                currentAlerts.add(0, criticalAlert)
            }
        }
        // Verificar umbral de advertencia
        else if (limit.isWarningThresholdExceeded(currentUsage)) {
            val warningAlert = DataUsageAlert(
                id = "warning_${System.currentTimeMillis()}",
                type = AlertType.WARNING,
                message = "Te estás acercando a tu límite de datos (${String.format("%.1f", limit.getUsagePercentage(currentUsage))}% usado)",
                timestamp = System.currentTimeMillis(),
                currentUsage = currentUsage,
                limit = limit
            )
            
            if (!currentAlerts.any { it.type == AlertType.WARNING && !it.isRead }) {
                currentAlerts.add(0, warningAlert)
            }
        }
        
        // Verificar si se alcanzó el límite
        if (currentUsage >= limit.limitBytes) {
            val limitAlert = DataUsageAlert(
                id = "limit_reached_${System.currentTimeMillis()}",
                type = AlertType.LIMIT_REACHED,
                message = "¡Has alcanzado tu límite de datos! Considera comprar más datos o esperar al próximo ciclo",
                timestamp = System.currentTimeMillis(),
                currentUsage = currentUsage,
                limit = limit
            )
            
            if (!currentAlerts.any { it.type == AlertType.LIMIT_REACHED && !it.isRead }) {
                currentAlerts.add(0, limitAlert)
            }
        }
        
        _alerts.value = currentAlerts.take(50) // Mantener solo las últimas 50 alertas
    }
    
    /**
     * Genera un recordatorio diario
     */
    suspend fun generateDailyReminder(currentUsage: Long) {
        if (!isDailyReminderEnabled()) return
        
        val limit = _dataLimit.value
        val currentAlerts = _alerts.value.toMutableList()
        
        val reminderAlert = DataUsageAlert(
            id = "daily_${System.currentTimeMillis()}",
            type = AlertType.DAILY_REMINDER,
            message = "Uso diario: ${formatBytes(currentUsage)} de ${formatBytes(limit.limitBytes)} (${String.format("%.1f", limit.getUsagePercentage(currentUsage))}%)",
            timestamp = System.currentTimeMillis(),
            currentUsage = currentUsage,
            limit = limit
        )
        
        currentAlerts.add(0, reminderAlert)
        _alerts.value = currentAlerts.take(50)
    }
    
    /**
     * Marca una alerta como leída
     */
    fun markAlertAsRead(alertId: String) {
        val currentAlerts = _alerts.value.toMutableList()
        val alertIndex = currentAlerts.indexOfFirst { it.id == alertId }
        if (alertIndex != -1) {
            currentAlerts[alertIndex] = currentAlerts[alertIndex].copy(isRead = true)
            _alerts.value = currentAlerts
        }
    }
    
    /**
     * Marca todas las alertas como leídas
     */
    fun markAllAlertsAsRead() {
        val currentAlerts = _alerts.value.map { it.copy(isRead = true) }
        _alerts.value = currentAlerts
    }
    
    /**
     * Elimina una alerta
     */
    fun deleteAlert(alertId: String) {
        val currentAlerts = _alerts.value.toMutableList()
        currentAlerts.removeAll { it.id == alertId }
        _alerts.value = currentAlerts
    }
    
    /**
     * Elimina todas las alertas
     */
    fun clearAllAlerts() {
        _alerts.value = emptyList()
    }
    
    /**
     * Obtiene las alertas no leídas
     */
    fun getUnreadAlerts(): List<DataUsageAlert> {
        return _alerts.value.filter { !it.isRead }
    }
    
    /**
     * Obtiene las alertas por tipo
     */
    fun getAlertsByType(type: AlertType): List<DataUsageAlert> {
        return _alerts.value.filter { it.type == type }
    }
    
    /**
     * Carga las alertas guardadas (implementación básica)
     */
    private fun loadAlerts() {
        // En una implementación completa, esto cargaría desde SharedPreferences o base de datos
        // Por ahora, iniciamos con lista vacía
        _alerts.value = emptyList()
    }
    
    /**
     * Obtiene estadísticas del ciclo actual
     */
    fun getCycleStats(currentUsage: Long): CycleStats {
        val limit = _dataLimit.value
        val (startTime, endTime) = getCurrentCycleDates()
        val currentTime = System.currentTimeMillis()
        
        val cycleDuration = endTime - startTime
        val elapsed = currentTime - startTime
        val remaining = endTime - currentTime
        
        val daysInCycle = (cycleDuration / (24 * 60 * 60 * 1000)).toInt()
        val daysElapsed = (elapsed / (24 * 60 * 60 * 1000)).toInt()
        val daysRemaining = (remaining / (24 * 60 * 60 * 1000)).toInt()
        
        return CycleStats(
            startTime = startTime,
            endTime = endTime,
            currentTime = currentTime,
            currentUsage = currentUsage,
            limit = limit,
            daysInCycle = daysInCycle,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            usagePercentage = limit.getUsagePercentage(currentUsage),
            timePercentage = if (cycleDuration > 0) (elapsed.toFloat() / cycleDuration.toFloat()) * 100f else 0f
        )
    }
    
    /**
     * Predice el uso final del ciclo basado en el consumo actual
     */
    fun predictEndOfCycleUsage(currentUsage: Long): PredictedUsage {
        val stats = getCycleStats(currentUsage)
        
        val dailyAverage = if (stats.daysElapsed > 0) currentUsage / stats.daysElapsed else 0L
        val predictedUsage = dailyAverage * stats.daysInCycle
        val predictedPercentage = stats.limit.getUsagePercentage(predictedUsage)
        
        return PredictedUsage(
            dailyAverage = dailyAverage,
            predictedEndOfCycleUsage = predictedUsage,
            predictedPercentage = predictedPercentage,
            willExceedLimit = predictedUsage > stats.limit.limitBytes,
            excessAmount = (predictedUsage - stats.limit.limitBytes).coerceAtLeast(0L)
        )
    }
}

/**
 * Clase de datos para estadísticas del ciclo
 */
data class CycleStats(
    val startTime: Long,
    val endTime: Long,
    val currentTime: Long,
    val currentUsage: Long,
    val limit: DataLimit,
    val daysInCycle: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val usagePercentage: Float,
    val timePercentage: Float
)

/**
 * Clase de datos para predicción de uso
 */
data class PredictedUsage(
    val dailyAverage: Long,
    val predictedEndOfCycleUsage: Long,
    val predictedPercentage: Float,
    val willExceedLimit: Boolean,
    val excessAmount: Long
)

/**
 * Función auxiliar para formatear bytes
 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
