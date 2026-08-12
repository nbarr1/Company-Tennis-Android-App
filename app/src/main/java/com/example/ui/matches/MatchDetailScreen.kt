package com.example.ui.matches

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Match
import com.example.data.models.MatchStats
import com.example.data.repository.TennisRepository
import com.example.scoring.Player
import com.example.scoring.ScoreEngine
import com.example.wear.WearOsCompanionSimulatorCard
import com.example.wear.WearOsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    matchId: String,
    onNavigateBack: () -> Unit
) {
    val matches by TennisRepository.matches.collectAsState()
    val user by TennisRepository.currentUser.collectAsState()
    val match = matches.find { it.id == matchId }
    val context = LocalContext.current

    if (match == null) {
        Scaffold { p ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(p),
                contentAlignment = Alignment.Center
            ) {
                Text("Match not found")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${match.player1Name} vs ${match.player2Name}") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("match_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    StatusBadge(status = match.status)
                    IconButton(
                        onClick = {
                            val shareText = "Tennis Match: ${match.player1Name} vs ${match.player2Name}\n" +
                                    "Status: ${match.status.replaceFirstChar { it.uppercase() }}\n" +
                                    "Score: ${ScoreEngine.formatScoreDisplay(match.liveScore)}"
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Match"))
                        },
                        modifier = Modifier.testTag("share_match_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Match")
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
            when (match.status) {
                "proposed" -> ProposedMatchView(match, user?.id ?: "")
                "scheduled" -> ScheduledMatchView(match)
                "in_progress" -> LiveScoringMatchView(match)
                "pending_report" -> PendingReportView(match, user?.id ?: "")
                "completed" -> CompletedMatchView(match)
                "disputed" -> DisputedMatchView(match)
                else -> CompletedMatchView(match)
            }
        }
    }
}

@Composable
fun ProposedMatchView(match: Match, currentUserId: String) {
    val isRecipient = match.player2Id == currentUserId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isRecipient) "Match Challenge Received" else "Match Proposal Sent",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Challenger: ${match.player1Name}\nFormat: Best of ${match.format.setsToWin} sets",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isRecipient) {
            Button(
                onClick = { TennisRepository.acceptMatchProposal(match.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("accept_proposal_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Accept Challenge", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { TennisRepository.declineMatchProposal(match.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("decline_proposal_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Decline", fontSize = 16.sp)
            }
        } else {
            OutlinedButton(
                onClick = { TennisRepository.declineMatchProposal(match.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("withdraw_proposal_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Withdraw Proposal", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ScheduledMatchView(match: Match) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Match Scheduled", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${match.player1Name} vs ${match.player2Name}\nFormat: Best of ${match.format.setsToWin} sets",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        match.courtLocation?.takeIf { it.isNotBlank() }?.let { location ->
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val gmmIntentUri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(location)}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    context.startActivity(mapIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("navigate_to_court_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Place, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Navigate to Court", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { TennisRepository.startMatch(match.id) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_live_scoring_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Live Scoring", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LiveScoringMatchView(match: Match) {
    val scrollState = rememberScrollState()
    var showAdvancedStats by remember { mutableStateOf(false) }

    val liveScore = match.liveScore
    val tip = ScoreEngine.detectOpportunityTip(liveScore, match.format)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Wear OS connection status chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Watch, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Wear OS Sync Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }

        // Live Scoreboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sets Score Table
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = match.player1Name ?: "Player 1",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (liveScore.server == Player.PLAYER1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = match.player2Name ?: "Player 2",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (liveScore.server == Player.PLAYER2) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        liveScore.sets.forEach { set ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${set.player1Games}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                Text("${set.player2Games}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Game Score Banner
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (liveScore.isTiebreak) "TIEBREAK" else "GAME SCORE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = ScoreEngine.formatGameScore(liveScore),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Opportunity Tip Banner if active
                tip?.let { t ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🔥 ${t.name.replace("_", " ")}!",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tennis Court Canvas Visualizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TennisCourtCanvas(server = liveScore.server)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Point Addition Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { TennisRepository.applyLivePoint(match.id, Player.PLAYER1) },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("point_player1_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+ Point", fontSize = 12.sp)
                    Text(match.player1Name ?: "P1", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Button(
                onClick = { TennisRepository.applyLivePoint(match.id, Player.PLAYER2) },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("point_player2_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+ Point", fontSize = 12.sp)
                    Text(match.player2Name ?: "P2", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Undo Last Point Button
        OutlinedButton(
            onClick = { TennisRepository.undoLivePoint(match.id) },
            enabled = match.undoSnapshot != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("undo_point_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Undo Last Point")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wear OS Companion Watch Display
        WearOsCompanionSimulatorCard(
            player1Name = match.player1Name ?: "Player 1",
            player2Name = match.player2Name ?: "Player 2",
            liveScore = liveScore,
            onAddPointP1 = { TennisRepository.applyLivePoint(match.id, Player.PLAYER1) },
            onAddPointP2 = { TennisRepository.applyLivePoint(match.id, Player.PLAYER2) },
            onUndoPoint = { TennisRepository.undoLivePoint(match.id) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Stats Expandable Section
        TextButton(
            onClick = { showAdvancedStats = !showAdvancedStats },
            modifier = Modifier.testTag("toggle_advanced_stats_button")
        ) {
            Text(if (showAdvancedStats) "Hide Advanced Stats" else "Show Advanced Match Stats")
            Icon(if (showAdvancedStats) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }

        if (showAdvancedStats) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live Match Stats", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Aces: ${match.stats.acesP1} - ${match.stats.acesP2}")
                        Text("Winners: ${match.stats.winnersP1} - ${match.stats.winnersP2}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Double Faults: ${match.stats.doubleFaultsP1} - ${match.stats.doubleFaultsP2}")
                        Text("Unforced Errors: ${match.stats.unforcedErrorsP1} - ${match.stats.unforcedErrorsP2}")
                    }
                }
            }
        }
    }
}

@Composable
fun TennisCourtCanvas(server: Player) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        // Court boundary
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            style = Stroke(width = 3f)
        )

        // Net line (middle vertical)
        drawLine(
            color = Color.White,
            start = Offset(w / 2, 0f),
            end = Offset(w / 2, h),
            strokeWidth = 4f
        )

        // Baseline / Service lines
        drawLine(
            color = Color.White,
            start = Offset(w * 0.2f, 0f),
            end = Offset(w * 0.2f, h),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.8f, 0f),
            end = Offset(w * 0.8f, h),
            strokeWidth = 2f
        )

        // Server ball icon position
        val serverX = if (server == Player.PLAYER1) w * 0.1f else w * 0.9f
        val serverY = h * 0.5f

        drawCircle(
            color = Color(0xFFC0CA33),
            radius = 12f,
            center = Offset(serverX, serverY)
        )
    }
}

@Composable
fun PendingReportView(match: Match, currentUserId: String) {
    val isWinner = (match.winner == "player1" && match.player1Id == currentUserId) ||
            (match.winner == "player2" && match.player2Id == currentUserId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Pending Report Confirmation", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Final Score: ${ScoreEngine.formatScoreDisplay(match.liveScore)}\nWinner: ${if (match.winner == "player1") match.player1Name else match.player2Name}",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { TennisRepository.confirmMatchReport(match.id) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("confirm_report_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Confirm Match Result", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { TennisRepository.disputeMatchReport(match.id, "Score mismatch") },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("dispute_report_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Dispute Score", fontSize = 16.sp)
        }
    }
}

@Composable
fun CompletedMatchView(match: Match) {
    val context = LocalContext.current
    val reportUrl = match.reportUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF2E7D32))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Match Completed & Confirmed", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Winner: ${if (match.winner == "player1") match.player1Name else match.player2Name}\nScore: ${ScoreEngine.formatScoreDisplay(match.liveScore)}",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (!reportUrl.isNullOrBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl)))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("download_pdf_report_button"),
            shape = RoundedCornerShape(12.dp),
            enabled = !reportUrl.isNullOrBlank()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (reportUrl.isNullOrBlank()) "Generating PDF Report…" else "Download Official PDF Report")
        }
    }
}

@Composable
fun DisputedMatchView(match: Match) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFFD32F2F))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Match Report Disputed", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "A division leader has been notified to resolve this disputed match report.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
