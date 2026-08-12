package com.example.ui.rankings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import com.example.data.models.PlayerRanking
import com.example.data.repository.TennisRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen() {
    val rankings by TennisRepository.rankings.collectAsState()
    val user by TennisRepository.currentUser.collectAsState()
    val division by TennisRepository.currentDivision.collectAsState()
    val selectedSeasonId by TennisRepository.selectedSeasonId.collectAsState()
    val availableSeasons by TennisRepository.availableSeasons.collectAsState()

    var selectedSortBy by remember { mutableIntStateOf(0) } // 0: Rank, 1: Win %, 2: Diff
    var isRefreshing by remember { mutableStateOf(false) }
    var seasonMenuExpanded by remember { mutableStateOf(false) }

    val currentSeasonName = availableSeasons.find { it.id == selectedSeasonId }?.name ?: "Fall 2026"

    // Real-time Firestore sync setup on initial launch
    LaunchedEffect(user?.divisionId) {
        val divId = user?.divisionId ?: "div_metro_1"
        TennisRepository.listenToPlayersRealtime(divId)
        TennisRepository.listenToDivisionMatchesRealtime(divId)
        TennisRepository.listenToLeagueRealtime(divId)
    }

    val sortedRankings = remember(rankings, selectedSortBy) {
        when (selectedSortBy) {
            1 -> rankings.sortedByDescending { if (it.matchesPlayed > 0) (it.matchesWon.toFloat() / it.matchesPlayed) else 0f }
            2 -> rankings.sortedByDescending { it.gameDifferential }
            else -> rankings.sortedBy { it.rank }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Leaderboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Division Standings", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isRefreshing = true
                                TennisRepository.recalculateDivisionRankings()
                                user?.divisionId?.let { divId ->
                                    TennisRepository.listenToPlayersRealtime(divId)
                                    TennisRepository.listenToDivisionMatchesRealtime(divId)
                                }
                                isRefreshing = false
                            },
                            modifier = Modifier.testTag("rankings_recalculate_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Firestore Stats")
                        }
                    }
                )

                // Season Selector Bar
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
                                    .testTag("rankings_season_selector")
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
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // League Header Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
                            text = division?.name ?: "Metro Division 1",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "$currentSeasonName Season • Live Firestore Standings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${rankings.size} Players",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Sort Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSortBy == 0,
                    onClick = { selectedSortBy = 0 },
                    label = { Text("Rank Order") }
                )
                FilterChip(
                    selected = selectedSortBy == 1,
                    onClick = { selectedSortBy = 1 },
                    label = { Text("Win Rate %") }
                )
                FilterChip(
                    selected = selectedSortBy == 2,
                    onClick = { selectedSortBy = 2 },
                    label = { Text("Game Differential") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Material3 Data Table Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rankings_table_header")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Player", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("W - L", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Win %", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Diff", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }

            // Data Table Content Rows
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("rankings_table"),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(sortedRankings) { ranking ->
                    RankingRowItem(
                        ranking = ranking,
                        isCurrentUser = ranking.userId == user?.id
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
fun RankingRowItem(ranking: PlayerRanking, isCurrentUser: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ranking_item_${ranking.userId}"),
        color = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Badge / Medal
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (ranking.rank <= 3) {
                    val (medalBg, medalText) = when (ranking.rank) {
                        1 -> Pair(Color(0xFFFFD700), Color.Black)
                        2 -> Pair(Color(0xFFC0C0C0), Color.Black)
                        else -> Pair(Color(0xFFCD7F32), Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(medalBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${ranking.rank}",
                            fontWeight = FontWeight.ExtraBold,
                            color = medalText,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = "${ranking.rank}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Player Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ranking.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrentUser) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "You",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${ranking.setsWon} Sets W • ${ranking.gamesWon} Games W",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Matches W - L
            Text(
                text = "${ranking.matchesWon} - ${ranking.matchesLost}",
                modifier = Modifier.width(56.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Win %
            val winPct = if (ranking.matchesPlayed > 0) {
                ((ranking.matchesWon.toFloat() / ranking.matchesPlayed) * 100).toInt()
            } else 0

            Text(
                text = "$winPct%",
                modifier = Modifier.width(52.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Game Differential
            val diffText = if (ranking.gameDifferential > 0) "+${ranking.gameDifferential}" else "${ranking.gameDifferential}"
            Text(
                text = diffText,
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (ranking.gameDifferential >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End
            )
        }
    }
}
