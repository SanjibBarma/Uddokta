package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.SaleRecord
import com.uddoktahisab.app.ui.components.EmptyCard
import com.uddoktahisab.app.ui.components.RecordCard

@Composable
fun HistoryScreen(
    records: List<SaleRecord>,
    onRequest: (SaleRecord, Double, Double, String, String) -> Unit
) {
    var editing by remember { mutableStateOf<SaleRecord?>(null) }; LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "আমার বিক্রির হিসাব",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold
            ); Text(
            "পরিবর্তন করতে অ্যাডমিনের অনুমতি আবশ্যক",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }; if (records.isEmpty()) item { EmptyCard("কোনো হিসাব পাওয়া যায়নি") }; items(records) { r ->
        RecordCard(
            r
        ) { editing = r }
    }
    }; editing?.let { r ->
        ChangeDialog(r, { editing = null }) { q, p, n, reason ->
            onRequest(
                r,
                q,
                p,
                n,
                reason
            ); editing = null
        }
    }
}

@Composable
private fun ChangeDialog(
    r: SaleRecord,
    onDismiss: () -> Unit,
    onSend: (Double, Double, String, String) -> Unit
) {
    var q by remember { mutableStateOf(r.quantity.toString()) };
    var p by remember { mutableStateOf(r.unitPrice.toString()) };
    var n by remember { mutableStateOf(r.note) };
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var reason by remember { mutableStateOf("") }; AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পরিবর্তনের অনুমতি চান") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    q,
                    { q = it },
                    label = { Text("নতুন পরিমাণ") }); OutlinedTextField(
                p,
                { p = it },
                label = { Text("নতুন মূল্য") }); OutlinedTextField(
                n,
                { n = it },
                label = { Text("নতুন নোট") }); OutlinedTextField(
                reason,
                { reason = it },
                label = { Text("পরিবর্তনের কারণ*") })
            }
        },
        confirmButton = {
            Button({
                keyboard?.hide()
                focus.clearFocus(force = true)
                onSend(
                    q.toDoubleOrNull() ?: 0.0,
                    p.toDoubleOrNull() ?: 0.0,
                    n,
                    reason
                )
            }, enabled = reason.isNotBlank()) { Text("রিকোয়েস্ট পাঠান") }
        },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } })
}