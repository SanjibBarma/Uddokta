package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.BootstrapData
import com.uddoktahisab.app.data.model.Dashboard
import com.uddoktahisab.app.data.model.Role
import com.uddoktahisab.app.data.model.SaleRecord
import com.uddoktahisab.app.data.model.UserSummary
import com.uddoktahisab.app.ui.components.BrandMark
import com.uddoktahisab.app.ui.components.EmptyCard
import com.uddoktahisab.app.ui.components.MetricCard
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

/** সবার recent records মিলিয়ে সর্বশেষ এন্ট্রি (createdAt) অনুসারে সাজায় — যাতে নতুন এন্ট্রি সবার উপরে দেখায় */
private fun combinedRecent(summaries: List<UserSummary>): List<SaleRecord> =
    summaries.flatMap { it.dashboard.recentRecords }
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<SaleRecord> { it.createdAt }
                .thenByDescending { it.date }
        )
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
    try {
        val d = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        return d
    } catch (_: Exception) {}
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
            if (r.taskName.isNotBlank()) {
                Text("কাজ: ${r.taskName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Text("তারিখ: $dateTime", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("পরিমাণ: ${number(r.quantity)} ${r.unit}", fontSize = 13.sp)
            Text("একক মূল্য: ${money(r.unitPrice)}", fontSize = 13.sp)
            Text(
                "মোট মূল্য: ${money(r.total)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Admin dashboard-এর recent record বিস্তারিত কার্ড — Long Press করলে ডিলিট অপশন আসবে */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AdminRecordCard(
    r: SaleRecord,
    onLongPressDelete: () -> Unit
) {
    val dateTime = remember(r.date, r.createdAt) { formatRecordDateTime(r.date, r.createdAt) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPressDelete
            )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("বিক্রেতা: ${r.userName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (r.taskName.isNotBlank()) {
                Text("কাজ: ${r.taskName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Text("তারিখ: $dateTime", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("পরিমাণ: ${number(r.quantity)} ${r.unit}", fontSize = 13.sp)
            Text("একক মূল্য: ${money(r.unitPrice)}", fontSize = 13.sp)
            Text(
                "মোট মূল্য: ${money(r.total)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(data: BootstrapData, vm: AppViewModel) {
    val isAdmin = data.user.role == Role.SUPER_ADMIN
    val greeting = remember { greetingText() }
    var recordToDelete by remember { mutableStateOf<SaleRecord?>(null) }

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
    val recent = remember(isAdmin, data.userSummaries, data.dashboard.recentRecords) {
        if (isAdmin) {
            combinedRecent(data.userSummaries)
        } else {
            data.dashboard.recentRecords.sortedWith(
                compareByDescending<SaleRecord> { it.createdAt }
                    .thenByDescending { it.date }
            )
        }
    }

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
                BrandMark(true)
                Spacer(Modifier.height(18.dp))
                Text(
                    "$greeting,",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(data.user.fullName, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
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
                            "সর্বমোট বিক্রি",
                            money(combined.monthSales),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "সর্বমোট পরিমাণ",
                            "${number(combined.monthQuantity)} kg",
                            Modifier.weight(1f)
                        )
                    }
                }
                val skus = data.skus.orEmpty()
                if (skus.isNotEmpty()) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("SKU স্টক বিবরণ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                skus.take(8).forEach { sku ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    sku.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    if (sku.profit >= 0) "লাভ ${money(sku.profit)}" else "ক্ষতি ${money(-sku.profit)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sku.profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("কেনা: ${number(sku.totalStock)} ${sku.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                Text("বিক্রি: ${number(sku.totalSold)} ${sku.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                                Text("বাকি: ${number(sku.remaining)} ${sku.unit}", fontSize = 12.sp, color = if (sku.remaining > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("খরচ: ${money(sku.totalCost)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("বিক্রি: ${money(sku.totalRevenue)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                    Column {
                        Text(
                            "সকলের সাম্প্রতিক হিসাব",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "যেকোনো হিসাব মুছে ফেলতে আইটেমের ওপর চাপ দিয়ে ধরে রাখুন (Long Press)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            "সর্বমোট বিক্রি",
                            money(data.dashboard.monthSales),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "সর্বমোট পরিমাণ",
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
                items(recent.take(20), key = { it.id }) { r ->
                    if (isAdmin) {
                        AdminRecordCard(
                            r = r,
                            onLongPressDelete = { recordToDelete = r }
                        )
                    } else {
                        UserRecordCard(r)
                    }
                }
            }
        }
    }

    recordToDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("হিসাব মুছে ফেলুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("আপনি কি নিশ্চিত যে এই বিক্রির হিসাবটি মুছে ফেলতে চান?")
                    Spacer(Modifier.height(4.dp))
                    Text("বিক্রেতা: ${rec.userName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (rec.taskName.isNotBlank()) {
                        Text("কাজ: ${rec.taskName}", fontSize = 13.sp)
                    }
                    Text("পরিমাণ: ${number(rec.quantity)} ${rec.unit}", fontSize = 13.sp)
                    Text(
                        "মোট মূল্য: ${money(rec.total)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "এটি মুছে ফেললে মোট বিক্রি ও পরিমাণের হিসাব থেকেও বাদ যাবে।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteRecord(rec.id)
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
