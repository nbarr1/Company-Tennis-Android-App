package com.example.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Match
import com.example.data.repository.TennisRepository
import kotlin.math.max
import kotlin.math.min

data class TrendPoint(
    val matchIndex: Int,
    val isWin: Boolean,
    val scoreText: String,
    val opponentName: String,
    val cumulativeValue: Int
)

@Composable
fun PlayerMatchTrendChart(
    userId: String,
    modifier: Modifier = Modifier
) {
    val matches by TennisRepository.matches.collectAsState()

    // Filter completed matches involving the user, sorted by completed time or creation
    val playerMatches = remember(matches, userId) {
        matches
            .filter { match ->
                match.status == "completed" && (match.player1Id == userId || match.player2Id == userId)
            }
            .sortedBy { it.completedAt ?: it.createdAt }
            .takeLast(10)
    }

    val trendPoints = remember(playerMatches, userId) {
        if (playerMatches.isEmpty()) {
            // Generates representative fallback trends for demonstration if no finished matches
            listOf(
                TrendPoint(1, true, "6-4, 6-3", "R. Federer", 1),
                TrendPoint(2, true, "7-5, 6-2", "C. Alcaraz", 2),
                TrendPoint(3, false, "4-6, 3-6", "N. Djokovic", 1),
                TrendPoint(4, true, "6-2, 6-4", "D. Medvedev", 2),
                TrendPoint(5, true, "6-1, 6-3", "A. Zverev", 3),
                TrendPoint(6, false, "5-7, 4-6", "J. Sinner", 2),
                TrendPoint(7, true, "6-3, 6-4", "H. Rune", 3),
                TrendPoint(8, true, "6-2, 7-6", "S. Tsitsipas", 4),
                TrendPoint(9, false, "6-7, 2-6", "C. Ruud", 3),
                TrendPoint(10, true, "6-4, 6-2", "G. Monfils", 4)
            )
        } else {
            var runningScore = 0
            playerMatches.mapIndexed { index, match ->
                val isP1 = match.player1Id == userId
                val isWinner = if (isP1) match.winner == "player1" else match.winner == "player2"
                if (isWinner) runningScore++ else runningScore = max(0, runningScore - 1)

                val opponent = if (isP1) match.player2Name ?: "Opponent" else match.player1Name ?: "Opponent"
                val score = if (match.liveScore.sets.isNotEmpty()) {
                    match.liveScore.sets.joinToString(", ") { "${it.player1Games}-${it.player2Games}" }
                } else {
                    "${match.liveScore.player1SetsWon}-${match.liveScore.player2SetsWon}"
                }

                TrendPoint(
                    matchIndex = index + 1,
                    isWin = isWinner,
                    scoreText = score,
                    opponentName = opponent,
                    cumulativeValue = runningScore
                )
            }
        }
    }

    val winsCount = trendPoints.count { it.isWin }
    val totalCount = trendPoints.size
    val winPercentage = if (totalCount > 0) (winsCount * 100) / totalCount else 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_match_trend_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Match Performance Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Last $totalCount Matches Trend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${winsCount}W - ${totalCount - winsCount}L",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "($winPercentage%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Trend Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                val lineColor = MaterialTheme.colorScheme.primary
                val winPointColor = Color(0xFF2E7D32)
                val lossPointColor = Color(0xFFD32F2F)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (trendPoints.isEmpty()) return@Canvas

                    val width = size.width
                    val height = size.height

                    val minVal = 0
                    val maxVal = max(4, trendPoints.maxOf { it.cumulativeValue } + 1)

                    val xStep = width / (trendPoints.size - 1).coerceAtLeast(1)

                    val points = trendPoints.mapIndexed { i, pt ->
                        val x = i * xStep
                        val normalizedY = (pt.cumulativeValue - minVal).toFloat() / (maxVal - minVal)
                        val y = height - (normalizedY * (height - 30.dp.toPx())) - 15.dp.toPx()
                        Offset(x, y)
                    }

                    // Draw Gradient Fill under line
                    val fillPath = Path().apply {
                        moveTo(points.first().x, height)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                lineColor.copy(alpha = 0.0f)
                            )
                        )
                    )

                    // Draw connecting line
                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }

                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Points with indicators
                    points.forEachIndexed { idx, pt ->
                        val isWin = trendPoints[idx].isWin
                        val color = if (isWin) winPointColor else lossPointColor

                        // Halo circle
                        drawCircle(
                            color = color.copy(alpha = 0.25f),
                            radius = 8.dp.toPx(),
                            center = pt
                        )

                        // Solid dot
                        drawCircle(
                            color = color,
                            radius = 5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            // Match Result Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                trendPoints.forEach { pt ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (pt.isWin) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (pt.isWin) "W" else "L",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pt.isWin) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                        Text(
                            text = "M${pt.matchIndex}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
