package com.jeevabindu.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ── Donor data class ───────────────────────────────────────────────────────
data class Donor(
    val uid: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val bloodGroup: String = "",
    val panchayat: String = "",
    val isAvailable: Boolean = true,
    val donationCount: Int = 0
)

// Blood group badge colors
fun bloodGroupColor(bg: String): Color = when (bg) {
    "A+"  -> Color(0xFFE53935)
    "A-"  -> Color(0xFFD81B60)
    "B+"  -> Color(0xFF43A047)
    "B-"  -> Color(0xFF00897B)
    "AB+" -> Color(0xFF8E24AA)
    "AB-" -> Color(0xFF6D4C41)
    "O+"  -> Color(0xFFFB8C00)
    "O-"  -> Color(0xFF1E88E5)
    else  -> Color(0xFF757575)
}

// ── HomeScreen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLogout: () -> Unit) {

    val db   = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    var donors              by remember { mutableStateOf<List<Donor>>(emptyList()) }
    var selectedBloodGroup  by remember { mutableStateOf("All") }
    var isLoading           by remember { mutableStateOf(true) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var currentUserName     by remember { mutableStateOf("") }
    var currentBloodGroup   by remember { mutableStateOf("") }
    var currentAvailable    by remember { mutableStateOf(true) }
    var activeAlertsCount   by remember { mutableStateOf(0) }
    var myDonationsCount    by remember { mutableStateOf(0) }
    var searchQuery         by remember { mutableStateOf("") }

    val bloodGroups = listOf("All", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    val filteredDonors = donors.filter { donor ->
        searchQuery.isEmpty() ||
                donor.name.contains(searchQuery, ignoreCase = true) ||
                donor.panchayat.contains(searchQuery, ignoreCase = true)
    }

    // Load current user profile with real-time listener
    DisposableEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@DisposableEffect onDispose {}
        val listener = db.collection("donors").document(uid)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    currentUserName  = doc.getString("name") ?: ""
                    currentBloodGroup = doc.getString("bloodGroup") ?: ""
                    currentAvailable  = doc.getBoolean("isAvailable") ?: true
                    myDonationsCount  = (doc.getLong("donationCount") ?: 0L).toInt()
                }
            }
        
        // Save FCM token (one-time)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                db.collection("donors").document(uid).update("fcmToken", token)
            }
            
        onDispose { listener.remove() }
    }

    // Watch active alerts count
    DisposableEffect(Unit) {
        val listener = db.collection("alerts")
            .addSnapshotListener { snapshot, _ ->
                activeAlertsCount = snapshot?.documents?.size ?: 0
            }
        onDispose { listener.remove() }
    }

    // Load donors real-time
    DisposableEffect(selectedBloodGroup) {
        val query = if (selectedBloodGroup == "All") {
            db.collection("donors")
        } else {
            db.collection("donors").whereEqualTo("bloodGroup", selectedBloodGroup)
        }
        val listener = query.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                val uid   = doc.getString("uid") ?: return@mapNotNull null
                val name  = doc.getString("name") ?: return@mapNotNull null
                if (uid.isEmpty() || name.isEmpty()) return@mapNotNull null
                Donor(
                    uid           = uid,
                    name          = name,
                    phoneNumber   = doc.getString("phoneNumber") ?: "",
                    bloodGroup    = doc.getString("bloodGroup") ?: "",
                    panchayat     = doc.getString("panchayat") ?: "",
                    isAvailable   = doc.getBoolean("isAvailable") ?: true,
                    donationCount = (doc.getLong("donationCount") ?: 0L).toInt()
                )
            } ?: emptyList()
            donors    = list
            isLoading = false
        }
        onDispose { listener.remove() }
    }

    // Emergency dialog
    if (showEmergencyDialog) {
        EmergencyDialog(
            db    = db,
            auth  = auth,
            onDismiss = { showEmergencyDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F2F5))) {

        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // ── Red header ──────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFE53935), Color(0xFFC62828))
                            )
                        )
                        .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 24.dp)
                ) {
                    Column {
                        // Top row — greeting + icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Good Evening,",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentUserName.ifEmpty { "User" },
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("👋", fontSize = 20.sp)
                                }
                            }
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUserName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Stat cards row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatCard(
                                emoji = "🩸",
                                value = "${donors.size}",
                                label = "Donors Online",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                emoji = "🚨",
                                value = "$activeAlertsCount",
                                label = "Active Alerts",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                emoji = "💉",
                                value = "$myDonationsCount",
                                label = "My Donations",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Blood type banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(bloodGroupColor(currentBloodGroup)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentBloodGroup.ifEmpty { "?" },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Your Blood Type",
                                        color = Color.White.copy(alpha = 0.80f),
                                        fontSize = 11.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentBloodGroup.ifEmpty { "Unknown" },
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = " · ",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (currentAvailable) "✅ Available to Donate"
                                            else "⏳ Not Available",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── White content section ────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(Color(0xFFF0F2F5))
                        .padding(top = 20.dp)
                ) {
                    // Search bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .shadow(2.dp, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔍", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search donors by name or location...",
                                    color = Color(0xFFBDBDBD),
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        if (searchQuery.isNotEmpty()) {
                            TextButton(
                                onClick = { searchQuery = "" },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("✕", color = Color.Gray, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Blood group filter chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bloodGroups) { group ->
                            val isSelected = selectedBloodGroup == group
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) Color(0xFFC62828)
                                        else Color.White
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFC62828) else Color(0xFFE0E0E0),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selectedBloodGroup = group }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (group == "All") {
                                        Text(
                                            text = "⚡ ",
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = group,
                                        color = if (isSelected) Color.White else Color(0xFF424242),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Nearby Donors" header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nearby Donors",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${filteredDonors.size}",
                                color = Color(0xFFC62828),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Donor cards ──────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFC62828))
                    }
                }
            } else if (filteredDonors.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("😔", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty())
                                    "No donors found for \"$searchQuery\""
                                else "No donors found",
                                fontSize = 15.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                items(filteredDonors, key = { it.uid }) { donor ->
                    DonorCard(donor = donor, context = context)
                }
            }

            // Bottom padding so FAB doesn't cover last card
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        // ── SOS floating button ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 88.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFE53935), Color(0xFFC62828))
                    )
                )
                .clickable { showEmergencyDialog = true }
                .shadow(8.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚨", fontSize = 18.sp)
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Stat Card ──────────────────────────────────────────────────────────────
@Composable
fun StatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column {
            Text(emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 11.sp
            )
        }
    }
}

// ── Donor Card ─────────────────────────────────────────────────────────────
@Composable
fun DonorCard(donor: Donor, context: android.content.Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Colored blood group circle
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(bloodGroupColor(donor.bloodGroup)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = donor.bloodGroup,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = donor.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A1A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 11.sp)
                        Text(
                            text = " ${donor.panchayat}",
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🩸", fontSize = 11.sp)
                        Text(
                            text = " ${donor.donationCount} donations",
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    }
                }

                // Availability badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (donor.isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (donor.isAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (donor.isAvailable) "Ready" else "Resting",
                            color = if (donor.isAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Call button for available donors
            if (donor.isAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91${donor.phoneNumber}"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFE53935), Color(0xFFC62828))
                                ),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📞", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Call Donor",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Emergency Dialog ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyDialog(
    db: FirebaseFirestore,
    auth: FirebaseAuth,
    onDismiss: () -> Unit
) {
    var selectedBloodGroup by remember { mutableStateOf("") }
    var hospitalName       by remember { mutableStateOf("") }
    var location           by remember { mutableStateOf("") }
    var unitsNeeded        by remember { mutableStateOf(1) }
    var isLoading          by remember { mutableStateOf(false) }

    val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    val unitOptions = listOf(1, 2, 3, 4, 5)

    // Auto-fill location from user profile
    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        db.collection("donors").document(uid).get()
            .addOnSuccessListener { doc ->
                location = doc.getString("panchayat") ?: ""
            }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
        ) {
            Column {
                // Red header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFE53935), Color(0xFFC62828))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🚨", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Emergency Alert",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Post urgent blood request",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

                    // Blood Group label
                    Text(
                        text = "BLOOD GROUP NEEDED *",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF757575),
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Blood group grid buttons (2 rows × 4)
                    val rows = bloodGroups.chunked(4)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { bg ->
                                val isSelected = selectedBloodGroup == bg
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFC62828) else Color(0xFFE0E0E0),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .background(
                                            if (isSelected) Color(0xFFFFEBEE) else Color.White
                                        )
                                        .clickable { selectedBloodGroup = bg }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = bg,
                                        color = if (isSelected) Color(0xFFC62828) else Color(0xFF424242),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hospital / Location
                    Text(
                        text = "HOSPITAL / LOCATION *",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF757575),
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hospitalName,
                        onValueChange = { hospitalName = it },
                        placeholder = {
                            Text(
                                "e.g., Medical College Hospital, Malappuram",
                                color = Color(0xFFBDBDBD),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFC62828),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFFC62828)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Area / Location
                    Text(
                        text = "AREA / LOCATION *",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF757575),
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        placeholder = {
                            Text(
                                "e.g., Malappuram, Kozhikode...",
                                color = Color(0xFFBDBDBD),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFC62828),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFFC62828)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Units Needed
                    Text(
                        text = "UNITS NEEDED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF757575),
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        unitOptions.forEach { unit ->
                            val isSelected = unitsNeeded == unit
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFC62828) else Color(0xFFE0E0E0),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .background(
                                        if (isSelected) Color(0xFFFFEBEE) else Color.White
                                    )
                                    .clickable { unitsNeeded = unit }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unit == 5) "5+" else "$unit",
                                    color = if (isSelected) Color(0xFFC62828) else Color(0xFF424242),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Post button
                    Button(
                        onClick = {
                            if (hospitalName.isNotEmpty() && selectedBloodGroup.isNotEmpty() && location.isNotEmpty()) {
                                isLoading = true
                                val alert = hashMapOf(
                                    "bloodGroup"       to selectedBloodGroup,
                                    "hospitalName"     to hospitalName,
                                    "panchayat"        to location,
                                    "unitsNeeded"      to unitsNeeded,
                                    "postedBy"         to (auth.currentUser?.uid ?: ""),
                                    "timestamp"        to System.currentTimeMillis(),
                                    "respondedDonors"  to emptyList<String>()
                                )
                                db.collection("alerts").add(alert)
                                    .addOnSuccessListener {
                                        isLoading = false
                                        onDismiss()
                                    }
                                    .addOnFailureListener { isLoading = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoading
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFE53935), Color(0xFFC62828))
                                    ),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🚨", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Post Emergency Alert",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("›", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Disclaimer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Please use only for genuine medical emergencies",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}