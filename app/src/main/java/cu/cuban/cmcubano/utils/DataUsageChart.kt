package cu.cuban.cmcubano.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Componente de gráfico de pastel para mostrar distribución de uso de datos
 */
@Composable
fun DataUsagePieChart(
    data: Map<String, Long>,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color(0xFF2196F3), // Azul
        Color(0xFF4CAF50), // Verde
        Color(0xFFFF9800), // Naranja
        Color(0xFF9C27B0), // Púrpura
        Color(0xFFF44336), // Rojo
        Color(0xFF00BCD4), // Cian
        Color(0xFFFFEB3B), // Amarillo
        Color(0xFF795548)  // Marrón
    )
) {
    val totalBytes = data.values.sum()
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic)
    )
    
    Canvas(
        modifier = modifier
            .size(200.dp)
            .padding(16.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2
        val radius = kotlin.math.min(canvasWidth, canvasHeight) / 2 - 20
        
        var startAngle = -90f
        
        data.entries.forEachIndexed { index, (label, bytes) ->
            val percentage = if (totalBytes > 0) bytes.toFloat() / totalBytes.toFloat() else 0f
            val sweepAngle = (percentage * 360f) * animatedProgress
            
            // Dibujar segmento del pastel
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(centerX - radius, centerY - radius)
            )
            
            startAngle += sweepAngle
        }
        
        // Dibujar círculo central para efecto de donut
        drawCircle(
            color = Color.White,
            radius = radius * 0.6f,
            center = Offset(centerX, centerY)
        )
        
        // Dibujar texto central
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 32f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        
        drawContext.canvas.nativeCanvas.drawText(
            formatBytes(totalBytes),
            centerX,
            centerY + 10,
            paint
        )
    }
}

/**
 * Componente de gráfico de barras para mostrar uso de datos por aplicación
 */
@Composable
fun DataUsageBarChart(
    data: List<AppDataUsage>,
    modifier: Modifier = Modifier,
    maxItems: Int = 5
) {
    val topApps = data.take(maxItems)
    val maxBytes = topApps.maxOfOrNull { it.totalBytes } ?: 0L
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        topApps.forEachIndexed { index, appData ->
            val percentage = if (maxBytes > 0) appData.totalBytes.toFloat() / maxBytes.toFloat() else 0f
            val animatedPercentage by animateFloatAsState(
                targetValue = percentage,
                animationSpec = tween(
                    durationMillis = 800,
                    delayMillis = index * 100,
                    easing = EaseOutCubic
                )
            )
            
            AppUsageBar(
                appName = appData.appName,
                usage = appData.totalBytes,
                percentage = animatedPercentage,
                color = getAppCategoryColor(appData.category)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Componente individual para barra de uso de aplicación
 */
@Composable
fun AppUsageBar(
    appName: String,
    usage: Long,
    percentage: Float,
    color: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = formatBytes(usage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.8f),
                                color
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Componente de gráfico de líneas para mostrar uso de datos en el tiempo
 */
@Composable
fun DataUsageLineChart(
    data: List<DailyDataUsage>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF2196F3)
) {
    if (data.isEmpty()) return
    
    val maxBytes = data.maxOfOrNull { it.totalBytes } ?: 0L
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic)
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val padding = 20f
        
        // Dibujar línea base
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(padding, canvasHeight - padding),
            end = Offset(canvasWidth - padding, canvasHeight - padding),
            strokeWidth = 1f
        )
        
        // Crear ruta para la línea de datos
        val linePath = Path().apply {
            data.forEachIndexed { index, dailyData ->
                val x = padding + (index * (canvasWidth - 2 * padding) / (data.size - 1))
                val y = if (maxBytes > 0) {
                    canvasHeight - padding - ((dailyData.totalBytes.toFloat() / maxBytes.toFloat()) * (canvasHeight - 2 * padding) * animatedProgress)
                } else {
                    canvasHeight - padding
                }
                
                if (index == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
        }
        
        // Dibujar línea
        drawPath(
            path = linePath,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3f
            )
        )
        
        // Dibujar puntos de datos
        data.forEachIndexed { index, dailyData ->
            val x = padding + (index * (canvasWidth - 2 * padding) / (data.size - 1))
            val y = if (maxBytes > 0) {
                canvasHeight - padding - ((dailyData.totalBytes.toFloat() / maxBytes.toFloat()) * (canvasHeight - 2 * padding) * animatedProgress)
            } else {
                canvasHeight - padding
            }
            
            drawCircle(
                color = color,
                radius = 4f,
                center = Offset(x, y)
            )
        }
        
        // Dibujar etiquetas
        val paint = android.graphics.Paint().apply {
            setColor(android.graphics.Color.GRAY)
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        val step = if (data.size / 7 > 0) data.size / 7 else 1 // Mostrar cada 7ª etiqueta como máximo
        data.forEachIndexed { index, dailyData ->
            if (index % step == 0) {
                val x = padding + (index * (canvasWidth - 2 * padding) / (data.size - 1))
                drawContext.canvas.nativeCanvas.drawText(
                    dailyData.dayOfMonth.toString(),
                    x,
                    canvasHeight - 2f,
                    paint
                )
            }
        }
    }
}

/**
 * Componente de tarjeta de resumen con gráfico circular
 */
@Composable
fun DataUsageSummaryCard(
    mobileData: Long,
    wifiData: Long,
    totalData: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Resumen de Uso",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Gráfico de pastel
            val data = mapOf(
                "Datos Móviles" to mobileData,
                "WiFi" to wifiData
            )
            
            DataUsagePieChart(
                data = data,
                modifier = Modifier.size(150.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Leyenda
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                LegendItem(
                    color = Color(0xFF2196F3),
                    label = "Móviles",
                    value = formatBytes(mobileData)
                )
                
                LegendItem(
                    color = Color(0xFF4CAF50),
                    label = "WiFi",
                    value = formatBytes(wifiData)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Total
            Text(
                text = "Total: ${formatBytes(totalData)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Componente para leyenda del gráfico
 */
@Composable
fun LegendItem(
    color: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Componente de gráfico de progreso para límites de datos
 */
@Composable
fun DataLimitProgressBar(
    currentUsage: Long,
    dataLimit: DataLimit,
    modifier: Modifier = Modifier
) {
    val percentage = dataLimit.getUsagePercentage(currentUsage) / 100f
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic)
    )
    
    val progressColor = when {
        dataLimit.isCriticalThresholdExceeded(currentUsage) -> Color(0xFFF44336) // Rojo
        dataLimit.isWarningThresholdExceeded(currentUsage) -> Color(0xFFFF9800) // Naranja
        else -> Color(0xFF4CAF50) // Verde
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Límite de Datos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "${formatBytes(currentUsage)} / ${formatBytes(dataLimit.limitBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPercentage)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    progressColor.copy(alpha = 0.8f),
                                    progressColor
                                )
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${String.format("%.1f", dataLimit.getUsagePercentage(currentUsage))}% utilizado",
                style = MaterialTheme.typography.bodySmall,
                color = progressColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (dataLimit.isWarningThresholdExceeded(currentUsage)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (dataLimit.isCriticalThresholdExceeded(currentUsage)) {
                        "¡Cuidado! Estás cerca del límite"
                    } else {
                        "Te estás acercando al límite de datos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = progressColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Clase de datos para uso diario
 */
data class DailyDataUsage(
    val date: Long,
    val dayOfMonth: Int,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val totalBytes: Long
)

/**
 * Función auxiliar para obtener color según categoría
 */
private fun getAppCategoryColor(category: AppCategory): Color {
    return when (category) {
        AppCategory.VIDEO_STREAMING -> Color(0xFFF44336) // Rojo
        AppCategory.SOCIAL_MEDIA -> Color(0xFF2196F3) // Azul
        AppCategory.GAMING -> Color(0xFF9C27B0) // Púrpura
        AppCategory.PRODUCTIVITY -> Color(0xFF4CAF50) // Verde
        AppCategory.OTHER -> Color(0xFF757575) // Gris
    }
}

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
