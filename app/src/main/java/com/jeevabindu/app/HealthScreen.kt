package com.jeevabindu.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

// ── Color palette ──────────────────────────────────────────────────────────────
private val RedPrimary   = Color(0xFFE53935)
private val RedDark      = Color(0xFFB71C1C)
private val RedDeep      = Color(0xFF8B0000)
private val GreenOk      = Color(0xFF2E7D32)
private val GreenLight   = Color(0xFF4CAF50)
private val Surface      = Color(0xFFF8F8F8)
private val CardBg       = Color.White
private val TextPrimary  = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF8A8A8A)
private val Divider      = Color(0xFFF2F2F2)

@Composable
fun HealthScreen() {
    val db   = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var lastDonationDate by remember { mutableStateOf(0L) }
    var isLoading        by remember { mutableStateOf(true) }
    var isSaving         by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var saveMessage      by remember { mutableStateOf("") }

    val uid = auth.currentUser?.uid ?: ""

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        db.collection("donors").document(uid).get()
            .addOnSuccessListener { doc ->
                lastDonationDate = doc.getLong("lastDonationDate") ?: 0L
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    val ninetyDaysMillis = 90L * 24 * 60 * 60 * 1000
    val eligibleDate     = lastDonationDate + ninetyDaysMillis
    val now              = System.currentTimeMillis()
    val isEligible       = lastDonationDate == 0L || now >= eligibleDate
    val daysLeft         = if (!isEligible) ((eligibleDate - now) / (1000 * 60 * 60 * 24)).toInt() + 1 else 0
    val daysGone         = if (!isEligible) (90 - daysLeft) else 90
    val progressFraction = if (!isEligible) {
        ((now - lastDonationDate).toFloat() / ninetyDaysMillis.toFloat()).coerceIn(0f, 1f)
    } else 1f

    val dateFormat       = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val lastDonationText = if (lastDonationDate == 0L) "Not recorded yet"
    else dateFormat.format(Date(lastDonationDate))
    val eligibleDateText = if (lastDonationDate == 0L) "Donate today!"
    else dateFormat.format(Date(eligibleDate))

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue  = 1.07f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue   = progressFraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label         = "progress"
    )

    // ── Confirm dialog ─────────────────────────────────────────────────────────
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = CardBg,
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(RedPrimary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🩸", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Log Donation?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Text(
                    "This will log today as your donation date and set your status to Resting for 90 days.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isSaving = true
                        saveMessage = ""
                        val today = System.currentTimeMillis()
                        db.collection("donors").document(uid)
                            .update(mapOf(
                                "lastDonationDate" to today, 
                                "isAvailable" to false,
                                "donationCount" to FieldValue.increment(1)
                            ))
                            .addOnSuccessListener {
                                lastDonationDate = today
                                isSaving = false
                                saveMessage = "✅ Donation logged successfully!"
                            }
                            .addOnFailureListener { e ->
                                isSaving = false
                                saveMessage = "❌ Failed: ${e.message}"
                            }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(RedPrimary, RedDark)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Yes, Log It",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text("Cancel", color = TextSecondary, fontSize = 15.sp)
                }
            }
        )
    }

    // ── Root ───────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {

        // Hero gradient header — taller with more top room
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(RedDeep, RedPrimary, Color(0xFFEF5350))
                    )
                )
        ) {
            // Large soft circle — top left
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(x = (-80).dp, y = (-80).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            // Medium circle — bottom right
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(x = 250.dp, y = 60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            )
            // Small accent circle
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .offset(x = 300.dp, y = (-20).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header — extra top padding for breathing room ──────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // windowInsets-aware top padding + generous extra space
                    .statusBarsPadding()
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 0.dp)
            ) {
                // Pill badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        "HEALTH TRACKER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.92f),
                        letterSpacing = 1.8.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "Donation\nEligibility",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Track your recovery and stay ready to give life",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Scrollable body ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()          // clears the bottom nav bar
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 40.dp),         // extra breathing room
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (isLoading) {
                    Spacer(modifier = Modifier.height(100.dp))
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                } else {

                    // ── Status hero card ───────────────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Layered pulsing orb
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .scale(pulse)
                                    .clip(CircleShape)
                                    .background(
                                        if (isEligible) GreenLight.copy(alpha = 0.10f)
                                        else RedPrimary.copy(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isEligible) GreenLight.copy(alpha = 0.18f)
                                            else RedPrimary.copy(alpha = 0.13f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isEligible) GreenLight.copy(alpha = 0.22f)
                                                else RedPrimary.copy(alpha = 0.17f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (isEligible) "✅" else "⏳",
                                            fontSize = 26.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Status chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (isEligible) GreenOk.copy(alpha = 0.10f)
                                        else RedPrimary.copy(alpha = 0.09f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (isEligible) "ELIGIBLE" else "RESTING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                    color = if (isEligible) GreenOk else RedPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = if (isEligible) "Ready to Donate!" else "Recovery Period",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEligible) GreenOk else RedDark,
                                letterSpacing = (-0.3).sp
                            )

                            Spacer(modifier = Modifier.height(7.dp))

                            Text(
                                text = if (isEligible)
                                    "You are eligible to donate blood today"
                                else
                                    "$daysLeft day${if (daysLeft > 1) "s" else ""} remaining until eligibility",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            // Progress section (only when resting)
                            if (!isEligible) {
                                Spacer(modifier = Modifier.height(24.dp))

                                // Day counters row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Surface)
                                        .padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    DayStat(
                                        value = "$daysGone",
                                        label = "Days Done",
                                        color = GreenLight
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(36.dp)
                                            .background(Divider)
                                    )
                                    DayStat(
                                        value = "90",
                                        label = "Total Days",
                                        color = TextSecondary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(36.dp)
                                            .background(Divider)
                                    )
                                    DayStat(
                                        value = "$daysLeft",
                                        label = "Days Left",
                                        color = RedPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Progress bar label row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Recovery Progress",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${(animatedProgress * 100).toInt()}%",
                                        fontSize = 13.sp,
                                        color = RedPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(9.dp))

                                // Segmented progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFFEEEEEE))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animatedProgress)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(RedPrimary, Color(0xFFFF6F61))
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Donation details card ──────────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(22.dp)) {

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(RedPrimary.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📋", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Donation Details",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    letterSpacing = (-0.1).sp
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            HealthInfoRow(
                                icon = "🩸",
                                label = "Last Donation",
                                value = lastDonationText,
                                valueColor = TextPrimary
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = Divider
                            )
                            HealthInfoRow(
                                icon = "📅",
                                label = "Eligible From",
                                value = eligibleDateText,
                                valueColor = if (isEligible) GreenOk else RedPrimary
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = Divider
                            )
                            HealthInfoRow(
                                icon = "💊",
                                label = "Status",
                                value = if (isEligible) "Available" else "Resting",
                                valueColor = if (isEligible) GreenOk else Color(0xFFE65100)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── WHO tip card ───────────────────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFE082)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💡", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    "WHO Guideline",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5D4037),
                                    letterSpacing = 0.1.sp
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    "Wait at least 90 days between whole blood donations to allow full recovery and maintain healthy iron levels.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF795548),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Blood facts card ───────────────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F0)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🩸", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Did You Know?",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDark,
                                    letterSpacing = (-0.1).sp
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            BloodFact("One donation can save up to 3 lives")
                            Spacer(modifier = Modifier.height(8.dp))
                            BloodFact("Blood cannot be manufactured — only donated")
                            Spacer(modifier = Modifier.height(8.dp))
                            BloodFact("Your body replenishes plasma within 24 hours")
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Log Donation CTA button ────────────────────────────────
                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(18.dp),
                        enabled = !isSaving,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(RedPrimary, RedDark)),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("🩸", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Log Today's Donation",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 0.2.sp
                                    )
                                }
                            }
                        }
                    }

                    // Save feedback banner
                    if (saveMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val isSuccess = saveMessage.startsWith("✅")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                )
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isSuccess) "✅" else "❌",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                saveMessage.removePrefix("✅ ").removePrefix("❌ "),
                                color = if (isSuccess) GreenOk else RedDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun DayStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun HealthInfoRow(
    icon: String,
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = TextSecondary)
        }
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
fun BloodFact(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(RedPrimary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = Color(0xFF5D4037),
            lineHeight = 20.sp
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF212121)
        )
    }
}