package cu.cuban.cmcubano.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cu.cuban.cmcubano.R

enum class ExpandedCard {
    NONE,
    CONCEPTOS,
    FRECUENCIAS,
    B8_LTE,
    REFARMING,
    MAPA
}

data class ConceptItem(val title: String, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatosScreen(navController: NavController) {
    var expandedCard by remember { mutableStateOf(ExpandedCard.NONE) }
    
    // Define concepts data
    val concepts = remember {
        listOf(
            ConceptItem("eNB (Evolved Node B)", "Es básicamente la antena o la estación base en una red de telefonía móvil 4G (LTE). Piensa en ella como el \"router\" gigante que conecta tus dispositivos móviles a la red."),
            ConceptItem("CID (Cell ID)", "Es el identificador único de cada celda de una red de telefonía. Es como la dirección postal de cada antena. Cuando tu teléfono se conecta a una antena, usa este ID para saber a qué celda está conectado."),
            ConceptItem("PCI (Physical Cell ID)", "Es un número que identifica físicamente una celda dentro de una red. Mientras que el CID es administrativo, el PCI es como el nombre de pila de la celda para diferenciarla de las cercanas."),
            ConceptItem("EARFCN", "Número que indica la frecuencia de operación de una celda. Como sintonizar una estación de radio; cada número representa una frecuencia diferente."),
            ConceptItem("RSSI", "Mide la fuerza total de la señal recibida, incluyendo interferencia. Como el volumen total en una habitación ruidosa."),
            ConceptItem("RSRP", "Potencia de la señal de referencia recibida (la parte útil). Es como la claridad de una voz específica en medio del ruido."),
            ConceptItem("RSRQ", "Calidad de la señal recibida. Una medida de qué tan \"limpia\" es la señal en comparación con el ruido. Alta RSRQ significa mejor calidad."),
            ConceptItem("SNR", "Relación Señal/Ruido. Cuánto más fuerte es la señal útil comparada con el ruido de fondo."),
            ConceptItem("QCI", "Identificador de clase de servicio. Define prioridades y latencia (ej. QCI 1 para VoLTE, QCI 9 para datos generales)."),
            ConceptItem("LTE CA", "Carrier Aggregation. Combina varias frecuencias para aumentar la velocidad y ancho de banda."),
            ConceptItem("RRU & BBU", "RRU (Radio Remota) maneja las señales de radio finales. BBU (Banda Base) procesa los datos digitales antes de ser transmitidos o después de ser recibidos.")
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Datos y Conceptos",
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
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Conceptos
            ExpandableSection(
                title = "Conceptos Básicos",
                subtitle = "Terminología de redes móviles",
                icon = Icons.Outlined.Info, // Modern outlined icon
                isExpanded = expandedCard == ExpandedCard.CONCEPTOS,
                onToggle = { expandedCard = if (expandedCard == ExpandedCard.CONCEPTOS) ExpandedCard.NONE else ExpandedCard.CONCEPTOS }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    concepts.forEach { item ->
                        ConceptRow(item)
                        if (item != concepts.last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            // Section: Frecuencias
            ExpandableSection(
                title = "Frecuencias en Cuba",
                subtitle = "Bandas y tecnologías activas",
                icon = Icons.Outlined.CellTower,
                isExpanded = expandedCard == ExpandedCard.FRECUENCIAS,
                onToggle = { expandedCard = if (expandedCard == ExpandedCard.FRECUENCIAS) ExpandedCard.NONE else ExpandedCard.FRECUENCIAS }
            ) {
                FrequencyContent()
            }

            // Section: B8 LTE
            ExpandableSection(
                title = "Proyecto B8 LTE",
                subtitle = "Futura implementación en 900 MHz",
                icon = Icons.Outlined.NetworkCheck,
                isExpanded = expandedCard == ExpandedCard.B8_LTE,
                onToggle = { expandedCard = if (expandedCard == ExpandedCard.B8_LTE) ExpandedCard.NONE else ExpandedCard.B8_LTE }
            ) {
                B8LteContent()
            }

            // Section: Refarming
            ExpandableSection(
                title = "Refarming",
                subtitle = "Reorganización del espectro",
                icon = Icons.Outlined.SwapHoriz,
                isExpanded = expandedCard == ExpandedCard.REFARMING,
                onToggle = { expandedCard = if (expandedCard == ExpandedCard.REFARMING) ExpandedCard.NONE else ExpandedCard.REFARMING }
            ) {
                RefarmingContent()
            }

            // Section: Mapa
            ExpandableSection(
                title = "Mapa de Tecnología",
                subtitle = "Cobertura por provincias",
                icon = Icons.Outlined.Map,
                isExpanded = expandedCard == ExpandedCard.MAPA,
                onToggle = { expandedCard = if (expandedCard == ExpandedCard.MAPA) ExpandedCard.NONE else ExpandedCard.MAPA }
            ) {
                MapContent()
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ExpandableSection(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    
    Card(
        shape = RoundedCornerShape(24.dp), // Modern rounded corners
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .clickable(onClick = onToggle)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .rotate(animateFloatAsState(targetValue = if (isExpanded) 180f else 0f).value)
                )
            }
            
            if (isExpanded) {
                Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ConceptRow(item: ConceptItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
fun FrequencyContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Tech Table
        CompactTable(
            title = "Tecnologías Activas",
            data = listOf(
                "2G" to "900 MHz",
                "3G" to "900 / 2100 MHz",
                "4G" to "700 / 900 / 1800 / 2100 MHz",
                "5G" to "3500 MHz (N78) [Esperado]"
            )
        )
        
        CompactTable(
            title = "Bandas Específicas",
            data = listOf(
                "B28 (LTE)" to "700 MHz",
                "B8 (GSM/LTE)" to "900 MHz",
                "B3 (LTE)" to "1800 MHz",
                "B1 (UMTS/LTE)" to "2100 MHz"
            )
        )
    }
}

@Composable
fun CompactTable(title: String, data: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            data.forEach { (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun B8LteContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            text = "Se prevé la implementación de la banda B8 (900 MHz) en LTE desde Ciego de Ávila hasta Oriente. Ayuda a la comunidad a reportar cambios.",
            icon = Icons.Outlined.Announcement
        )

        SectionTitle("Cambios esperados en 3G (UARFCN)")
        CompactDataList(listOf("2997 → 2987", "2972 → 2962", "2947 → 2937"))

        SectionTitle("Cambios esperados en 2G (ARFCN)")
        CompactDataList(listOf("Todo ARFCN < 75 debe cambiar a rango 75-124"))
        
        Text(
            text = "Reporta cualquier cambio detectado en la comunidad.",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun RefarmingContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Introducción
        Text(
            text = "El refarming (o refrecuenciación) es el proceso en el que se reorganiza el uso del espectro radioeléctrico para poder introducir nuevas tecnologías (como LTE o 5G) dentro de las bandas que antes estaban ocupadas por 2G o 3G.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "En el caso de Cuba , ETECSA usa 2G y 3G en 900 MHz y planea usar dicha frecuencia también en 4g.(A la hora de escribir este articulo La Habana y Cienfuegos ya tienen LTE 900).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Qué hay en 900 MHz hoy
        SectionTitle("Qué hay en 900 MHz hoy")
        CompactDataList(listOf(
            "2G GSM: opera en 900 MHz (canales ARFCN de 200 kHz).",
            "3G UMTS: típicamente tres portadoras (5 MHz c/u) con UARFCN 2947, 2972 y 2997.",
            "4G LTE Band 8 (900): una portadora de 10 MHz (bloque contiguo dentro de 925–960 MHz DL / 880–915 MHz UL)."
        ))

        // Cómo se hace el refarming
        SectionTitle("Cómo se hace el refarming dentro de 900 MHz")
        Text(
            text = "Objetivo: liberar un bloque contiguo de 10 MHz para LTE B8 sin dejar sin servicio a 2G/3G.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        
        CompactDataList(listOf(
            "1. Compactar 2G\nReducir el número de ARFCN activos y reubicarlos en los bordes del E-GSM.\nMantener distancias mínimas (≈200–400 kHz) contra UMTS/LTE.",
            "2. Acomodar 3G \nMantener 3×5 MHz (UARFCN 2947/2972/2997) donde la demanda 3G lo requiera, o bajar a 2 portadoras en celdas de baja carga para dar más espacio a 2G.",
            "3. Ubicar LTE B8 (10 MHz)\nReservar un bloque contiguo de 10 MHz dentro de 925–960 MHz (DL).\nSe coloca normalmente alejado de las portadoras UMTS para minimizar interferencia y facilitar las distancias."
        ))

        InfoCard(
            text = "💢El ancho total DL en 900 es 35 MHz (925–960). Con UMTS (15 MHz) + LTE B8 (10 MHz) quedan ~10 MHz para 2G, por eso se compacta GSM y/o se reduce UMTS en celdas de baja carga.",
            icon = Icons.Outlined.Warning
        )

        // Aspectos Positivos
        SectionTitle("Aspectos Positivos")
        CompactDataList(listOf(
            "1- Reducción del costo de expansión:\nPermite añadir LTE en la misma frecuencia que 2/3G sin necesidad de implementar nuevos paneles o RRU",
            "2- Mejor cobertura 4G:\nLa banda 900 MHz tiene gran alcance y buena penetración en interiores.\n➤ Beneficia zonas rurales y urbanas con mala cobertura de las bandas altas (B3/B1).",
            "3- Eficiencia espectral y mejor experiencia:\nLTE B8 ofrece mayor capacidad de datos por MHz respecto a UMTS 900 o GSM, mejorando velocidades y eficiencia energética por bit transmitido.",
            "4- Capacidad Adicional\nPosibilidad de hacer agregación de portadoras (4G+) con otras bandas aumentando así el ancho de banda final."
        ))

        // Aspectos Negativos
        SectionTitle("Aspectos Negativos")
        CompactDataList(listOf(
            "1- Competencia entre tecnologías por el espectro:\nLos 35 MHz del bloque DL (925–960 MHz) deben repartirse entre 2G, 3G y 4G.\n➤ Esto obliga a compactar GSM y reducir UMTS, lo que puede degradar el servicio de voz o datos 3G en celdas con alta carga.",
            "2- Limitaciones de capacidad:\nCon solo 10 MHz asignados a LTE B8, la capacidad de datos es modesta.\n➤ Aunque mejora la cobertura, no ofrece grandes velocidades ni soporta mucho tráfico por lo que se necesita un buen balance de carga para evitar su congestión."
        ))
    }
}

@Composable
fun InfoCard(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun CompactDataList(items: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowRight, 
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun MapContent() {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.mapa_tecnologia),
            contentDescription = "Mapa de tecnologías por provincias",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    }
}