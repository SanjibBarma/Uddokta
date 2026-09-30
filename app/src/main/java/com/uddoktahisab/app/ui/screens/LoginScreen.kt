package com.uddoktahisab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uddoktahisab.app.ui.components.BrandMark

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
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(); Spacer(Modifier.height(40.dp)); Card(
            shape = RoundedCornerShape(30.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("স্বাগতম", fontSize = 25.sp, fontWeight = FontWeight.Bold);

                Text("ইউজারনেম ও পাসওয়ার্ড দিয়ে প্রবেশ করুন", color = MaterialTheme.colorScheme.onSurfaceVariant);

                OutlinedTextField(
                    user,
                    { user = it; onClear() },
                    Modifier.fillMaxWidth(),
                    label = { Text("ইউজারনেম") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                );

                OutlinedTextField(
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
