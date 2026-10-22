package com.easylife.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.easylife.app.core.i18n.SNav
import com.easylife.app.core.i18n.Txt
import com.easylife.app.core.i18n.t
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val label: Txt, val icon: ImageVector)

/** Today · Modules · [Scan] · Settings, with a big raised Scan button in the middle. */
@Composable
fun EasyBottomBar(current: NavDestination?, onSelect: (Any) -> Unit, onScan: () -> Unit) {
    val left = listOf(Tab(TodayRoute, TodayRoute::class, SNav.today, Icons.Rounded.WbSunny), Tab(ModulesRoute, ModulesRoute::class, SNav.modules, Icons.Rounded.GridView))
    val right = listOf(Tab(SettingsRoute, SettingsRoute::class, SNav.settings, Icons.Rounded.Settings))
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            left.forEach { TabItem(it, current, onSelect) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val label = SNav.scan.t()
                    FloatingActionButton(
                        onClick = onScan,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier.size(58.dp).semantics { contentDescription = label },
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
            }
            right.forEach { TabItem(it, current, onSelect) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, current: NavDestination?, onSelect: (Any) -> Unit) {
    val selected = current?.hasRoute(tab.cls) == true
    NavigationBarItem(
        selected = selected,
        onClick = { onSelect(tab.route) },
        icon = { Icon(tab.icon, contentDescription = null) },
        label = { Text(tab.label.t(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.padding(top = 2.dp),
    )
}
