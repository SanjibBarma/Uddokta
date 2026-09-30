package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.BootstrapData
import com.uddoktahisab.app.data.model.Sku
import com.uddoktahisab.app.data.model.Task
import com.uddoktahisab.app.data.model.User
import com.uddoktahisab.app.data.model.UserSummary
import com.uddoktahisab.app.ui.components.EmptyCard
import com.uddoktahisab.app.ui.components.MetricCard
import com.uddoktahisab.app.ui.components.RecordCard
import com.uddoktahisab.app.ui.components.money
import com.uddoktahisab.app.ui.components.number
import com.uddoktahisab.app.viewmodel.AppViewModel

@Composable
fun AdminScreen(data: BootstrapData, vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var add by remember { mutableStateOf(false) }
    var assignUser by remember { mutableStateOf<User?>(null) }
    var viewSummary by remember { mutableStateOf<UserSummary?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(20.dp)
    ) {
        Text(
            "সুপার অ্যাডমিন",
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold
        )
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = {
                    Text(
                        "ইউজার",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = {
                    Text(
                        "অনুমতির অনুরোধ",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = {
                    Text(
                        "SKU",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
            )
        }
        Spacer(Modifier.height(14.dp))

        if (tab == 0) {
            Button({ add = true }) {
                Icon(Icons.Rounded.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("নতুন ইউজার")
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(data.users) { u ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    u.fullName.ifBlank { u.username },
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (u.profileComplete) "প্রোফাইল সম্পন্ন" else "প্রোফাইল অসম্পূর্ণ",
                                    color = if (u.profileComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                                data.userSummaries.find { it.user.id == u.id }?.dashboard?.let {
                                    Text(
                                        "আজ ${money(it.todaySales)} • মাসে ${money(it.monthSales)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton({
                                    viewSummary = data.userSummaries.find { it.user.id == u.id }
                                }) { Text("হিসাব") }
                                TextButton({ assignUser = u }) { Text("কাজ দিন") }
                            }
                        }
                    }
                }
            }
        } else if (tab == 1) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val requests = data.requests.filter { it.status == "PENDING" }
                if (requests.isEmpty()) {
                    item { EmptyCard("কোনো অপেক্ষমাণ অনুরোধ নেই") }
                }
                items(requests) { r ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(r.userName, fontWeight = FontWeight.Bold)
                            Text(
                                "নতুন পরিমাণ: ${number(r.newQuantity)} • নতুন মূল্য: ${money(r.newUnitPrice)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (r.newNote.isNotBlank()) {
                                Text("নোট: ${r.newNote}", fontSize = 12.sp)
                            }
                            Text("কারণ: ${r.reason}", fontSize = 13.sp)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton({ vm.decide(r.id, false) }) {
                                    Text("বাতিল", color = MaterialTheme.colorScheme.error)
                                }
                                Button({ vm.decide(r.id, true) }) { Text("অনুমোদন") }
                            }
                        }
                    }
                }
            }
        } else if (tab == 2) {
            SkuTab(data.skus.orEmpty(), vm, Modifier.weight(1f))
        }
    }

    viewSummary?.let {
        AdminUserDialog(it) { viewSummary = null }
    }
    if (add) CreateUserDialog({ add = false }, vm::createUser)
    assignUser?.let {
        AssignDialog(
            it,
            data.tasks,
            data.assignments.filter { a -> a.userId == it.id }.map { a -> a.taskId },
            { assignUser = null }
        ) { ids -> vm.assign(it.id, ids); assignUser = null }
    }
}

@Composable
private fun SkuTab(
    skus: List<Sku>,
    vm: AppViewModel,
    modifier: Modifier = Modifier
) {
    var add by remember { mutableStateOf(false) }
    var purchaseFor by remember { mutableStateOf<Sku?>(null) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SKU ইউনিট ব্যবস্থাপনা",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button({ add = true }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("নতুন SKU")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (skus.isEmpty()) {
            Text(
                "কোনো SKU নেই। নতুন SKU যোগ করুন।",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(skus) { sku ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(sku.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("একক: ${sku.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton({ purchaseFor = sku }) {
                                        Text("আরো কিনেছি", fontSize = 12.sp)
                                    }
                                    IconButton({ vm.deleteSku(sku.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "মুছুন")
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("কেনা: ${number(sku.totalStock)} ${sku.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("বিক্রি: ${number(sku.totalSold)} ${sku.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                Text("বাকি: ${number(sku.remaining)} ${sku.unit}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sku.remaining > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("খরচ: ${money(sku.totalCost)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("বিক্রি: ${money(sku.totalRevenue)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (sku.profit >= 0) "লাভ: ${money(sku.profit)}" else "ক্ষতি: ${money(-sku.profit)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sku.profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) {
        CreateSkuDialog(
            onDismiss = { add = false },
            onCreate = { name, unit, stock, cost ->
                vm.addSku(name, unit, stock, cost)
                add = false
            }
        )
    }
    purchaseFor?.let { sku ->
        AddPurchaseDialog(
            sku = sku,
            onDismiss = { purchaseFor = null },
            onConfirm = { qty, cost ->
                vm.addPurchase(sku.id, qty, cost)
                purchaseFor = null
            }
        )
    }
}

@Composable
private fun AddPurchaseDialog(
    sku: Sku,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("আরো কিনেছি — ${sku.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "বর্তমান বাকি: ${number(sku.remaining)} ${sku.unit} • মোট খরচ: ${money(sku.totalCost)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    qty,
                    { qty = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("নতুন কেনা পরিমাণ (${sku.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    cost,
                    { cost = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("মোট খরচ (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                { onConfirm(qty.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0); onDismiss() },
                enabled = (qty.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("যোগ করুন") }
        },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun CreateSkuDialog(onDismiss: () -> Unit, onCreate: (String, String, Double, Double) -> Unit) {
    var n by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("kg") }
    var stock by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন SKU যোগ করুন") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("SKU-এর নাম, একক, প্রাথমিক স্টক ও মোট খরচ দিন।")
                OutlinedTextField(n, { n = it }, label = { Text("SKU নাম (যেমন: চাল)") })
                OutlinedTextField(u, { u = it }, label = { Text("একক (kg / pcs / liter)") })
                OutlinedTextField(
                    stock,
                    { stock = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("প্রাথমিক স্টক (শুধু সংখ্যা)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    cost,
                    { cost = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("মোট খরচ (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                { onCreate(n, u, stock.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0); onDismiss() },
                enabled = n.isNotBlank()
            ) { Text("যোগ করুন") }
        },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun CreateUserDialog(close: () -> Unit, create: (String, String) -> Unit) {
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("নতুন ইউজার তৈরি") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("শুধু অস্থায়ী ইউজারনেম ও পাসওয়ার্ড দিন। ইউজার নিজে প্রোফাইল পূরণ করবে।")
                OutlinedTextField(u, { u = it }, label = { Text("ইউজারনেম") })
                OutlinedTextField(p, { p = it }, label = { Text("অস্থায়ী পাসওয়ার্ড") })
            }
        },
        confirmButton = {
            Button(
                { create(u, p); close() },
                enabled = u.isNotBlank() && p.length >= 6
            ) { Text("তৈরি করুন") }
        },
        dismissButton = { TextButton(close) { Text("বাতিল") } }
    )
}

@Composable
private fun AssignDialog(
    user: User,
    tasks: List<Task>,
    initial: List<String>,
    close: () -> Unit,
    save: (List<String>) -> Unit
) {
    val selected = remember(user.id) {
        mutableStateListOf<String>().apply { addAll(initial) }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("${user.username}-কে কাজ দিন") },
        text = {
            LazyColumn {
                items(tasks) { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (t.id in selected) selected.remove(t.id) else selected.add(t.id)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(t.id in selected, { if (it) selected.add(t.id) else selected.remove(t.id) })
                        Text("${t.name} (${t.unit})")
                    }
                }
            }
        },
        confirmButton = { Button({ save(selected.toList()) }) { Text("অ্যাসাইন করুন") } },
        dismissButton = { TextButton(close) { Text("বাতিল") } }
    )
}

@Composable
private fun AdminUserDialog(summary: UserSummary, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(summary.user.fullName.ifBlank { summary.user.username }) },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("আজ", money(summary.dashboard.todaySales), Modifier.weight(1f))
                        MetricCard("এই মাস", money(summary.dashboard.monthSales), Modifier.weight(1f))
                    }
                }
                item {
                    Text("সকল সাম্প্রতিক হিসাব", fontWeight = FontWeight.Bold)
                }
                if (summary.dashboard.recentRecords.isEmpty()) {
                    item { Text("কোনো হিসাব নেই") }
                }
                items(summary.dashboard.recentRecords) { RecordCard(it) }
            }
        },
        confirmButton = { TextButton(close) { Text("বন্ধ করুন") } }
    )
}
