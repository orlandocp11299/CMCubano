package cu.cuban.cmcubano.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.navigation.NavController

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(navController: NavController) {
    var showSupportDialog by remember { mutableStateOf(false) }
    var showPrivacidadDialog by remember { mutableStateOf(false) }
    var showAcercaDeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Configuración",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de Soporte
            Card(
                onClick = { showSupportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                ListItem(
                    headlineContent = { 
                        Text(
                            "Soporte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = "Soporte",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    supportingContent = {
                        Text(
                            "Obtén ayuda y soporte técnico",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            }

            // Tarjeta de Política de Privacidad
            Card(
                onClick = { showPrivacidadDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                ListItem(
                    headlineContent = { 
                        Text(
                            "Política de Privacidad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Privacidad",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    supportingContent = {
                        Text(
                            "Información sobre el manejo de datos",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            }

            // Tarjeta de Acerca de
            Card(
                onClick = { showAcercaDeDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                ListItem(
                    headlineContent = { 
                        Text(
                            "Acerca de",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Acerca de",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    supportingContent = {
                        Text(
                            "Información de la aplicación y desarrollador",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de redes sociales
            Text(
                text = "Síguenos en redes sociales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Botones de redes sociales
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón de Twitter
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/CellmapperCuban"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DA1F2)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        AsyncImage(
                            model = "https://abs.twimg.com/responsive-web/client-web/icon-ios.b1fc727a.png",
                            contentDescription = "Twitter Icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Twitter(X)",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Botón de Telegram
                Button(
                    onClick = {
                        try {
                            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=CellMapper_Cubano_Oficial"))
                            context.startActivity(telegramIntent)
                        } catch (e: Exception) {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/CellMapper_Cubano_Oficial"))
                            context.startActivity(webIntent)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0088CC)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        AsyncImage(
                            model = "https://telegram.org/img/t_logo.png",
                            contentDescription = "Telegram Icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Telegram",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Diálogos
        if (showSupportDialog) {
            AlertDialog(
                onDismissRequest = { showSupportDialog = false },
                title = { Text("Soporte") },
                text = { Text("Si necesitas ayuda, puedes contactarnos a través de nuestras redes sociales las cuales estan disponibles en la parte inferiror") },
                confirmButton = {
                    TextButton(onClick = { showSupportDialog = false }) {
                        Text("Aceptar")
                    }
                }
            )
        }

        if (showPrivacidadDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacidadDialog = false },
                title = { Text("Política de Privacidad") },
                text = { Text("Esta aplicación no recopila datos personales. Solo se utiliza para mostrar información sobre las redes móviles.") },
                confirmButton = {
                    TextButton(onClick = { showPrivacidadDialog = false }) {
                        Text("Aceptar")
                    }
                }
            )
        }

        if (showAcercaDeDialog) {
            AlertDialog(
                onDismissRequest = { showAcercaDeDialog = false },
                title = { Text("Acerca de") },
                text = { 
                    Text("Versión: 1.6 Beta\nDesarrollador: Orlan_2\n\nCellMapper Cubano es una aplicación diseñada para la comunidad cubana interesada en el mapeo de redes móviles.")
                },
                confirmButton = {
                    TextButton(onClick = { showAcercaDeDialog = false }) {
                        Text("Aceptar")
                    }
                }
            )
        }
    }
}