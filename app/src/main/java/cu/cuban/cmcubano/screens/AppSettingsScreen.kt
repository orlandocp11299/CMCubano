package cu.cuban.cmcubano.screens



import android.Manifest

import android.content.Context

import android.content.Intent

import android.content.pm.PackageManager

import android.net.Uri

import android.os.Build

import android.os.Environment

import android.provider.Settings

import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.ActivityResultLauncher

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.*

import androidx.compose.animation.core.*

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.KeyboardOptions

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

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import androidx.navigation.NavController

import cu.cuban.cmcubano.utils.EnbNameManager

import cu.cuban.cmcubano.utils.VibrationManager

import java.io.File

import java.io.FileOutputStream

import java.nio.charset.StandardCharsets



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun AppSettingsScreen(navController: NavController) {

    val context = LocalContext.current

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    

    var themeMode by remember { mutableStateOf(prefs.getInt("theme_mode", 0)) }

    val showCustomEnbNamesInNeighboring = remember { mutableStateOf(prefs.getBoolean("show_custom_enb_names_neighboring", false)) }

    val isSystemDark = isSystemInDarkTheme()

    

    val vibrationEnabled = remember { mutableStateOf(prefs.getBoolean("vibration_enabled", false)) }

    val vibrationIntensity = remember { mutableStateOf(prefs.getInt("vibration_intensity", VibrationManager.INTENSITY_LOW).toFloat()) }



    // Estados para gestión de eNBs

    var showEnbListDialog by remember { mutableStateOf(false) }

    var showDeleteAllEnbsDialog by remember { mutableStateOf(false) }

    var showAddEnbDialog by remember { mutableStateOf(false) }

    

    // Estados compartidos para Editar/Añadir

    var currentEditingEnbId by remember { mutableStateOf("") }

    var currentEditingEnbName by remember { mutableStateOf("") }

    var currentEditingPcis by remember { mutableStateOf(listOf("", "", "")) }





    // Launcher para permisos de almacenamiento

    val storagePermissionLauncher = rememberLauncherForActivityResult(

        contract = ActivityResultContracts.RequestMultiplePermissions()

    ) { permissions ->

        val allGranted = permissions.values.all { it }

        if (!allGranted) {

            Toast.makeText(context, "Se requieren permisos de almacenamiento", Toast.LENGTH_SHORT).show()

        }

    }



    // Launcher para seleccionar archivo de importación

    val filePickerLauncher = rememberLauncherForActivityResult(

        contract = ActivityResultContracts.GetContent()

    ) { uri ->

        uri?.let { importEnbNamesFromFile(context, it) }

    }



    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = { 

                    Text(

                        "Ajustes de la app",

                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

                    ) 

                },

                navigationIcon = {

                    IconButton(onClick = { 

                        VibrationManager.vibrate(context)

                        navController.popBackStack() 

                    }) {

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

            // --- SECCIÓN: TEMA ---

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically

            ) {

                Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))

                Spacer(modifier = Modifier.width(12.dp))

                Text(

                    text = "Apariencia",

                    style = MaterialTheme.typography.labelLarge,

                    color = MaterialTheme.colorScheme.primary,

                    fontWeight = FontWeight.Bold

                )

            }

            

            // Selector tipo píldora (Adaptativo / Claro / Oscuro)

            val pillOffset by animateFloatAsState(

                targetValue = themeMode.toFloat(),

                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),

                label = "pillOffset"

            )



            BoxWithConstraints(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(48.dp)

                    .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f), RoundedCornerShape(24.dp))

                    .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f), RoundedCornerShape(24.dp))

                    .padding(4.dp)

            ) {

                val pillWidth = maxWidth / 3

                Box(

                    modifier = Modifier

                        .width(pillWidth)

                        .fillMaxHeight()

                        .offset(x = pillWidth * pillOffset)

                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))

                )

                Row(modifier = Modifier.fillMaxSize()) {

                    val options = listOf("Adaptativo", "Claro", "Oscuro")

                    options.forEachIndexed { index, label ->

                        Box(

                            modifier = Modifier

                                .weight(1f)

                                .fillMaxHeight()

                                .clip(RoundedCornerShape(20.dp))

                                .clickable {

                                    VibrationManager.vibrate(context)

                                    themeMode = index

                                    prefs.edit().putInt("theme_mode", index).apply()

                                },

                            contentAlignment = Alignment.Center

                        ) {

                            Text(

                                label,

                                fontWeight = if (themeMode == index) FontWeight.Bold else FontWeight.Normal,

                                color = if (themeMode == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,

                                fontSize = 13.sp

                            )

                        }

                    }

                }

            }



            Divider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.05f))



            // --- SECCIÓN: VIBRACIÓN ---

            Row(

                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))

                    .clickable {

                        vibrationEnabled.value = !vibrationEnabled.value

                        prefs.edit().putBoolean("vibration_enabled", vibrationEnabled.value).apply()

                        if (vibrationEnabled.value) VibrationManager.vibrate(context)

                    }.padding(8.dp),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                    Icon(Icons.Default.Smartphone, null, tint = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {

                        Text("Vibración", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                        Text("Respuesta háptica al tocar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))

                    }

                }

                Switch(checked = vibrationEnabled.value, onCheckedChange = {

                    vibrationEnabled.value = it

                    prefs.edit().putBoolean("vibration_enabled", it).apply()

                    if (it) VibrationManager.vibrate(context)

                })

            }



            AnimatedVisibility(visible = vibrationEnabled.value) {

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {

                    Text("Intensidad: ${when(vibrationIntensity.value.toInt()) {

                        VibrationManager.INTENSITY_LOW -> "Baja"; VibrationManager.INTENSITY_MEDIUM -> "Media"; else -> "Alta"

                    }}", style = MaterialTheme.typography.labelSmall)

                    Slider(

                        value = vibrationIntensity.value,

                        onValueChange = { vibrationIntensity.value = it },

                        onValueChangeFinished = {

                            prefs.edit().putInt("vibration_intensity", vibrationIntensity.value.toInt()).apply()

                            VibrationManager.vibrate(context)

                        },

                        valueRange = 0f..2f, steps = 1

                    )

                }

            }



            Divider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.05f))



            // --- SECCIÓN: NOMBRES DE eNB ---

            Text("Nombres de eNB personalizados", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            

            val enbNames = remember { EnbNameManager.getAllEnbNames(context) }

            val hasEnbNames = remember { enbNames.isNotEmpty() }



            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                EnbActionButton(Icons.Default.List, "Ver", MaterialTheme.colorScheme.primary, { showEnbListDialog = true }, Modifier.weight(1f))

                EnbActionButton(Icons.Default.FileUpload, "Exp", Color(0xFF4CAF50), { exportEnbNamesToDownloads(context, storagePermissionLauncher) }, Modifier.weight(1f), hasEnbNames)

                EnbActionButton(Icons.Default.FileDownload, "Imp", Color(0xFF2196F3), { checkStoragePermissionAndImport(context, storagePermissionLauncher, filePickerLauncher) }, Modifier.weight(1f))

                EnbActionButton(Icons.Default.DeleteForever, "Del", MaterialTheme.colorScheme.error, { showDeleteAllEnbsDialog = true }, Modifier.weight(1f), hasEnbNames)

            }

            

            Row(

                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))

                    .clickable {

                        showCustomEnbNamesInNeighboring.value = !showCustomEnbNamesInNeighboring.value

                        prefs.edit().putBoolean("show_custom_enb_names_neighboring", showCustomEnbNamesInNeighboring.value).apply()

                        VibrationManager.vibrate(context)

                    }.padding(8.dp),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                    Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.width(12.dp))

                    Text("Ver en vecinas", style = MaterialTheme.typography.bodySmall)

                }

                Switch(checked = showCustomEnbNamesInNeighboring.value, onCheckedChange = {

                    showCustomEnbNamesInNeighboring.value = it

                    prefs.edit().putBoolean("show_custom_enb_names_neighboring", it).apply()

                    VibrationManager.vibrate(context)

                })

            }



            Divider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.05f))



            // --- SECCIÓN: NOTIFICACIONES ---

            val monitorNetworkNotification = remember { mutableStateOf(prefs.getBoolean("monitor_network_notification", false)) }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(

                contract = ActivityResultContracts.RequestPermission()

            ) { isGranted ->

                if (isGranted) {

                    monitorNetworkNotification.value = true

                    prefs.edit().putBoolean("monitor_network_notification", true).apply()

                    val intent = Intent(context, cu.cuban.cmcubano.NotificationService::class.java).apply {

                        action = cu.cuban.cmcubano.NotificationService.ACTION_START_MONITOR

                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                        context.startForegroundService(intent)

                    } else {

                        context.startService(intent)

                    }

                } else {

                    monitorNetworkNotification.value = false

                    Toast.makeText(context, "Se requiere permiso de notificaciones", Toast.LENGTH_SHORT).show()

                }

            }



            Row(

                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))

                    .clickable {

                        val newValue = !monitorNetworkNotification.value

                        if (newValue) {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 

                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                            } else {

                                monitorNetworkNotification.value = true

                                prefs.edit().putBoolean("monitor_network_notification", true).apply()

                                val intent = Intent(context, cu.cuban.cmcubano.NotificationService::class.java).apply {

                                    action = cu.cuban.cmcubano.NotificationService.ACTION_START_MONITOR

                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                                    context.startForegroundService(intent)

                                } else {

                                    context.startService(intent)

                                }

                            }

                        } else {

                            monitorNetworkNotification.value = false

                            prefs.edit().putBoolean("monitor_network_notification", false).apply()

                            val intent = Intent(context, cu.cuban.cmcubano.NotificationService::class.java).apply {

                                action = cu.cuban.cmcubano.NotificationService.ACTION_STOP_MONITOR

                            }

                            context.startService(intent)

                        }

                        VibrationManager.vibrate(context)

                    }.padding(8.dp),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {

                        Text("Seguimiento de red", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                        Text("Notificación de los parámetros de la red en vivo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))

                    }

                }

                Switch(checked = monitorNetworkNotification.value, onCheckedChange = {

                    val newValue = it

                    if (newValue) {

                         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 

                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                        } else {

                            monitorNetworkNotification.value = true

                            prefs.edit().putBoolean("monitor_network_notification", true).apply()

                            val intent = Intent(context, cu.cuban.cmcubano.NotificationService::class.java).apply {

                                action = cu.cuban.cmcubano.NotificationService.ACTION_START_MONITOR

                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                                context.startForegroundService(intent)

                            } else {

                                context.startService(intent)

                            }

                        }

                    } else {

                        monitorNetworkNotification.value = false

                        prefs.edit().putBoolean("monitor_network_notification", false).apply()

                        val intent = Intent(context, cu.cuban.cmcubano.NotificationService::class.java).apply {

                            action = cu.cuban.cmcubano.NotificationService.ACTION_STOP_MONITOR

                        }

                        context.startService(intent)

                    }

                    VibrationManager.vibrate(context)

                })

            }



            // --- SECCIÓN: RED MÓVIL / FORZAR LTE ---

            Row(

                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))

                    .clickable {

                        VibrationManager.vibrate(context)

                        openRadioInfo(context)

                    }.padding(8.dp),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                    Icon(Icons.Default.SignalCellularAlt, null, tint = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {

                        Text("Forzar 4G / Modos de Red", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                        Text("Menú oculto de Información del Teléfono", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))

                    }

                }

                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))

            }

        }



        // --- DIÁLOGOS ---

        

        // Diálogo de lista de eNBs

        if (showEnbListDialog) {

            val enbNamesMap = EnbNameManager.getAllEnbNames(context)

            AlertDialog(

                onDismissRequest = { showEnbListDialog = false },

                title = { Text("Nombres de eNB personalizados (${enbNamesMap.size})") },

                text = {

                    if (enbNamesMap.isEmpty()) {

                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                Text("No hay eNBs guardados", color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(

                                    onClick = {

                                        currentEditingEnbId = ""

                                        currentEditingEnbName = ""

                                        currentEditingPcis = listOf("", "", "")

                                        showEnbListDialog = false

                                        showAddEnbDialog = true

                                    }

                                ) {

                                    Icon(Icons.Default.Add, contentDescription = null)

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text("Añadir eNB")

                                }

                            }

                        }

                    } else {

                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                            enbNamesMap.forEach { (enbId, name) ->

                                Row(

                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),

                                    horizontalArrangement = Arrangement.SpaceBetween,

                                    verticalAlignment = Alignment.CenterVertically

                                ) {

                                    Column(modifier = Modifier.weight(1f)) {

                                        Text("eNB $enbId", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                        Text(name)

                                    }

                                    IconButton(onClick = {

                                        currentEditingEnbId = enbId

                                        currentEditingEnbName = name

                                        val pciList = EnbNameManager.getEnbPcis(context, enbId).split(",")

                                        currentEditingPcis = listOf(

                                            pciList.getOrElse(0) { "" }.trim(),

                                            pciList.getOrElse(1) { "" }.trim(),

                                            pciList.getOrElse(2) { "" }.trim()

                                        )

                                        showEnbListDialog = false

                                        showAddEnbDialog = true

                                    }) {

                                        Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)

                                    }

                                    IconButton(onClick = {

                                        EnbNameManager.removeEnbName(context, enbId)

                                        showEnbListDialog = false

                                        showEnbListDialog = true

                                    }) {

                                        Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)

                                    }

                                }

                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(

                                onClick = {

                                    currentEditingEnbId = ""

                                    currentEditingEnbName = ""

                                    currentEditingPcis = listOf("", "", "")

                                    showEnbListDialog = false

                                    showAddEnbDialog = true

                                },

                                modifier = Modifier.fillMaxWidth()

                            ) {

                                Icon(Icons.Default.Add, contentDescription = null)

                                Spacer(modifier = Modifier.width(8.dp))

                                Text("Añadir eNB")

                            }

                        }

                    }

                },

                confirmButton = { 

                    TextButton(onClick = { showEnbListDialog = false }) { Text("Cerrar") } 

                }

            )

        }



        // Diálogo de añadir/editar eNB

        if (showAddEnbDialog) {

            var enbIdState by remember { mutableStateOf(currentEditingEnbId) }

            var enbNameState by remember { mutableStateOf(currentEditingEnbName) }

            var pci1State by remember { mutableStateOf(currentEditingPcis.getOrElse(0) { "" }) }

            var pci2State by remember { mutableStateOf(currentEditingPcis.getOrElse(1) { "" }) }

            var pci3State by remember { mutableStateOf(currentEditingPcis.getOrElse(2) { "" }) }

            var errorMsgState by remember { mutableStateOf("") }

            

            AlertDialog(

                onDismissRequest = { 

                    showAddEnbDialog = false

                    showEnbListDialog = true

                },

                title = { Text(if (currentEditingEnbId.isNotEmpty()) "Editar eNB" else "Añadir eNB personalizado") },

                text = {

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        OutlinedTextField(

                            value = enbIdState,

                            onValueChange = { s -> 

                                enbIdState = s

                                errorMsgState = ""

                            },

                            label = { Text("ID del eNB") },

                            modifier = Modifier.fillMaxWidth(),

                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                            singleLine = true,

                            enabled = currentEditingEnbId.isEmpty()

                        )

                        

                        OutlinedTextField(

                            value = enbNameState,

                            onValueChange = { s -> 

                                enbNameState = s

                                errorMsgState = ""

                            },

                            label = { Text("Nombre personalizado") },

                            modifier = Modifier.fillMaxWidth(),

                            singleLine = true

                        )



                        Row(

                            modifier = Modifier.fillMaxWidth(),

                            horizontalArrangement = Arrangement.spacedBy(8.dp)

                        ) {

                            OutlinedTextField(

                                value = pci1State,

                                onValueChange = { s -> if (s.length <= 3 && s.all { it.isDigit() }) pci1State = s },

                                label = { Text("PCI 1", fontSize = 12.sp) },

                                modifier = Modifier.weight(1f),

                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                                singleLine = true,

                                shape = RoundedCornerShape(12.dp)

                            )

                            OutlinedTextField(

                                value = pci2State,

                                onValueChange = { s -> if (s.length <= 3 && s.all { it.isDigit() }) pci2State = s },

                                label = { Text("PCI 2", fontSize = 12.sp) },

                                modifier = Modifier.weight(1f),

                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                                singleLine = true,

                                shape = RoundedCornerShape(12.dp)

                            )

                            OutlinedTextField(

                                value = pci3State,

                                onValueChange = { s -> if (s.length <= 3 && s.all { it.isDigit() }) pci3State = s },

                                label = { Text("PCI 3", fontSize = 12.sp) },

                                modifier = Modifier.weight(1f),

                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                                singleLine = true,

                                shape = RoundedCornerShape(12.dp)

                            )

                        }



                        if (pci1State.isNotEmpty() || pci2State.isNotEmpty() || pci3State.isNotEmpty()) {

                            Text("Previsualización de PCIs:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                            Row(

                                modifier = Modifier.fillMaxWidth(),

                                horizontalArrangement = Arrangement.spacedBy(8.dp)

                            ) {

                                listOf(pci1State, pci2State, pci3State).forEachIndexed { index, pci ->

                                    Box(

                                        modifier = Modifier

                                            .weight(1f)

                                            .background(

                                                if (pci.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)

                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),

                                                RoundedCornerShape(8.dp)

                                            )

                                            .padding(vertical = 8.dp),

                                        contentAlignment = Alignment.Center

                                    ) {

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                            Text("Sector $index", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)

                                            Text(if (pci.isNotEmpty()) pci else "-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                                        }

                                    }

                                }

                            }

                        }

                        

                        if (errorMsgState.isNotEmpty()) {

                            Text(text = errorMsgState, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                        }

                    }

                },

                confirmButton = {

                    TextButton(

                        onClick = {

                            if (enbIdState.isBlank() || enbNameState.isBlank()) {

                                errorMsgState = "Por favor completa ID y Nombre"

                            } else {

                                val pcis = listOf(pci1State.trim(), pci2State.trim(), pci3State.trim())

                                    .joinToString(",")

                                EnbNameManager.saveEnbName(context, enbIdState.trim(), enbNameState.trim(), pcis)

                                showAddEnbDialog = false

                                showEnbListDialog = true

                                Toast.makeText(context, if (currentEditingEnbId.isNotEmpty()) "Actualizado" else "Añadido", Toast.LENGTH_SHORT).show()

                            }

                        }

                    ) {

                        Text(if (currentEditingEnbId.isNotEmpty()) "Actualizar" else "Añadir")

                    }

                },

                dismissButton = {

                    TextButton(onClick = { 

                        showAddEnbDialog = false

                        showEnbListDialog = true

                    }) { Text("Cancelar") }

                }

            )

        }



        // Diálogo de eliminar todos

        if (showDeleteAllEnbsDialog) {

            AlertDialog(

                onDismissRequest = { showDeleteAllEnbsDialog = false },

                title = { Text("Eliminar todos los nombres") },

                text = { Text("¿Estás seguro? Esta acción no se puede deshacer.") },

                confirmButton = {

                    TextButton(onClick = {

                        EnbNameManager.clearAllEnbNames(context)

                        showDeleteAllEnbsDialog = false

                        Toast.makeText(context, "Eliminados", Toast.LENGTH_SHORT).show()

                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }

                },

                dismissButton = { TextButton(onClick = { showDeleteAllEnbsDialog = false }) { Text("Cancelar") } }

            )

        }



    }

}



@Composable

fun EnbActionButton(

    icon: ImageVector,

    text: String,

    color: Color,

    onClick: () -> Unit,

    modifier: Modifier = Modifier,

    enabled: Boolean = true

) {

    Button(

        onClick = onClick,

        modifier = modifier.height(48.dp),

        enabled = enabled,

        colors = ButtonDefaults.buttonColors(

            containerColor = color.copy(alpha = 0.15f),

            contentColor = color

        ),

        shape = RoundedCornerShape(12.dp),

        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)

    ) {

        Icon(imageVector = icon, contentDescription = text, modifier = Modifier.size(24.dp))

    }

}



private fun exportEnbNamesToDownloads(context: Context, storagePermissionLauncher: ActivityResultLauncher<Array<String>>) {

    try {

        val enbNames = EnbNameManager.getAllEnbNames(context)

        if (enbNames.isEmpty()) {

            Toast.makeText(context, "No hay nombres para exportar", Toast.LENGTH_SHORT).show()

            return

        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        val permissionsNeeded = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

        if (permissionsNeeded.isNotEmpty()) {

            storagePermissionLauncher.launch(permissionsNeeded.toTypedArray())

            return

        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val cmCubanoDir = File(downloadsDir, "CM Cubano")

        if (!cmCubanoDir.exists()) cmCubanoDir.mkdirs()

        val content = enbNames.entries.joinToString("\n") { (enbId, name) -> 

            val pcis = EnbNameManager.getEnbPcis(context, enbId)

            "$enbId=$name|$pcis"

        }

        val file = File(cmCubanoDir, "enb_names_${System.currentTimeMillis()}.txt")

        FileOutputStream(file).use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }

        Toast.makeText(context, "Archivo guardado en: Downloads/CM Cubano/", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {

        Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_SHORT).show()

    }

}



private fun checkStoragePermissionAndImport(context: Context, storagePermissionLauncher: ActivityResultLauncher<Array<String>>, filePickerLauncher: ActivityResultLauncher<String>) {

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val permissionsNeeded = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    if (permissionsNeeded.isNotEmpty()) {

        storagePermissionLauncher.launch(permissionsNeeded.toTypedArray())

    } else {

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val cmCubanoDir = File(downloadsDir, "CM Cubano")

        if (!cmCubanoDir.exists()) cmCubanoDir.mkdirs()

        filePickerLauncher.launch("text/plain")

    }

}



private fun importEnbNamesFromFile(context: Context, uri: Uri) {

    try {

        val inputStream = context.contentResolver.openInputStream(uri)

        val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

        val lines = content.lines()

        var importedCount = 0

        lines.forEach { line ->

            val parts = line.split("=", limit = 2)

            if (parts.size == 2) {

                val enbId = parts[0].trim()

                val valuePart = parts[1].trim()

                val valueParts = valuePart.split("|", limit = 2)

                val name = valueParts[0].trim()

                val pcis = if (valueParts.size > 1) valueParts[1].trim() else ""

                if (enbId.isNotEmpty() && name.isNotEmpty()) {

                    EnbNameManager.saveEnbName(context, enbId, name, pcis)

                    importedCount++

                }

            }

        }

        if (importedCount > 0) Toast.makeText(context, "Se importaron $importedCount nombres de eNB", Toast.LENGTH_SHORT).show()

        else Toast.makeText(context, "No se encontraron nombres válidos en el archivo", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) { Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_SHORT).show() }

}



private fun openRadioInfo(context: Context) {
    cu.cuban.cmcubano.utils.NetworkUtils.openRadioInfo(context)
}

