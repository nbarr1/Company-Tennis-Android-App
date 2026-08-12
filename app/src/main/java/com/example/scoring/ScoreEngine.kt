package com.example.scoring

object ScoreEngine {

    fun createInitialScore(format: MatchFormatConfig = MatchFormatConfig()): LiveScore {
        return LiveScore(
            sets = listOf(SetScore(setNumber = 1, startedAt = System.currentTimeMillis())),
            currentSet = 0,
            currentGame = GameScore(),
            isTiebreak = false,
            tiebreakScore = null,
            server = Player.PLAYER1,
            serviceSide = ServiceSide.DEUCE,
            player1SetsWon = 0,
            player2SetsWon = 0
        )
    }

    fun applyPoint(
        score: LiveScore,
        scorer: Player,
        format: MatchFormatConfig = MatchFormatConfig()
    ): ScoreResult {
        // If match already won, no points can be applied
        if (score.player1SetsWon >= format.setsToWin || score.player2SetsWon >= format.setsToWin) {
            val winner = if (score.player1SetsWon >= format.setsToWin) Player.PLAYER1 else Player.PLAYER2
            return ScoreResult(nextScore = score, matchWinner = winner)
        }

        val tips = mutableListOf<TipTrigger>()

        if (score.isTiebreak) {
            return applyTiebreakPoint(score, scorer, format, tips)
        } else {
            return applyNormalGamePoint(score, scorer, format, tips)
        }
    }

    private fun applyNormalGamePoint(
        score: LiveScore,
        scorer: Player,
        format: MatchFormatConfig,
        tips: MutableList<TipTrigger>
    ): ScoreResult {
        val currentSet = score.sets.getOrNull(score.currentSet) ?: SetScore(setNumber = score.currentSet + 1)
        val game = score.currentGame
        val p1Pt = game.player1
        val p2Pt = game.player2

        var gameWonBy: Player? = null
        var nextGame = game
        var nextServiceSide = score.serviceSide.other

        val (scorerPt, opponentPt) = if (scorer == Player.PLAYER1) Pair(p1Pt, p2Pt) else Pair(p2Pt, p1Pt)

        if (scorerPt == TennisPoint.LOVE) {
            nextGame = updateGameScore(game, scorer, TennisPoint.P15)
        } else if (scorerPt == TennisPoint.P15) {
            nextGame = updateGameScore(game, scorer, TennisPoint.P30)
        } else if (scorerPt == TennisPoint.P30) {
            nextGame = updateGameScore(game, scorer, TennisPoint.P40)
        } else if (scorerPt == TennisPoint.P40) {
            if (opponentPt == TennisPoint.LOVE || opponentPt == TennisPoint.P15 || opponentPt == TennisPoint.P30) {
                // Game won!
                gameWonBy = scorer
            } else if (opponentPt == TennisPoint.P40) {
                // Advantage scorer!
                nextGame = updateGameScore(game, scorer, TennisPoint.AD)
                tips.add(TipTrigger.ADVANTAGE)
            } else if (opponentPt == TennisPoint.AD) {
                // Back to Deuce!
                nextGame = GameScore(TennisPoint.P40, TennisPoint.P40)
                tips.add(TipTrigger.DEUCE)
            }
        } else if (scorerPt == TennisPoint.AD) {
            // Advantage player wins game!
            gameWonBy = scorer
        }

        if (gameWonBy != null) {
            // Game completed
            val p1Games = if (gameWonBy == Player.PLAYER1) currentSet.player1Games + 1 else currentSet.player1Games
            val p2Games = if (gameWonBy == Player.PLAYER2) currentSet.player2Games + 1 else currentSet.player2Games

            val updatedSet = currentSet.copy(
                player1Games = p1Games,
                player2Games = p2Games
            )

            val updatedSets = score.sets.toMutableList()
            updatedSets[score.currentSet] = updatedSet

            val isDecidingSet = score.currentSet == (format.setsToWin * 2 - 2)
            val shouldTiebreak = (p1Games == format.tiebreakAt && p2Games == format.tiebreakAt) &&
                    (!isDecidingSet || format.finalSetTiebreak)

            if (shouldTiebreak) {
                tips.add(TipTrigger.SERVICE_CHANGE)
                tips.add(TipTrigger.TIEBREAK_START)

                val nextScore = score.copy(
                    sets = updatedSets,
                    currentGame = GameScore(),
                    isTiebreak = true,
                    tiebreakScore = TiebreakScore(0, 0),
                    server = score.server.other,
                    serviceSide = ServiceSide.DEUCE
                )

                addOpportunityTip(nextScore, format, tips)

                return ScoreResult(
                    nextScore = nextScore,
                    tips = tips,
                    gameCompleted = GameCompletion(gameWonBy)
                )
            }

            // Check set completion
            val setWinner = resolveSetWinner(p1Games, p2Games, format.gamesPerSet)
            if (setWinner != null) {
                return completeSet(
                    score = score,
                    updatedSets = updatedSets,
                    setWinner = setWinner,
                    gameWonBy = gameWonBy,
                    format = format,
                    tips = tips
                )
            } else {
                // Regular game transition
                tips.add(TipTrigger.SERVICE_CHANGE)

                val nextScore = score.copy(
                    sets = updatedSets,
                    currentGame = GameScore(),
                    isTiebreak = false,
                    tiebreakScore = null,
                    server = score.server.other,
                    serviceSide = ServiceSide.DEUCE
                )

                addOpportunityTip(nextScore, format, tips)

                return ScoreResult(
                    nextScore = nextScore,
                    tips = tips,
                    gameCompleted = GameCompletion(gameWonBy)
                )
            }
        } else {
            // Game continues
            val nextScore = score.copy(
                currentGame = nextGame,
                serviceSide = nextServiceSide
            )

            addOpportunityTip(nextScore, format, tips)

            return ScoreResult(nextScore = nextScore, tips = tips)
        }
    }

    private fun applyTiebreakPoint(
        score: LiveScore,
        scorer: Player,
        format: MatchFormatConfig,
        tips: MutableList<TipTrigger>
    ): ScoreResult {
        val currentSet = score.sets.getOrNull(score.currentSet) ?: SetScore(setNumber = score.currentSet + 1)
        val tb = score.tiebreakScore ?: TiebreakScore(0, 0)

        val p1Tb = if (scorer == Player.PLAYER1) tb.player1Points + 1 else tb.player1Points
        val p2Tb = if (scorer == Player.PLAYER2) tb.player2Points + 1 else tb.player2Points

        val updatedTb = TiebreakScore(p1Tb, p2Tb)
        val totalPts = p1Tb + p2Tb

        // Check if ends flip (every 6 points)
        if (totalPts % 6 == 0) {
            tips.add(TipTrigger.SERVICE_CHANGE)
        }

        // Check tiebreak winner (first to 7 win by 2)
        val (scorerPts, opponentPts) = if (scorer == Player.PLAYER1) Pair(p1Tb, p2Tb) else Pair(p2Tb, p1Tb)
        val tiebreakWon = scorerPts >= 7 && (scorerPts - opponentPts) >= 2

        if (tiebreakWon) {
            val p1Games = if (scorer == Player.PLAYER1) currentSet.player1Games + 1 else currentSet.player1Games
            val p2Games = if (scorer == Player.PLAYER2) currentSet.player2Games + 1 else currentSet.player2Games

            val updatedSet = currentSet.copy(
                player1Games = p1Games,
                player2Games = p2Games,
                tiebreak = updatedTb,
                winner = scorer
            )

            val updatedSets = score.sets.toMutableList()
            updatedSets[score.currentSet] = updatedSet

            return completeSet(
                score = score,
                updatedSets = updatedSets,
                setWinner = scorer,
                gameWonBy = scorer,
                format = format,
                tips = tips
            )
        } else {
            // Tiebreak continues
            // Service rotation in tiebreak: 1st point server A, then 2 points server B, 2 points server A...
            // Point 1 -> server changes after pt 1.
            // Points 3, 5, 7, 9... -> server changes after every odd total points.
            val nextServer = if (totalPts % 2 == 1) score.server.other else score.server
            if (nextServer != score.server) {
                if (!tips.contains(TipTrigger.SERVICE_CHANGE)) {
                    tips.add(TipTrigger.SERVICE_CHANGE)
                }
            }

            val nextScore = score.copy(
                tiebreakScore = updatedTb,
                server = nextServer
            )

            addOpportunityTip(nextScore, format, tips)

            return ScoreResult(nextScore = nextScore, tips = tips)
        }
    }

    private fun completeSet(
        score: LiveScore,
        updatedSets: MutableList<SetScore>,
        setWinner: Player,
        gameWonBy: Player,
        format: MatchFormatConfig,
        tips: MutableList<TipTrigger>
    ): ScoreResult {
        val completedSetIndex = score.currentSet
        val currentSet = updatedSets[completedSetIndex].copy(
            winner = setWinner,
            completedAt = System.currentTimeMillis()
        )
        updatedSets[completedSetIndex] = currentSet

        val p1SetsWon = if (setWinner == Player.PLAYER1) score.player1SetsWon + 1 else score.player1SetsWon
        val p2SetsWon = if (setWinner == Player.PLAYER2) score.player2SetsWon + 1 else score.player2SetsWon

        val matchWonBy = if (p1SetsWon >= format.setsToWin) Player.PLAYER1
        else if (p2SetsWon >= format.setsToWin) Player.PLAYER2
        else null

        if (matchWonBy != null) {
            tips.add(TipTrigger.MATCH_COMPLETE)
            val finalScore = score.copy(
                sets = updatedSets,
                currentGame = GameScore(),
                isTiebreak = false,
                tiebreakScore = null,
                player1SetsWon = p1SetsWon,
                player2SetsWon = p2SetsWon
            )
            return ScoreResult(
                nextScore = finalScore,
                tips = tips,
                matchWinner = matchWonBy,
                setCompleted = SetCompletion(completedSetIndex, setWinner),
                gameCompleted = GameCompletion(gameWonBy)
            )
        } else {
            tips.add(TipTrigger.NEW_SET)
            tips.add(TipTrigger.SERVICE_CHANGE)

            val nextSetNumber = completedSetIndex + 2
            updatedSets.add(SetScore(setNumber = nextSetNumber, startedAt = System.currentTimeMillis()))

            val nextScore = score.copy(
                sets = updatedSets,
                currentSet = completedSetIndex + 1,
                currentGame = GameScore(),
                isTiebreak = false,
                tiebreakScore = null,
                server = score.server.other,
                serviceSide = ServiceSide.DEUCE,
                player1SetsWon = p1SetsWon,
                player2SetsWon = p2SetsWon
            )

            addOpportunityTip(nextScore, format, tips)

            return ScoreResult(
                nextScore = nextScore,
                tips = tips,
                setCompleted = SetCompletion(completedSetIndex, setWinner),
                gameCompleted = GameCompletion(gameWonBy)
            )
        }
    }

    private fun addOpportunityTip(score: LiveScore, format: MatchFormatConfig, tips: MutableList<TipTrigger>) {
        val oppTip = detectOpportunityTip(score, format)
        if (oppTip != null && !tips.contains(oppTip)) {
            tips.add(oppTip)
        }
    }

    fun detectOpportunityTip(score: LiveScore, format: MatchFormatConfig): TipTrigger? {
        val currentSet = score.sets.getOrNull(score.currentSet) ?: return null
        val p1Games = currentSet.player1Games
        val p2Games = currentSet.player2Games

        for (player in listOf(Player.PLAYER1, Player.PLAYER2)) {
            val isOnePtFromGame = if (score.isTiebreak) {
                val tb = score.tiebreakScore ?: TiebreakScore(0, 0)
                val (pPts, oPts) = if (player == Player.PLAYER1) Pair(tb.player1Points, tb.player2Points)
                else Pair(tb.player2Points, tb.player1Points)
                pPts >= 6 && (pPts - oPts) >= 1
            } else {
                val (pPt, oPt) = if (player == Player.PLAYER1) Pair(score.currentGame.player1, score.currentGame.player2)
                else Pair(score.currentGame.player2, score.currentGame.player1)
                (pPt == TennisPoint.AD) ||
                        (pPt == TennisPoint.P40 && (oPt == TennisPoint.LOVE || oPt == TennisPoint.P15 || oPt == TennisPoint.P30))
            }

            if (isOnePtFromGame) {
                val (pGames, oGames) = if (player == Player.PLAYER1) Pair(p1Games, p2Games) else Pair(p2Games, p1Games)
                val winningGameWinsSet = score.isTiebreak ||
                        ((pGames + 1) >= format.gamesPerSet && (pGames + 1 - oGames) >= 2)

                if (winningGameWinsSet) {
                    val pSets = if (player == Player.PLAYER1) score.player1SetsWon else score.player2SetsWon
                    val winningSetWinsMatch = (pSets + 1) >= format.setsToWin

                    return if (winningSetWinsMatch) TipTrigger.MATCH_POINT else TipTrigger.SET_POINT
                } else {
                    return TipTrigger.GAME_POINT
                }
            }
        }
        return null
    }

    private fun resolveSetWinner(p1Games: Int, p2Games: Int, gamesPerSet: Int): Player? {
        if (p1Games >= gamesPerSet && (p1Games - p2Games) >= 2) return Player.PLAYER1
        if (p2Games >= gamesPerSet && (p2Games - p1Games) >= 2) return Player.PLAYER2
        return null
    }

    private fun updateGameScore(game: GameScore, player: Player, point: TennisPoint): GameScore {
        return if (player == Player.PLAYER1) game.copy(player1 = point) else game.copy(player2 = point)
    }

    fun formatSetsSummary(score: LiveScore): String {
        return score.sets.joinToString(" ") { set ->
            "${set.player1Games}-${set.player2Games}"
        }
    }

    fun formatScoreDisplay(score: LiveScore): String {
        return score.sets.joinToString(", ") { set ->
            val tbStr = if (set.tiebreak != null) {
                val loserPts = if (set.winner == Player.PLAYER1) set.tiebreak.player2Points else set.tiebreak.player1Points
                "($loserPts)"
            } else ""
            "${set.player1Games}-${set.player2Games}$tbStr"
        }
    }

    fun formatGameScore(score: LiveScore): String {
        if (score.isTiebreak) {
            val tb = score.tiebreakScore ?: TiebreakScore(0, 0)
            return "${tb.player1Points}-${tb.player2Points}"
        }

        val p1 = score.currentGame.player1
        val p2 = score.currentGame.player2

        if (p1 == TennisPoint.P40 && p2 == TennisPoint.P40) return "Deuce"
        if (p1 == TennisPoint.AD) return if (score.server == Player.PLAYER1) "Ad In" else "Ad Out"
        if (p2 == TennisPoint.AD) return if (score.server == Player.PLAYER2) "Ad In" else "Ad Out"
        if (p1 == TennisPoint.LOVE && p2 == TennisPoint.LOVE) return "Love-all"
        if (p1 == p2) return "${p1.label}-all"

        return "${p1.label} - ${p2.label}"
    }
}
