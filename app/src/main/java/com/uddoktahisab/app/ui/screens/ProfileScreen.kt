package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.uddoktahisab.app.data.model.User
import com.uddoktahisab.app.ui.components.BrandMark

@OptIn(ExperimentalMaterial3Api::class)
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
    val keyboard = LocalSoftwareKeyboardController.current  // ← যোগ
    val focus = LocalFocusManager.current
    Scaffold(topBar = {
        TopAppBar(
            title = { BrandMark(true) },
            actions = { TextButton(onLogout) { Text("লগআউট") } })
    }) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp)); Text(
                if (locked) "প্রোফাইল ১০০% সম্পন্ন করুন" else "আমার প্রোফাইল",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold
            ); Text(
                "সব তথ্য পূরণ না করা পর্যন্ত অন্য কোনো ফিচার দেখা যাবে না।",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
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
                {
                    keyboard?.hide()
                    focus.clearFocus(force = true)
                    onSave(name, present, permanent, phone, father, nid)
                },
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