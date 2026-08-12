package com.example.scoring

import com.example.data.models.HeadToHead
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingEngineTest {

    @Test
    fun testRankingSortCascade() {
        val playerA = RankingInput("p1", "Alice", "div1", matchesWon = 3, setsWon = 6, gamesWon = 36, gamesLost = 12)
        val playerB = RankingInput("p2", "Bob", "div1", matchesWon = 3, setsWon = 6, gamesWon = 36, gamesLost = 20) // lower game diff
        val playerC = RankingInput("p3", "Charlie", "div1", matchesWon = 2, setsWon = 4, gamesWon = 24, gamesLost = 24)

        val inputs = listOf(playerC, playerB, playerA)
        val rankings = RankingEngine.computeRankings(inputs)

        assertEquals("Alice", rankings[0].displayName)
        assertEquals(1, rankings[0].rank)

        assertEquals("Bob", rankings[1].displayName)
        assertEquals(2, rankings[1].rank)

        assertEquals("Charlie", rankings[2].displayName)
        assertEquals(3, rankings[2].rank)
    }

    @Test
    fun testHeadToHeadTiebreaker() {
        // Both equal in matches, sets, games, game diff
        val p1 = RankingInput("p1", "Zack", "div1", matchesWon = 2, setsWon = 4, gamesWon = 24, gamesLost = 20)
        val p2 = RankingInput("p2", "Aaron", "div1", matchesWon = 2, setsWon = 4, gamesWon = 24, gamesLost = 20)

        // H2H: p1 beat p2
        val h2h = listOf(HeadToHead("h1", "div1", "p1", "p2", player1Wins = 1, player2Wins = 0))

        val rankings = RankingEngine.computeRankings(listOf(p2, p1), h2h)

        // Even though Aaron is alphabetically first, Zack won H2H so Zack is #1
        assertEquals("Zack", rankings[0].displayName)
        assertEquals("Aaron", rankings[1].displayName)
    }
}
