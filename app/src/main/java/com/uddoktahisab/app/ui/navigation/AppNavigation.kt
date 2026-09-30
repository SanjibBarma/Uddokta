package com.uddoktahisab.app.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uddoktahisab.app.ui.components.LoadingOverlay
import com.uddoktahisab.app.ui.screens.*
import com.uddoktahisab.app.viewmodel.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun AppNavigation(vm: AppViewModel = hiltViewModel()) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1500L)
        showSplash = false
    }

    Crossfade(
        targetState = showSplash,
        animationSpec = tween(400),
        label = "SplashTransition"
    ) { isSplash ->
        if (isSplash) {
            SplashScreen()
        } else {
            val s by vm.state.collectAsState()
            Box(Modifier.fillMaxSize()) {
                when {
                    !s.loggedIn -> LoginScreen(s.error, vm::login, vm::clearError)
                    s.data?.user?.profileComplete != true -> ProfileScreen(
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
                                Icons.Rounded.CloudOff,
                                null
                            )
                        },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(12.dp)
                            .align(Alignment.TopCenter)
                    )
                }
                val message = if (s.loggedIn) (s.error ?: s.notice) else null
                if (message != null) {
                    val bottomPadding = if (s.data?.user?.profileComplete == true) 88.dp else 16.dp
                    Snackbar(
                        Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = bottomPadding)
                            .align(Alignment.BottomCenter),
                        action = { TextButton(vm::clearError) { Text("ঠিক আছে") } }
                    ) {
                        Text(message)
                    }
                }
                if (s.loading) LoadingOverlay()
            }
        }
    }
}
