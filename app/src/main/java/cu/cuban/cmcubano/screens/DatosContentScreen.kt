package cu.cuban.cmcubano.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.outlined.Announcement
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cu.cuban.cmcubano.R

data class ConceptItem(val title: String, val description: String, val technicalDescription: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatosContentScreen(navController: NavController, section: String) {
    var expandedConceptTitle by remember { mutableStateOf<String?>(null) }
    val concepts = remember {
        listOf(
            ConceptItem(
                title = "eNB (Evolved Node B)",
                description = "Es básicamente la antena o la estación base en una red de telefonía móvil 4G (LTE). Piensa en ella como el \"router\" gigante que conecta tus dispositivos móviles a la red de tu proveedor de telefonía.",
                technicalDescription = "El eNB (Evolved Node B) constituye el elemento fundamental de acceso radio en redes LTE (Long Term Evolution), encargado de proveer la interfaz aire (Uu) entre el usuario y la red del operador. A diferencia de arquitecturas previas (BTS en 2G y Node B en 3G), el eNB integra funciones de capa física, MAC y RLC, además de control de recursos de radio (RRM), scheduling y adaptación de modulación y codificación (AMC), lo que permite optimizar el uso del espectro y garantizar calidad de servicio (QoS). Establece conexión directa con el Evolved Packet Core (EPC) mediante la interfaz S1 para el transporte de datos y señalización, y con otros eNB a través de la interfaz X2 para soportar handover intercelda y coordinación de interferencias (ICIC, eICIC, CoMP). En esencia, el eNB no es solo una estación base, sino un nodo inteligente que combina funciones de transmisión, control y coordinación, siendo la pieza central que posibilita las altas tasas de datos, baja latencia y confiabilidad propias de LTE."
            ),
            ConceptItem(
                title = "CID (Cell ID)",
                description = "Es el identificador único de cada celda de una red de telefonía. Es como la dirección postal de cada antena (eNB) específica. Cuando tu teléfono se conecta a una antena, usa este ID para saber a qué celda está conectado.",
                technicalDescription = "El CID (Cell Identity o Cell ID) es el identificador único asignado a cada celda dentro de una red móvil, permitiendo distinguirlas incluso cuando pertenecen al mismo eNB. Forma parte de la información de señalización transmitida en el sistema LTE y se utiliza para que el UE (User Equipment) reconozca a qué celda está conectado, facilitando procedimientos de acceso, registro y handover. En LTE, el CID se compone de dos niveles: el eNB ID, que identifica de forma global a la estación base, y el Cell ID secundario, que diferencia las celdas sectorizadas o portadoras dentro del mismo sitio. Este identificador es crucial para la gestión de movilidad, optimización de cobertura y planificación de red, ya que permite al operador monitorear desempeño, realizar handovers eficientes y coordinar la asignación de recursos radio en entornos con múltiples celdas adyacentes."
            ),
            ConceptItem(
                title = "PCI (Physical Cell ID)",
                description = "Es un número que identifica físicamente una celda dentro de una red. Mientras que el CID es más bien una dirección administrativa, el PCI es como el nombre de pila de la celda que tu teléfono usa para diferenciarla de otras celdas cercanas.",
                technicalDescription = "El PCI (Physical Cell Identity) es un identificador numérico que permite a los equipos de usuario (UE) distinguir las celdas vecinas en el plano físico de una red LTE. A diferencia del CID, que es único y de carácter administrativo, el PCI se asigna dentro de un rango de 504 valores posibles (0–503) y puede reutilizarse en celdas no adyacentes para optimizar el uso del espectro de numeración. Este parámetro se transmite en las señales de sincronización primaria y secundaria (PSS/SSS) y es esencial para que el terminal ejecute procesos de sincronización, adquisición de celda, medición de potencia y selección de celda durante el acceso inicial o el handover. Una correcta planificación de PCI es crítica en ingeniería de radio, ya que la reutilización indebida en celdas vecinas genera confusión de identificación (PCI collision) o indistinción (PCI confusion), lo cual impacta negativamente la calidad de servicio y la continuidad de la movilidad."
            ),
            ConceptItem(
                title = "EARFCN (E-UTRA Absolute Radio Frequency Channel Number)",
                description = "Es un número que indica en qué frecuencia está operando una celda específica. Es como sintonizar una estación de radio; cada número representa una frecuencia diferente donde tu teléfono puede comunicarse con la red.",
                technicalDescription = "El EARFCN (E-UTRA Absolute Radio Frequency Channel Number) es un identificador numérico estandarizado en LTE que define la frecuencia portadora exacta utilizada por una celda. Cada EARFCN corresponde a una frecuencia central dentro de una banda LTE específica, permitiendo al UE y a la red establecer con precisión el canal de operación sin ambigüedades. Su uso es análogo a un canal de sintonización: el dispositivo traduce el número EARFCN en una frecuencia en MHz según fórmulas establecidas por el 3GPP, diferenciando así tanto uplink como downlink. Este parámetro es crítico en la planificación y optimización de redes, ya que garantiza la correcta asignación de espectro, evita solapamientos entre portadoras y facilita la configuración de agregación de portadoras (CA, Carrier Aggregation) y procedimientos de movilidad interfrecuencia. En resumen, el EARFCN es la referencia técnica que asegura la alineación precisa entre red y terminal en el dominio de frecuencia."
            ),
            ConceptItem(
                title = "RSSI (Received Signal Strength Indicator)",
                description = "Mide la fuerza total de la señal que tu teléfono recibe, incluyendo tanto la señal útil como la interferencia. Es como medir el volumen de una música en una habitación ruidosa; cuanto más alto, más fuerte es la señal, pero no necesariamente mejor la calidad.",
                technicalDescription = "El RSSI (Received Signal Strength Indicator) es una métrica que cuantifica la potencia total recibida por el terminal en el canal de referencia, incluyendo tanto la señal útil como la interferencia y el ruido térmico. En redes LTE, se expresa en dBm y se utiliza como indicador bruto de cobertura, aunque no refleja directamente la calidad de la transmisión. A diferencia de parámetros como RSRP (que mide la potencia de la portadora de referencia) o RSRQ (que relaciona potencia y calidad), el RSSI representa la suma de todas las fuentes de energía en el ancho de banda observado. Su función principal es servir de base para cálculos de otros indicadores de calidad de radio, como RSRQ y SINR, así como para procedimientos de cell reselection y handover. En ingeniería de telecomunicaciones, un valor elevado de RSSI implica buena potencia recibida, pero su interpretación aislada puede ser engañosa si la interferencia es significativa."
            ),
            ConceptItem(
                title = "RSRP (Reference Signal Received Power)",
                description = "Mide solo la potencia de la señal de referencia recibida, que es la parte útil de la señal. Puedes pensar en esto como la claridad de una voz en medio del ruido; cuanto más alto, mejor es la señal de tu celda.",
                technicalDescription = "El RSRP (Reference Signal Received Power) es un indicador clave en redes LTE, que mide la potencia promedio recibida de las señales de referencia (RS) transmitidas por una celda, excluyendo ruido e interferencia. A diferencia del RSSI, que refleja la energía total en el canal, el RSRP se centra en la parte útil de la señal, proporcionando una estimación más precisa de la cobertura real. Se expresa en dBm y suele encontrarse en rangos de –140 dBm a –44 dBm, siendo valores más altos indicativos de mejor recepción. Este parámetro es esencial en procesos de cell selection, reselection y handover, ya que permite al UE comparar objetivamente la potencia de diferentes celdas. En ingeniería de redes, el RSRP se utiliza para evaluar la intensidad de la cobertura radioeléctrica y como métrica base para optimizar la planificación de celdas, la asignación de recursos y la experiencia de usuario en servicios de banda ancha móvil."
            ),
            ConceptItem(
                title = "RSRQ (Reference Signal Received Quality)",
                description = "Indica la calidad de la señal recibida, considerando tanto la potencia de la señal útil como la interferencia. Es una medida de cómo de \"limpia\" es la señal en comparación con el ruido alrededor. Alta RSRQ significa buena calidad de señal.",
                technicalDescription = "El RSRQ (Reference Signal Received Quality) es un indicador de calidad en LTE que relaciona la potencia útil de la señal de referencia (RSRP) con la potencia total recibida (RSSI) en el ancho de banda asignado. Matemáticamente se expresa como: RSRQ = N * RSRP / RSSI donde N es el número de recursos físicos utilizados en la medición. Este parámetro refleja cuán “limpia” es la señal respecto al ruido e interferencia circundante, proporcionando una visión más precisa de la calidad de enlace que el RSRP por sí solo. Sus valores típicos oscilan entre –19,5 dB (muy pobre) y –3 dB (excelente). Un RSRQ alto indica condiciones favorables para la modulación eficiente, mayor throughput y menor probabilidad de errores, mientras que valores bajos suelen asociarse a interferencia intercelda, congestión o solapamiento de cobertura. En planificación y optimización de red, el RSRQ es esencial para la gestión de movilidad, el control de handovers y la evaluación de la experiencia de usuario."
            ),
            ConceptItem(
                title = "SNR (Signal-to-Noise Ratio)",
                description = "Es la relación entre la potencia de la señal útil (lo que quieres escuchar) y el ruido (las interferencias). Es como intentar tener una conversación en una fiesta; un buen SNR significa que tu voz se escucha claramente por encima del bullicio.",
                technicalDescription = "El SNR (Signal-to-Noise Ratio) es un parámetro fundamental en telecomunicaciones que expresa la relación entre la potencia de la señal útil y la potencia del ruido en el canal de comunicación. Se mide en decibelios (dB) y constituye un indicador directo de la calidad de recepción: valores altos implican que la señal se distingue claramente del ruido, favoreciendo modulaciones de orden superior (64QAM, 256QAM) y mayores tasas de datos, mientras que valores bajos limitan la eficiencia espectral y elevan la probabilidad de errores. A diferencia de métricas como el RSRP o RSSI, el SNR no mide potencia absoluta, sino la proporción señal/ruido, lo que lo convierte en un parámetro crítico para procesos de link adaptation, programación de recursos y estimación de capacidad de celda. En ingeniería de redes LTE y 5G, mantener un SNR adecuado es esencial para garantizar cobertura robusta, throughput sostenido y calidad de experiencia (QoE) del usuario."
            ),
            ConceptItem(
                title = "QCI (Quality of Service Class Identifier)",
                description = "En redes móviles, especialmente en LTE, el QCI es un identificador numérico que se usa para definir la clase de servicio de calidad que debe recibir un flujo de datos específico. Piensa en ello como una etiqueta que le dice a la red cómo tratar cierto tráfico de datos.",
                technicalDescription = "Cada QCI viene con un conjunto de características predeterminadas como prioridad, demora (latencia), tasa de pérdida de paquetes y tasa de error de bits. Estos parámetros determinan cómo se maneja el tráfico para asegurar que aplicaciones críticas, como una llamada de voz (alta prioridad, baja latencia) o un video en streaming (necesidad de una tasa de datos constante), reciban el tratamiento adecuado en la red.\n\nEjemplos:\n- QCI 1: Es para servicios como la voz en tiempo real (VoLTE), donde la latencia es muy baja y la prioridad es alta.\n- QCI 9: Podría ser para servicios de datos con requisitos menos estrictos, como navegación web casual.\n\nImpacto para el usuario: Indirectamente, QCI afecta tu experiencia al usar diferentes servicios en tu móvil. Si estás en una videollamada y la calidad es consistente sin interrupciones, es probable que la red esté manejando ese tráfico con un QCI que asegura una baja latencia y alta prioridad. Sin embargo, como QCI es una característica técnica de la red, el usuario promedio no interactúa directamente con estos valores; simplemente experimenta las consecuencias de una buena o mala gestión de calidad de servicio."
            ),
            ConceptItem(
                title = "LTE CA (Carrier Aggregation)",
                description = "Carrier Aggregation es una técnica utilizada en redes LTE para combinar varias bandas de frecuencia (o \"portadoras\") para aumentar la velocidad de datos y la eficiencia espectral. Básicamente, en lugar de usar una sola frecuencia para transmitir datos, CA permite que un dispositivo use varias frecuencias al mismo tiempo.",
                technicalDescription = "Es una técnica avanzada de LTE-Advanced que permite combinar simultáneamente múltiples portadoras (component carriers, CC) de distintas bandas de frecuencia para incrementar el throughput y optimizar la eficiencia espectral. Cada portadora mantiene su propio ancho de banda y EARFCN, y el UE puede recibir y transmitir datos de manera concurrente sobre todas ellas, logrando velocidades agregadas superiores a las de una sola portadora. CA admite configuraciones intra-banda (portadoras dentro de la misma banda) o inter-banda (portadoras en bandas diferentes), lo que mejora cobertura y capacidad, y permite balancear carga entre celdas. Esta técnica requiere coordinación entre eNB y UE para scheduling, control de potencia y asignación de recursos, y es esencial en la evolución hacia LTE-Advanced Pro, habilitando servicios de banda ancha móvil con alta capacidad y baja latencia para aplicaciones intensivas en datos."
            ),
            ConceptItem(
                title = "RRU (Remote Radio Unit)",
                description = "Una Unidad de Radio Remota, o RRU, es un dispositivo que forma parte de las estaciones base de las redes móviles. Es básicamente el equipo que se encarga de transmitir y recibir las señales de radio entre tu teléfono móvil y la red del proveedor de servicios.",
                technicalDescription = "La RRU (Remote Radio Unit) es un elemento crítico en las estaciones base modernas de LTE y 5G, encargado de la transmisión y recepción de señales de radio hacia los equipos de usuario. Se conecta al BBU (Baseband Unit) mediante enlaces de fibra óptica (como CPRI o eCPRI), lo que permite separar físicamente la parte de procesamiento digital de la parte radioeléctrica, reduciendo pérdidas y mejorando la eficiencia energética. La RRU realiza funciones de amplificación, filtrado, conversión de frecuencia y digitalización de la señal, soportando múltiples antenas y MIMO. Su despliegue cercano a las antenas permite minimizar la atenuación en el cableado y facilita la implementación de sitios distribuidos y nodos remotos, optimizando cobertura, capacidad y flexibilidad en la planificación de la red."
            ),
            ConceptItem(
                title = "BBU (Baseband Unit)",
                description = "La Unidad de Banda Base, o BBU, es una parte esencial de las estaciones base en las redes de telefonía móvil. Es el componente que se encarga del procesamiento digital de los datos, transformando las señales que llegan desde tu teléfono móvil en datos digitales que pueden ser enviados a través de la red del proveedor de servicios, y viceversa.",
                technicalDescription = "La BBU (Baseband Unit) es el núcleo de procesamiento digital de una estación base LTE o 5G, responsable de manejar la capa física, MAC, RLC y PDCP, así como la señalización de control y datos del usuario. Recibe señales de la RRU mediante enlaces de fibra óptica (CPRI/eCPRI), realiza modulación, codificación, multiplexación y procesamiento de paquetes, y las envía al Evolved Packet Core (EPC) a través de la interfaz S1. La BBU también gestiona funciones críticas como scheduler, handover, QoS y coordinación intercelda (X2), centralizando la inteligencia de la estación base. Al separar la BBU de la RRU, se optimiza la eficiencia operativa, se facilita el despliegue de sitios remotos y cloud-RAN y se mejora la escalabilidad, capacidad y flexibilidad de la red."
            )
        )
    }

    val sectionData = when (section) {
        "conceptos" -> SectionData(
            title = "Conceptos Básicos",
            subtitle = "Terminología de redes móviles",
            icon = Icons.Outlined.Info
        )
        "frecuencias" -> SectionData(
            title = "Frecuencias en Cuba",
            subtitle = "Bandas y tecnologías activas",
            icon = Icons.Outlined.CellTower
        )
        "b8_lte" -> SectionData(
            title = "Proyecto B8 LTE",
            subtitle = "Futura implementación en 900 MHz",
            icon = Icons.Outlined.NetworkCheck
        )
        "refarming" -> SectionData(
            title = "Refarming",
            subtitle = "Reorganización del espectro",
            icon = Icons.Outlined.SwapHoriz
        )
        "mapa" -> SectionData(
            title = "Mapa de Tecnología",
            subtitle = "Cobertura por provincias",
            icon = Icons.Outlined.Map
        )
        else -> SectionData(
            title = "Datos y Conceptos",
            subtitle = "Información técnica",
            icon = Icons.Outlined.Info
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    cu.cuban.cmcubano.components.ScreenTitlePill(title = sectionData.title)
                },
                navigationIcon = {
                    val isDark = isSystemInDarkTheme()
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                        )
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Atrás",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
            when (section) {
                "conceptos" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        concepts.forEach { item ->
                            ConceptCard(
                                item = item,
                                expanded = item.title == expandedConceptTitle,
                                onClick = {
                                    expandedConceptTitle = if (expandedConceptTitle == item.title) null else item.title
                                }
                            )
                        }
                    }
                }
                "frecuencias" -> {
                    FrequencyContent()
                }
                "b8_lte" -> {
                    B8LteContent()
                }
                "refarming" -> {
                    RefarmingContent()
                }
                "mapa" -> {
                    MapContent()
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

data class SectionData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptCard(
    item: ConceptItem,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "Ver menos" else "Saber más",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            )
            
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Detalle Técnico Avanzado",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = item.technicalDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

data class FrequencyBlock(
    val leftMain: String,
    val rightMain: String,
    val leftSub: String? = null,
    val rightSub: String? = null,
    val isHeader: Boolean = false
)

@Composable
fun FrequencyCard(
    tech: String,
    protocol: String,
    blocks: List<FrequencyBlock>,
    accentColor: Color,
    isAlert: Boolean = false
) {
    val lightAccentColor = accentColor.copy(alpha = 0.12f)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isAlert) accentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(lightAccentColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tech,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                
                // Protocol Badge
                Box(
                    modifier = Modifier
                        .background(lightAccentColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = protocol,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        textAlign = TextAlign.End
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Blocks
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                blocks.forEach { block ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Column
                            Column(modifier = Modifier.weight(1f)) {
                                if (block.isHeader) {
                                    Text(
                                        text = block.leftMain,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    block.leftSub?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    Text(
                                        text = block.leftMain,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    block.leftSub?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            // Separator
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Right Column
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                if (block.isHeader) {
                                    Text(
                                        text = block.rightMain,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    block.rightSub?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    Text(
                                        text = block.rightMain,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    block.rightSub?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrequencyContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FrequencyCard(
            tech = "2G",
            protocol = "GSM",
            accentColor = Color(0xFF2E7D32),
            blocks = listOf(
                FrequencyBlock(leftMain = "Frecuencia", rightMain = "Banda", leftSub = "900 MHz", rightSub = "B8", isHeader = true),
                FrequencyBlock(leftMain = "Canales", rightMain = "BW", leftSub = "canales", rightSub = "200 kHz/canal", isHeader = true)
            )
        )

        FrequencyCard(
            tech = "3G",
            protocol = "UMTS",
            accentColor = Color(0xFF1565C0),
            blocks = listOf(
                FrequencyBlock(leftMain = "900 MHz", rightMain = "B8", leftSub = "3 portadoras", rightSub = "5 MHz c/u"),
                FrequencyBlock(leftMain = "2100 MHz", rightMain = "B1", leftSub = "? portadoras", rightSub = "?")
            )
        )

        FrequencyCard(
            tech = "4G",
            protocol = "LTE",
            accentColor = Color(0xFF6A1B9A),
            blocks = listOf(
                FrequencyBlock(leftMain = "700 MHz", rightMain = "B28", leftSub = "2 portadoras", rightSub = "20 + 10 MHz"),
                FrequencyBlock(leftMain = "900 MHz", rightMain = "B8", leftSub = "1 portadora", rightSub = "10 MHz"),
                FrequencyBlock(leftMain = "1800 MHz", rightMain = "B3", leftSub = "2 portadoras", rightSub = "20 MHz c/u"),
                FrequencyBlock(leftMain = "2100 MHz", rightMain = "B1", leftSub = "1 portadora", rightSub = "20 MHz")
            )
        )

        FrequencyCard(
            tech = "5G",
            protocol = "ESPERADO\nNO CONFIRMADO",
            accentColor = Color(0xFFC62828),
            isAlert = true,
            blocks = listOf(
                FrequencyBlock(leftMain = "Frecuencia", rightMain = "Banda", leftSub = "3500 MHz", rightSub = "n78", isHeader = true),
                FrequencyBlock(leftMain = "Portadoras", rightMain = "BW", leftSub = "?", rightSub = "?", isHeader = true)
            )
        )
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
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun RefarmingContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

        SectionTitle("Qué hay en 900 MHz hoy")
        CompactDataList(listOf(
            "2G GSM: opera en 900 MHz (canales ARFCN de 200 kHz).",
            "3G UMTS: típicamente tres portadoras (5 MHz c/u) con UARFCN 2947, 2972 y 2997.",
            "4G LTE Band 8 (900): una portadora de 10 MHz (bloque contiguo dentro de 925–960 MHz DL / 880–915 MHz UL)."
        ))

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

        SectionTitle("Aspectos Positivos")
        CompactDataList(listOf(
            "1- Reducción del costo de expansión:\nPermite añadir LTE en la misma frecuencia que 2/3G sin necesidad de implementar nuevos paneles o RRU",
            "2- Mejor cobertura 4G:\nLa banda 900 MHz tiene gran alcance y buena penetración en interiores.\n➤ Beneficia zonas rurales y urbanas con mala cobertura de las bandas altas (B3/B1).",
            "3- Eficiencia espectral y mejor experiencia:\nLTE B8 ofrece mayor capacidad de datos por MHz respecto a UMTS 900 o GSM, mejorando velocidades y eficiencia energética por bit transmitido.",
            "4- Capacidad Adicional\nPosibilidad de hacer agregación de portadoras (4G+) con otras bandas aumentando así el ancho de banda final."
        ))

        SectionTitle("Aspectos Negativos")
        CompactDataList(listOf(
            "1- Competencia entre tecnologías por el espectro:\nLos 35 MHz del bloque DL (925–960 MHz) deben repartirse entre 2G, 3G y 4G.\n➤ Esto obliga a compactar GSM y reducir UMTS, lo que puede degradar el servicio de voz o datos 3G en celdas con alta carga.",
            "2- Limitaciones de capacidad:\nCon solo 10 MHz asignados a LTE B8, la capacidad de datos es modesta.\n➤ Aunque mejora la cobertura, no ofrece grandes velocidades ni soporta mucho tráfico por lo que se necesita un buen balance de carga para evitar su congestión."
        ))
    }
}

@Composable
fun MapContent() {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(450.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.mapa_tecnologia),
                contentDescription = "Mapa de tecnología en Cuba",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun InfoCard(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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