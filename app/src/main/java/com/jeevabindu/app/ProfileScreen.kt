package com.jeevabindu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// JeevaBindu Theme Colors
private val RedPrimary = Color(0xFFE53935)
private val RedDark = Color(0xFFC62828)
private val RedDeep = Color(0xFFB71C1C)
private val AppBackground = Color(0xFFF8F9FB)
private val CardBackground = Color.White

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""

    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var panchayat by remember { mutableStateOf("") }
    var isAvailable by remember { mutableStateOf(true) }
    var donationCount by remember { mutableStateOf(0) }
    
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    // State for temporary edits
    var editName by remember { mutableStateOf("") }
    var editPanchayat by remember { mutableStateOf("") }

    // Load Donor Profile with real-time updates
    DisposableEffect(uid) {
        if (uid.isEmpty()) return@DisposableEffect onDispose {}
        val listener = db.collection("donors").document(uid)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    name = doc.getString("name") ?: ""
                    phoneNumber = doc.getString("phoneNumber") ?: ""
                    bloodGroup = doc.getString("bloodGroup") ?: ""
                    panchayat = doc.getString("panchayat") ?: ""
                    isAvailable = doc.getBoolean("isAvailable") ?: true
                    donationCount = (doc.getLong("donationCount") ?: 0L).toInt()
                    
                    // Only update edit fields if not currently editing
                    if (!isEditing) {
                        editName = name
                        editPanchayat = panchayat
                    }
                }
                isLoading = false
            }
        onDispose { listener.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ─── Header Section with Gradient ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp) // Height increased to give more vertical space
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(RedDeep, RedPrimary, Color(0xFFEF5350))
                            )
                        )
                ) {
                    // Decorative patterns for depth
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .offset(x = (-40).dp, y = (-40).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 16.dp), // Adjust padding for optimal spacing
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Blood Group Circular Badge
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .shadow(12.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(3.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = bloodGroup.ifEmpty { "?" },
                                color = RedPrimary,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = name,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // FIX: Badge for high visibility - using solid background and shadow
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "LIFESAVER DONOR",
                                color = RedPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }

                // ─── Content Section (Overlapping the Header) ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-30).dp) // Reduced overlap offset to prevent covering the "LIFESAVER DONOR" badge
                        .padding(horizontal = 20.dp)
                ) {
                    // Quick Stats Dashboard
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileStatItem(label = "Donations", value = "$donationCount", icon = "💉")
                            VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xFFF0F0F0))
                            ProfileStatItem(label = "Type", value = bloodGroup, icon = "🩸")
                            VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xFFF0F0F0))
                            ProfileStatItem(
                                label = "Status", 
                                value = if (isAvailable) "Ready" else "Resting", 
                                icon = if (isAvailable) "✅" else "⏳"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Availability Control Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isAvailable) "🔔" else "🔕", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Ready to Help", 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 16.sp,
                                    color = Color(0xFF2D3142)
                                )
                                Text(
                                    text = if (isAvailable) "Visible for emergencies" else "Currently hidden from list",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                            Switch(
                                checked = isAvailable,
                                onCheckedChange = { newValue ->
                                    isAvailable = newValue
                                    db.collection("donors").document(uid).update("isAvailable", newValue)
                                    saveMessage = if (newValue) "✅ Profile set to Ready" else "⏳ Profile set to Resting"
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color(0xFF4CAF50)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Personal Information Details
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Personal Details", 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 17.sp,
                                    color = Color(0xFF2D3142)
                                )
                                TextButton(onClick = { 
                                    if (isEditing) { editName = name; editPanchayat = panchayat }
                                    isEditing = !isEditing
                                    saveMessage = ""
                                }) {
                                    Text(
                                        text = if (isEditing) "Cancel" else "Edit Info", 
                                        color = RedPrimary, 
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isEditing) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        label = { Text("Display Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RedPrimary,
                                            focusedLabelColor = RedPrimary
                                        )
                                    )
                                    OutlinedTextField(
                                        value = editPanchayat,
                                        onValueChange = { editPanchayat = it },
                                        label = { Text("Location (Panchayat/Town)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RedPrimary,
                                            focusedLabelColor = RedPrimary
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (editName.isBlank()) {
                                                saveMessage = "Name is required"
                                                return@Button
                                            }
                                            isSaving = true
                                            db.collection("donors").document(uid)
                                                .update(mapOf("name" to editName, "panchayat" to editPanchayat))
                                                .addOnSuccessListener {
                                                    name = editName
                                                    panchayat = editPanchayat
                                                    isSaving = false
                                                    isEditing = false
                                                    saveMessage = "✅ Profile updated!"
                                                }
                                                .addOnFailureListener { isSaving = false; saveMessage = "❌ Update failed" }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(54.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = !isSaving
                                    ) {
                                        if (isSaving) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                        } else {
                                            Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                }
                            } else {
                                ProfileDetailRow(icon = "👤", label = "Full Name", value = name)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF5F5F5))
                                ProfileDetailRow(icon = "📞", label = "Phone Number", value = "+91 $phoneNumber")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF5F5F5))
                                ProfileDetailRow(icon = "📍", label = "Panchayat", value = panchayat)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF5F5F5))
                                ProfileDetailRow(icon = "🆔", label = "Donor Identity", value = uid.take(8).uppercase())
                            }
                        }
                    }

                    // Status Messages (Feedback)
                    if (saveMessage.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (saveMessage.contains("✅")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = saveMessage,
                                color = if (saveMessage.contains("✅")) Color(0xFF2E7D32) else RedPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Log Out Action
                    Button(
                        onClick = { auth.signOut(); onLogout() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚪", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Log Out", color = Color(0xFF616161), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileStatItem(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF2D3142))
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun ProfileDetailRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(
                text = value.ifEmpty { "Not specified" },
                fontSize = 15.sp, 
                fontWeight = FontWeight.SemiBold, 
                color = Color(0xFF2D3142)
            )
        }
    }
}
