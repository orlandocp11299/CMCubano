package cu.cuban.cmcubano.screens

import android.content.Context
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import cu.cuban.cmcubano.utils.PremiumManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import cu.cuban.cmcubano.utils.NotificationScheduler




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanesMenuScreen(navController: NavController) {
    val menuItems = listOf(
        MenuCardItem(
            "Compra de Planes y Paquetes",
            "Datos, Voz y SMS",
            Icons.Default.ShoppingCart,
            Color(0xFFFF9800),
            "planes_list"
        ),
        MenuCardItem(
            "Consultas Rápidas",
            "Saldo, bonos y recursos disponibles",
            Icons.Default.Search,
            Color(0xFF2196F3),
            "consultas_list"
        ),
        MenuCardItem(
            "Transferencia de Saldo",
            "Envía saldo a otros números",
            Icons.Default.SwapHoriz,
            Color(0xFF4CAF50),
            "transferencia_list"
        )
    )
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val isPremium = remember { PremiumManager.isPremium(context) }
    var isTapped by remember { mutableStateOf(false) }

    LaunchedEffect(isTapped) {
        if (isTapped) {
            delay(3000)
            isTapped = false
        }
    }

    var isEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
                .getBoolean("recordatorio_enabled", isPremium)
        )
    }

    var isExpanded by remember { mutableStateOf(false) }
    val recordatorioPrefs = remember { context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE) }
    var lastSmsTimestamp by remember {
        mutableStateOf(recordatorioPrefs.getLong("hidden_start_date", 0L))
    }
    
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val nextAlertDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }



    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Planes y Consultas",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            menuItems.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clickable { navController.navigate(item.route) },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        item.color.copy(alpha = 0.15f),
                                        item.color.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = item.color.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = item.color
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(20.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            
            // Tarjeta de Recordatorio (Intermactiva / Reverted Color)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = !isPremium) { isTapped = !isTapped },
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF9C27B0).copy(alpha = 0.15f),
                                    Color(0xFF9C27B0).copy(alpha = 0.05f)
                                )
                            )
                        )
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            AnimatedContent(
                                targetState = isTapped && !isPremium,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f))
                                        .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f))
                                },
                                label = "PremiumMessageAnimation"
                            ) { showingPremiumMessage ->
                                if (showingPremiumMessage) {
                                    // Mensaje Premium al tocar (Purple theme)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Esta función es premium,",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "activa tu suscripción en los ajustes.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )

                                        }
                                    }
                                } else {
                                    // Contenido Normal (Purple theme)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(56.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFF9C27B0).copy(alpha = 0.2f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Notifications,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = Color(0xFF9C27B0)
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.width(20.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Recordatorio",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                ),
                                                color = if (!isPremium) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            
                                            Text(
                                                text = "Aviso a la vigencia de los datos",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (!isPremium) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (isPremium) {
                                            Switch(
                                                checked = isEnabled,
                                                onCheckedChange = { checked ->
                                                    isEnabled = checked
                                                    context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)
                                                        .edit()
                                                        .putBoolean("recordatorio_enabled", checked)
                                                        .apply()
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                )
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = Color(0xFF9C27B0).copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Flecha de expansión (Overlay en el header de 120dp)
                            if (isPremium && !(isTapped && !isPremium)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .clickable { isExpanded = !isExpanded }
                                        .padding(bottom = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Ver detalles",
                                        tint = Color(0xFF9C27B0).copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        // Sección expandible (Datos)
                        AnimatedVisibility(
                            visible = isPremium && isExpanded && !(isTapped && !isPremium),
                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    color = Color(0xFF9C27B0).copy(alpha = 0.1f)
                                )
                                
                                if (lastSmsTimestamp > 0) {
                                    val lastDate = Date(lastSmsTimestamp)
                                    val nextDate = Date(lastSmsTimestamp + (35L * 24 * 60 * 60 * 1000))
                                    
                                    DetailItem(
                                        "Compra del Plan:",
                                        dateFormatter.format(lastDate),
                                        Icons.Default.History,
                                        trailingContent = {
                                            IconButton(
                                                onClick = { showDatePicker = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Editar fecha",
                                                    tint = Color(0xFF9C27B0),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DetailItem(
                                        "Vence el:",
                                        nextAlertDateFormatter.format(nextDate),
                                        Icons.Default.Event
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No se han registrado mensajes de ETECSA.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { showDatePicker = true },
                                            modifier = Modifier.height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Ingresar fecha", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showDatePicker) {
                Dialog(onDismissRequest = { showDatePicker = false }) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.width(300.dp) // Tamaño exterior minimalista
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Box que contiene el DatePicker con espacio suficiente pero escalado
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(360.dp), // Aumentado aún más para garantizar cero solapamiento
                                contentAlignment = Alignment.Center
                            ) {
                                DatePicker(
                                    state = datePickerState,
                                    title = null,
                                    headline = null,
                                    showModeToggle = false,
                                    modifier = Modifier
                                        .requiredWidth(360.dp) // Engañamos al componente dándole su ancho natural
                                        .scale(0.83f), // Lo encogemos visualmente para que quepa en 300dp
                                    colors = DatePickerDefaults.colors(
                                        containerColor = Color.Transparent,
                                        titleContentColor = Color(0xFF9C27B0),
                                        headlineContentColor = Color(0xFF9C27B0),
                                        weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        yearContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        currentYearContentColor = Color(0xFF9C27B0),
                                        selectedYearContentColor = Color.White,
                                        selectedYearContainerColor = Color(0xFF9C27B0),
                                        dayContentColor = MaterialTheme.colorScheme.onSurface,
                                        selectedDayContentColor = Color.White,
                                        selectedDayContainerColor = Color(0xFF9C27B0),
                                        todayContentColor = Color(0xFF9C27B0),
                                        todayDateBorderColor = Color(0xFF9C27B0)
                                    )
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showDatePicker = false },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                ) {
                                    Text("Cancelar", style = MaterialTheme.typography.labelLarge)
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Button(
                                    onClick = {
                                        datePickerState.selectedDateMillis?.let { utcMillis ->
                                            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                                            calendar.timeInMillis = utcMillis
                                            
                                            val localCalendar = Calendar.getInstance()
                                            localCalendar.set(
                                                calendar.get(Calendar.YEAR),
                                                calendar.get(Calendar.MONTH),
                                                calendar.get(Calendar.DAY_OF_MONTH),
                                                12, 0, 0
                                            )
                                            
                                            val finalMillis = localCalendar.timeInMillis
                                            lastSmsTimestamp = finalMillis
                                            recordatorioPrefs.edit()
                                                .putLong("hidden_start_date", finalMillis)
                                                .putInt("hidden_days_passed", 0)
                                                .putInt("last_notified_day", -1) // Reset notifications logic
                                                .apply()
                                            
                                            // Schedule and Trigger immediate check
                                            NotificationScheduler.scheduleDailyCheck(context)
                                            NotificationScheduler.triggerImmediateCheck(context)
                                        }
                                        showDatePicker = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Text("Confirmar", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }


        }
    }
}

data class MenuCardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@Composable
fun DetailItem(
    label: String, 
    value: String, 
    icon: ImageVector,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color(0xFF9C27B0)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        trailingContent?.invoke()
    }
}
