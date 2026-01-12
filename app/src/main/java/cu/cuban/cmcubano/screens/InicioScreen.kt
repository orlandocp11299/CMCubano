package cu.cuban.cmcubano.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(navController: NavController) {
    val carouselItems = listOf(
        CarouselItem(
            "Ver información de la red en tiempo real",
            Icons.Default.NetworkCheck,
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
            Icons.Default.AccountCircle,
            Color(0xFFE91E63),
            "datos"
        ),
        CarouselItem(
            "Ver un mapa de las Radio Bases",
            Icons.Default.Map,
            Color(0xFF9C27B0),
            "mapa"
        ),
        CarouselItem(
            "Ajustes de la aplicación",
            Icons.Default.Settings,
            Color(0xFF607D8B),
            "mas"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Encabezado principal moderno
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFF673AB7)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "CM CUBANO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mini encabezado
        Text(
            text = "Con nuestra app podrás:",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

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
                                    navController.navigate(item.route)
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
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        
        Surface(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .height(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ) {}
    }
}

data class CarouselItem(
    val text: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)
