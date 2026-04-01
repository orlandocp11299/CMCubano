package cu.cuban.cmcubano.services

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cu.cuban.cmcubano.R
import cu.cuban.cmcubano.utils.DataUsageManager
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.abs
import cu.cuban.cmcubano.utils.VibrationManager
import cu.cuban.cmcubano.utils.PremiumManager

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var ivAppIcon: ImageView? = null
    private var tvAppUsage: TextView? = null
    private var ivTotalIcon: ImageView? = null
    private var tvDailyUsage: TextView? = null
    private var ivSpeedIcon: ImageView? = null
    private var tvSpeed: TextView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dataUsageManager: DataUsageManager
    private lateinit var usageStatsManager: UsageStatsManager
    
    private var lastTotalRx: Long = 0
    private var lastTotalTx: Long = 0
    private var lastUpdateTime: Long = 0
    private var lastPackageName: String? = null
    
    // Default to no decimals
    private var decimalFormatStr = "%.0f"
    // Default to mobile
    private var dataSource = "mobile"

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateData()
            handler.postDelayed(this, 1000)
        }
    }

    private lateinit var connectivityManager: ConnectivityManager
    private var hideWhenOffline = false
    private var isNetworkAvailable = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isNetworkAvailable = true
            handler.post { updateVisibility() }
        }

        override fun onLost(network: Network) {
            // Check if there are other networks available
            isNetworkAvailable = checkNetworkAvailability()
            handler.post { updateVisibility() }
        }
    }

    private fun checkNetworkAvailability(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateVisibility() {
        floatingView?.let { view ->
            if (hideWhenOffline && !isNetworkAvailable) {
                view.visibility = View.GONE
            } else {
                view.visibility = View.VISIBLE
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        dataUsageManager = DataUsageManager(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        updatePreferences()
        
        lastTotalRx = TrafficStats.getTotalRxBytes()
        lastTotalTx = TrafficStats.getTotalTxBytes()
        lastUpdateTime = System.currentTimeMillis()
        
        createFloatingBubble()
        
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        isNetworkAvailable = checkNetworkAvailability()
        updateVisibility()

        startForegroundService()
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updatePreferences()
        return START_STICKY
    }

    private fun updatePreferences() {
        val prefs = getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
        decimalFormatStr = prefs.getString("decimal_format", "%.0f") ?: "%.0f"
        dataSource = prefs.getString("bubble_data_source", "auto") ?: "auto"
        hideWhenOffline = prefs.getBoolean("bubble_hide_offline", false)
        
        // Force update UI and visibility immediately if view exists
        if (floatingView != null) {
            updateData()
            updateVisibility()
        }
    }

    private fun startForegroundService() {
        // --- Premium Trial Logic ---
        serviceScope.launch {
            if (!PremiumManager.isPremium(applicationContext)) {
                delay(180_000) // 3 minutes trial
                if (isActive && !PremiumManager.isPremium(applicationContext)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext, 
                            "Prueba de Burbuja finalizada. Activa Premium para uso ilimitado.", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    // Disable preference
                    val prefs = getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("floating_bubble_enabled", false).apply()
                    
                    stopSelf()
                }
            }
        }

        val channelId = "floating_bubble_channel"
        // ... rest of method
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Burbuja Flotante",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Burbuja Flotante")
            .setContentText("Calculando consumo de datos...")
            .setSmallIcon(R.drawable.outline_bubble_chart_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private var removeView: ImageView? = null
    private var removeParams: WindowManager.LayoutParams? = null
    private var isOverRemoveView = false

    private fun createFloatingBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // --- Remove View (The removal zone at bottom) ---
        removeView = ImageView(this).apply {
            setImageResource(R.drawable.ic_trash_vector)
            setColorFilter(android.graphics.Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = android.graphics.drawable.GradientDrawable().apply {
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.parseColor("#B3D32F2F") // Material Red 700 semi-transparent
                )
            }
            visibility = View.GONE
            alpha = 0.8f
            elevation = 10f
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        removeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            280, // Height
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0 // Exactly at bottom
        }

        windowManager.addView(removeView, removeParams)

        // --- Main Bubble Layout ---
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#CC000000")) // Slightly darker background
                cornerRadius = 30f
                setStroke(2, android.graphics.Color.parseColor("#44FFFFFF"))
            }
            background = shape
            setPadding(24, 16, 24, 16)
            elevation = 10f
        }
        
        floatingView = root
        
        // --- Line 1: App Usage (Icon + Text) ---
        val appLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        
        ivAppIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(52, 52).apply { marginEnd = 12 }
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Default icon initially
            setImageResource(android.R.drawable.sym_def_app_icon)
        }
        
        tvAppUsage = TextView(this).apply {
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            text = "Cargando..."
        }
        
        appLayout.addView(ivAppIcon)
        appLayout.addView(tvAppUsage)
        root.addView(appLayout)
        
        // --- Line 2: Daily Usage (Icon + Text) ---
        val dailyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        }
        
        ivTotalIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 12 }
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Use custom drawable or system fallback
            try {
                setImageResource(R.drawable.ic_data_usage_bubble)
            } catch (e: Exception) {
                setImageResource(android.R.drawable.stat_sys_download)
            }
            setColorFilter(android.graphics.Color.LTGRAY)
        }
        
        tvDailyUsage = TextView(this).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#EEEEEE"))
            text = "Total Hoy: -- MB"
        }
        
        dailyLayout.addView(ivTotalIcon)
        dailyLayout.addView(tvDailyUsage)
        root.addView(dailyLayout)
        
        // --- Line 3: Network Speed (Icon + Text) ---
        val speedLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        ivSpeedIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { marginEnd = 12 }
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                setImageResource(R.drawable.ic_speed_bubble)
            } catch (e: Exception) {
                // Fallback
                setImageResource(android.R.drawable.stat_notify_sync)
            }
            setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
        }

        tvSpeed = TextView(this).apply {
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Green
            text = "0.0 KB/s"
        }
        
        speedLayout.addView(ivSpeedIcon)
        speedLayout.addView(tvSpeed)
        root.addView(speedLayout)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Add Global Layout Listener for boundary and keyboard check
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            root.getWindowVisibleDisplayFrame(rect)
            
            val usableWidth = rect.width()
            val usableHeight = rect.bottom 
            
            params?.let { p ->
                val viewWidth = root.width
                val viewHeight = root.height
                
                if (viewWidth > 0 && viewHeight > 0) {
                    var changed = false
                    
                    // Clamp X
                    val maxX = usableWidth - viewWidth
                    if (p.x > maxX) {
                        p.x = maxX.coerceAtLeast(0)
                        changed = true
                    } else if (p.x < 0) {
                        p.x = 0
                        changed = true
                    }
                    
                    // Clamp Y (respecting usableHeight which changes with keyboard)
                    val maxY = usableHeight - viewHeight
                    if (p.y > maxY) {
                        p.y = maxY.coerceAtLeast(0)
                        changed = true
                    } else if (p.y < 0) {
                        p.y = 0
                        changed = true
                    }
                    
                    if (changed) {
                        try {
                            windowManager.updateViewLayout(root, p)
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                }
            }
        }

        windowManager.addView(floatingView, params)

        setupTouchListener()
    }

    private fun setupTouchListener() {
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            
            // Screen dimensions for collision detection
            private val displayMetrics = android.util.DisplayMetrics()

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                val screenHeight = displayMetrics.heightPixels
                val screenWidth = displayMetrics.widthPixels

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        
                        // Show Remove View
                        removeView?.visibility = View.VISIBLE
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val rect = android.graphics.Rect()
                        v.getWindowVisibleDisplayFrame(rect)
                        
                        var newX = initialX + (event.rawX - initialTouchX).toInt()
                        var newY = initialY + (event.rawY - initialTouchY).toInt()
                        
                        // Limit to usable area
                        val maxX = displayMetrics.widthPixels - v.width
                        val maxY = rect.bottom - v.height
                        
                        newX = newX.coerceIn(0, maxX.coerceAtLeast(0))
                        newY = newY.coerceIn(0, maxY.coerceAtLeast(0))
                        
                        params!!.x = newX
                        params!!.y = newY
                        windowManager.updateViewLayout(floatingView, params)

                        // Check collision with the bottom removal zone
                        val zoneHeight = removeParams?.height ?: 280
                        val zoneTop = rect.bottom - zoneHeight
                        val bubbleCenterY = newY + (v.height / 2)
                        
                        if (bubbleCenterY > zoneTop) {
                            if (!isOverRemoveView) {
                                isOverRemoveView = true
                                // Feedback: more alpha and slight scale up
                                removeView?.animate()?.alpha(1.0f)?.scaleY(1.2f)?.setDuration(200)?.start()
                                VibrationManager.vibrate(this@FloatingBubbleService)
                            }
                        } else {
                            if (isOverRemoveView) {
                                isOverRemoveView = false
                                removeView?.animate()?.alpha(0.8f)?.scaleY(1.0f)?.setDuration(200)?.start()
                            }
                        }
                        
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        removeView?.visibility = View.GONE
                        removeView?.scaleX = 1.0f
                        removeView?.scaleY = 1.0f
                        removeView?.alpha = 0.8f

                        if (isOverRemoveView) {
                            // STOP SERVICE AND DISABLE
                            val prefs = getSharedPreferences("control_datos_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("floating_bubble_enabled", false).apply()
                            stopSelf()
                        }
                        isOverRemoveView = false
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateData() {
        if (floatingView == null) return
        
        val now = System.currentTimeMillis()
        val timeDiff = (now - lastUpdateTime) / 1000.0
        if (timeDiff <= 0) return

        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        
        val rxSpeed = (currentRx - lastTotalRx) / timeDiff
        val txSpeed = (currentTx - lastTotalTx) / timeDiff
        val totalSpeed = rxSpeed + txSpeed
        
        lastTotalRx = currentRx
        lastTotalTx = currentTx
        lastUpdateTime = now

        serviceScope.launch {
            try {
                // Get foreground package usage
                val foregroundPackage = withContext(Dispatchers.Default) { getForegroundPackage() }
                
                // Period: Today (Start of day to now)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis

                // Run heavy DB/Stats queries in IO
                val appData = withContext(Dispatchers.IO) {
                    val activeNetwork = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    val isWifiActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    val isMobileActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

                    val effectiveSource = if (dataSource == "auto") {
                        when {
                            isWifiActive -> "wifi"
                            isMobileActive -> "mobile"
                            else -> "mobile" // Fallback
                        }
                    } else dataSource

                    // Calculate Total Usage based on source
                    val totalUsage = when (effectiveSource) {
                        "wifi" -> dataUsageManager.getWifiDataUsage(startOfDay, now)
                        "both" -> dataUsageManager.getMobileDataUsage(startOfDay, now) + dataUsageManager.getWifiDataUsage(startOfDay, now)
                        else -> dataUsageManager.getMobileDataUsage(startOfDay, now)
                    }
                    
                    var appUsage = "0 B"
                    var appIconDrawable: Drawable? = null
                    var currentPackage = foregroundPackage
                    
                    if (currentPackage != null) {
                        // Get Icon
                        try {
                            appIconDrawable = packageManager.getApplicationIcon(currentPackage)
                        } catch (e: Exception) {
                            // Keep null
                        }

                        val appStats = dataUsageManager.getAppDataUsage(startOfDay, now)
                        val targetApp = appStats.find { it.packageName == currentPackage }
                        
                        if (targetApp != null) {
                            val bytes = when (effectiveSource) {
                                "wifi" -> targetApp.wifiBytes
                                "both" -> targetApp.mobileBytes + targetApp.wifiBytes
                                else -> targetApp.mobileBytes
                            }
                            appUsage = formatBytes(bytes)
                        } else {
                            appUsage = "0 B"
                        }
                    }
                    
                    val iconRes = when (effectiveSource) {
                        "wifi" -> R.drawable.ic_wifi_vector
                        "mobile" -> R.drawable.ic_mobile_data_vector
                        else -> R.drawable.ic_data_usage_bubble
                    }
                    
                    Quad(appUsage, totalUsage, appIconDrawable, iconRes)
                }

                val (appUsage, totalUsage, appIcon, totalIconRes) = appData

                // Update UI on Main Thread
                tvAppUsage?.text = appUsage // Only the value
                
                if (foregroundPackage != null && foregroundPackage != lastPackageName) {
                     if (appIcon != null) {
                        ivAppIcon?.setImageDrawable(appIcon)
                    } else {
                        ivAppIcon?.setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                    lastPackageName = foregroundPackage
                } else if (lastPackageName == null) {
                     ivAppIcon?.setImageResource(android.R.drawable.sym_def_app_icon)
                }
                
                // Update Total Usage Icon based on Result from IO
                try {
                     ivTotalIcon?.setImageResource(totalIconRes)
                } catch(e: Exception) {
                     ivTotalIcon?.setImageResource(android.R.drawable.stat_sys_download)
                }
                
                tvDailyUsage?.text = formatBytes(totalUsage)
                tvSpeed?.text = formatSpeed(totalSpeed)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getForegroundPackage(): String? {
        val time = System.currentTimeMillis()
        // Query events for the last hour to be safe. "Activity" moves are distinct from "Service" usage.
        val events = usageStatsManager.queryEvents(time - 1000 * 3600, time)
        var lastForegroundApp: String? = null
        val event = android.app.usage.UsageEvents.Event()
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "$decimalFormatStr KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "$decimalFormatStr MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.getDefault(), "$decimalFormatStr GB", gb)
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        if (bytesPerSecond < 1024) return String.format("%.0f B/s", bytesPerSecond)
        val kbps = bytesPerSecond / 1024.0
        if (kbps < 1024) return String.format(Locale.getDefault(), "$decimalFormatStr KB/s", kbps)
        val mbps = kbps / 1024.0
        return String.format(Locale.getDefault(), "$decimalFormatStr MB/s", mbps)
    }

    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        handler.removeCallbacks(updateRunnable)
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Callback might not be registered
        }
        
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }
        if (removeView != null) {
            windowManager.removeView(removeView)
        }
    }
}
