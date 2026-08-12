package com.example.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.TennisRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DivisionJoinCreateScreen(
    onDivisionReady: () -> Unit
) {
    var isCreating by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("METRO2026") }
    var divisionName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isCreating) "Create New Division" else "Join Division",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = if (isCreating) "Set up a new tennis league division for your club or group"
                    else "Enter your league invite code to join your division",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isCreating) {
                    OutlinedTextField(
                        value = divisionName,
                        onValueChange = { divisionName = it },
                        label = { Text("Division Name") },
                        placeholder = { Text("e.g. Metro Premier Tennis") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("division_name_input"),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it },
                        label = { Text("Division Invite Code") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invite_code_input"),
                        singleLine = true
                    )
                }

                errorMsg?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (isCreating) {
                            if (divisionName.isBlank()) {
                                errorMsg = "Please enter a division name"
                                return@Button
                            }
                            TennisRepository.createDivision(divisionName)
                            onDivisionReady()
                        } else {
                            if (inviteCode.isBlank()) {
                                errorMsg = "Please enter an invite code"
                                return@Button
                            }
                            val success = TennisRepository.joinDivisionByCode(inviteCode)
                            if (success) onDivisionReady() else errorMsg = "Invalid invite code"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("division_submit_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isCreating) "Create & Continue" else "Join Division",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = {
                    isCreating = !isCreating
                    errorMsg = null
                }) {
                    Text(text = if (isCreating) "Have an invite code? Join instead" else "Want to start a new league? Create division")
                }
            }
        }
    }
}

@Composable
fun TutorialScreen(
    onTutorialComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step Indicator
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    repeat(3) { index ->
                        val isCurrent = index + 1 == step
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isCurrent) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                when (step) {
                    1 -> {
                        Icon(Icons.Default.SportsTennis, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Live Point-by-Point Scoring", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Score matches live with official tennis rules, automatic tiebreak triggers, deuce/advantage handling, and point tips.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    2 -> {
                        Icon(Icons.Default.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Real-Time Division Rankings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Division rankings update automatically after both players confirm the match report. Head-to-head tiebreakers are calculated live.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    3 -> {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Wear OS Watch Sync", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Pair your Wear OS watch to see real-time scores on your wrist and record points directly during a match.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            TennisRepository.markTutorialDone()
                            onTutorialComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("tutorial_next_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (step < 3) "Next" else "Get Started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
