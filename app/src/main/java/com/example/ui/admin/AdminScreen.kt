package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.PublicProfile
import com.example.data.repository.TennisRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onNavigateToMatchScheduler: () -> Unit = {}
) {
    val players by TennisRepository.divisionPlayers.collectAsState()
    val division by TennisRepository.currentDivision.collectAsState()
    val matches by TennisRepository.matches.collectAsState()
    val reports by TennisRepository.reports.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val disputedMatches = matches.filter { it.status == "disputed" }
    val openReports = reports.filter { it.status == "open" }

    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showAddPlaceholderDialog by remember { mutableStateOf(false) }
    var csvExportContent by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Division Leader Admin", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Division Code & CSV Export Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Division Invite Code", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(division?.inviteCode ?: "METRO2026", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        csvExportContent = TennisRepository.exportDivisionCsv()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_csv_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV")
                            }

                            Button(
                                onClick = { coroutineScope.launch { TennisRepository.recalculateDivisionRankings() } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_recalculate_rankings_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recompute")
                            }
                        }

                        csvExportContent?.let { csv ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "CSV Generated:\n${csv.take(150)}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Auto Match Scheduler Entry Card
            item {
                Card(
                    onClick = onNavigateToMatchScheduler,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_match_scheduler_button"),
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
                                imageVector = Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Round-Robin Match Scheduler",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Generate division fixtures based on current rankings",
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
            }

            // Disputed Reports Section
            if (disputedMatches.isNotEmpty()) {
                item {
                    Text("Disputed Match Reports", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                items(disputedMatches) { match ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Dispute: ${match.player1Name} vs ${match.player2Name}", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { TennisRepository.resolveDisputedReport(match.id, "player1") }) {
                                    Text("Award to P1")
                                }
                                Button(onClick = { TennisRepository.resolveDisputedReport(match.id, "player2") }) {
                                    Text("Award to P2")
                                }
                            }
                        }
                    }
                }
            }

            // Reported Messages Section (UGC moderation)
            if (openReports.isNotEmpty()) {
                item {
                    Text("Reported Messages", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                items(openReports) { report ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reported_message_${report.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Reported: ${report.reportedUserName}", fontWeight = FontWeight.Bold)
                            Text("Reason: ${report.reason}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("\"${report.messageContent}\"", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { TennisRepository.resolveReport(report) },
                                modifier = Modifier.testTag("remove_reported_message_${report.id}")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove Message")
                            }
                        }
                    }
                }
            }

            // Roster Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Division Roster (${players.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { showAddPlayerDialog = true },
                            modifier = Modifier.testTag("add_player_by_email_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Email")
                        }

                        IconButton(
                            onClick = { showAddPlaceholderDialog = true },
                            modifier = Modifier.testTag("add_placeholder_button")
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "Add Placeholder")
                        }
                    }
                }
            }

            items(players) { player ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(player.displayName, fontWeight = FontWeight.Bold)
                            Text("Role: ${player.role}", style = MaterialTheme.typography.bodySmall)
                        }

                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text("Active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showAddPlayerDialog) {
        AddPlayerDialog(
            onDismiss = { showAddPlayerDialog = false },
            onAdd = { email ->
                coroutineScope.launch { TennisRepository.addPlayerByEmail(email, "lvl_advanced") }
                showAddPlayerDialog = false
            }
        )
    }

    if (showAddPlaceholderDialog) {
        AddPlaceholderDialog(
            onDismiss = { showAddPlaceholderDialog = false },
            onAdd = { name ->
                coroutineScope.launch { TennisRepository.addPlaceholderMember(name, "lvl_advanced") }
                showAddPlaceholderDialog = false
            }
        )
    }
}

@Composable
fun AddPlayerDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player by Email") },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth().testTag("add_player_email_input")
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(email) }) { Text("Send Invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddPlaceholderDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Placeholder Player") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Player Name") },
                modifier = Modifier.fillMaxWidth().testTag("placeholder_name_input")
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(name) }) { Text("Add Member") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
