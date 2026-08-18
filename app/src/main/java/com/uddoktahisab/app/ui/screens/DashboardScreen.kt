package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.BootstrapData
import com.uddoktahisab.app.data.model.Dashboard
import com.uddoktahisab.app.data.model.SaleRecord
import com.uddoktahisab.app.data.model.User
import com.uddoktahisab.app.data.model.UserSummary
import com.uddoktahisab.app.ui.components.BrandMark
import com.uddoktahisab.app.ui.components.EmptyCard
import com.uddoktahisab.app.ui.components.MetricCard
import com.uddoktahisab.app.ui.components.RecordCard
import com.uddoktahisab.app.ui.components.money
import com.uddoktahisab.app.ui.components.number
import com.uddoktahisab.app.viewmodel.AppViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** সময় অনুযায়ী শুভেচ্ছা — সকাল / দুপুর / অপরাহ্ন / সন্ধ্যা */
private fun greetingText(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "শুভ সকাল"
        in 12..15 -> "শুভ দুপুর"
        in 16..17 -> "শুভ অপরাহ্ন"
        else -> "শুভ সন্ধ্যা"
    }
}

/** সবার dashboard যোগ করে combined dashboard রিটার্ন করে */
private fun combinedDashboard(summaries: List<UserSummary>): Dashboard =
    summaries.fold(Dashboard()) { acc, s ->
        Dashboard(
            todayQuantity = acc.todayQuantity + s.dashboard.todayQuantity,
            todaySales = acc.todaySales + s.dashboard.todaySales,
            monthQuantity = acc.monthQuantity + s.dashboard.monthQuantity,
            monthSales = acc.monthSales + s.dashboard.monthSales,
            recordCount = acc.recordCount + s.dashboard.recordCount
        )
    }

/** সবার recent records মিলিয়ে date অনুসারে sort করে — admin combined view তে দেখায় */
private fun combinedRecent(summaries: List<UserSummary>): List<SaleRecord> =
    summaries.flatMap { it.dashboard.recentRecords }
        .sortedByDescending { it.date }
        .take(20)

/** Date (YYYY-MM-DD) এবং createdAt (ISO 8601) থেকে 12hr ফরম্যাটে তারিখ ও সময় তৈরি */
private fun formatRecordDateTime(date: String, createdAt: String): String {
    if (createdAt.isNotBlank()) {
        try {
            val local = Instant.parse(createdAt).atZone(ZoneId.systemDefault())
            val time = local.format(DateTimeFormatter.ofPattern("hh:mm a"))
            val d = local.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            return "$d $time"
        } catch (_: Exception) {}
    }
    // Fallback: শুধু date থাকলে
    try { val d = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM d, yyyy")); return "$d" }
    catch (_: Exception) {}
    return date.ifBlank { "" }
}

/** User dashboard-এর recent record বিস্তারিত কার্ড — বিক্রেতা ছাড়া */
@Composable
private fun UserRecordCard(r: SaleRecord) {
    val dateTime = remember(r.date, r.createdAt) { formatRecordDateTime(r.date, r.createdAt) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("তারিখ: $dateTime", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("পরিমাণ: ${number(r.quantity)} ${r.unit}", fontSize = 13.sp)
            Text("একক মূল্য: ${money(r.unitPrice)}", fontSize = 13.sp)
            Text("মোট মূল্য: ${money(r.total)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Admin dashboard-এর recent record বিস্তারিত কার্ড */
@Composable
private fun AdminRecordCard(r: SaleRecord) {
    val dateTime = remember(r.date, r.createdAt) { formatRecordDateTime(r.date, r.createdAt) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("বিক্রেতা: ${r.userName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("তারিখ: $dateTime", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("পরিমাণ: ${number(r.quantity)} ${r.unit}", fontSize = 13.sp)
            Text("একক মূল্য: ${money(r.unitPrice)}", fontSize = 13.sp)
            Text("মোট মূল্য: ${money(r.total)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(data: BootstrapData, vm: AppViewModel) {
    val isAdmin = data.user.role == com.uddoktahisab.app.data.model.Role.SUPER_ADMIN
    val greeting = remember { greetingText() }

    // ─── Swipe-to-refresh ───
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            try {
                vm.refresh()
                snapshotFlow { vm.state.value.loading }
                    .filter { !it }
                    .first()
            } finally {
                refreshing = false
            }
        }
    }

    val combined = if (isAdmin) combinedDashboard(data.userSummaries) else data.dashboard
    val recent = if (isAdmin) combinedRecent(data.userSummaries) else data.dashboard.recentRecords

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true },
        state = rememberPullToRefreshState(),
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BrandMark(true); Spacer(Modifier.height(18.dp)); Text(
                "$greeting,",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ); Text(data.user.fullName, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }

            if (isAdmin) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🌐 সবার মোট হিসাব",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            "আজকের বিক্রি",
                            money(combined.todaySales),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "আজকের পরিমাণ",
                            "${number(combined.todayQuantity)} kg",
                            Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            "মাসিক বিক্রি",
                            money(combined.monthSales),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "মাসিক পরিমাণ",
                            "${number(combined.monthQuantity)} kg",
                            Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .heightIn(min = 56.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "মোট রেকর্ড",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "${combined.recordCount} টি",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                item {
                    Text("সকলের সাম্প্রতিক হিসাব",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            "আজকের বিক্রি",
                            money(data.dashboard.todaySales),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "আজকের পরিমাণ",
                            "${number(data.dashboard.todayQuantity)} kg",
                            Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            "মাসিক বিক্রি",
                            money(data.dashboard.monthSales),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "মাসিক পরিমাণ",
                            "${number(data.dashboard.monthQuantity)} kg",
                            Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Text(
                        "সাম্প্রতিক হিসাব",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (recent.isEmpty()) {
                item { EmptyCard("এখনও কোনো বিক্রির হিসাব নেই") }
            } else {
                items(recent.take(8)) { r ->
                    if (isAdmin) AdminRecordCard(r)
                    else UserRecordCard(r)
                }
            }
        }
    }

}
