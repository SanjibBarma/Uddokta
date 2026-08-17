package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.data.model.Task
import com.uddoktahisab.app.ui.components.money
import java.time.LocalDate
import kotlin.collections.forEach

@Composable
fun SaleScreen(tasks: List<Task>, onSave: (Task, String, Double, Double, String) -> Unit) {
    var selected by remember { mutableStateOf<Task?>(null) };
    var date by remember { mutableStateOf(LocalDate.now().toString()) };
    var qty by remember { mutableStateOf("") };
    var price by remember { mutableStateOf("") };
    var note by remember { mutableStateOf("") };
    var expanded by remember { mutableStateOf(false) };
    val total = (qty.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0)
    val keyboard = LocalSoftwareKeyboardController.current  // ← যোগ
    val focus = LocalFocusManager.current
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
            }; DropdownMenu(
            expanded,
            {
                expanded = false
            }) {
            tasks.forEach {
                DropdownMenuItem(
                    { Text("${it.name} • ${it.unit}") },
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
                keyboard?.hide()
                focus.clearFocus(force = true)
                selected?.let {
                    onSave(it, date, qty.toDouble(), price.toDouble(), note)
                    qty = ""; price = ""; note = ""
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
