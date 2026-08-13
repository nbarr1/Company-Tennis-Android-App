package com.example.data.models

import com.example.scoring.LiveScore
import com.example.scoring.MatchFormatConfig

/** Entity Aliases for Player and League domain models */
typealias PlayerEntity = User
typealias League = Division

data class ContactPreferences(
    val allowEmail: Boolean = true,
    val allowSMS: Boolean = false,
    val allowInApp: Boolean = true
)

data class AvailabilitySlot(
    val day: String = "Monday",
    val from: String = "18:00",
    val to: String = "20:00"
)

data class Availability(
    val slots: List<AvailabilitySlot> = emptyList(),
    val note: String? = null
)

data class RankingSummary(
    val divisionId: String = "",
    val rank: Int = 1,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val matchesLost: Int = 0,
    val setsWon: Int = 0,
    val setsLost: Int = 0,
    val gamesWon: Int = 0,
    val gamesLost: Int = 0,
    val gameDifferential: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class User(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String? = null,
    val avatarUrl: String? = null,
    val contactPreferences: ContactPreferences = ContactPreferences(),
    val availability: Availability? = null,
    val divisionId: String? = null,
    val role: String = "player", // player | division_leader | admin | app_developer
    val blockedUserIds: List<String> = emptyList(),
    val fcmTokens: List<String> = emptyList(),
    val tipsEnabled: Boolean = true,
    val tutorialDone: Boolean? = false,
    val isRegistered: Boolean? = true,
    val inviteStatus: String? = null, // none | invite_sent | registered
    val invitedAt: Long? = null,
    val invitedBy: String? = null,
    val rankingSummary: RankingSummary? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PublicProfile(
    val id: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val divisionId: String? = null,
    val role: String = "player",
    val tutorialDone: Boolean? = true,
    val rankingSummary: RankingSummary? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class Division(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val leaderIds: List<String> = emptyList(),
    val playerIds: List<String> = emptyList(),
    val activeSeasonId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Season(
    val id: String = "",
    val divisionId: String = "",
    val year: Int = 2026,
    val half: String = "fall", // spring | fall
    val name: String = "",
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val status: String = "active", // draft | active | completed | archived
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DivisionLevel(
    val id: String = "",
    val divisionId: String = "",
    val seasonId: String = "",
    val year: Int = 2026,
    val seasonHalf: String = "fall",
    val name: String = "",
    val skillLevel: String = "intermediate", // beginner | intermediate | advanced | open
    val matchType: String = "singles", // singles | doubles
    val description: String? = null,
    val rankingsEnabled: Boolean = true,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DivisionMembership(
    val id: String = "",
    val divisionId: String = "",
    val seasonId: String = "",
    val divisionLevelId: String = "",
    val userId: String = "",
    val displayNameSnapshot: String = "",
    val emailSnapshot: String? = null,
    val phoneSnapshot: String? = null,
    val role: String = "player", // player | division_leader
    val status: String = "active", // active | waitlisted | removed
    val source: String = "registered_user", // registered_user | placeholder | imported | migration
    val assignedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class MatchSide(
    val playerIds: List<String> = emptyList(),
    val displayName: String? = null
)

data class MatchStats(
    val acesP1: Int = 0,
    val acesP2: Int = 0,
    val doubleFaultsP1: Int = 0,
    val doubleFaultsP2: Int = 0,
    val firstServesInP1: Int = 0,
    val firstServesTotalP1: Int = 0,
    val firstServesInP2: Int = 0,
    val firstServesTotalP2: Int = 0,
    val winnersP1: Int = 0,
    val winnersP2: Int = 0,
    val unforcedErrorsP1: Int = 0,
    val unforcedErrorsP2: Int = 0,
    val breakPointsWonP1: Int = 0,
    val breakPointsFacedP1: Int = 0,
    val breakPointsWonP2: Int = 0,
    val breakPointsFacedP2: Int = 0
)

data class ReportSubmission(
    val submittedBy: String = "",
    val submittedAt: Long = System.currentTimeMillis(),
    val status: String = "pending_confirmation", // pending_confirmation | confirmed | disputed
    val confirmedBy: String? = null,
    val confirmedAt: Long? = null,
    val disputedBy: String? = null,
    val disputedAt: Long? = null,
    val leaderNotifiedAt: Long? = null
)

data class UndoSnapshot(
    val liveScore: LiveScore,
    val status: String,
    val winner: String? = null,
    val completedAt: Long? = null,
    val stats: MatchStats = MatchStats(),
    val currentSetStartedAt: Long? = null,
    val matchDurationMs: Long? = null
)

data class Match(
    val id: String = "",
    val divisionId: String = "",
    val seasonId: String? = null,
    val divisionLevelId: String? = null,
    val matchType: String? = "singles",
    val side1: MatchSide? = null,
    val side2: MatchSide? = null,
    val player1Id: String = "",
    val player2Id: String = "",
    val player1Name: String? = null,
    val player2Name: String? = null,
    val player2IsGuest: Boolean? = false,
    val playerIds: List<String> = emptyList(),
    val format: MatchFormatConfig = MatchFormatConfig(),
    val status: String = "proposed", // proposed | scheduled | in_progress | pending_report | completed | disputed | cancelled
    val liveScore: LiveScore = LiveScore(),
    val stats: MatchStats = MatchStats(),
    val advancedStatsEnabled: Boolean? = false,
    val currentSetStartedAt: Long? = null,
    val matchDurationMs: Long? = null,
    val reportSubmission: ReportSubmission? = null,
    val winner: String? = null, // player1 | player2
    val reportUrl: String? = null,
    val tipsEnabled: Boolean = true,
    val undoSnapshot: UndoSnapshot? = null,
    val source: String? = "live",
    val isDivisionMatch: Boolean? = true,
    val courtLocation: String? = null,
    val createdBy: String = "",
    val scheduledAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class LastMessage(
    val content: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class Channel(
    val id: String = "",
    val type: String = "direct", // division | direct
    val divisionId: String? = null,
    val participantIds: List<String> = emptyList(),
    val name: String? = null,
    val lastMessage: LastMessage? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class SharedContact(
    val phone: String? = null,
    val email: String? = null
)

data class Message(
    val id: String = "",
    val channelId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val type: String = "text", // text | system | contact_share
    val sharedContact: SharedContact? = null,
    val readBy: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class MessageReport(
    val id: String = "",
    val divisionId: String = "",
    val channelId: String = "",
    val messageId: String = "",
    val reportedUserId: String = "",
    val reportedUserName: String = "",
    val reporterId: String = "",
    val messageContent: String = "",
    val reason: String = "Other", // Harassment | Spam | Inappropriate | Other
    val status: String = "open", // open | reviewed
    val createdAt: Long = System.currentTimeMillis()
)

data class PlayerRanking(
    val userId: String = "",
    val displayName: String = "",
    val divisionId: String = "",
    val season: String = "",
    val seasonId: String? = null,
    val divisionLevelId: String? = null,
    val matchType: String? = null,
    val rank: Int = 0,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val matchesLost: Int = 0,
    val setsWon: Int = 0,
    val setsLost: Int = 0,
    val gamesWon: Int = 0,
    val gamesLost: Int = 0,
    val gameDifferential: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class HeadToHead(
    val id: String = "",
    val divisionId: String = "",
    val player1Id: String = "",
    val player2Id: String = "",
    val player1Wins: Int = 0,
    val player2Wins: Int = 0
)
