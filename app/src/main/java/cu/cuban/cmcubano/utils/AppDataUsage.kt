package cu.cuban.cmcubano.utils

/**
 * Clase de datos que representa el uso de datos de una aplicación
 */
data class AppDataUsage(
    val appName: String,
    val packageName: String,
    val uid: Int,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val packetsSent: Long,
    val packetsReceived: Long
) {
    /**
     * Obtiene el total de bytes usados (móviles + WiFi)
     */
    val totalBytes: Long
        get() = mobileBytes + wifiBytes
    
    /**
     * Obtiene el total de paquetes (enviados + recibidos)
     */
    val totalPackets: Long
        get() = packetsSent + packetsReceived
    
    /**
     * Obtiene el porcentaje de uso en primer plano
     */
    val foregroundPercentage: Float
        get() = if (totalBytes > 0) foregroundBytes.toFloat() / totalBytes.toFloat() else 0f
    
    /**
     * Obtiene el porcentaje de uso en segundo plano
     */
    val backgroundPercentage: Float
        get() = if (totalBytes > 0) backgroundBytes.toFloat() / totalBytes.toFloat() else 0f
    
    /**
     * Obtiene el porcentaje de uso de datos móviles respecto al total
     */
    val mobilePercentage: Float
        get() = if (totalBytes > 0) mobileBytes.toFloat() / totalBytes.toFloat() else 0f
    
    /**
     * Obtiene el porcentaje de uso de WiFi respecto al total
     */
    val wifiPercentage: Float
        get() = if (totalBytes > 0) wifiBytes.toFloat() / totalBytes.toFloat() else 0f
    
    /**
     * Determina si la aplicación es una aplicación de streaming de video
     */
    val isVideoStreaming: Boolean
        get() {
            val streamingApps = setOf(
                "com.netflix.mediaclient",
                "com.youtube.android",
                "com.amazon.avod.thirdpartyclient",
                "com.disney.disneyplus",
                "com.hbo.hbonow",
                "com.hulu.livingroomplus",
                "com.spotify.tv.android",
                "com.vimeo.android.videoapp",
                "com.twitch.android.app",
                "com.facebook.katana",
                "com.instagram.android",
                "com.tiktok",
                "com.zhiliaoapp.musically",
                "com.twitter.android",
                "com.snapchat.android"
            )
            return streamingApps.contains(packageName)
        }
    
    /**
     * Determina si la aplicación es una aplicación de redes sociales
     */
    val isSocialMedia: Boolean
        get() {
            val socialApps = setOf(
                "com.facebook.katana",
                "com.instagram.android",
                "com.twitter.android",
                "com.snapchat.android",
                "com.tiktok",
                "com.zhiliaoapp.musically",
                "com.linkedin.android",
                "com.pinterest",
                "com.whatsapp",
                "com.facebook.orca",
                "com.discord",
                "com.telegram.messenger",
                "org.thoughtcrime.securesms",
                "com.viber.voip",
                "com.kik.chat",
                "com.linecorp.line"
            )
            return socialApps.contains(packageName)
        }
    
    /**
     * Determina si la aplicación es una aplicación de juegos
     */
    val isGaming: Boolean
        get() {
            val gamingApps = setOf(
                "com.king.candycrushsaga",
                "com.supercell.clashofclans",
                "com.supercell.clashroyale",
                "com.roblox.client",
                "com.ea.gp.fifamobile",
                "com.gameloft.android.ANMP.GloftA8HM",
                "com.miniclip.8ballpool",
                "com.outfit7.talkingtom",
                "com.king.candycrushsodasaga",
                "com.playrix.gardenscapes",
                "com.playrix.homescapes",
                "com.playrix.township",
                "com.zeptolab.ctr.ads",
                "com.king.farmheroessuper",
                "com.mojang.minecraftearth"
            )
            return gamingApps.contains(packageName)
        }
    
    /**
     * Determina si la aplicación es una aplicación de productividad
     */
    val isProductivity: Boolean
        get() {
            val productivityApps = setOf(
                "com.google.android.apps.docs.editors.docs",
                "com.google.android.apps.docs.editors.sheets",
                "com.google.android.apps.docs.editors.slides",
                "com.microsoft.office.word",
                "com.microsoft.office.excel",
                "com.microsoft.office.powerpoint",
                "com.microsoft.office.outlook",
                "com.google.android.gm",
                "com.google.android.apps.gmail",
                "com.slack",
                "com.microsoft.teams",
                "us.zoom.videomeetings",
                "com.google.android.apps.meetings",
                "com.dropbox.android",
                "com.google.android.apps.drive",
                "com.evernote",
                "com.notion.android",
                "com.todoist",
                "com.trello"
            )
            return productivityApps.contains(packageName)
        }
    
    /**
     * Obtiene la categoría de la aplicación
     */
    val category: AppCategory
        get() = when {
            isVideoStreaming -> AppCategory.VIDEO_STREAMING
            isSocialMedia -> AppCategory.SOCIAL_MEDIA
            isGaming -> AppCategory.GAMING
            isProductivity -> AppCategory.PRODUCTIVITY
            else -> AppCategory.OTHER
        }
    
    /**
     * Obtiene una descripción del tipo de aplicación
     */
    val categoryDescription: String
        get() = when (category) {
            AppCategory.VIDEO_STREAMING -> "Streaming de Video"
            AppCategory.SOCIAL_MEDIA -> "Redes Sociales"
            AppCategory.GAMING -> "Juegos"
            AppCategory.PRODUCTIVITY -> "Productividad"
            AppCategory.OTHER -> "Otra"
        }
}

/**
 * Enumeración para categorizar las aplicaciones
 */
enum class AppCategory {
    VIDEO_STREAMING,
    SOCIAL_MEDIA,
    GAMING,
    PRODUCTIVITY,
    OTHER
}

/**
 * Clase de datos para representar estadísticas de uso de datos agrupadas
 */
data class DataUsageStats(
    val totalMobileBytes: Long,
    val totalWifiBytes: Long,
    val totalBytes: Long,
    val appCount: Int,
    val topApps: List<AppDataUsage>,
    val categoryStats: Map<AppCategory, Long>
) {
    /**
     * Obtiene el porcentaje de uso de datos móviles
     */
    val mobilePercentage: Float
        get() = if (totalBytes > 0) totalMobileBytes.toFloat() / totalBytes.toFloat() else 0f
    
    /**
     * Obtiene el porcentaje de uso de WiFi
     */
    val wifiPercentage: Float
        get() = if (totalBytes > 0) totalWifiBytes.toFloat() / totalBytes.toFloat() else 0f
    
    /**
     * Obtiene la categoría que más datos consume
     */
    val topCategory: AppCategory?
        get() = categoryStats.maxByOrNull { it.value }?.key
}

/**
 * Clase de datos para representar límites de datos
 */
data class DataLimit(
    val limitBytes: Long,
    val cycleStartDay: Int = 1, // Día del mes en que inicia el ciclo
    val warningThreshold: Float = 0.8f, // 80% para advertencia
    val criticalThreshold: Float = 0.95f // 95% para crítico
) {
    /**
     * Verifica si el uso actual está por encima del umbral de advertencia
     */
    fun isWarningThresholdExceeded(currentUsage: Long): Boolean {
        return currentUsage >= (limitBytes * warningThreshold)
    }
    
    /**
     * Verifica si el uso actual está por encima del umbral crítico
     */
    fun isCriticalThresholdExceeded(currentUsage: Long): Boolean {
        return currentUsage >= (limitBytes * criticalThreshold)
    }
    
    /**
     * Obtiene el porcentaje de uso actual
     */
    fun getUsagePercentage(currentUsage: Long): Float {
        return if (limitBytes > 0) (currentUsage.toFloat() / limitBytes.toFloat()) * 100f else 0f
    }
    
    /**
     * Obtiene los bytes restantes
     */
    fun getRemainingBytes(currentUsage: Long): Long {
        return (limitBytes - currentUsage).coerceAtLeast(0L)
    }
}

/**
 * Clase de datos para representar alertas de uso de datos
 */
data class DataUsageAlert(
    val id: String,
    val type: AlertType,
    val message: String,
    val timestamp: Long,
    val currentUsage: Long,
    val limit: DataLimit,
    val isRead: Boolean = false
) {
    /**
     * Obtiene el nivel de severidad de la alerta
     */
    val severity: AlertSeverity
        get() = when (type) {
            AlertType.WARNING -> AlertSeverity.MEDIUM
            AlertType.CRITICAL -> AlertSeverity.HIGH
            AlertType.LIMIT_REACHED -> AlertSeverity.CRITICAL
            AlertType.DAILY_REMINDER -> AlertSeverity.LOW
        }
}

/**
 * Enumeración para tipos de alertas
 */
enum class AlertType {
    WARNING,
    CRITICAL,
    LIMIT_REACHED,
    DAILY_REMINDER
}

/**
 * Enumeración para niveles de severidad
 */
enum class AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
