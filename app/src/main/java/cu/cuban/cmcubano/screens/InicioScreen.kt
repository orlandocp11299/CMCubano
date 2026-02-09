package cu.cuban.cmcubano.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import android.content.Context
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import cu.cuban.cmcubano.utils.PremiumManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(navController: NavController) {
    val carouselItems = listOf(
        CarouselItem(
            "Ver información de la red en tiempo real",
            Icons.Filled.CellTower,
            Color(0xFF2196F3),
            "red_info"
        ),
        CarouselItem(
            "Comprobar la velocidad de tu internet",
            Icons.Default.Speed,
            Color(0xFF4CAF50),
            "speedtest"
        ),
        CarouselItem(
            "Comprar planes y consultar tus recursos",
            Icons.Default.ShoppingBag,
            Color(0xFFFF9800),
            "planes_menu"
        ),
        CarouselItem(
            "Información precisa",
            Icons.Default.MenuBook,
            Color(0xFFE91E63),
            "datos"
        ),
        CarouselItem(
            "Control de tus datos",
            Icons.Default.Storage,
            Color(0xFF3F51B5),
            "control_datos"
        ),
        CarouselItem(
            "Ver un mapa de las Radio Bases",
            Icons.Default.Map,
            Color(0xFF9C27B0),
            "mapa"
        )
    )

    val context = LocalContext.current
    val isPremium = remember { PremiumManager.isPremium(context) }
    
    // Estado para el bloqueo de Control de Datos
    val trialPrefs = remember { context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE) }
    val trialUsed = trialPrefs.getBoolean("control_datos_trial_used", false)
    val trialEndTime = trialPrefs.getLong("control_datos_trial_end_time", 0L)
    val isTrialExpired = trialUsed && System.currentTimeMillis() >= trialEndTime
    var isControlDatosLockedTapped by remember { mutableStateOf(false) }

    LaunchedEffect(isControlDatosLockedTapped) {
        if (isControlDatosLockedTapped) {
            delay(3000)
            isControlDatosLockedTapped = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Encabezado principal moderno
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(
                    if (isPremium) {
                        Modifier.border(
                            width = 1.8.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFA000), Color(0xFFFFD700))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CM Cubano",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = {
                        cu.cuban.cmcubano.utils.VibrationManager.vibrate(context)
                        navController.navigate("mas")
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(32.dp))

        // Cuadrícula de tarjetas (Dos columnas)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            carouselItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .padding(vertical = 6.dp)
                                .clickable {
                                    if (item.route == "control_datos" && !isPremium && isTrialExpired) {
                                        isControlDatosLockedTapped = true
                                    } else {
                                        navController.navigate(item.route)
                                    }
                                },
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                item.color.copy(alpha = 0.12f),
                                                item.color.copy(alpha = 0.03f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = isControlDatosLockedTapped && item.route == "control_datos",
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f))
                                            .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f))
                                    },
                                    label = "PremiumMessageAnimation"
                                ) { isLocked ->
                                    if (isLocked) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Text(
                                                text = "Esta función es premium,",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "activa tu suscripción en los ajustes.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .padding(bottom = 8.dp),
                                                tint = item.color
                                            )
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 18.sp
                                                ),
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

data class CarouselItem(
    val text: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)
