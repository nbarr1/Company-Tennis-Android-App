package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Availability
import com.example.data.models.AvailabilitySlot
import com.example.data.repository.TennisRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToMatchHistory: () -> Unit = {},
    onNavigateToUserProfile: () -> Unit = {}
) {
    val user by TennisRepository.currentUser.collectAsState()
    val scrollState = rememberScrollState()

    var tipsEnabled by remember(user) { mutableStateOf(user?.tipsEnabled ?: true) }
    var noteText by remember(user) { mutableStateOf(user?.availability?.note ?: "") }
    var slots by remember(user) { mutableStateOf(user?.availability?.slots ?: emptyList()) }

    var showAddSlotDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Player Profile", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Header Card
            Card(
                onClick = onNavigateToUserProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_user_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = user?.displayName ?: "Alex Rivera",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = user?.email ?: "alex@example.com",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = (user?.role ?: "player").uppercase().replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Match History Entry Card
            Card(
                onClick = onNavigateToMatchHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_match_history_button"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Match History & Past Stats",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "View previous division results fetched from Firestore",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Win / Loss Performance Trend Line Chart
            PlayerMatchTrendChart(
                userId = user?.id ?: "user_me"
            )

            // Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Game & Notification Preferences", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Live Scoring Point Tips", fontWeight = FontWeight.SemiBold)
                            Text("Show opportunity tips during live scoring", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = tipsEnabled,
                            onCheckedChange = {
                                tipsEnabled = it
                                user?.let { u -> TennisRepository.updateProfile(u.copy(tipsEnabled = it)) }
                            },
                            modifier = Modifier.testTag("tips_enabled_switch")
                        )
                    }
                }
            }

            // Weekly Availability Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weekly Playing Availability", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                        IconButton(
                            onClick = { showAddSlotDialog = true },
                            modifier = Modifier.testTag("add_availability_slot_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Slot", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (slots.isEmpty()) {
                        Text("No availability slots added yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        slots.forEachIndexed { index, slot ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(slot.day, fontWeight = FontWeight.Bold)
                                        Text("${slot.from} - ${slot.to}", style = MaterialTheme.typography.bodySmall)
                                    }

                                    IconButton(onClick = {
                                        slots = slots.toMutableList().also { it.removeAt(index) }
                                        TennisRepository.updateAvailability(Availability(slots, noteText))
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            TennisRepository.updateAvailability(Availability(slots, it))
                        },
                        label = { Text("Preferred Courts & Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("availability_note_input")
                    )
                }
            }

            // Logout Button
            OutlinedButton(
                onClick = {
                    TennisRepository.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAddSlotDialog) {
        AddSlotDialog(
            onDismiss = { showAddSlotDialog = false },
            onAdd = { day, from, to ->
                val newSlot = AvailabilitySlot(day, from, to)
                slots = slots + newSlot
                TennisRepository.updateAvailability(Availability(slots, noteText))
                showAddSlotDialog = false
            }
        )
    }
}

@Composable
fun AddSlotDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var selectedDay by remember { mutableStateOf(days[0]) }
    var fromTime by remember { mutableStateOf("18:00") }
    var toTime by remember { mutableStateOf("20:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Availability Slot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Day")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.take(4).forEach { day ->
                        FilterChip(selected = selectedDay == day, onClick = { selectedDay = day }, label = { Text(day.take(3)) })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.drop(4).forEach { day ->
                        FilterChip(selected = selectedDay == day, onClick = { selectedDay = day }, label = { Text(day.take(3)) })
                    }
                }

                OutlinedTextField(value = fromTime, onValueChange = { fromTime = it }, label = { Text("From (e.g. 17:00)") })
                OutlinedTextField(value = toTime, onValueChange = { toTime = it }, label = { Text("To (e.g. 19:00)") })
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(selectedDay, fromTime, toTime) }) {
                Text("Add Slot")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
