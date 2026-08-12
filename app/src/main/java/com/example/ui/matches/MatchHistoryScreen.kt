package com.example.ui.matches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Match
import com.example.data.models.MatchStats
import com.example.data.repository.TennisRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchHistoryScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToMatchDetail: (String) -> Unit = {}
) {
    val user by TennisRepository.currentUser.collectAsState()
    val allMatches by TennisRepository.matches.collectAsState()

    var selectedFilter by remember { mutableIntStateOf(0) } // 0: All Past, 1: Wins, 2: Losses
    var expandedMatchId by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val userId = user?.id ?: "user_me"

    // Realtime Firestore synchronization for user's matches
    LaunchedEffect(userId) {
        TennisRepository.listenToPlayerMatchesRealtime(userId)
    }

    // Filter matches involving the logged in user
    val userMatches = remember(allMatches, userId) {
        allMatches.filter { match ->
            match.player1Id == userId || match.player2Id == userId || match.playerIds.contains(userId)
        }.sortedByDescending { it.completedAt ?: it.scheduledAt ?: it.createdAt }
    }

    val pastMatches = remember(userMatches, selectedFilter) {
        userMatches.filter { match ->
            val isP1 = match.player1Id == userId
            val isWinner = if (match.winner == "player1") isP1 else if (match.winner == "player2") !isP1 else false

            when (selectedFilter) {
                1 -> isWinner && (match.status == "completed" || match.winner != null)
                2 -> !isWinner && (match.status == "completed" || match.winner != null)
                else -> true
            }
        }
    }

    val totalWins = remember(userMatches) {
        userMatches.count { m ->
            val isP1 = m.player1Id == userId
            if (m.winner == "player1") isP1 else if (m.winner == "player2") !isP1 else false
        }
    }

    val totalLosses = remember(userMatches) {
        userMatches.count { m ->
            val isP1 = m.player1Id == userId
            m.status == "completed" && (if (m.winner == "player1") !isP1 else if (m.winner == "player2") isP1 else false)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        modifier = Modifier.testTag("match_history_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Match History", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("match_history_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            TennisRepository.listenToPlayerMatchesRealtime(userId)
                            isRefreshing = false
                        },
                        modifier = Modifier.testTag("match_history_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Firestore Matches")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Player Record Summary Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = user?.displayName ?: "Player",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Firestore Player Match Records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBadge(label = "Wins", count = "$totalWins", color = Color(0xFF2E7D32))
                        StatBadge(label = "Losses", count = "$totalLosses", color = MaterialTheme.colorScheme.error)
                        StatBadge(
                            label = "Win Rate",
                            count = if (totalWins + totalLosses > 0) "${((totalWins.toFloat() / (totalWins + totalLosses)) * 100).toInt()}%" else "0%",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == 0,
                    onClick = { selectedFilter = 0 },
                    label = { Text("All Matches (${userMatches.size})") },
                    modifier = Modifier.testTag("match_filter_chip_all")
                )
                FilterChip(
                    selected = selectedFilter == 1,
                    onClick = { selectedFilter = 1 },
                    label = { Text("Victories ($totalWins)") },
                    modifier = Modifier.testTag("match_filter_chip_wins")
                )
                FilterChip(
                    selected = selectedFilter == 2,
                    onClick = { selectedFilter = 2 },
                    label = { Text("Losses ($totalLosses)") },
                    modifier = Modifier.testTag("match_filter_chip_losses")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (pastMatches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SportsTennis,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No match history found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Completed and recorded division matches from Firestore will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("match_history_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pastMatches) { match ->
                        MatchHistoryCard(
                            match = match,
                            currentUserId = userId,
                            isExpanded = expandedMatchId == match.id,
                            onToggleExpand = {
                                expandedMatchId = if (expandedMatchId == match.id) null else match.id
                            },
                            onNavigateToDetail = { onNavigateToMatchDetail(match.id) },
                            dateFormatter = dateFormatter
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatBadge(label: String, count: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = color
            )
        }
    }
}

@Composable
fun MatchHistoryCard(
    match: Match,
    currentUserId: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNavigateToDetail: () -> Unit,
    dateFormatter: SimpleDateFormat
) {
    val isP1 = match.player1Id == currentUserId
    val opponentName = if (isP1) (match.player2Name ?: "Player 2") else (match.player1Name ?: "Player 1")
    val isWinner = if (match.winner == "player1") isP1 else if (match.winner == "player2") !isP1 else false
    val isCompleted = match.status == "completed"

    val statusBg = if (!isCompleted) MaterialTheme.colorScheme.surfaceVariant
    else if (isWinner) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)

    val statusText = if (!isCompleted) match.status.replace("_", " ").replaceFirstChar { it.uppercase() }
    else if (isWinner) "VICTORY" else "DEFEAT"

    val statusColor = if (!isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
    else if (isWinner) Color(0xFF2E7D32) else Color(0xFFC62828)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_history_item_${match.id}")
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header: Date & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormatter.format(Date(match.completedAt ?: match.scheduledAt ?: match.createdAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Opponent & Score Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "vs. $opponentName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isP1) "Home Match" else "Away Match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sets & Score Display
                Column(horizontalAlignment = Alignment.End) {
                    val setsText = buildString {
                        match.liveScore.sets.forEachIndexed { idx, set ->
                            val s1 = set.player1Games
                            val s2 = set.player2Games
                            if (idx > 0) append("  ")
                            if (isP1) append("$s1-$s2") else append("$s2-$s1")
                        }
                    }
                    Text(
                        text = setsText.ifEmpty { "0-0" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sets Won: ${if (isP1) match.liveScore.player1SetsWon else match.liveScore.player2SetsWon}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable Stats Panel
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Match Statistics (Firestore)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val stats = match.stats
                    val p1Aces = if (isP1) stats.acesP1 else stats.acesP2
                    val p2Aces = if (isP1) stats.acesP2 else stats.acesP1

                    val p1Df = if (isP1) stats.doubleFaultsP1 else stats.doubleFaultsP2
                    val p2Df = if (isP1) stats.doubleFaultsP2 else stats.doubleFaultsP1

                    val p1Winners = if (isP1) stats.winnersP1 else stats.winnersP2
                    val p2Winners = if (isP1) stats.winnersP2 else stats.winnersP1

                    val p1Ue = if (isP1) stats.unforcedErrorsP1 else stats.unforcedErrorsP2
                    val p2Ue = if (isP1) stats.unforcedErrorsP2 else stats.unforcedErrorsP1

                    StatComparisonRow("Aces", "$p1Aces", "$p2Aces")
                    StatComparisonRow("Double Faults", "$p1Df", "$p2Df")
                    StatComparisonRow("Winners", "$p1Winners", "$p2Winners")
                    StatComparisonRow("Unforced Errors", "$p1Ue", "$p2Ue")

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onNavigateToDetail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("match_detail_button_${match.id}"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Full Live Match Scoring & Stats")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isExpanded) "Tap to collapse stats ▲" else "Tap to expand stats ▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StatComparisonRow(label: String, val1: String, val2: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = val1,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = val2,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}
