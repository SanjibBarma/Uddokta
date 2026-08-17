package com.uddoktahisab.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uddoktahisab.app.ui.components.LoadingOverlay
import com.uddoktahisab.app.ui.screens.*
import com.uddoktahisab.app.viewmodel.AppViewModel

@Composable
fun AppNavigation(vm: AppViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when {
            !s.loggedIn -> LoginScreen(s.error, vm::login, vm::clearError)
            s.data == null || s.data!!.user.profileComplete != true -> ProfileScreen(
                s.data?.user, vm::completeProfile, true, vm::logout
            )
            else -> MainShell(s.data!!, vm)
        }
        if (s.offline && s.loggedIn && !s.loading) {
            AssistChip(
                onClick = {},
                label = { Text("Offline mode • ডাটা পরে sync হবে") },
                leadingIcon = {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.CloudOff,
                        null
                    )
                },
                modifier = Modifier
                    .padding(12.dp)
                    .align(androidx.compose.ui.Alignment.TopCenter)
            )
        }
        val message = if (s.loggedIn) (s.error ?: s.notice) else null
        if (message != null) {
            Snackbar(
                Modifier
                    .padding(16.dp)
                    .align(androidx.compose.ui.Alignment.BottomCenter),
                action = { TextButton(vm::clearError) { Text("ঠিক আছে") } }) { Text(message) }
        }
        if (s.loading) LoadingOverlay()
    }
}
