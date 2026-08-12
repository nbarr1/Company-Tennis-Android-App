package com.example.scoring

import org.junit.Assert.*
import org.junit.Test

class ScoreEngineTest {

    @Test
    fun testGameProgressionToWin() {
        var score = ScoreEngine.createInitialScore()
        assertEquals(TennisPoint.LOVE, score.currentGame.player1)

        // Point 1: 15-0
        var result = ScoreEngine.applyPoint(score, Player.PLAYER1)
        assertEquals(TennisPoint.P15, result.nextScore.currentGame.player1)

        // Point 2: 30-0
        result = ScoreEngine.applyPoint(result.nextScore, Player.PLAYER1)
        assertEquals(TennisPoint.P30, result.nextScore.currentGame.player1)

        // Point 3: 40-0 (Game point)
        result = ScoreEngine.applyPoint(result.nextScore, Player.PLAYER1)
        assertEquals(TennisPoint.P40, result.nextScore.currentGame.player1)
        assertTrue(result.tips.contains(TipTrigger.GAME_POINT))

        // Point 4: Game won for Player 1
        result = ScoreEngine.applyPoint(result.nextScore, Player.PLAYER1)
        assertEquals(1, result.nextScore.sets[0].player1Games)
        assertEquals(TennisPoint.LOVE, result.nextScore.currentGame.player1)
        assertTrue(result.tips.contains(TipTrigger.SERVICE_CHANGE))
    }

    @Test
    fun testDeuceAndAdvantageCycling() {
        var score = ScoreEngine.createInitialScore()

        // Reach 40-40
        repeat(3) { score = ScoreEngine.applyPoint(score, Player.PLAYER1).nextScore }
        repeat(3) { score = ScoreEngine.applyPoint(score, Player.PLAYER2).nextScore }

        assertEquals(TennisPoint.P40, score.currentGame.player1)
        assertEquals(TennisPoint.P40, score.currentGame.player2)

        // Player 1 gets Advantage
        var result = ScoreEngine.applyPoint(score, Player.PLAYER1)
        assertEquals(TennisPoint.AD, result.nextScore.currentGame.player1)
        assertTrue(result.tips.contains(TipTrigger.ADVANTAGE))

        // Player 2 wins point -> back to Deuce
        result = ScoreEngine.applyPoint(result.nextScore, Player.PLAYER2)
        assertEquals(TennisPoint.P40, result.nextScore.currentGame.player1)
        assertEquals(TennisPoint.P40, result.nextScore.currentGame.player2)
        assertTrue(result.tips.contains(TipTrigger.DEUCE))

        // Player 2 gets Advantage
        result = ScoreEngine.applyPoint(result.nextScore, Player.PLAYER2)
        assertEquals(TennisPoint.AD, result.nextScore.currentGame.player2)

        // Player 2 wins point -> wins Game
        result = ScoreEngine.applyPoint(result.nextScore, Player.PLAYER2)
        assertEquals(1, result.nextScore.sets[0].player2Games)
    }

    @Test
    fun testTiebreakTriggerAndScoring() {
        var score = ScoreEngine.createInitialScore()

        // Bring set to 6-6
        for (i in 1..6) {
            repeat(4) { score = ScoreEngine.applyPoint(score, Player.PLAYER1).nextScore }
            repeat(4) { score = ScoreEngine.applyPoint(score, Player.PLAYER2).nextScore }
        }

        assertEquals(6, score.sets[0].player1Games)
        assertEquals(6, score.sets[0].player2Games)
        assertTrue(score.isTiebreak)

        // Play tiebreak points
        var result = ScoreEngine.applyPoint(score, Player.PLAYER1) // 1-0
        assertEquals(1, result.nextScore.tiebreakScore?.player1Points)

        // Advance P1 to 7 points in tiebreak
        repeat(6) {
            result = ScoreEngine.applyPoint(result.nextScore, Player.PLAYER1)
        }

        // P1 wins tiebreak 7-0 -> set 1 won (7-6)
        assertEquals(Player.PLAYER1, result.setCompleted?.winner)
        assertEquals(1, result.nextScore.player1SetsWon)
        assertTrue(result.tips.contains(TipTrigger.NEW_SET))
    }

    @Test
    fun testMatchPointDetection() {
        var score = ScoreEngine.createInitialScore(MatchFormatConfig(setsToWin = 1, gamesPerSet = 6))

        // P1 games = 5, P2 games = 0
        for (i in 1..5) {
            repeat(4) { score = ScoreEngine.applyPoint(score, Player.PLAYER1).nextScore }
        }

        // At 40-0 in 6th game -> MATCH_POINT tip
        repeat(3) { score = ScoreEngine.applyPoint(score, Player.PLAYER1).nextScore }
        val tip = ScoreEngine.detectOpportunityTip(score, MatchFormatConfig(setsToWin = 1, gamesPerSet = 6))

        assertEquals(TipTrigger.MATCH_POINT, tip)
    }
}
