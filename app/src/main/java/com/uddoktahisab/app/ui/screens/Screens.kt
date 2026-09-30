@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.viewmodel.AppViewModel

data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun MainShell(data: BootstrapData, vm: AppViewModel) {
    val s by vm.state.collectAsState()
    val admin = data.user.role == Role.SUPER_ADMIN
    val items = remember(admin) {
        buildList {
            add(NavItem("ড্যাশবোর্ড", Icons.Rounded.Dashboard))
            add(NavItem("বিক্রি", Icons.Rounded.AddCircle))
            add(NavItem("হিসাব", Icons.Rounded.ReceiptLong))
            if (admin) add(NavItem("অ্যাডমিন", Icons.Rounded.AdminPanelSettings))
            add(NavItem("প্রোফাইল", Icons.Rounded.Person))
        }
    }
    val page = s.selectedTab.coerceIn(0, items.lastIndex)

    val historyRecords = remember(admin, data.dashboard.recentRecords, data.userSummaries) {
        if (admin && data.userSummaries.isNotEmpty()) {
            (data.userSummaries.flatMap { it.dashboard.recentRecords } + data.dashboard.recentRecords)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt.ifBlank { it.date } }
        } else {
            data.dashboard.recentRecords
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                items.forEachIndexed { i, x ->
                    NavigationBarItem(
                        selected = page == i,
                        onClick = { vm.selectTab(i) },
                        icon = { Icon(x.icon, contentDescription = x.label) },
                        label = {
                            Text(
                                text = x.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
        ) {
            when (items[page].label) {
                "ড্যাশবোর্ড" -> DashboardScreen(data, vm)
                "বিক্রি" -> SaleScreen(data.tasks, vm::addSale)
                "হিসাব" -> HistoryScreen(historyRecords, vm::requestChange, admin)
                "অ্যাডমিন" -> AdminScreen(data, vm)
                else -> ProfileScreen(data.user, vm::completeProfile, false, vm::logout)
            }
        }
    }
}
