package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.SaleRecord
import com.uddoktahisab.app.ui.components.EmptyCard
import com.uddoktahisab.app.ui.components.money
import com.uddoktahisab.app.ui.components.number
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val banglaDays = listOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")

private fun formatDateTime(date: String, createdAt: String): String {
    if (createdAt.isNotBlank()) {
        try {
            val local = Instant.parse(createdAt).atZone(ZoneId.systemDefault())
            val time = local.format(DateTimeFormatter.ofPattern("hh:mm a"))
            val d = local.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            return "$d $time"
        } catch (_: Exception) {}
    }
    try { return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) }
    catch (_: Exception) {}
    return date
}

private fun getBanglaDay(date: String): String {
    try {
        val day = LocalDate.parse(date).dayOfWeek.value % 7
        return banglaDays[day]
    } catch (_: Exception) { return "" }
}

@Composable
private fun HistoryRecordCard(r: SaleRecord, onEdit: (() -> Unit)? = null) {
    val dateTime = remember(r.date, r.createdAt) { formatDateTime(r.date, r.createdAt) }
    val bar = remember(r.date) { getBanglaDay(r.date) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("তারিখ: $dateTime", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("বার/দিন: $bar", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("পরিমাণ: ${number(r.quantity)} ${r.unit}", fontSize = 13.sp)
                Text("একক মূল্য: ${money(r.unitPrice)}", fontSize = 13.sp)
                Text("নোট: ${r.note}", fontSize = 13.sp)
                Text("মোট মূল্য: ${money(r.total)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            onEdit?.let {
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = it) {
                    Icon(Icons.Rounded.EditNote, "পরিবর্তনের অনুরোধ")
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    records: List<SaleRecord>,
    onRequest: (SaleRecord, Double, Double, String, String) -> Unit,
    isAdmin: Boolean = false
) {
    var editing by remember { mutableStateOf<SaleRecord?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "আমার বিক্রির হিসাব",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (isAdmin) "সকলের বিক্রির হিসাব" else "পরিবর্তন করতে অ্যাডমিনের অনুমতি আবশ্যক",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        if (records.isEmpty()) item { EmptyCard("কোনো হিসাব পাওয়া যায়নি") }
        items(records) { r ->
            HistoryRecordCard(
                r = r,
                onEdit = if (isAdmin) null else { { editing = r } }
            )
        }
    }
    editing?.let { r ->
        ChangeDialog(r, { editing = null }) { q, p, n, reason ->
            onRequest(r, q, p, n, reason); editing = null
        }
    }
}

@Composable
private fun ChangeDialog(
    r: SaleRecord,
    onDismiss: () -> Unit,
    onSend: (Double, Double, String, String) -> Unit
) {
    var q by remember { mutableStateOf(r.quantity.toString()) }
    var p by remember { mutableStateOf(r.unitPrice.toString()) }
    var n by remember { mutableStateOf(r.note) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পরিবর্তনের অনুমতি চান") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text("নতুন পরিমাণ") })
                OutlinedTextField(p, { p = it }, label = { Text("নতুন মূল্য") })
                OutlinedTextField(n, { n = it }, label = { Text("নতুন নোট") })
                OutlinedTextField(reason, { reason = it }, label = { Text("পরিবর্তনের কারণ*") })
            }
        },
        confirmButton = {
            Button({
                keyboard?.hide()
                focus.clearFocus(force = true)
                onSend(q.toDoubleOrNull() ?: 0.0, p.toDoubleOrNull() ?: 0.0, n, reason)
            }, enabled = reason.isNotBlank()) { Text("রিকোয়েস্ট পাঠান") }
        },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } }
    )
}