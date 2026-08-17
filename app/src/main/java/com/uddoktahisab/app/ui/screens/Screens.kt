@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.uddoktahisab.app.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.viewmodel.AppViewModel

data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MainShell(data: BootstrapData, vm: AppViewModel) {
    val admin = data.user.role == Role.SUPER_ADMIN;
    val items = buildList {
        add(NavItem("ড্যাশবোর্ড", Icons.Rounded.Dashboard)); add(
        NavItem(
            "বিক্রি",
            Icons.Rounded.AddCircle
        )
    ); add(NavItem("হিসাব", Icons.Rounded.ReceiptLong)); if (admin) add(
        NavItem(
            "অ্যাডমিন",
            Icons.Rounded.AdminPanelSettings
        )
    ); add(NavItem("প্রোফাইল", Icons.Rounded.Person))
    };
    var page by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            items.forEachIndexed { i, x ->
                NavigationBarItem(
                    selected = page == i,
                    onClick = { page = i },
                    icon = { Icon(x.icon, null) },
                    label = { Text(x.label) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (items[page].label) {
                "ড্যাশবোর্ড" -> DashboardScreen(data); "বিক্রি" -> SaleScreen(
                data.tasks,
                vm::addSale
            ); "হিসাব" -> HistoryScreen(
                data.dashboard.recentRecords,
                vm::requestChange
            ); "অ্যাডমিন" -> AdminScreen(data, vm); else -> ProfileScreen(
                data.user,
                vm::completeProfile,
                false,
                vm::logout
            )
            }
        }
    }
}