package com.example.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Match
import com.example.data.models.PublicProfile
import com.example.data.repository.TennisRepository
import com.example.scoring.MatchFormatConfig
import com.example.scoring.ScoreEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onNavigateToMatchDetail: (String) -> Unit,
    onNavigateToCreateMatch: () -> Unit = {},
    onNavigateToMatchHistory: () -> Unit = {}
) {
    val matches by TennisRepository.matches.collectAsState()
    val user by TennisRepository.currentUser.collectAsState()
    val players by TennisRepository.divisionPlayers.collectAsState()
    val selectedSeasonId by TennisRepository.selectedSeasonId.collectAsState()
    val availableSeasons by TennisRepository.availableSeasons.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showProposeDialog by remember { mutableStateOf(false) }
    var seasonMenuExpanded by remember { mutableStateOf(false) }

    val tabTitles = listOf("All", "Pending", "Upcoming", "Live", "Completed")

    val seasonMatches = remember(matches, selectedSeasonId) {
        matches.filter { it.seasonId.isNullOrEmpty() || it.seasonId == selectedSeasonId }
    }

    val filteredMatches = remember(seasonMatches, selectedTab, user) {
        when (selectedTab) {
            1 -> seasonMatches.filter { it.status == "proposed" }
            2 -> seasonMatches.filter { it.status == "scheduled" }
            3 -> seasonMatches.filter { it.status == "in_progress" }
            4 -> seasonMatches.filter { it.status == "completed" || it.status == "pending_report" }
            else -> seasonMatches
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text("Division Matches", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(
                            onClick = onNavigateToMatchHistory,
                            modifier = Modifier.testTag("match_history_top_button")
                        ) {
                            Icon(Icons.Default.History, contentDescription = "Match History")
                        }
                        IconButton(
                            onClick = onNavigateToCreateMatch,
                            modifier = Modifier.testTag("create_match_top_button")
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Create Match")
                        }
                        IconButton(
                            onClick = { showProposeDialog = true },
                            modifier = Modifier.testTag("propose_match_top_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Propose Match")
                        }
                    }
                )

                // Season Selector Bar
                val currentSeasonName = availableSeasons.find { it.id == selectedSeasonId }?.name ?: "Fall 2026"
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Season:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            FilterChip(
                                selected = true,
                                onClick = { seasonMenuExpanded = true },
                                label = { Text(currentSeasonName, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("matches_season_selector")
                            )

                            DropdownMenu(
                                expanded = seasonMenuExpanded,
                                onDismissRequest = { seasonMenuExpanded = false }
                            ) {
                                availableSeasons.forEach { season ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = season.name,
                                                fontWeight = if (season.id == selectedSeasonId) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            TennisRepository.setSelectedSeason(season.id)
                                            seasonMenuExpanded = false
                                        },
                                        leadingIcon = if (season.id == selectedSeasonId) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showProposeDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Propose Match") },
                modifier = Modifier.testTag("propose_match_fab")
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredMatches.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsTennis,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No matches found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap 'Propose Match' to challenge a player in your division.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMatches) { match ->
                        MatchCardItem(
                            match = match,
                            currentUserId = user?.id ?: "",
                            onClick = { onNavigateToMatchDetail(match.id) }
                        )
                    }
                }
            }
        }
    }

    if (showProposeDialog) {
        ProposeMatchDialog(
            players = players.filter { it.id != user?.id },
            onDismiss = { showProposeDialog = false },
            onPropose = { opponentId, format ->
                TennisRepository.proposeMatch(
                    opponentId = opponentId,
                    levelId = "lvl_advanced",
                    proposedTime = System.currentTimeMillis() + 86400000 * 2,
                    format = format
                )
                showProposeDialog = false
            }
        )
    }
}

@Composable
fun MatchCardItem(
    match: Match,
    currentUserId: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("match_item_${match.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = match.status)

                Text(
                    text = "Format: Best of ${match.format.setsToWin}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = match.player1Name ?: "Player 1",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (match.winner == "player1") FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    Text(
                        text = match.player2Name ?: "Player 2",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (match.winner == "player2") FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }

                if (match.status == "completed" || match.status == "in_progress" || match.status == "pending_report") {
                    Text(
                        text = ScoreEngine.formatScoreDisplay(match.liveScore),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            match.courtLocation?.let { loc ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Court Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = loc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, text) = when (status) {
        "proposed" -> Pair(MaterialTheme.colorScheme.tertiary, "PROPOSED")
        "scheduled" -> Pair(MaterialTheme.colorScheme.primary, "SCHEDULED")
        "in_progress" -> Pair(MaterialTheme.colorScheme.error, "LIVE")
        "pending_report" -> Pair(MaterialTheme.colorScheme.secondary, "PENDING REPORT")
        "completed" -> Pair(Color(0xFF2E7D32), "FINAL")
        "disputed" -> Pair(Color(0xFFD32F2F), "DISPUTED")
        else -> Pair(MaterialTheme.colorScheme.outline, status.uppercase())
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeMatchDialog(
    players: List<PublicProfile>,
    onDismiss: () -> Unit,
    onPropose: (String, MatchFormatConfig) -> Unit
) {
    var selectedPlayer by remember { mutableStateOf(players.firstOrNull()) }
    var setsToWin by remember { mutableIntStateOf(2) }
    var gamesPerSet by remember { mutableIntStateOf(6) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propose Match Challenge", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Opponent", style = MaterialTheme.typography.titleSmall)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    players.forEach { p ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlayer = p },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedPlayer?.id == p.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(p.displayName, fontWeight = FontWeight.Medium)
                                if (selectedPlayer?.id == p.id) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Match Format", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = setsToWin == 2,
                        onClick = { setsToWin = 2 },
                        label = { Text("Best of 3 Sets") }
                    )
                    FilterChip(
                        selected = setsToWin == 1,
                        onClick = { setsToWin = 1 },
                        label = { Text("Pro Set (1 Set)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedPlayer?.let { onPropose(it.id, MatchFormatConfig(setsToWin = setsToWin, gamesPerSet = gamesPerSet)) }
                },
                enabled = selectedPlayer != null
            ) {
                Text("Send Proposal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
