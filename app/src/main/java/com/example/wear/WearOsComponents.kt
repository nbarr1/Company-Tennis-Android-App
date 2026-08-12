package com.example.wear

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scoring.GameScore
import com.example.scoring.LiveScore
import com.example.scoring.Player
import com.example.scoring.ScoreEngine
import com.example.scoring.TennisPoint

/**
 * Basic UI components for Wear OS companion watch display and score controller.
 */

// Colors customized for high contrast watch ambient display
private val WatchBgColor = Color(0xFF0F0F14)
private val WatchCardBg = Color(0xFF1C1B26)
private val WatchAccentLime = Color(0xFFC0CA33)
private val WatchAccentViolet = Color(0xFFB69DF8)
private val WatchTextBright = Color(0xFFF4F3F7)
private val WatchTextMuted = Color(0xFFA0A0B0)

/**
 * Circular Wear OS Watch Face displaying current live match score.
 */
@Composable
fun WearScoreWatchFace(
    player1Name: String,
    player2Name: String,
    liveScore: LiveScore,
    onAddPointP1: () -> Unit = {},
    onAddPointP2: () -> Unit = {},
    onUndoPoint: () -> Unit = {},
    onToggleServer: () -> Unit = {},
    isInteractive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(230.dp)
            .clip(CircleShape)
            .background(WatchBgColor)
            .border(3.dp, WatchAccentViolet.copy(alpha = 0.6f), CircleShape)
            .padding(12.dp)
            .testTag("wear_os_watch_face"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Watch Top Header: Set status & Serve indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = WatchAccentViolet.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (WearOsManager.isWearOsAvailable()) Color(0xFF4CAF50) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (liveScore.isTiebreak) "TIEBREAK" else "SET ${liveScore.sets.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WatchAccentViolet,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Center Score Readout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Sets games row summary (e.g. 6-4, 3-2)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = player1Name.take(6).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (liveScore.server == Player.PLAYER1) WatchAccentLime else WatchTextMuted,
                        maxLines = 1
                    )
                    Text(
                        text = ScoreEngine.formatSetsSummary(liveScore),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WatchTextBright,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = player2Name.take(6).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (liveScore.server == Player.PLAYER2) WatchAccentLime else WatchTextMuted,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Current Game / Tiebreak Score Display
                Text(
                    text = ScoreEngine.formatGameScore(liveScore),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = WatchTextBright,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("wear_os_game_score_text")
                )

                // Serve side / tip indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsTennis,
                        contentDescription = "Server",
                        tint = WatchAccentLime,
                        modifier = Modifier
                            .size(12.dp)
                            .clickable(enabled = isInteractive) { onToggleServer() }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Serving: ${if (liveScore.server == Player.PLAYER1) player1Name.take(5) else player2Name.take(5)} (${liveScore.serviceSide.name})",
                        fontSize = 9.sp,
                        color = WatchTextMuted
                    )
                }
            }

            // Watch Action Buttons (Touch targets for Watch)
            if (isInteractive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // + Point P1 Button
                    Surface(
                        onClick = onAddPointP1,
                        shape = CircleShape,
                        color = WatchAccentViolet,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("wear_os_btn_p1")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "P1",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    // Undo Button
                    Surface(
                        onClick = onUndoPoint,
                        shape = CircleShape,
                        color = WatchCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, WatchTextMuted.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("wear_os_btn_undo")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = WatchTextBright,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // + Point P2 Button
                    Surface(
                        onClick = onAddPointP2,
                        shape = CircleShape,
                        color = WatchAccentViolet,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("wear_os_btn_p2")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "P2",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Compact Tile / Complication UI component for Wear OS glanceable score.
 */
@Composable
fun WearScoreTileCard(
    player1Name: String,
    player2Name: String,
    liveScore: LiveScore,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(180.dp)
            .testTag("wear_os_tile_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WatchCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = null,
                        tint = WatchAccentViolet,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Match Watch",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WatchAccentViolet
                    )
                }

                Surface(
                    color = WatchAccentLime.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "LIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WatchAccentLime,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Players and current game points
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = player1Name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WatchTextBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (liveScore.server == Player.PLAYER1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(WatchAccentLime))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = player2Name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WatchTextBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (liveScore.server == Player.PLAYER2) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(WatchAccentLime))
                        }
                    }
                }

                Text(
                    text = ScoreEngine.formatGameScore(liveScore),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = WatchAccentLime,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Interactive Phone Companion Preview Overlay displaying the Wear OS watch face in action.
 */
@Composable
fun WearOsCompanionSimulatorCard(
    player1Name: String,
    player2Name: String,
    liveScore: LiveScore,
    onAddPointP1: () -> Unit,
    onAddPointP2: () -> Unit,
    onUndoPoint: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wear_os_companion_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wear OS Companion Watch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Wear OS display"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Watch Live View & Direct Wrist Input Simulation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    WearScoreWatchFace(
                        player1Name = player1Name,
                        player2Name = player2Name,
                        liveScore = liveScore,
                        onAddPointP1 = onAddPointP1,
                        onAddPointP2 = onAddPointP2,
                        onUndoPoint = onUndoPoint,
                        onToggleServer = {
                            WearOsManager.simulatePointInputFromWatch(liveScore.server.other)
                        },
                        isInteractive = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Tap P1 / P2 on watch face to trigger live point inputs via Wear OS DataClient.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
