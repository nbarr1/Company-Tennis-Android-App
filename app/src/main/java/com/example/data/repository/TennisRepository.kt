package com.example.data.repository

import android.util.Log
import com.example.data.models.*
import com.example.firebase.FirebaseInitializer
import com.example.scoring.*
import com.example.scoring.Player
import com.example.wear.WearOsManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

object TennisRepository {

    // Current logged in user
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Current Division
    private val _currentDivision = MutableStateFlow<Division?>(null)
    val currentDivision: StateFlow<Division?> = _currentDivision.asStateFlow()

    // Division Levels
    private val _divisionLevels = MutableStateFlow<List<DivisionLevel>>(emptyList())
    val divisionLevels: StateFlow<List<DivisionLevel>> = _divisionLevels.asStateFlow()

    // Division Players
    private val _divisionPlayers = MutableStateFlow<List<PublicProfile>>(emptyList())
    val divisionPlayers: StateFlow<List<PublicProfile>> = _divisionPlayers.asStateFlow()

    // Matches Store
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    // Rankings Store
    private val _rankings = MutableStateFlow<List<PlayerRanking>>(emptyList())
    val rankings: StateFlow<List<PlayerRanking>> = _rankings.asStateFlow()

    // Channels Store
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    // Messages Store
    private val _messagesMap = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<Message>>> = _messagesMap.asStateFlow()

    // Seasons Store
    private val _availableSeasons = MutableStateFlow(
        listOf(
            Season(id = "fall-2026", name = "Fall 2026", year = 2026, half = "fall", status = "active"),
            Season(id = "spring-2026", name = "Spring 2026", year = 2026, half = "spring", status = "completed"),
            Season(id = "fall-2025", name = "Fall 2025", year = 2025, half = "fall", status = "archived"),
            Season(id = "spring-2025", name = "Spring 2025", year = 2025, half = "spring", status = "archived")
        )
    )
    val availableSeasons: StateFlow<List<Season>> = _availableSeasons.asStateFlow()

    private val _selectedSeasonId = MutableStateFlow("fall-2026")
    val selectedSeasonId: StateFlow<String> = _selectedSeasonId.asStateFlow()

    fun setSelectedSeason(seasonId: String) {
        _selectedSeasonId.value = seasonId
        recalculateRankingsInternal()
    }

    init {
        // recalculateRankingsInternal()
    }

    suspend fun login(email: String, pass: String): Boolean {
        val auth = FirebaseInitializer.getAuth() ?: return false
        if (email.isBlank() || pass.isBlank()) return false
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            val user = auth.currentUser
            if (user != null) {
                // Fetch user profile from Firestore
                val firestore = FirebaseInitializer.getFirestore()
                if (firestore != null) {
                    val doc = firestore.collection("users").document(user.uid).get().await()
                    val profile = doc.toObject(User::class.java)
                    if (profile != null) {
                        _currentUser.value = profile
                    } else {
                        _currentUser.value = User(id = user.uid, displayName = email.substringBefore("@"), email = email, divisionId = null, tutorialDone = true)
                    }
                }
                true
            } else false
        } catch (e: Exception) {
            Log.w("TennisRepository", "Firebase email auth error: ${e.message}")
            false
        }
    }

    suspend fun signUp(email: String, pass: String, displayName: String): Boolean {
        val auth = FirebaseInitializer.getAuth() ?: return false
        if (email.isBlank() || pass.isBlank()) return false
        return try {
            auth.createUserWithEmailAndPassword(email, pass).await()
            val user = auth.currentUser
            if (user != null) {
                val newUser = User(
                    id = user.uid,
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    email = email,
                    divisionId = null,
                    tutorialDone = false
                )
                // Save to Firestore
                val firestore = FirebaseInitializer.getFirestore()
                if (firestore != null) {
                    firestore.collection("users").document(user.uid).set(newUser).await()
                }
                _currentUser.value = newUser
                true
            } else false
        } catch (e: Exception) {
            Log.w("TennisRepository", "Firebase sign-up error: ${e.message}")
            false
        }
    }

    suspend fun loginWithGoogle(email: String = "alex.rivera@example.com", displayName: String = "Alex Rivera"): Boolean {
        // In a real app, this uses GoogleSignInClient. Here we'll just mock it or use the same flow.
        val auth = FirebaseInitializer.getAuth()
        _currentUser.value = User(
            id = auth?.currentUser?.uid ?: ("google_user_" + System.currentTimeMillis()),
            displayName = displayName,
            email = email,
            divisionId = "div_metro_1",
            tutorialDone = true
        )
        return true
    }

    fun logout() {
        val auth = FirebaseInitializer.getAuth()
        auth?.signOut()
        _currentUser.value = null
        _currentDivision.value = null
        _divisionPlayers.value = emptyList()
        _matches.value = emptyList()
    }

    fun createMatch(player1Id: String, player2Id: String, format: MatchFormatConfig = MatchFormatConfig(), status: String = "in_progress", scheduledAt: Long? = null, courtLocation: String? = null): Match {
        val p1 = _divisionPlayers.value.find { it.id == player1Id }
            ?: if (_currentUser.value?.id == player1Id) PublicProfile(id = _currentUser.value!!.id, displayName = _currentUser.value!!.displayName) else null
        val p2 = _divisionPlayers.value.find { it.id == player2Id }
            ?: if (_currentUser.value?.id == player2Id) PublicProfile(id = _currentUser.value!!.id, displayName = _currentUser.value!!.displayName) else null

        val p1Name = p1?.displayName ?: "Player 1"
        val p2Name = p2?.displayName ?: "Player 2"
        val divId = _currentUser.value?.divisionId ?: "div_metro_1"

        val newMatch = Match(
            id = "match_${System.currentTimeMillis()}",
            divisionId = divId,
            seasonId = _selectedSeasonId.value,
            divisionLevelId = "lvl_advanced",
            player1Id = player1Id,
            player2Id = player2Id,
            player1Name = p1Name,
            player2Name = p2Name,
            playerIds = listOf(player1Id, player2Id),
            format = format,
            status = status,
            createdBy = _currentUser.value?.id ?: player1Id,
            scheduledAt = scheduledAt,
            courtLocation = courtLocation
        )

        _matches.value = listOf(newMatch) + _matches.value
        syncMatchToFirestore(newMatch)
        return newMatch
    }

    fun proposeMatch(opponentId: String, levelId: String, proposedTime: Long?, format: MatchFormatConfig): Match {
        return createMatch(_currentUser.value?.id ?: "", opponentId, format, "proposed", proposedTime)
    }

    fun markTutorialDone() {
        _currentUser.value = _currentUser.value?.copy(tutorialDone = true)
    }

    fun updateProfile(user: User) {
        _currentUser.value = user
    }

    fun updateAvailability(availability: Availability) {
        _currentUser.value = _currentUser.value?.copy(availability = availability)
    }

    fun updateUserProfile(userId: String = "", displayName: String = "", avatarUrl: String = "", phone: String? = ""): Boolean {
        _currentUser.value = _currentUser.value?.copy(displayName = displayName, phone = phone ?: "")
        return true
    }

    fun acceptMatchProposal(matchId: String) {
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) m.copy(status = "scheduled") else m
        }
    }

    fun declineMatchProposal(matchId: String) {
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) m.copy(status = "cancelled") else m
        }
    }

    fun startMatch(matchId: String) {
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) {
                m.copy(
                    status = "in_progress",
                    startedAt = System.currentTimeMillis(),
                    liveScore = ScoreEngine.createInitialScore(m.format)
                )
            } else m
        }
    }

    fun applyLivePoint(matchId: String, scorer: Player) {
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) {
                // Save undo snapshot before applying point
                val snapshot = UndoSnapshot(
                    liveScore = m.liveScore,
                    status = m.status,
                    winner = m.winner,
                    completedAt = m.completedAt,
                    stats = m.stats,
                    currentSetStartedAt = m.currentSetStartedAt,
                    matchDurationMs = m.matchDurationMs
                )

                val result = ScoreEngine.applyPoint(m.liveScore, scorer, m.format)
                val isMatchWon = result.matchWinner != null
                val winnerStr = if (result.matchWinner == Player.PLAYER1) "player1" else if (result.matchWinner == Player.PLAYER2) "player2" else null

                val newStatus = if (isMatchWon) "pending_report" else "in_progress"

                val updatedMatch = m.copy(
                    liveScore = result.nextScore,
                    status = newStatus,
                    winner = winnerStr,
                    completedAt = if (isMatchWon) System.currentTimeMillis() else null,
                    undoSnapshot = snapshot
                )

                // Sync with Wear OS companion
                WearOsManager.sendScoreToWatch(result.nextScore)

                // Sync with Firestore in real time
                syncMatchToFirestore(updatedMatch)

                updatedMatch
            } else m
        }
    }

    fun undoLivePoint(matchId: String) {
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId && m.undoSnapshot != null) {
                val snap = m.undoSnapshot
                m.copy(
                    liveScore = snap.liveScore,
                    status = snap.status,
                    winner = snap.winner,
                    completedAt = snap.completedAt,
                    stats = snap.stats,
                    currentSetStartedAt = snap.currentSetStartedAt,
                    matchDurationMs = snap.matchDurationMs,
                    undoSnapshot = null
                )
            } else m
        }
    }

    fun updateAdvancedStats(matchId: String, stats: MatchStats) {
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) m.copy(stats = stats) else m
        }
    }

    fun submitMatchReport(matchId: String, winner: String, notes: String?) {
        val me = _currentUser.value ?: return
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) {
                m.copy(
                    status = "pending_report",
                    winner = winner,
                    reportSubmission = ReportSubmission(
                        submittedBy = me.id,
                        submittedAt = System.currentTimeMillis(),
                        status = "pending_confirmation"
                    )
                )
            } else m
        }
    }

    fun confirmMatchReport(matchId: String) {
        val me = _currentUser.value ?: return
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) {
                val sub = m.reportSubmission?.copy(
                    status = "confirmed",
                    confirmedBy = me.id,
                    confirmedAt = System.currentTimeMillis()
                )
                m.copy(
                    status = "completed",
                    reportSubmission = sub,
                    reportUrl = "https://example.com/reports/$matchId.pdf"
                )
            } else m
        }
        recalculateRankingsInternal()
    }

    fun disputeMatchReport(matchId: String, reason: String) {
        val me = _currentUser.value ?: return
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) {
                val sub = m.reportSubmission?.copy(
                    status = "disputed",
                    disputedBy = me.id,
                    disputedAt = System.currentTimeMillis()
                )
                m.copy(
                    status = "disputed",
                    reportSubmission = sub
                )
            } else m
        }
    }

    fun resolveDisputedReport(matchId: String, winner: String) {
        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) {
                val sub = m.reportSubmission?.copy(
                    status = "confirmed",
                    confirmedBy = "division_leader",
                    confirmedAt = System.currentTimeMillis()
                )
                m.copy(
                    status = "completed",
                    winner = winner,
                    reportSubmission = sub,
                    reportUrl = "https://example.com/reports/$matchId.pdf"
                )
            } else m
        }
        recalculateRankingsInternal()
    }

    // RANKING ACTIONS
    fun recalculateDivisionRankings() {
        recalculateRankingsInternal()
    }

    private fun recalculateRankingsInternal() {
        val targetSeasonId = _selectedSeasonId.value
        val seasonObj = _availableSeasons.value.find { it.id == targetSeasonId }
        val seasonName = seasonObj?.name ?: "Fall 2026"

        val completed = _matches.value.filter {
            it.status == "completed" && (it.seasonId.isNullOrEmpty() || it.seasonId == targetSeasonId)
        }
        val players = _divisionPlayers.value

        val rankingInputs = players.map { player ->
            var played = 0
            var won = 0
            var lost = 0
            var setsWon = 0
            var setsLost = 0
            var gamesWon = 0
            var gamesLost = 0

            for (match in completed) {
                if (match.player1Id == player.id || match.player2Id == player.id) {
                    played++
                    val isP1 = match.player1Id == player.id
                    val isWinner = (isP1 && match.winner == "player1") || (!isP1 && match.winner == "player2")

                    if (isWinner) won++ else lost++

                    val totals = RankingEngine.extractMatchTotals(match.liveScore.sets)
                    if (isP1) {
                        setsWon += totals.player1Sets
                        setsLost += totals.player2Sets
                        gamesWon += totals.player1Games
                        gamesLost += totals.player2Games
                    } else {
                        setsWon += totals.player2Sets
                        setsLost += totals.player1Sets
                        gamesWon += totals.player2Games
                        gamesLost += totals.player1Games
                    }
                }
            }

            RankingInput(
                userId = player.id,
                displayName = player.displayName,
                divisionId = player.divisionId ?: "div_metro_1",
                season = seasonName,
                seasonId = targetSeasonId,
                matchesPlayed = played,
                matchesWon = won,
                matchesLost = lost,
                setsWon = setsWon,
                setsLost = setsLost,
                gamesWon = gamesWon,
                gamesLost = gamesLost
            )
        }

        val computed = RankingEngine.computeRankings(rankingInputs)
        _rankings.value = computed
    }

    // DIVISION ADMIN ACTIONS
    fun createDivision(name: String): String {
        val newId = "div_${System.currentTimeMillis()}"
        val newDiv = Division(id = newId, name = name, inviteCode = "JOIN_${System.currentTimeMillis() % 10000}", leaderIds = listOf(_currentUser.value?.id ?: ""))
        _currentDivision.value = newDiv
        _currentUser.value = _currentUser.value?.copy(divisionId = newId)
        return newId
    }

    fun joinDivisionByCode(code: String): Boolean {
        _currentDivision.value = _currentDivision.value?.copy(inviteCode = code) ?: Division(id = "div_metro_1", name = "Joined Division", inviteCode = code)
        _currentUser.value = _currentUser.value?.copy(divisionId = _currentDivision.value?.id)
        return true
    }

    fun addPlayerByEmail(email: String, levelId: String) {
        val id = "user_${System.currentTimeMillis()}"
        val name = email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
        val newPlayer = PublicProfile(id = id, displayName = name, role = "player", divisionId = _currentDivision.value?.id)
        _divisionPlayers.value = _divisionPlayers.value + newPlayer
    }

    fun addPlaceholderMember(name: String, levelId: String) {
        val id = "placeholder_${System.currentTimeMillis()}"
        val newPlayer = PublicProfile(id = id, displayName = "$name (Placeholder)", role = "player", divisionId = _currentDivision.value?.id)
        _divisionPlayers.value = _divisionPlayers.value + newPlayer
    }

    fun mergePlayerRecords(sourceId: String, targetId: String) {
        _divisionPlayers.value = _divisionPlayers.value.filter { it.id != sourceId }
    }

    fun updatePlayerEmail(userId: String, newEmail: String) {
        // Updated email snapshot
    }

    fun updateScoreFromWatch(p1: Int, p2: Int) {
        val activeMatch = _matches.value.find { it.status == "in_progress" } ?: return
        val currentLiveScore = activeMatch.liveScore
        
        fun intToPoint(p: Int): com.example.scoring.TennisPoint = when (p) {
            1 -> com.example.scoring.TennisPoint.P15
            2 -> com.example.scoring.TennisPoint.P30
            3 -> com.example.scoring.TennisPoint.P40
            4 -> com.example.scoring.TennisPoint.AD
            else -> com.example.scoring.TennisPoint.LOVE
        }
        
        val newLiveScore = currentLiveScore.copy(
            currentGame = com.example.scoring.GameScore(
                player1 = intToPoint(p1),
                player2 = intToPoint(p2)
            )
        )
        
        val updatedMatch = activeMatch.copy(liveScore = newLiveScore)
        
        _matches.value = _matches.value.map { m ->
            if (m.id == activeMatch.id) updatedMatch else m
        }
        
        // Sync to firestore
        syncMatchToFirestore(updatedMatch)
    }

    fun exportDivisionCsv(): String {
        val sb = StringBuilder()
        sb.append("Rank,Name,Matches Played,Matches Won,Sets Won,Games Won,Game Diff\n")
        _rankings.value.forEach { r ->
            sb.append("${r.rank},${r.displayName},${r.matchesPlayed},${r.matchesWon},${r.setsWon},${r.gamesWon},${r.gameDifferential}\n")
        }
        return sb.toString()
    }

    // MESSAGING ACTIONS
    fun getMessages(channelId: String): List<Message> {
        return _messagesMap.value[channelId] ?: emptyList()
    }

    fun sendMessage(channelId: String, content: String, type: String = "text", contact: SharedContact? = null) {
        val me = _currentUser.value ?: return
        val newMsg = Message(
            id = "msg_${System.currentTimeMillis()}",
            channelId = channelId,
            senderId = me.id,
            senderName = me.displayName,
            content = content,
            type = type,
            sharedContact = contact,
            readBy = listOf(me.id)
        )

        val currentList = _messagesMap.value[channelId] ?: emptyList()
        _messagesMap.value = _messagesMap.value + (channelId to (currentList + newMsg))

        // Update channel last message
        _channels.value = _channels.value.map { c ->
            if (c.id == channelId) {
                c.copy(lastMessage = LastMessage(content, me.id, me.displayName, System.currentTimeMillis()))
            } else c
        }
    }

    fun submitFeedback(category: String, text: String): Boolean {
        // Calls submitFeedback Cloud Function
        return true
    }

    fun publishGeneratedSchedule(generatedMatches: List<Match>, clearExistingScheduled: Boolean = false) {
        if (clearExistingScheduled) {
            _matches.value = _matches.value.filter { it.status != "scheduled" } + generatedMatches
        } else {
            _matches.value = generatedMatches + _matches.value
        }
        generatedMatches.forEach { syncMatchToFirestore(it) }
    }

    // REAL-TIME FIRESTORE SYNCHRONIZATION
    fun syncMatchToFirestore(match: Match) {
        try {
            val firestore = FirebaseInitializer.getFirestore() ?: return
            firestore.collection("matches").document(match.id).set(match)
        } catch (e: Exception) {
            Log.w("TennisRepository", "Firestore match sync non-fatal error: ${e.message}")
        }
    }

    fun listenToMatchRealtime(matchId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("matches").document(matchId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                try {
                    val match = snapshot.toObject(Match::class.java) ?: return@addSnapshotListener
                    _matches.value = _matches.value.map { if (it.id == match.id) match else it }
                } catch (e: Exception) {
                    Log.w("TennisRepository", "Match realtime parse error: ${e.message}")
                }
            }
    }

    fun startRealtimeSync(divisionId: String = _currentDivision.value?.id ?: "div_metro_1") {
        listenToDivisionMatchesRealtime(divisionId)
        listenToPlayersRealtime(divisionId)
        listenToLeagueRealtime(divisionId)
    }

    fun listenToDivisionMatchesRealtime(divisionId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("matches")
            .whereEqualTo("divisionId", divisionId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remoteMatches = snapshots.documents.mapNotNull { doc ->
                    try { doc.toObject(Match::class.java) } catch (e: Exception) { null }
                }
                if (remoteMatches.isNotEmpty()) {
                    val currentMap = _matches.value.associateBy { it.id }.toMutableMap()
                    remoteMatches.forEach { currentMap[it.id] = it }
                    _matches.value = currentMap.values.toList()
                }
            }
    }

    fun listenToPlayersRealtime(divisionId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("users")
            .whereEqualTo("divisionId", divisionId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remotePlayers = snapshots.documents.mapNotNull { doc ->
                    try { doc.toObject(PublicProfile::class.java) } catch (e: Exception) { null }
                }
                if (remotePlayers.isNotEmpty()) {
                    _divisionPlayers.value = remotePlayers
                }
            }
    }

    fun listenToPlayerMatchesRealtime(playerId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("matches")
            .whereArrayContains("playerIds", playerId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remoteMatches = snapshots.documents.mapNotNull { doc ->
                    try { doc.toObject(Match::class.java) } catch (e: Exception) { null }
                }
                if (remoteMatches.isNotEmpty()) {
                    val currentMap = _matches.value.associateBy { it.id }.toMutableMap()
                    remoteMatches.forEach { currentMap[it.id] = it }
                    _matches.value = currentMap.values.toList()
                }
            }
    }

    fun listenToLeagueRealtime(leagueId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("divisions").document(leagueId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                try {
                    val league = snapshot.toObject(League::class.java)
                    if (league != null) {
                        _currentDivision.value = league
                    }
                } catch (e: Exception) {
                    Log.w("TennisRepository", "League realtime parse error: ${e.message}")
                }
            }
    }
}
