package com.example.wear

import android.content.Context
import com.example.scoring.LiveScore
import com.example.scoring.Player
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object WearOsManager {
    private val _pointInputs = MutableSharedFlow<Player>(extraBufferCapacity = 64)
    val pointInputs: SharedFlow<Player> = _pointInputs.asSharedFlow()

    private val _latestScore = MutableStateFlow<LiveScore?>(null)
    val latestScore: StateFlow<LiveScore?> = _latestScore.asStateFlow()

    private var isConnected: Boolean = false

    fun init(context: Context) {
        // Wearable API initialization if google play services present
        try {
            isConnected = true
        } catch (e: Exception) {
            isConnected = false
        }
    }

    fun isWearOsAvailable(): Boolean = isConnected

    fun sendScoreToWatch(score: LiveScore) {
        _latestScore.value = score
        // Send serialized score via Wearable DataClient / MessageClient
    }

    fun simulatePointInputFromWatch(player: Player) {
        _pointInputs.tryEmit(player)
    }
}

