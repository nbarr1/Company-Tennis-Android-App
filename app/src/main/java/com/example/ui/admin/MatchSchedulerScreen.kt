package com.example.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.text.SimpleDateFormat
import java.util.*

data class ScheduledRound(
    val roundNumber: Int,
    val scheduledDate: Long,
    val matches: List<Match>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSchedulerScreen(
    onNavigateBack: () -> Unit = {}
) {
    val players by TennisRepository.divisionPlayers.collectAsState()
    val rankings by TennisRepository.rankings.collectAsState()
    val division by TennisRepository.currentDivision.collectAsState()

    // Player selection state
    var selectedPlayerIds by remember(players) {
        mutableStateOf(players.map { it.id }.toSet())
    }

    // Scheduling Parameters
    var isDoubleRoundRobin by remember { mutableStateOf(false) }
    var intervalDays by remember { mutableIntStateOf(7) } // Default 1 week between rounds
    var seedByRankings by remember { mutableStateOf(true) }
    var clearExistingScheduled by remember { mutableStateOf(true) }
    var defaultCourtLocation by remember { mutableStateOf("Center Court - Metro Club") }

    // Start date offset from today (0 = today, 1 = tomorrow, 7 = next week)
    var startDateDaysOffset by remember { mutableIntStateOf(1) }

    // Generated Schedule
    var generatedRounds by remember { mutableStateOf<List<ScheduledRound>>(emptyList()) }
    var showPublishConfirmDialog by remember { mutableStateOf(false) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

    // Sort players by rank if requested
    val sortedPlayers = remember(players, rankings, seedByRankings) {
        if (seedByRankings && rankings.isNotEmpty()) {
            val rankMap = rankings.associateBy { it.userId }
            players.sortedBy { rankMap[it.id]?.rank ?: 999 }
        } else {
            players
        }
    }

    // Helper to generate Round-Robin schedule algorithm
    fun generateSchedule() {
        val activePlayers = sortedPlayers.filter { selectedPlayerIds.contains(it.id) }
        if (activePlayers.size < 2) {
            generatedRounds = emptyList()
            return
        }

        val playerList = activePlayers.toMutableList()
        // If odd number of players, add a dummy BYE player
        val isOdd = playerList.size % 2 != 0
        if (isOdd) {
            playerList.add(PublicProfile(id = "BYE", displayName = "BYE"))
        }

        val numPlayers = playerList.size
        val numRounds = numPlayers - 1
        val matchesPerRound = numPlayers / 2

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, startDateDaysOffset)
            set(Calendar.HOUR_OF_DAY, 18) // 6:00 PM default match time
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val baseTimeMs = calendar.timeInMillis

        val roundsList = mutableListOf<ScheduledRound>()

        fun buildSingleLeg(legOffsetRounds: Int = 0): List<ScheduledRound> {
            val legRounds = mutableListOf<ScheduledRound>()
            // Berger Rotation algorithm
            val pool = playerList.toMutableList()

            for (r in 0 until numRounds) {
                val roundNum = legOffsetRounds + r + 1
                val roundDateMs = baseTimeMs + ((legOffsetRounds + r) * intervalDays * 86400000L)
                val roundMatches = mutableListOf<Match>()

                for (i in 0 until matchesPerRound) {
                    val p1 = pool[i]
                    val p2 = pool[numPlayers - 1 - i]

                    // Skip BYE pairings
                    if (p1.id != "BYE" && p2.id != "BYE") {
                        // Alternate home/away based on round index
                        val (home, away) = if ((r + i) % 2 == 0) Pair(p1, p2) else Pair(p2, p1)

                        val match = Match(
                            id = "sched_match_${System.currentTimeMillis()}_${roundNum}_$i",
                            divisionId = division?.id ?: "div_metro_1",
                            seasonId = "fall-2026",
                            divisionLevelId = "lvl_advanced",
                            player1Id = home.id,
                            player2Id = away.id,
                            player1Name = home.displayName,
                            player2Name = away.displayName,
                            playerIds = listOf(home.id, away.id),
                            status = "scheduled",
                            courtLocation = defaultCourtLocation,
                            scheduledAt = roundDateMs
                        )
                        roundMatches.add(match)
                    }
                }

                legRounds.add(ScheduledRound(roundNumber = roundNum, scheduledDate = roundDateMs, matches = roundMatches))

                // Rotate pool elements (keep index 0 fixed, rotate 1..numPlayers-1)
                val last = pool.removeAt(pool.size - 1)
                pool.add(1, last)
            }
            return legRounds
        }

        val firstLeg = buildSingleLeg(0)
        roundsList.addAll(firstLeg)

        if (isDoubleRoundRobin) {
            // Second leg: Reverse home and away for all matchups
            val secondLeg = buildSingleLeg(numRounds).map { round ->
                val reversedMatches = round.matches.map { m ->
                    m.copy(
                        id = m.id + "_return",
                        player1Id = m.player2Id,
                        player2Id = m.player1Id,
                        player1Name = m.player2Name,
                        player2Name = m.player1Name,
                        playerIds = listOfNotNull(m.player2Id, m.player1Id)
                    )
                }
                round.copy(matches = reversedMatches)
            }
            roundsList.addAll(secondLeg)
        }

        generatedRounds = roundsList
    }

    val totalMatchesCount = generatedRounds.sumOf { it.matches.size }

    Scaffold(
        modifier = Modifier.testTag("match_scheduler_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Round-Robin Match Scheduler", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("match_scheduler_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            if (showSuccessSnackbar) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { showSuccessSnackbar = false }) {
                            Text("OK", color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                ) {
                    Text(successMessage)
                }
            }
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
            // Header Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Auto-Schedule Generator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Generate a balanced round-robin fixture list for ${division?.name ?: "Metro Division 1"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Step 1: Select Roster
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Participating Roster (${selectedPlayerIds.size}/${players.size})", fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = {
                                    selectedPlayerIds = if (selectedPlayerIds.size == players.size) {
                                        emptySet()
                                    } else {
                                        players.map { it.id }.toSet()
                                    }
                                }
                            ) {
                                Text(if (selectedPlayerIds.size == players.size) "Deselect All" else "Select All")
                            }
                        }

                        sortedPlayers.forEach { player ->
                            val isSelected = selectedPlayerIds.contains(player.id)
                            val rankInfo = rankings.find { it.userId == player.id }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedPlayerIds = if (isSelected) {
                                            selectedPlayerIds - player.id
                                        } else {
                                            selectedPlayerIds + player.id
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedPlayerIds = if (checked) selectedPlayerIds + player.id else selectedPlayerIds - player.id
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(player.displayName, fontWeight = FontWeight.Medium)
                                }

                                rankInfo?.let {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Rank #${it.rank}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 2: Configure Rules & Schedule Spacing
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Schedule Rules & Spacing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        // Format Choice: Single vs Double Round Robin
                        Column {
                            Text("Tournament Format", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !isDoubleRoundRobin,
                                    onClick = { isDoubleRoundRobin = false },
                                    label = { Text("Single Round-Robin") },
                                    leadingIcon = if (!isDoubleRoundRobin) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null
                                )
                                FilterChip(
                                    selected = isDoubleRoundRobin,
                                    onClick = { isDoubleRoundRobin = true },
                                    label = { Text("Double (Home & Away)") },
                                    leadingIcon = if (isDoubleRoundRobin) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null
                                )
                            }
                        }

                        // Round Spacing
                        Column {
                            Text("Interval Between Rounds", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3 to "3 Days", 7 to "1 Week", 14 to "2 Weeks").forEach { (days, label) ->
                                    FilterChip(
                                        selected = intervalDays == days,
                                        onClick = { intervalDays = days },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("interval_chip_$days")
                                    )
                                }
                            }
                        }

                        // Start Offset
                        Column {
                            Text("Start Schedule From", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1 to "Tomorrow", 3 to "In 3 Days", 7 to "Next Week").forEach { (offset, label) ->
                                    FilterChip(
                                        selected = startDateDaysOffset == offset,
                                        onClick = { startDateDaysOffset = offset },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        // Default Court Location
                        Column {
                            Text("Tennis Court Venue / Location", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = defaultCourtLocation,
                                onValueChange = { defaultCourtLocation = it },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                label = { Text("Court Location") },
                                placeholder = { Text("e.g. Center Court - Metro Tennis Center") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("court_location_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Seed By Ranking Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Seed Using Current Rankings", fontWeight = FontWeight.SemiBold)
                                Text("Sorts players by leaderboard position for optimal matchups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = seedByRankings,
                                onCheckedChange = { seedByRankings = it }
                            )
                        }
                    }
                }
            }

            // Step 3: Generate Button & Action Card
            item {
                Button(
                    onClick = { generateSchedule() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_schedule_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.EventRepeat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Schedule Preview", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Generated Schedule Preview Section
            if (generatedRounds.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Generated Fixture Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$totalMatchesCount Matches (${generatedRounds.size} Rounds)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                items(generatedRounds) { round ->
                    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Round ${round.roundNumber}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = dateFormat.format(Date(round.scheduledDate)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            round.matches.forEach { match ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(match.player1Name ?: "Player 1", fontWeight = FontWeight.SemiBold)
                                            Text("  vs  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Text(match.player2Name ?: "Player 2", fontWeight = FontWeight.SemiBold)
                                        }

                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Scheduled", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }

                                    match.courtLocation?.let { loc ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = "Location",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = loc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 4: Publish Schedule Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { clearExistingScheduled = !clearExistingScheduled }
                            ) {
                                Checkbox(
                                    checked = clearExistingScheduled,
                                    onCheckedChange = { clearExistingScheduled = it },
                                    modifier = Modifier.testTag("clear_existing_checkbox")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Clear existing unscheduled matches", fontWeight = FontWeight.SemiBold)
                                    Text("Replaces unplayed scheduled matches with this new fixture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Button(
                                onClick = { showPublishConfirmDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("publish_schedule_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publish Schedule to Division", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog before publishing to Firestore
    if (showPublishConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPublishConfirmDialog = false },
            title = { Text("Publish Division Schedule?") },
            text = {
                Text(
                    "This will save $totalMatchesCount generated round-robin matches across ${generatedRounds.size} rounds to Firestore for ${division?.name ?: "Metro Division 1"}."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val allMatches = generatedRounds.flatMap { it.matches }
                        TennisRepository.publishGeneratedSchedule(allMatches, clearExistingScheduled)

                        showPublishConfirmDialog = false
                        successMessage = "Successfully published $totalMatchesCount matches to division schedule!"
                        showSuccessSnackbar = true
                    }
                ) {
                    Text("Publish Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublishConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
