package com.example.scoring

enum class TennisPoint(val value: Int, val label: String) {
    LOVE(0, "0"),
    P15(15, "15"),
    P30(30, "30"),
    P40(40, "40"),
    AD(-1, "Ad")
}

enum class Player {
    PLAYER1,
    PLAYER2;

    val other: Player
        get() = if (this == PLAYER1) PLAYER2 else PLAYER1
}

enum class ServiceSide {
    DEUCE,
    ADVANTAGE;

    val other: ServiceSide
        get() = if (this == DEUCE) ADVANTAGE else DEUCE
}

data class MatchFormatConfig(
    val setsToWin: Int = 2,
    val gamesPerSet: Int = 6,
    val tiebreakAt: Int = 6,
    val finalSetTiebreak: Boolean = true
)

data class TiebreakScore(
    val player1Points: Int = 0,
    val player2Points: Int = 0
)

data class SetScore(
    val setNumber: Int = 1,
    val player1Games: Int = 0,
    val player2Games: Int = 0,
    val tiebreak: TiebreakScore? = null,
    val winner: Player? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMs: Long? = null
)

data class GameScore(
    val player1: TennisPoint = TennisPoint.LOVE,
    val player2: TennisPoint = TennisPoint.LOVE
)

data class LiveScore(
    val sets: List<SetScore> = listOf(SetScore(setNumber = 1)),
    val currentSet: Int = 0,
    val currentGame: GameScore = GameScore(),
    val isTiebreak: Boolean = false,
    val tiebreakScore: TiebreakScore? = null,
    val server: Player = Player.PLAYER1,
    val serviceSide: ServiceSide = ServiceSide.DEUCE,
    val player1SetsWon: Int = 0,
    val player2SetsWon: Int = 0
)

enum class TipTrigger {
    SERVICE_CHANGE,
    TIEBREAK_START,
    DEUCE,
    ADVANTAGE,
    GAME_POINT,
    SET_POINT,
    MATCH_POINT,
    NEW_SET,
    MATCH_COMPLETE
}

data class SetCompletion(val setIndex: Int, val winner: Player)
data class GameCompletion(val winner: Player)

data class ScoreResult(
    val nextScore: LiveScore,
    val tips: List<TipTrigger> = emptyList(),
    val matchWinner: Player? = null,
    val setCompleted: SetCompletion? = null,
    val gameCompleted: GameCompletion? = null
)
