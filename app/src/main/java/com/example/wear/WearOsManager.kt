package com.example.wear

import android.content.Context
import android.util.Log
import com.example.scoring.LiveScore
import com.example.scoring.Player
import com.example.scoring.TennisPoint
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maps a [TennisPoint] to the raw 0-4 int scale the wear module's [/match_score] DataItem
 * schema already uses (see WearScoreActivity's `getScoreString`/`updateP1`/`updateP2` and
 * MatchScoreWearableListenerService's `intToPoint`). Kept as a standalone pure function so it's
 * cheaply unit-testable without any Android framework/Wearable dependency.
 */
fun tennisPointToInt(point: TennisPoint): Int = when (point) {
    TennisPoint.LOVE -> 0
    TennisPoint.P15 -> 1
    TennisPoint.P30 -> 2
    TennisPoint.P40 -> 3
    TennisPoint.AD -> 4
}

object WearOsManager {
    private const val TAG = "WearOsManager"
    private const val MATCH_SCORE_PATH = "/match_score"

    private val _pointInputs = MutableSharedFlow<Player>(extraBufferCapacity = 64)
    val pointInputs: SharedFlow<Player> = _pointInputs.asSharedFlow()

    private val _latestScore = MutableStateFlow<LiveScore?>(null)
    val latestScore: StateFlow<LiveScore?> = _latestScore.asStateFlow()

    private var isConnected: Boolean = false
    private var appContext: Context? = null

    fun init(context: Context) {
        try {
            appContext = context.applicationContext
            isConnected = true
        } catch (e: Exception) {
            isConnected = false
        }
    }

    fun isWearOsAvailable(): Boolean = isConnected

    /**
     * Pushes the live score to a paired watch via the Wearable Data Layer, on the same
     * `/match_score` path + `p1_score`/`p2_score` int schema the watch already uses for its
     * (working) watch->phone direction — this app and its :wear module only need to agree with
     * each other, not with any other client.
     */
    fun sendScoreToWatch(score: LiveScore) {
        _latestScore.value = score
        val context = appContext ?: return
        try {
            val p1 = tennisPointToInt(score.currentGame.player1)
            val p2 = tennisPointToInt(score.currentGame.player2)
            val putDataReq = PutDataMapRequest.create(MATCH_SCORE_PATH).run {
                dataMap.putInt("p1_score", p1)
                dataMap.putInt("p2_score", p2)
                dataMap.putLong("timestamp", System.currentTimeMillis())
                asPutDataRequest()
            }
            Wearable.getDataClient(context).putDataItem(putDataReq)
                .addOnFailureListener { e -> Log.w(TAG, "sendScoreToWatch failed: ${e.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "sendScoreToWatch error: ${e.message}")
        }
    }

    fun simulatePointInputFromWatch(player: Player) {
        _pointInputs.tryEmit(player)
    }
}

