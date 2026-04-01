package cu.cuban.cmcubano.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cu.cuban.cmcubano.utils.VibrationManager
import androidx.compose.ui.platform.LocalContext

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    object UpToDate : UpdateCheckState()
    object NoInternet : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

@Composable
fun CheckUpdateDialog(
    state: UpdateCheckState,
    onDismiss: () -> Unit
) {
    if (state == UpdateCheckState.Idle) return

    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Area
                when (state) {
                    is UpdateCheckState.Checking -> {
                        val infiniteTransition = rememberInfiniteTransition(label = "rotate")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).rotate(rotation)
                            )
                        }
                    }
                    is UpdateCheckState.UpToDate -> {
                        StatusIcon(Icons.Default.CheckCircle, Color(0xFF4CAF50))
                    }
                    is UpdateCheckState.NoInternet -> {
                        StatusIcon(Icons.Default.CloudOff, Color(0xFFE91E63))
                    }
                    is UpdateCheckState.Error -> {
                        StatusIcon(Icons.Default.CloudOff, MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title Area
                val title = when (state) {
                    is UpdateCheckState.Checking -> "Buscando actualizaciones"
                    is UpdateCheckState.UpToDate -> "¡Estás al día!"
                    is UpdateCheckState.NoInternet -> "Sin conexión"
                    is UpdateCheckState.Error -> "Ups, algo salió mal"
                    else -> ""
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message Area
                val message = when (state) {
                    is UpdateCheckState.Checking -> ""
                    is UpdateCheckState.UpToDate -> "Tienes la última versión de CM Cubano instalada. Te avisaremos en cuanto haya novedades."
                    is UpdateCheckState.NoInternet -> "Al parecer no tienes conexión a internet. Por favor, verifica tu red y vuelve a intentarlo."
                    is UpdateCheckState.Error -> (state as UpdateCheckState.Error).message
                    else -> ""
                }

                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }

                if (state != UpdateCheckState.Checking) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            VibrationManager.vibrate(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Entendido", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(color, color.copy(alpha = 0.7f))
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}
