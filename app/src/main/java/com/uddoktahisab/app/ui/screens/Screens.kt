@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.ui.components.*
import com.uddoktahisab.app.viewmodel.AppViewModel
import java.time.LocalDate

data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun LoginScreen(error: String?, onLogin: (String, String) -> Unit, onClear: () -> Unit) {
    var user by remember { mutableStateOf("") };
    var pass by remember { mutableStateOf("") };
    var visible by remember { mutableStateOf(false) };
    val keyboard = LocalSoftwareKeyboardController.current;
    val focus = LocalFocusManager.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(.10f)
                    )
                )
            )
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(Modifier.widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(); Spacer(Modifier.height(40.dp)); Card(
            shape = RoundedCornerShape(30.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "স্বাগতম",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                ); Text(
                "অ্যাডমিনের দেওয়া ইউজারনেম ও পাসওয়ার্ড দিয়ে প্রবেশ করুন",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ); OutlinedTextField(
                user,
                { user = it; onClear() },
                Modifier.fillMaxWidth(),
                label = { Text("ইউজারনেম") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            ); OutlinedTextField(
                pass,
                { pass = it; onClear() },
                Modifier.fillMaxWidth(),
                label = { Text("পাসওয়ার্ড") },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                trailingIcon = {
                    IconButton({
                        visible = !visible
                    }) {
                        Icon(
                            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            null
                        )
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            ); if (error != null) Text(
                error,
                color = MaterialTheme.colorScheme.error
            ); Button(
                { focus.clearFocus(force = true); keyboard?.hide(); onLogin(user, pass) },
                Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = user.isNotBlank() && pass.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("লগইন করুন", fontWeight = FontWeight.Bold) }
            }
        }
        }
    }
}

@Composable
fun ProfileScreen(
    user: User?,
    onSave: (String, String, String, String, String, String) -> Unit,
    locked: Boolean,
    onLogout: () -> Unit
) {
    var name by remember { mutableStateOf(user?.fullName.orEmpty()) };
    var present by remember { mutableStateOf(user?.presentAddress.orEmpty()) };
    var permanent by remember { mutableStateOf(user?.permanentAddress.orEmpty()) };
    var phone by remember { mutableStateOf(user?.phone.orEmpty()) };
    var father by remember { mutableStateOf(user?.fatherPhone.orEmpty()) };
    var nid by remember { mutableStateOf(user?.nid.orEmpty()) }
    val valid = listOf(name, present, permanent, phone, father, nid).all { it.isNotBlank() }
    Scaffold(topBar = {
        TopAppBar(
            title = { BrandMark(true) },
            actions = { TextButton(onLogout) { Text("লগআউট") } })
    }) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp)); Text(
                if (locked) "প্রোফাইল ১০০% সম্পন্ন করুন" else "আমার প্রোফাইল",
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold
            ); Text(
                "সব তথ্য পূরণ না করা পর্যন্ত অন্য কোনো ফিচার দেখা যাবে না।",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }; item {
            ProfileField("পূর্ণ নাম", name, enabled = locked) {
                name = it
            }
        }; item {
            ProfileField("বর্তমান ঠিকানা", present, enabled = locked) {
                present = it
            }
        }; item {
            ProfileField("স্থায়ী ঠিকানা", permanent, enabled = locked) {
                permanent = it
            }
        }; item {
            ProfileField("ফোন নম্বর", phone, KeyboardType.Phone, locked) {
                phone = it
            }
        }; item {
            ProfileField("বাবার ফোন নম্বর", father, KeyboardType.Phone, locked) {
                father = it
            }
        }; item {
            ProfileField("জাতীয় পরিচয়পত্র নম্বর", nid, KeyboardType.Number, locked) {
                nid = it
            }
        }; if (locked) item {
            Button(
                { onSave(name, present, permanent, phone, father, nid) },
                Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = valid,
                shape = RoundedCornerShape(16.dp)
            ) { Text("প্রোফাইল সম্পন্ন করুন") }
        }
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        singleLine = type != KeyboardType.Text
    )
}

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

@Composable
fun DashboardScreen(data: BootstrapData) {
    val d = data.dashboard; LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandMark(true); Spacer(Modifier.height(18.dp)); Text(
            "আসসালামু আলাইকুম,",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ); Text(data.user.fullName, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        }; item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                "আজকের বিক্রি",
                money(d.todaySales),
                Modifier.weight(1f)
            ); MetricCard("আজকের পরিমাণ", number(d.todayQuantity), Modifier.weight(1f))
        }
    }; item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                "মাসিক বিক্রি",
                money(d.monthSales),
                Modifier.weight(1f)
            ); MetricCard("মাসিক পরিমাণ", number(d.monthQuantity), Modifier.weight(1f))
        }
    }; item {
        Text(
            "সাম্প্রতিক হিসাব",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }; if (d.recentRecords.isEmpty()) item { EmptyCard("এখনও কোনো বিক্রির হিসাব নেই") }; items(
        d.recentRecords.take(
            8
        )
    ) { RecordCard(it) }
    }
}

@Composable
fun SaleScreen(tasks: List<Task>, onSave: (Task, String, Double, Double, String) -> Unit) {
    var selected by remember { mutableStateOf<Task?>(null) };
    var date by remember { mutableStateOf(LocalDate.now().toString()) };
    var qty by remember { mutableStateOf("") };
    var price by remember { mutableStateOf("") };
    var note by remember { mutableStateOf("") };
    var expanded by remember { mutableStateOf(false) };
    val total = (qty.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Text(
                "নতুন বিক্রির হিসাব",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold
            ); Text(
            "আপনাকে দেওয়া কাজ থেকে একটি বেছে নিন",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }; item {
        Box {
            OutlinedButton(
                { expanded = true },
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(selected?.let { "${it.name} (${it.unit})" } ?: "কাজ নির্বাচন করুন"); Spacer(
                Modifier.weight(1f)
            ); Icon(Icons.Rounded.ExpandMore, null)
            }; DropdownMenu(expanded,
            {
                expanded = false
            }) {
            tasks.forEach {
                DropdownMenuItem({ Text("${it.name} • ${it.unit}") },
                    { selected = it; expanded = false })
            }
        }
        }
    }; item {
        OutlinedTextField(
            date,
            { date = it },
            Modifier.fillMaxWidth(),
            label = { Text("তারিখ (YYYY-MM-DD)") },
            shape = RoundedCornerShape(16.dp)
        )
    }; item {
        OutlinedTextField(
            qty,
            { qty = it },
            Modifier.fillMaxWidth(),
            label = { Text("পরিমাণ ${selected?.unit?.let { "($it)" }.orEmpty()}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(16.dp)
        )
    }; item {
        OutlinedTextField(
            price,
            { price = it },
            Modifier.fillMaxWidth(),
            label = { Text("প্রতি ইউনিট বিক্রয় মূল্য") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(16.dp)
        )
    }; item {
        OutlinedTextField(
            note,
            { note = it },
            Modifier.fillMaxWidth(),
            label = { Text("নোট (ঐচ্ছিক)") },
            shape = RoundedCornerShape(16.dp)
        )
    }; item {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("মোট বিক্রি"); Text(
                money(total),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            )
            }
        }
    }; item {
        Button(
            {
                selected?.let {
                    onSave(
                        it,
                        date,
                        qty.toDouble(),
                        price.toDouble(),
                        note
                    ); qty = ""; price = ""; note = ""
                }
            },
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = selected != null && (qty.toDoubleOrNull()
                ?: 0.0) > 0 && (price.toDoubleOrNull() ?: 0.0) >= 0,
            shape = RoundedCornerShape(16.dp)
        ) { Text("হিসাব সংরক্ষণ করুন", fontWeight = FontWeight.Bold) }
    }
    }
}

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
private fun RecordCard(r: SaleRecord, onEdit: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    r.taskName,
                    fontWeight = FontWeight.Bold
                ); Text(
                "${r.date} • ${number(r.quantity)} ${r.unit} × ${money(r.unitPrice)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ); Text(
                money(r.total),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            }; onEdit?.let { IconButton(it) { Icon(Icons.Rounded.EditNote, "পরিবর্তনের অনুরোধ") } }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

@Composable
fun AdminScreen(data: BootstrapData, vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) };
    var add by remember { mutableStateOf(false) };
    var assignUser by remember { mutableStateOf<User?>(null) };
    var viewSummary by remember { mutableStateOf<UserSummary?>(null) }; Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "সুপার অ্যাডমিন",
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold
        ); TabRow(tab) {
        Tab(tab == 0, { tab = 0 }, text = { Text("ইউজার") }); Tab(
        tab == 1,
        { tab = 1 },
        text = { Text("অনুমতির অনুরোধ") })
    }; Spacer(Modifier.height(14.dp)); if (tab == 0) {
        Button({ add = true }) {
            Icon(
                Icons.Rounded.PersonAdd,
                null
            ); Spacer(Modifier.width(8.dp)); Text("নতুন ইউজার")
        }; Spacer(Modifier.height(10.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                data.users
            ) { u ->
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
                            ); Text(
                            if (u.profileComplete) "প্রোফাইল সম্পন্ন" else "প্রোফাইল অসম্পূর্ণ",
                            color = if (u.profileComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        ); data.userSummaries.find { it.user.id == u.id }?.dashboard?.let {
                            Text(
                                "আজ ${money(it.todaySales)} • মাসে ${
                                    money(
                                        it.monthSales
                                    )
                                }",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        }; Column {
                        TextButton({
                            viewSummary = data.userSummaries.find { it.user.id == u.id }
                        }) { Text("হিসাব") }; TextButton({ assignUser = u }) { Text("কাজ দিন") }
                    }
                    }
                }
            }
        }
    } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val requests =
            data.requests.filter { it.status == "PENDING" }; if (requests.isEmpty()) item {
        EmptyCard(
            "কোনো অপেক্ষমাণ অনুরোধ নেই"
        )
    }; items(
        requests
    ) { r ->
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text(
                    r.userName,
                    fontWeight = FontWeight.Bold
                ); Text(r.reason); Row {
                TextButton({ vm.decide(r.id, false) }) {
                    Text(
                        "বাতিল",
                        color = MaterialTheme.colorScheme.error
                    )
                }; Button({ vm.decide(r.id, true) }) { Text("অনুমোদন") }
            }
            }
        }
    }
    }
    }; viewSummary?.let {
        AdminUserDialog(it) {
            viewSummary = null
        }
    }; if (add) CreateUserDialog(
        { add = false },
        vm::createUser
    ); assignUser?.let {
        AssignDialog(
            it,
            data.tasks,
            data.assignments.filter { a -> a.userId == it.id }.map { a -> a.taskId },
            { assignUser = null }) { ids -> vm.assign(it.id, ids); assignUser = null }
    }
}

@Composable
private fun CreateUserDialog(close: () -> Unit, create: (String, String) -> Unit) {
    var u by remember { mutableStateOf("") };
    var p by remember { mutableStateOf("") }; AlertDialog(
        onDismissRequest = close,
        title = { Text("নতুন ইউজার তৈরি") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("শুধু অস্থায়ী ইউজারনেম ও পাসওয়ার্ড দিন। ইউজার নিজে প্রোফাইল পূরণ করবে।"); OutlinedTextField(
                u,
                { u = it },
                label = { Text("ইউজারনেম") }); OutlinedTextField(
                p,
                { p = it },
                label = { Text("অস্থায়ী পাসওয়ার্ড") })
            }
        },
        confirmButton = {
            Button(
                { create(u, p); close() },
                enabled = u.isNotBlank() && p.length >= 6
            ) { Text("তৈরি করুন") }
        },
        dismissButton = { TextButton(close) { Text("বাতিল") } })
}

@Composable
private fun AssignDialog(
    user: User,
    tasks: List<Task>,
    initial: List<String>,
    close: () -> Unit,
    save: (List<String>) -> Unit
) {
    val selected =
        remember(user.id) { mutableStateListOf<String>().apply { addAll(initial) } }; AlertDialog(
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
                            .padding(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(t.id in selected,
                            { if (it) selected.add(t.id) else selected.remove(t.id) }); Text("${t.name} (${t.unit})")
                    }
                }
            }
        },
        confirmButton = { Button({ save(selected.toList()) }) { Text("অ্যাসাইন করুন") } },
        dismissButton = { TextButton(close) { Text("বাতিল") } })
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
                        MetricCard(
                            "আজ",
                            money(summary.dashboard.todaySales),
                            Modifier.weight(1f)
                        ); MetricCard(
                        "এই মাস",
                        money(summary.dashboard.monthSales),
                        Modifier.weight(1f)
                    )
                    }
                }; item {
                Text(
                    "সকল সাম্প্রতিক হিসাব",
                    fontWeight = FontWeight.Bold
                )
            }; if (summary.dashboard.recentRecords.isEmpty()) item { Text("কোনো হিসাব নেই") }; items(
                summary.dashboard.recentRecords
            ) { RecordCard(it) }
            }
        },
        confirmButton = { TextButton(close) { Text("বন্ধ করুন") } })
}
