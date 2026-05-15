package com.jeevabindu.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            val auth = FirebaseAuth.getInstance()
            var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

            // Sync FCM token to Firestore on login or app start
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                        FirebaseFirestore.getInstance()
                            .collection("donors")
                            .document(uid)
                            .update("fcmToken", token)
                    }
                }
            }

            if (isLoggedIn) {
                MainApp(onLogout = { isLoggedIn = false })
            } else {
                AuthScreen(onLoginSuccess = { isLoggedIn = true })
            }
        }
    }
}

@Composable
fun MainApp(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var hasNewAlert by remember { mutableStateOf(false) }
    var alertCount by remember { mutableStateOf(0) }
    var seenCount by remember { mutableStateOf(-1) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    // Real-time listener to show in-app alerts and tab badges
    DisposableEffect(Unit) {
        val listener = db.collection("alerts")
            .addSnapshotListener { snapshot, _ ->
                val currentCount = snapshot?.documents?.size ?: 0

                if (seenCount == -1) {
                    // Initialize
                    seenCount = currentCount
                    alertCount = currentCount
                } else if (currentCount > alertCount) {
                    // New alert detected
                    alertCount = currentCount
                    if (selectedTab != 1) {
                        hasNewAlert = true
                        // Show in-app notification snackbar
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "🚨 New Blood Emergency Alert!",
                                actionLabel = "View",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                selectedTab = 1
                                hasNewAlert = false
                                seenCount = alertCount
                            }
                        }
                    }
                }
            }
        onDispose { listener.remove() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🏠") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = Color(0xFFC62828),
                        indicatorColor = Color(0xFFFFEBEE)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        hasNewAlert = false
                        seenCount = alertCount
                    },
                    icon = {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Text("🚨")
                            if (hasNewAlert) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                )
                            }
                        }
                    },
                    label = { Text("Alerts") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = Color(0xFFC62828),
                        indicatorColor = Color(0xFFFFEBEE)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("🩺") },
                    label = { Text("Health") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = Color(0xFFC62828),
                        indicatorColor = Color(0xFFFFEBEE)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("👤") },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = Color(0xFFC62828),
                        indicatorColor = Color(0xFFFFEBEE)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen(onLogout = onLogout)
                1 -> AlertsScreen()
                2 -> HealthScreen()
                3 -> ProfileScreen(onLogout = onLogout)
            }
        }
    }
}
