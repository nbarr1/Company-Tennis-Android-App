package com.example.data.repository

import android.util.Log
import com.example.data.models.*
import com.example.firebase.FirebaseInitializer
import com.example.scoring.*
import com.example.scoring.Player
import com.example.wear.WearOsManager
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctionsException
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

    // Head-to-head Store
    private val _headToHead = MutableStateFlow<List<HeadToHead>>(emptyList())
    val headToHead: StateFlow<List<HeadToHead>> = _headToHead.asStateFlow()

    // Channels Store
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    // Messages Store
    private val _messagesMap = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<Message>>> = _messagesMap.asStateFlow()

    // Reported Messages Store (UGC moderation)
    private val _reports = MutableStateFlow<List<MessageReport>>(emptyList())
    val reports: StateFlow<List<MessageReport>> = _reports.asStateFlow()

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
                    // Backfill the public "profiles" mirror for accounts created before this doc existed.
                    _currentUser.value?.let { syncPublicProfile(it) }
                }
                registerFcmTokenForCurrentUser()
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
                syncPublicProfile(newUser)
                registerFcmTokenForCurrentUser()
                true
            } else false
        } catch (e: Exception) {
            Log.w("TennisRepository", "Firebase sign-up error: ${e.message}")
            false
        }
    }

    private suspend fun registerFcmTokenForCurrentUser() {
        try {
            val uid = FirebaseInitializer.getAuth()?.currentUser?.uid ?: return
            val firestore = FirebaseInitializer.getFirestore() ?: return
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            firestore.collection("users").document(uid)
                .update("fcmTokens", com.google.firebase.firestore.FieldValue.arrayUnion(token))
                .await()
        } catch (e: Exception) {
            Log.w("TennisRepository", "FCM token registration error: ${e.message}")
        }
    }

    /** [idToken] is the Google ID token obtained via Credential Manager in AuthScreens.kt. */
    suspend fun loginWithGoogle(idToken: String): Boolean {
        val auth = FirebaseInitializer.getAuth() ?: return false
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            val firebaseUser = auth.currentUser ?: return false

            val firestore = FirebaseInitializer.getFirestore()
            if (firestore != null) {
                val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
                val profile = doc.toObject(User::class.java)
                if (profile != null) {
                    _currentUser.value = profile
                } else {
                    val newUser = User(
                        id = firebaseUser.uid,
                        displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Player",
                        email = firebaseUser.email ?: "",
                        divisionId = null,
                        tutorialDone = false
                    )
                    firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                    _currentUser.value = newUser
                }
                _currentUser.value?.let { syncPublicProfile(it) }
            }
            registerFcmTokenForCurrentUser()
            true
        } catch (e: FirebaseAuthException) {
            Log.w("TennisRepository", "Google sign-in auth error: ${e.errorCode} ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "Google sign-in unexpected error: ${e.message}")
            false
        }
    }

    fun logout() {
        val auth = FirebaseInitializer.getAuth()
        auth?.signOut()
        _currentUser.value = null
        _currentDivision.value = null
        _divisionPlayers.value = emptyList()
        _matches.value = emptyList()
    }

    /**
     * Permanently deletes the current user's account and personal data, per Google Play's
     * User Data policy account-deletion requirement. Match/ranking history that's jointly
     * owned with other division members is left intact; only the user's own account doc and
     * directly-identifying membership fields are removed.
     *
     * Returns false (without deleting anything) if Firebase requires a recent sign-in before
     * allowing account deletion — the caller should ask the user to sign out, sign back in,
     * and retry.
     */
    suspend fun deleteAccount(): Boolean {
        val auth = FirebaseInitializer.getAuth() ?: return false
        val firebaseUser = auth.currentUser ?: return false
        val uid = firebaseUser.uid
        val firestore = FirebaseInitializer.getFirestore()

        return try {
            if (firestore != null) {
                firestore.collection("users").document(uid).delete().await()
                firestore.collection("profiles").document(uid).delete().await()

                _currentUser.value?.divisionId?.let { divisionId ->
                    try {
                        firestore.collection("divisions").document(divisionId)
                            .update(
                                "playerIds", com.google.firebase.firestore.FieldValue.arrayRemove(uid),
                                "leaderIds", com.google.firebase.firestore.FieldValue.arrayRemove(uid)
                            )
                            .await()
                    } catch (e: Exception) {
                        Log.w("TennisRepository", "deleteAccount division cleanup error: ${e.message}")
                    }
                }
            }

            firebaseUser.delete().await()

            _currentUser.value = null
            _currentDivision.value = null
            _divisionPlayers.value = emptyList()
            _matches.value = emptyList()
            true
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            Log.w("TennisRepository", "deleteAccount requires recent login: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "deleteAccount error: ${e.message}")
            false
        }
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

    suspend fun updateUserProfile(userId: String = "", displayName: String = "", avatarUrl: String = "", phone: String? = ""): Boolean {
        val current = _currentUser.value ?: return false
        val updated = current.copy(
            displayName = displayName,
            avatarUrl = avatarUrl.ifBlank { null },
            phone = phone?.ifBlank { null }
        )
        _currentUser.value = updated

        val firestore = FirebaseInitializer.getFirestore() ?: return false
        return try {
            firestore.collection("users").document(updated.id)
                .set(
                    mapOf(
                        "displayName" to updated.displayName,
                        "avatarUrl" to updated.avatarUrl,
                        "phone" to updated.phone
                    ),
                    SetOptions.merge()
                ).await()
            syncPublicProfile(updated)
            true
        } catch (e: Exception) {
            Log.w("TennisRepository", "updateUserProfile Firestore error: ${e.message}")
            false
        }
    }

    /** Writes the non-PII mirror doc (`profiles/{uid}`) that roster/ranking reads depend on. */
    private suspend fun syncPublicProfile(user: User) {
        val firestore = FirebaseInitializer.getFirestore() ?: return
        try {
            firestore.collection("profiles").document(user.id)
                .set(
                    mapOf(
                        "id" to user.id,
                        "displayName" to user.displayName,
                        "avatarUrl" to user.avatarUrl,
                        "divisionId" to user.divisionId,
                        "role" to user.role,
                        "tutorialDone" to user.tutorialDone,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            Log.w("TennisRepository", "syncPublicProfile error: ${e.message}")
        }
    }

    fun acceptMatchProposal(matchId: String) {
        val existing = _matches.value.find { it.id == matchId } ?: return
        val updated = existing.copy(status = "scheduled")
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
    }

    fun declineMatchProposal(matchId: String) {
        val existing = _matches.value.find { it.id == matchId } ?: return
        val updated = existing.copy(status = "cancelled")
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
    }

    fun startMatch(matchId: String) {
        val existing = _matches.value.find { it.id == matchId } ?: return
        val updated = existing.copy(
            status = "in_progress",
            startedAt = System.currentTimeMillis(),
            liveScore = ScoreEngine.createInitialScore(existing.format)
        )
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
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
        val existing = _matches.value.find { it.id == matchId } ?: return
        val snap = existing.undoSnapshot ?: return
        val updated = existing.copy(
            liveScore = snap.liveScore,
            status = snap.status,
            winner = snap.winner,
            completedAt = snap.completedAt,
            stats = snap.stats,
            currentSetStartedAt = snap.currentSetStartedAt,
            matchDurationMs = snap.matchDurationMs,
            undoSnapshot = null
        )
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
    }

    fun updateAdvancedStats(matchId: String, stats: MatchStats) {
        val existing = _matches.value.find { it.id == matchId } ?: return
        val updated = existing.copy(stats = stats)
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
    }

    fun submitMatchReport(matchId: String, winner: String, notes: String?) {
        val me = _currentUser.value ?: return
        val existing = _matches.value.find { it.id == matchId } ?: return
        val updated = existing.copy(
            status = "pending_report",
            winner = winner,
            reportSubmission = ReportSubmission(
                submittedBy = me.id,
                submittedAt = System.currentTimeMillis(),
                status = "pending_confirmation"
            )
        )
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
    }

    fun confirmMatchReport(matchId: String) {
        val me = _currentUser.value ?: return
        val existing = _matches.value.find { it.id == matchId } ?: return
        val sub = existing.reportSubmission?.copy(
            status = "confirmed",
            confirmedBy = me.id,
            confirmedAt = System.currentTimeMillis()
        )
        // reportUrl is intentionally left as-is (usually null here): the already-deployed
        // generateMatchReport Firestore trigger on the live project populates the real signed
        // URL once status reaches "completed", and listenToMatchRealtime picks it up from there.
        val updated = existing.copy(
            status = "completed",
            reportSubmission = sub
        )
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
        recalculateRankingsInternal()
    }

    fun disputeMatchReport(matchId: String, reason: String) {
        val me = _currentUser.value ?: return
        val existing = _matches.value.find { it.id == matchId } ?: return
        val sub = existing.reportSubmission?.copy(
            status = "disputed",
            disputedBy = me.id,
            disputedAt = System.currentTimeMillis()
        )
        val updated = existing.copy(
            status = "disputed",
            reportSubmission = sub
        )
        _matches.value = _matches.value.map { if (it.id == matchId) updated else it }
        syncMatchToFirestore(updated)
    }

    // NOTE: kept local-state-only (not synced to Firestore) — the real `resolveDisputedReport`
    // Cloud Function callable only takes { matchId } and confirms whatever report was already
    // submitted; it has no way to honor an arbitrary "award to player X" choice like this UI
    // offers. Wiring the real callable here would silently ignore the leader's P1/P2 selection.
    // See the implementation report for what's needed to close this gap for real.
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
                    reportSubmission = sub
                )
            } else m
        }
        recalculateRankingsInternal()
    }

    // RANKING ACTIONS
    suspend fun recalculateDivisionRankings(): Boolean {
        val divisionId = _currentDivision.value?.id
        val functions = FirebaseInitializer.getFunctions()
        if (divisionId == null || functions == null) {
            recalculateRankingsInternal()
            return false
        }
        return try {
            functions.getHttpsCallable("recalculateDivisionRankings")
                .call(mapOf("divisionId" to divisionId))
                .await()
            // The callable writes divisions/{id}/rankings directly; listenToRankingsRealtime
            // (already subscribed via startRealtimeSync) will pick up the fresh results.
            true
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "recalculateDivisionRankings error: ${e.code} ${e.message}")
            recalculateRankingsInternal()
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "recalculateDivisionRankings unexpected error: ${e.message}")
            recalculateRankingsInternal()
            false
        }
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

    suspend fun createDivision(name: String): String? {
        val functions = FirebaseInitializer.getFunctions() ?: return null
        return try {
            val result = functions.getHttpsCallable("createDivision").call(mapOf("name" to name)).await()
            val data = result.data as? Map<*, *> ?: return null
            val divisionId = data["divisionId"] as? String ?: return null
            val inviteCode = data["inviteCode"] as? String ?: ""
            _currentDivision.value = Division(
                id = divisionId,
                name = name,
                inviteCode = inviteCode,
                leaderIds = listOfNotNull(_currentUser.value?.id)
            )
            _currentUser.value = _currentUser.value?.copy(divisionId = divisionId, role = "division_leader")
            divisionId
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "createDivision error: ${e.code} ${e.message}")
            null
        } catch (e: Exception) {
            Log.w("TennisRepository", "createDivision unexpected error: ${e.message}")
            null
        }
    }

    suspend fun joinDivisionByCode(code: String): Boolean {
        val functions = FirebaseInitializer.getFunctions() ?: return false
        return try {
            val result = functions.getHttpsCallable("joinDivisionByCode").call(mapOf("inviteCode" to code)).await()
            val data = result.data as? Map<*, *> ?: return false
            val divisionId = data["divisionId"] as? String ?: return false
            val divisionName = data["divisionName"] as? String ?: ""
            _currentDivision.value = (_currentDivision.value ?: Division()).copy(
                id = divisionId,
                name = divisionName,
                inviteCode = code
            )
            _currentUser.value = _currentUser.value?.copy(divisionId = divisionId)
            true
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "joinDivisionByCode error: ${e.code} ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "joinDivisionByCode unexpected error: ${e.message}")
            false
        }
    }

    /**
     * Routed through `upsertDivisionMembership` (not the simpler `addPlayerToDivisionByEmail`)
     * since it accepts seasonId/divisionLevelId and creates a real DivisionMembership record —
     * this is what makes season/level roster ("membership") tracking real instead of local-only.
     */
    suspend fun addPlayerByEmail(email: String, levelId: String): Boolean {
        val divisionId = _currentDivision.value?.id ?: return false
        val functions = FirebaseInitializer.getFunctions() ?: return false
        return try {
            functions.getHttpsCallable("upsertDivisionMembership").call(
                mapOf(
                    "divisionId" to divisionId,
                    "seasonId" to _selectedSeasonId.value,
                    "divisionLevelId" to levelId,
                    "email" to email,
                    "sendInvite" to true
                )
            ).await()
            true
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "addPlayerByEmail error: ${e.code} ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "addPlayerByEmail unexpected error: ${e.message}")
            false
        }
    }

    suspend fun addPlaceholderMember(name: String, levelId: String): Boolean {
        val divisionId = _currentDivision.value?.id ?: return false
        val functions = FirebaseInitializer.getFunctions() ?: return false
        return try {
            functions.getHttpsCallable("upsertDivisionMembership").call(
                mapOf(
                    "divisionId" to divisionId,
                    "seasonId" to _selectedSeasonId.value,
                    "divisionLevelId" to levelId,
                    "name" to name
                )
            ).await()
            true
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "addPlaceholderMember error: ${e.code} ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "addPlaceholderMember unexpected error: ${e.message}")
            false
        }
    }

    suspend fun mergePlayerRecords(sourceId: String, targetId: String): Boolean {
        val divisionId = _currentDivision.value?.id ?: return false
        val functions = FirebaseInitializer.getFunctions() ?: return false
        return try {
            functions.getHttpsCallable("mergeDivisionPlayerRecords").call(
                mapOf(
                    "divisionId" to divisionId,
                    "sourceUserId" to sourceId,
                    "targetUserId" to targetId
                )
            ).await()
            true
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "mergePlayerRecords error: ${e.code} ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "mergePlayerRecords unexpected error: ${e.message}")
            false
        }
    }

    suspend fun updatePlayerEmail(userId: String, newEmail: String): Boolean {
        val divisionId = _currentDivision.value?.id ?: return false
        val functions = FirebaseInitializer.getFunctions() ?: return false
        return try {
            functions.getHttpsCallable("updateDivisionPlayerEmail").call(
                mapOf(
                    "divisionId" to divisionId,
                    "userId" to userId,
                    "email" to newEmail
                )
            ).await()
            true
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "updatePlayerEmail error: ${e.code} ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "updatePlayerEmail unexpected error: ${e.message}")
            false
        }
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

    suspend fun exportDivisionCsv(): String {
        val divisionId = _currentDivision.value?.id
        val functions = FirebaseInitializer.getFunctions()
        if (divisionId != null && functions != null) {
            try {
                val result = functions.getHttpsCallable("exportDivisionCsv").call(
                    mapOf("divisionId" to divisionId, "exportType" to "rankings")
                ).await()
                val data = result.data as? Map<*, *>
                val csv = data?.get("csv") as? String
                if (csv != null) return csv
            } catch (e: FirebaseFunctionsException) {
                Log.w("TennisRepository", "exportDivisionCsv error: ${e.code} ${e.message}")
            } catch (e: Exception) {
                Log.w("TennisRepository", "exportDivisionCsv unexpected error: ${e.message}")
            }
        }
        // Fallback: build from local rankings state if the callable is unavailable/fails.
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
        val lastMessage = LastMessage(content, me.id, me.displayName, System.currentTimeMillis())

        val currentList = _messagesMap.value[channelId] ?: emptyList()
        _messagesMap.value = _messagesMap.value + (channelId to (currentList + newMsg))

        // Update channel last message (local)
        _channels.value = _channels.value.map { c ->
            if (c.id == channelId) c.copy(lastMessage = lastMessage) else c
        }

        // Persist to Firestore so other participants (and the onNewMessage trigger's FCM push
        // on the live project) actually see this message.
        try {
            val firestore = FirebaseInitializer.getFirestore() ?: return
            firestore.collection("channels").document(channelId)
                .collection("messages").document(newMsg.id)
                .set(newMsg)
                .addOnFailureListener { e -> Log.w("TennisRepository", "sendMessage sync error: ${e.message}") }
            firestore.collection("channels").document(channelId)
                .set(mapOf("lastMessage" to lastMessage), SetOptions.merge())
                .addOnFailureListener { e -> Log.w("TennisRepository", "channel lastMessage sync error: ${e.message}") }
        } catch (e: Exception) {
            Log.w("TennisRepository", "sendMessage non-fatal error: ${e.message}")
        }
    }

    /** Reports a message for review by division admins/leaders (UGC moderation). */
    fun reportMessage(channelId: String, message: Message, reason: String) {
        val me = _currentUser.value ?: return
        val report = MessageReport(
            id = "report_${System.currentTimeMillis()}",
            divisionId = me.divisionId ?: "",
            channelId = channelId,
            messageId = message.id,
            reportedUserId = message.senderId,
            reportedUserName = message.senderName,
            reporterId = me.id,
            messageContent = message.content,
            reason = reason
        )
        _reports.value = listOf(report) + _reports.value

        try {
            val firestore = FirebaseInitializer.getFirestore() ?: return
            firestore.collection("reports").document(report.id)
                .set(report)
                .addOnFailureListener { e -> Log.w("TennisRepository", "reportMessage sync error: ${e.message}") }
        } catch (e: Exception) {
            Log.w("TennisRepository", "reportMessage non-fatal error: ${e.message}")
        }
    }

    /** Blocks another user: their messages are hidden from the current user's chat threads. */
    fun blockUser(userId: String) {
        val current = _currentUser.value ?: return
        if (userId in current.blockedUserIds) return
        val updated = current.copy(blockedUserIds = current.blockedUserIds + userId)
        _currentUser.value = updated

        try {
            val firestore = FirebaseInitializer.getFirestore() ?: return
            firestore.collection("users").document(current.id)
                .update("blockedUserIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                .addOnFailureListener { e -> Log.w("TennisRepository", "blockUser sync error: ${e.message}") }
        } catch (e: Exception) {
            Log.w("TennisRepository", "blockUser non-fatal error: ${e.message}")
        }
    }

    fun unblockUser(userId: String) {
        val current = _currentUser.value ?: return
        val updated = current.copy(blockedUserIds = current.blockedUserIds - userId)
        _currentUser.value = updated

        try {
            val firestore = FirebaseInitializer.getFirestore() ?: return
            firestore.collection("users").document(current.id)
                .update("blockedUserIds", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                .addOnFailureListener { e -> Log.w("TennisRepository", "unblockUser sync error: ${e.message}") }
        } catch (e: Exception) {
            Log.w("TennisRepository", "unblockUser non-fatal error: ${e.message}")
        }
    }

    fun listenToReportsRealtime(divisionId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("reports")
            .whereEqualTo("divisionId", divisionId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remoteReports = snapshots.documents.mapNotNull { doc ->
                    try { doc.toObject(MessageReport::class.java) } catch (e: Exception) { null }
                }
                _reports.value = remoteReports.sortedByDescending { it.createdAt }
            }
    }

    /** Removes a reported message and marks the report reviewed — admin moderation action. */
    fun resolveReport(report: MessageReport) {
        _reports.value = _reports.value.map { if (it.id == report.id) it.copy(status = "reviewed") else it }
        _messagesMap.value = _messagesMap.value.mapValues { (_, messages) ->
            messages.filterNot { it.id == report.messageId }
        }

        try {
            val firestore = FirebaseInitializer.getFirestore() ?: return
            firestore.collection("channels").document(report.channelId)
                .collection("messages").document(report.messageId)
                .delete()
                .addOnFailureListener { e -> Log.w("TennisRepository", "resolveReport message delete error: ${e.message}") }
            firestore.collection("reports").document(report.id)
                .update("status", "reviewed")
                .addOnFailureListener { e -> Log.w("TennisRepository", "resolveReport status update error: ${e.message}") }
        } catch (e: Exception) {
            Log.w("TennisRepository", "resolveReport non-fatal error: ${e.message}")
        }
    }

    fun listenToChannelMessagesRealtime(channelId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("channels").document(channelId).collection("messages")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remoteMessages = snapshots.documents.mapNotNull { doc ->
                    try { doc.toObject(Message::class.java) } catch (e: Exception) { null }
                }
                if (remoteMessages.isNotEmpty()) {
                    _messagesMap.value = _messagesMap.value + (channelId to remoteMessages)
                }
            }
    }

    fun listenToUserChannelsRealtime(userId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("channels")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remoteChannels = snapshots.documents.mapNotNull { doc ->
                    try { doc.toObject(Channel::class.java) } catch (e: Exception) { null }
                }
                if (remoteChannels.isNotEmpty()) {
                    val currentMap = _channels.value.associateBy { it.id }.toMutableMap()
                    remoteChannels.forEach { currentMap[it.id] = it }
                    _channels.value = currentMap.values.toList()
                }
            }
    }

    suspend fun submitFeedback(category: String, text: String): Boolean {
        val functions = FirebaseInitializer.getFunctions() ?: return false
        return try {
            val data = hashMapOf(
                "title" to "[$category] Feedback from Tennis League Android app",
                "body" to text
            )
            functions.getHttpsCallable("submitFeedback").call(data).await()
            true
        } catch (e: FirebaseFunctionsException) {
            Log.w("TennisRepository", "submitFeedback callable error: ${e.code} ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("TennisRepository", "submitFeedback unexpected error: ${e.message}")
            false
        }
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
        listenToRankingsRealtime(divisionId)
        listenToHeadToHeadRealtime(divisionId)
        listenToReportsRealtime(divisionId)
        _currentUser.value?.id?.let { listenToUserChannelsRealtime(it) }
    }

    fun listenToRankingsRealtime(divisionId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("divisions").document(divisionId).collection("rankings")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remoteRankings = snapshots.documents.mapNotNull { doc -> parsePlayerRankingDoc(doc) }
                if (remoteRankings.isNotEmpty()) {
                    _rankings.value = remoteRankings.sortedBy { it.rank }
                } else {
                    // No server-computed rankings yet for this division — fall back to local
                    // recompute, same fallback shape TennisScoring's own useRankings hook uses.
                    recalculateRankingsInternal()
                }
            }
    }

    /**
     * Firestore's automatic POJO mapping throws if a document field is a Timestamp but the
     * target Kotlin property is typed Long — TennisScoring's Cloud Function writes rankings'
     * `updatedAt` via FieldValue.serverTimestamp() (unlike most other collections' plain
     * epoch-ms longs), so rankings are parsed manually instead of via doc.toObject(...).
     */
    private fun parsePlayerRankingDoc(doc: com.google.firebase.firestore.DocumentSnapshot): PlayerRanking? {
        return try {
            val updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time
                ?: doc.getLong("updatedAt")
                ?: 0L
            PlayerRanking(
                userId = doc.getString("userId") ?: return null,
                displayName = doc.getString("displayName") ?: "",
                divisionId = doc.getString("divisionId") ?: "",
                season = doc.getString("season") ?: "",
                seasonId = doc.getString("seasonId"),
                divisionLevelId = doc.getString("divisionLevelId"),
                matchType = doc.getString("matchType"),
                rank = (doc.getLong("rank") ?: 0L).toInt(),
                matchesPlayed = (doc.getLong("matchesPlayed") ?: 0L).toInt(),
                matchesWon = (doc.getLong("matchesWon") ?: 0L).toInt(),
                matchesLost = (doc.getLong("matchesLost") ?: 0L).toInt(),
                setsWon = (doc.getLong("setsWon") ?: 0L).toInt(),
                setsLost = (doc.getLong("setsLost") ?: 0L).toInt(),
                gamesWon = (doc.getLong("gamesWon") ?: 0L).toInt(),
                gamesLost = (doc.getLong("gamesLost") ?: 0L).toInt(),
                gameDifferential = (doc.getLong("gameDifferential") ?: 0L).toInt(),
                updatedAt = updatedAtMillis
            )
        } catch (e: Exception) {
            null
        }
    }

    fun listenToHeadToHeadRealtime(divisionId: String): ListenerRegistration? {
        val firestore = FirebaseInitializer.getFirestore() ?: return null
        return firestore.collection("headToHead")
            .whereEqualTo("divisionId", divisionId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val remote = snapshots.documents.mapNotNull { doc ->
                    try { doc.toObject(HeadToHead::class.java) } catch (e: Exception) { null }
                }
                _headToHead.value = remote
            }
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
        // Reads "profiles" (the public, non-PII mirror), not "users" (private, self/admin-only
        // under TennisScoring's Firestore rules) — querying "users" here would return empty for
        // every player but the caller once real security rules are enforced.
        return firestore.collection("profiles")
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
