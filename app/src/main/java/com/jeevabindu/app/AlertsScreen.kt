package com.jeevabindu.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class BloodAlert(
    val id: String = "",
    val bloodGroup: String = "",
    val hospitalName: String = "",
    val panchayat: String = "",
    val postedBy: String = "",
    val postedByPhone: String = "",
    val timestamp: Long = 0L,
    val unitsNeeded: Int = 1,
    val respondedDonors: List<String> = emptyList()
)

fun alertColor(bg: String): Color = when (bg) {
    "A+"  -> Color(0xFFE53935)
    "A-"  -> Color(0xFFD81B60)
    "B+"  -> Color(0xFF43A047)
    "B-"  -> Color(0xFF8E24AA)
    "AB+" -> Color(0xFF1E88E5)
    "AB-" -> Color(0xFF6D4C41)
    "O+"  -> Color(0xFF43A047)
    "O-"  -> Color(0xFFFB8C00)
    else  -> Color(0xFF757575)
}

fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1  -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24   -> "$hours hour${if (hours > 1) "s" else ""} ago"
        else         -> "$days day${if (days > 1) "s" else ""} ago"
    }
}

@Composable
fun AlertsScreen(initialFilter: Int = 0) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val currentUid = auth.currentUser?.uid ?: ""

    var alerts by remember { mutableStateOf<List<BloodAlert>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var alertToDelete by remember { mutableStateOf<BloodAlert?>(null) }
    var selectedTab by remember { mutableStateOf(initialFilter) }

    val urgentAlerts = alerts.filter { it.respondedDonors.size < 2 }
    val myAlerts = alerts.filter { it.postedBy == currentUid }

    val displayedAlerts = when (selectedTab) {
        1 -> urgentAlerts
        2 -> myAlerts
        else -> alerts
    }

    if (alertToDelete != null) {
        AlertDialog(
            onDismissRequest = { alertToDelete = null },
            title = { Text("Mark as Resolved?", fontWeight = FontWeight.Bold, color = Color(0xFFC62828)) },
            text = { Text("Delete the emergency at \"${alertToDelete?.hospitalName}\"? This means the situation is resolved.", fontSize = 14.sp) },
            confirmButton = {
                Button(onClick = { alertToDelete?.let { db.collection("alerts").document(it.id).delete() }; alertToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("Yes, Resolved") }
            },
            dismissButton = { TextButton(onClick = { alertToDelete = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    DisposableEffect(Unit) {
        val listener = db.collection("alerts").orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener { snapshot, _ ->
            alerts = snapshot?.documents?.map { doc ->
                BloodAlert(
                    id = doc.id,
                    bloodGroup = doc.getString("bloodGroup") ?: "",
                    hospitalName = doc.getString("hospitalName") ?: "",
                    panchayat = doc.getString("panchayat") ?: "",
                    postedBy = doc.getString("postedBy") ?: "",
                    postedByPhone = doc.getString("postedByPhone") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    unitsNeeded = (doc.getLong("unitsNeeded") ?: 1L).toInt(),
                    respondedDonors = doc.get("respondedDonors") as? List<String> ?: emptyList()
                )
            } ?: emptyList()
            isLoading = false
        }
        onDispose { listener.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
            .navigationBarsPadding() // FIX: Ensures content clears the system navigation bar
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFC62828))))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚨", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emergency Alerts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("${alerts.size} active blood requests", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterTab("All Alerts", alerts.size, selectedTab == 0, Color(0xFFC62828)) { selectedTab = 0 }
            FilterTab("🔴 Urgent", urgentAlerts.size, selectedTab == 1, Color(0xFFE53935)) { selectedTab = 1 }
            FilterTab("📌 Mine", null, selectedTab == 2, Color(0xFF757575)) { selectedTab = 2 }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFC62828)) }
        } else if (displayedAlerts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🕊️", fontSize = 52.sp)
                    Text("No emergencies right now", fontWeight = FontWeight.SemiBold, color = Color(0xFF424242))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp), // FIX: Extra padding at the bottom for the last card
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedAlerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        currentUid = currentUid,
                        context = context,
                        onImComing = { id -> db.collection("alerts").document(id).update("respondedDonors", FieldValue.arrayUnion(currentUid)) },
                        onCancelResponse = { id -> db.collection("alerts").document(id).update("respondedDonors", FieldValue.arrayRemove(currentUid)) },
                        onDeleteAlert = { alertToDelete = alert }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterTab(label: String, count: Int?, isSelected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) selectedColor else Color(0xFFF5F5F5),
        modifier = Modifier.height(36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(label, color = if (isSelected) Color.White else Color(0xFF616161), fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            if (count != null && count > 0) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = CircleShape, color = if (isSelected) Color.White.copy(alpha = 0.3f) else Color(0xFFEEEEEE)) {
                    Text("$count", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), color = if (isSelected) Color.White else Color(0xFF616161), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: BloodAlert, currentUid: String, context: android.content.Context, onImComing: (String) -> Unit, onCancelResponse: (String) -> Unit, onDeleteAlert: () -> Unit) {
    val hasResponded = alert.respondedDonors.contains(currentUid)
    val respondedCount = alert.respondedDonors.size
    val isOwner = alert.postedBy == currentUid
    val isUrgent = respondedCount < 2
    val cardColor = alertColor(alert.bloodGroup)
    val db = remember { FirebaseFirestore.getInstance() }
    var posterPhone by remember { mutableStateOf(alert.postedByPhone) }

    LaunchedEffect(alert.postedBy) {
        if (posterPhone.isEmpty() && alert.postedBy.isNotEmpty()) {
            db.collection("donors").document(alert.postedBy).get().addOnSuccessListener { posterPhone = it.getString("phoneNumber") ?: "" }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(cardColor))
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(cardColor), contentAlignment = Alignment.Center) {
                        Text(alert.bloodGroup, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(alert.hospitalName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A1A))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", fontSize = 11.sp)
                            Text(" ${alert.panchayat.ifEmpty { "Location" }}", fontSize = 12.sp, color = Color(0xFF757575))
                        }
                    }
                    if (isOwner) {
                        IconButton(onClick = onDeleteAlert) { Text("🗑️", fontSize = 16.sp) }
                    } else if (isUrgent) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFEBEE)) {
                            Text("URGENT", color = Color(0xFFC62828), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("🕐 ${timeAgo(alert.timestamp)}", fontSize = 12.sp, color = Color(0xFF757575))
                    Text("✏️ ${alert.unitsNeeded} units", fontSize = 12.sp, color = Color(0xFF757575))
                    Text("🙋 $respondedCount responded", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (respondedCount > 0) Color(0xFF4CAF50) else Color(0xFF757575))
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(Modifier.height(12.dp))
                if (isOwner) {
                    Button(onClick = onDeleteAlert, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)), shape = RoundedCornerShape(12.dp)) {
                        Text("✅ Mark as Resolved", fontWeight = FontWeight.Bold)
                    }
                } else if (hasResponded) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = { if (posterPhone.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91$posterPhone"))) }, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), color = Color.White) {
                            Box(contentAlignment = Alignment.Center) { Text("📞") }
                        }
                        Surface(modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFE8F5E9)) {
                            Box(contentAlignment = Alignment.Center) { Text("✅ You're on the way!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                    }
                    TextButton(onClick = { onCancelResponse(alert.id) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel Response", color = Color.Gray, fontSize = 13.sp) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = { if (posterPhone.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91$posterPhone"))) }, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), color = Color.White) {
                            Box(contentAlignment = Alignment.Center) { Text("📞") }
                        }
                        Button(onClick = { onImComing(alert.id) }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)), shape = RoundedCornerShape(12.dp)) {
                            Text("🏃 I'm Coming!", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}