package cu.cuban.cmcubano.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(var route: String, var icon: ImageVector, var title: String) {
    object Datos : NavigationItem("datos", Icons.Default.DataUsage, "Datos")
    object Utiles : NavigationItem("utiles", Icons.Default.Build, "Útiles")
    object Mas : NavigationItem("mas", Icons.Default.Menu, "Más")
} 