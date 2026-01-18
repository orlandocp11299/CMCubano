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
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
import androidx.compose.animation.core.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.graphics.vector.ImageVector


import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import cu.cuban.cmcubano.R
import coil.compose.AsyncImage

import androidx.navigation.NavController
import androidx.compose.foundation.isSystemInDarkTheme
import android.provider.Settings
import cu.cuban.cmcubano.utils.PremiumManager
import android.content.Context
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.widget.Toast

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(navController: NavController) {
    var showSupportDialog by remember { mutableStateOf(false) }
    var showPrivacidadDialog by remember { mutableStateOf(false) }
    var showAcercaDeDialog by remember { mutableStateOf(false) }
    var showDonacionesDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var amoledDark by remember { mutableStateOf(prefs.getBoolean("amoled_dark", false)) }
    val isDarkMode = isSystemInDarkTheme()

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
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sección Premium
            val isPremium = remember { mutableStateOf(PremiumManager.isPremium(context)) }
            var premiumCode by remember { mutableStateOf(if (isPremium.value) prefs.getString("premium_code", "") ?: "" else "") }
            var errorMessage by remember { mutableStateOf("") }
            val deviceReference = remember { PremiumManager.getDeviceReference(context) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isPremium.value) {
                            Modifier.border(
                                width = 1.6.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFA000), Color(0xFFFFD700))
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {



                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isPremium.value) Icons.Default.Star else Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = if (isPremium.value) Color(0xFFFFA000) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPremium.value) "Premium Activo" else "Activar Premium",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPremium.value) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 16.sp
                        )
                        if (isPremium.value) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFA000),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    
                    if (!isPremium.value) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = premiumCode,
                            onValueChange = { premiumCode = it.filter { char -> char.isLetterOrDigit() } },
                            label = { Text("Código de activación", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            singleLine = true,
                            isError = errorMessage.isNotEmpty(),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        if (errorMessage.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "REF: $deviceReference",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )

                            }
                            Button(
                                onClick = {
                                    val message = "Solicito la activación premium de CM Cubano ($deviceReference)"
                                    val url = "https://wa.me/5350576622?text=${Uri.encode(message)}"
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse(url)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.height(40.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF25D366) 
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_whatsapp),
                                        contentDescription = "WhatsApp",
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Obtener", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }


                        }


                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (PremiumManager.validateAndActivate(context, premiumCode)) {
                                    isPremium.value = true
                                    errorMessage = ""
                                    // Activar el recordatorio automáticamente al activar premium
                                    context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("recordatorio_enabled", true)
                                        .apply()
                                } else {
                                    errorMessage = "Código incorrecto"
                                }

                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Comprobar", fontSize = 14.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Gracias por apoyar el desarrollo de CM Cubano. Todas las funciones premium están desbloqueadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Ajustes de la aplicación",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))


                        // Selector tipo píldora con relieve animado
                        val pillOffset by animateFloatAsState(
                            targetValue = if (amoledDark) 1f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pillOffset"
                        )
                        
                        val darkTextColor by animateColorAsState(
                            targetValue = if (!amoledDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            label = "darkText"
                        )
                        val amoledTextColor by animateColorAsState(
                            targetValue = if (amoledDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            label = "amoledText"
                        )

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                                    RoundedCornerShape(24.dp)
                                )
                                .border(
                                    1.dp, 
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f), 
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(4.dp)
                        ) {
                            val pillWidth = maxWidth / 2
                            
                            // Indicador deslizante
                            Box(
                                modifier = Modifier
                                    .width(pillWidth)
                                    .fillMaxHeight()
                                    .offset(x = pillWidth * pillOffset)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            )

                            Row(modifier = Modifier.fillMaxSize()) {
                                // Opción OSCURO
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable(enabled = isDarkMode) {
                                            amoledDark = false
                                            prefs.edit().putBoolean("amoled_dark", false).apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "OSCURO",
                                        fontWeight = if (!amoledDark) FontWeight.Bold else FontWeight.Normal,
                                        color = darkTextColor
                                    )
                                }
                                
                                // Opción AMOLED
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable(enabled = isDarkMode) {
                                            amoledDark = true
                                            prefs.edit().putBoolean("amoled_dark", true).apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "AMOLED",
                                        fontWeight = if (amoledDark) FontWeight.Bold else FontWeight.Normal,
                                        color = amoledTextColor
                                    )
                                }
                            }
                        }


                        if (!isDarkMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Activa el modo oscuro para personalizar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                        )

                        // Optimización de Batería
                        val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager }
                        var isIgnoringBatteryOptimizations by remember {
                            mutableStateOf(
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                                } else true
                            )
                        }

                        // Actualizar estado al volver a la app
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    isIgnoringBatteryOptimizations = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        powerManager.isIgnoringBatteryOptimizations(context.packageName)
                                    } else true
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(fallbackIntent)
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOptimizations) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Optimización de batería",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (isIgnoringBatteryOptimizations) "Permitido en segundo plano" else "Toca para permitir segundo plano",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                            if (!isIgnoringBatteryOptimizations) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                        )

                        // Inicio Automático (Guía)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val intent = Intent().apply {
                                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Habilita 'Inicio Automático' en los ajustes de la app", Toast.LENGTH_LONG).show()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsPower,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Inicio automático",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Esencial para recordatorios tras reiniciar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Mensaje informativo sobre confiabilidad
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Si los recordatorios no aparecen, asegúrate de permitir el 'Inicio automático' y desactivar el 'Ahorro de batería'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }



            // Apartados de Soporte, Privacidad y Acerca de (Modo tarjetas pequeñas tipo Inicio)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Soporte
                    AjustesSmallCard(
                        icon = Icons.Default.Help,
                        text = "Soporte",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { showSupportDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    // Privacidad
                    AjustesSmallCard(
                        icon = Icons.Default.Security,
                        text = "Privacidad",
                        color = Color(0xFF4CAF50),
                        onClick = { showPrivacidadDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Acerca de
                    AjustesSmallCard(
                        icon = Icons.Default.Info,
                        text = "Acerca de",
                        color = Color(0xFF607D8B),
                        onClick = { showAcercaDeDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    // Donaciones
                    AjustesSmallCard(
                        icon = Icons.Default.Favorite,
                        text = "Donar",
                        color = Color(0xFFE91E63),
                        onClick = { showDonacionesDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }



            Spacer(modifier = Modifier.height(2.dp))

            // Sección de redes sociales
            Text(
                text = "Síguenos en redes sociales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
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
                text = { 
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Si necesitas ayuda con la aplicación Contacte con su desarrollador por la siguientes vias:")
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Botón de WhatsApp
                            Button(
                                onClick = {
                                    val url = "https://wa.me/5350576622"
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse(url)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF25D366) 
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_whatsapp),
                                        contentDescription = "WhatsApp",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            
                            // Botón de Telegram
                            Button(
                                onClick = {
                                    try {
                                        val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=CMCubano_dev"))
                                        context.startActivity(telegramIntent)
                                    } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/CMCubano_dev"))
                                        context.startActivity(webIntent)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0088CC) 
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = "https://telegram.org/img/t_logo.png",
                                        contentDescription = "Telegram",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Telegram", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                },
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
                text = { Text("Esta aplicación no recopila informacion de ningun tipo, los permisos que se solicitan estan estrictamente pensados para el correcto funcionamiento de la aplicación.") },
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
                    Text("Versión: 1.8 Beta\nDesarrollador: Orlando\n\nCM Cubano es una aplicación diseñada para la comunidad cubana interesada en el mapeo de redes móviles.")
                },
                confirmButton = {
                    TextButton(onClick = { showAcercaDeDialog = false }) {
                        Text("Aceptar")
                    }
                }
            )
        }

        if (showDonacionesDialog) {
            AlertDialog(
                onDismissRequest = { showDonacionesDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apoyar Proyecto")
                    }
                },
                text = { 
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Si valoras este trabajo, puedes ayudar a mantener el proyecto vivo mediante una pequeña donación, ya sea saldo móvil o transferencia bancaria.")
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Toca para copiar:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            
                            // Campo para Teléfono
                            CopyableDonationField(
                                label = "Teléfono",
                                value = "50576622",
                                context = context
                            )
                            
                            // Campo para Tarjeta
                            CopyableDonationField(
                                label = "Tarjeta",
                                value = "9205129972760673",
                                context = context
                            )
                        }
                        
                        Text("Cualquier aporte, por pequeño que sea, motiva a seguir mejorando la app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDonacionesDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

@Composable
fun CopyableDonationField(label: String, value: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Donación $label", value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copiar",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    }
}



@Composable
fun AjustesSmallCard(
    icon: ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.12f),
                            color.copy(alpha = 0.03f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}