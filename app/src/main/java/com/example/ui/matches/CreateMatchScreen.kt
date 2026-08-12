package com.example.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.PublicProfile
import com.example.data.repository.TennisRepository
import com.example.scoring.MatchFormatConfig
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMatchScreen(
    onNavigateBack: () -> Unit,
    onMatchCreated: (String) -> Unit
) {
    val players by TennisRepository.divisionPlayers.collectAsState()
    val currentUser by TennisRepository.currentUser.collectAsState()

    var selectedPlayer1 by remember(players, currentUser) {
        mutableStateOf(players.find { it.id == currentUser?.id } ?: players.firstOrNull())
    }
    var selectedPlayer2 by remember(players, currentUser) {
        mutableStateOf(players.find { it.id != currentUser?.id } ?: players.getOrNull(1))
    }

    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 86400000L) } // Default tomorrow
    var setsToWin by remember { mutableIntStateOf(2) }
    var gamesPerSet by remember { mutableIntStateOf(6) }

    var expandedP1 by remember { mutableStateOf(false) }
    var expandedP2 by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var courtLocation by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Match", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("create_match_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsTennis,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Schedule Division Match",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Select two players and a scheduled date to save to Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Player 1 Selection
            Text(
                text = "Player 1 (Home)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ExposedDropdownMenuBox(
                expanded = expandedP1,
                onExpandedChange = { expandedP1 = !expandedP1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player1_selector")
            ) {
                OutlinedTextField(
                    value = selectedPlayer1?.displayName ?: "Select Player 1",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Player 1") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedP1) },
                    modifier = Modifier
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedP1,
                    onDismissRequest = { expandedP1 = false }
                ) {
                    players.forEach { player ->
                        DropdownMenuItem(
                            text = { Text(player.displayName + if (player.id == currentUser?.id) " (You)" else "") },
                            onClick = {
                                selectedPlayer1 = player
                                expandedP1 = false
                                errorMessage = null
                            }
                        )
                    }
                }
            }

            // Player 2 Selection
            Text(
                text = "Player 2 (Away)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ExposedDropdownMenuBox(
                expanded = expandedP2,
                onExpandedChange = { expandedP2 = !expandedP2 },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player2_selector")
            ) {
                OutlinedTextField(
                    value = selectedPlayer2?.displayName ?: "Select Player 2",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Player 2") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedP2) },
                    modifier = Modifier
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedP2,
                    onDismissRequest = { expandedP2 = false }
                ) {
                    players.forEach { player ->
                        DropdownMenuItem(
                            text = { Text(player.displayName + if (player.id == currentUser?.id) " (You)" else "") },
                            onClick = {
                                selectedPlayer2 = player
                                expandedP2 = false
                                errorMessage = null
                            }
                        )
                    }
                }
            }

            // Match Date Selection
            Text(
                text = "Match Date",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedCard(
                onClick = { showDatePickerDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("match_date_picker"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Match Date",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = dateFormatter.format(Date(selectedDateMillis)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Quick date selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { selectedDateMillis = System.currentTimeMillis() },
                    label = { Text("Today") }
                )
                FilterChip(
                    selected = true,
                    onClick = { selectedDateMillis = System.currentTimeMillis() + 86400000L },
                    label = { Text("Tomorrow") }
                )
                FilterChip(
                    selected = false,
                    onClick = { selectedDateMillis = System.currentTimeMillis() + (86400000L * 3) },
                    label = { Text("In 3 Days") }
                )
            }

            // Location Selection
            Text(
                text = "Court Location",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = courtLocation,
                onValueChange = { courtLocation = it },
                label = { Text("Location Name or Address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("court_location_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Format Selection
            Text(
                text = "Match Format",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = setsToWin == 2,
                    onClick = { setsToWin = 2; gamesPerSet = 6 },
                    label = { Text("Best of 3 Sets") }
                )
                FilterChip(
                    selected = setsToWin == 1,
                    onClick = { setsToWin = 1; gamesPerSet = 8 },
                    label = { Text("Pro Set (8 Games)") }
                )
                FilterChip(
                    selected = setsToWin == 3,
                    onClick = { setsToWin = 3; gamesPerSet = 6 },
                    label = { Text("Best of 5 Sets") }
                )
            }

            errorMessage?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Submit Button
            Button(
                onClick = {
                    val p1 = selectedPlayer1
                    val p2 = selectedPlayer2
                    if (p1 == null || p2 == null) {
                        errorMessage = "Please select both players."
                        return@Button
                    }
                    if (p1.id == p2.id) {
                        errorMessage = "Player 1 and Player 2 must be different players."
                        return@Button
                    }

                    isSubmitting = true
                    val createdMatch = TennisRepository.createMatch(
                        player1Id = p1.id,
                        player2Id = p2.id,
                        scheduledAt = selectedDateMillis,
                        format = MatchFormatConfig(setsToWin = setsToWin, gamesPerSet = gamesPerSet),
                        status = "scheduled",
                        courtLocation = courtLocation.takeIf { it.isNotBlank() }
                    )
                    isSubmitting = false
                    onMatchCreated(createdMatch.id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_match_submit_button"),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save Match to Firestore",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDateMillis = it
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
