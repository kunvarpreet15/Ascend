package com.kunvarpreet.ascend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunvarpreet.ascend.ui.navigation.Screen
import com.kunvarpreet.ascend.ui.theme.BorderSubtle
import com.kunvarpreet.ascend.ui.theme.DarkSurface
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.TextMuted

sealed class NavItem(val screen: Screen, val label: String, val icon: ImageVector) {
    object Quests : NavItem(Screen.Dashboard, "Quests", Icons.Default.FitnessCenter)
    object Status : NavItem(Screen.Status, "Status", Icons.Default.Person)
    object Shop : NavItem(Screen.Shop, "Shop", Icons.Default.ShoppingCart)
    object Evolution : NavItem(Screen.Evolution, "Evolution", Icons.Default.Timeline)
}

@Composable
fun AscendBottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem.Quests,
        NavItem.Status,
        NavItem.Shop,
        NavItem.Evolution
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(DarkSurface)
            .border(width = 1.dp, color = BorderSubtle)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.screen.route
            val color = if (isSelected) ElectricCyan else TextMuted

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(item.screen) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
