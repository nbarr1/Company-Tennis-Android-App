package com.example.scoring

import com.example.data.models.HeadToHead
import com.example.data.models.PlayerRanking

data class RankingInput(
    val userId: String,
    val displayName: String,
    val divisionId: String,
    val season: String = "",
    val seasonId: String? = null,
    val divisionLevelId: String? = null,
    val matchType: String? = null,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val matchesLost: Int = 0,
    val setsWon: Int = 0,
    val setsLost: Int = 0,
    val gamesWon: Int = 0,
    val gamesLost: Int = 0
)

data class MatchTotals(
    val player1Sets: Int,
    val player2Sets: Int,
    val player1Games: Int,
    val player2Games: Int
)

object RankingEngine {

    fun computeRankings(
        inputs: List<RankingInput>,
        headToHeads: List<HeadToHead> = emptyList()
    ): List<PlayerRanking> {
        // Build H2H lookup map: pair(p1, p2) -> p1Wins
        val h2hMap = mutableMapOf<Pair<String, String>, Int>()
        for (h2h in headToHeads) {
            h2hMap[Pair(h2h.player1Id, h2h.player2Id)] = h2h.player1Wins
            h2hMap[Pair(h2h.player2Id, h2h.player1Id)] = h2h.player2Wins
        }

        val sorted = inputs.sortedWith { a, b ->
            // 1. Matches Won desc
            if (a.matchesWon != b.matchesWon) return@sortedWith b.matchesWon.compareTo(a.matchesWon)

            // 2. Sets Won desc
            if (a.setsWon != b.setsWon) return@sortedWith b.setsWon.compareTo(a.setsWon)

            // 3. Games Won desc
            if (a.gamesWon != b.gamesWon) return@sortedWith b.gamesWon.compareTo(a.gamesWon)

            // 4. Game Differential desc
            val aDiff = a.gamesWon - a.gamesLost
            val bDiff = b.gamesWon - b.gamesLost
            if (aDiff != bDiff) return@sortedWith bDiff.compareTo(aDiff)

            // 5. Head-to-Head wins
            val aH2hWins = h2hMap[Pair(a.userId, b.userId)] ?: 0
            val bH2hWins = h2hMap[Pair(b.userId, a.userId)] ?: 0
            if (aH2hWins != bH2hWins) return@sortedWith bH2hWins.compareTo(aH2hWins)

            // 6. Display Name alphabetical
            return@sortedWith a.displayName.compareTo(b.displayName, ignoreCase = true)
        }

        return sorted.mapIndexed { index, input ->
            PlayerRanking(
                userId = input.userId,
                displayName = input.displayName,
                divisionId = input.divisionId,
                season = input.season,
                seasonId = input.seasonId,
                divisionLevelId = input.divisionLevelId,
                matchType = input.matchType,
                rank = index + 1,
                matchesPlayed = input.matchesPlayed,
                matchesWon = input.matchesWon,
                matchesLost = input.matchesLost,
                setsWon = input.setsWon,
                setsLost = input.setsLost,
                gamesWon = input.gamesWon,
                gamesLost = input.gamesLost,
                gameDifferential = input.gamesWon - input.gamesLost,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun updateRankingWithMatchResult(
        existing: PlayerRanking,
        won: Boolean,
        setsWon: Int,
        setsLost: Int,
        gamesWon: Int,
        gamesLost: Int
    ): PlayerRanking {
        val newPlayed = existing.matchesPlayed + 1
        val newWon = if (won) existing.matchesWon + 1 else existing.matchesWon
        val newLost = if (!won) existing.matchesLost + 1 else existing.matchesLost
        val newSetsWon = existing.setsWon + setsWon
        val newSetsLost = existing.setsLost + setsLost
        val newGamesWon = existing.gamesWon + gamesWon
        val newGamesLost = existing.gamesLost + gamesLost

        return existing.copy(
            matchesPlayed = newPlayed,
            matchesWon = newWon,
            matchesLost = newLost,
            setsWon = newSetsWon,
            setsLost = newSetsLost,
            gamesWon = newGamesWon,
            gamesLost = newGamesLost,
            gameDifferential = newGamesWon - newGamesLost,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun extractMatchTotals(sets: List<SetScore>): MatchTotals {
        var p1Sets = 0
        var p2Sets = 0
        var p1Games = 0
        var p2Games = 0

        for (set in sets) {
            p1Games += set.player1Games
            p2Games += set.player2Games

            val setWinner = set.winner ?: run {
                if (set.player1Games >= 6 && (set.player1Games - set.player2Games) >= 2) Player.PLAYER1
                else if (set.player2Games >= 6 && (set.player2Games - set.player1Games) >= 2) Player.PLAYER2
                else if (set.player1Games == 7) Player.PLAYER1
                else if (set.player2Games == 7) Player.PLAYER2
                else null
            }

            if (setWinner == Player.PLAYER1) p1Sets++
            else if (setWinner == Player.PLAYER2) p2Sets++
        }

        return MatchTotals(
            player1Sets = p1Sets,
            player2Sets = p2Sets,
            player1Games = p1Games,
            player2Games = p2Games
        )
    }
}
