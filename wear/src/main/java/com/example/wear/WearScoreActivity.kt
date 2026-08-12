package com.example.wear

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearScoreActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private var p1Score = mutableIntStateOf(0)
    private var p2Score = mutableIntStateOf(0)
    private val canUndo = mutableStateOf(false)
    private val scoreHistory = java.util.Stack<Pair<Int, Int>>()

    private var lastKeyPressTime = 0L
    private var keyPressCount = 0
    private var longPressHandled = false
    private var actionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContent {
            val isHighContrast = remember { mutableStateOf(false) }
            val colors = if (isHighContrast.value) {
                androidx.wear.compose.material.Colors(
                    primary = Color.Black,
                    primaryVariant = Color.Black,
                    secondary = Color.Black,
                    secondaryVariant = Color.Black,
                    background = Color.White,
                    surface = Color.LightGray,
                    error = Color.Red,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = Color.Black,
                    onSurface = Color.Black,
                    onError = Color.White
                )
            } else {
                androidx.wear.compose.material.MaterialTheme.colors
            }
            MaterialTheme(colors = colors) {
                WearScoreScreen(
                    p1Score = p1Score.intValue,
                    p2Score = p2Score.intValue,
                    canUndo = canUndo.value,
                    isHighContrast = isHighContrast.value,
                    onToggleContrast = { isHighContrast.value = !isHighContrast.value },
                    onUndo = {
                        doUndo()
                    },
                    onP1Change = { newScore ->
                        updateP1(newScore)
                    },
                    onP2Change = { newScore ->
                        updateP2(newScore)
                    }
                )
            }
        }
    }

    private fun doUndo() {
        if (scoreHistory.isNotEmpty()) {
            val prev = scoreHistory.pop()
            p1Score.intValue = prev.first
            p2Score.intValue = prev.second
            canUndo.value = scoreHistory.isNotEmpty()
            syncScoreToPhone(p1Score.intValue, p2Score.intValue)
        }
    }

    private fun updateP1(newScore: Int) {
        scoreHistory.push(Pair(p1Score.intValue, p2Score.intValue))
        canUndo.value = true
        p1Score.intValue = newScore
        syncScoreToPhone(newScore, p2Score.intValue)
    }

    private fun updateP2(newScore: Int) {
        scoreHistory.push(Pair(p1Score.intValue, p2Score.intValue))
        canUndo.value = true
        p2Score.intValue = newScore
        syncScoreToPhone(p1Score.intValue, newScore)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_STEM_2,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.repeatCount == 0) {
                    longPressHandled = false
                    event.startTracking()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_STEM_2,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                longPressHandled = true
                doUndo()
                true
            }
            else -> super.onKeyLongPress(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_STEM_2,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!longPressHandled) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastKeyPressTime < 400) {
                        keyPressCount++
                    } else {
                        keyPressCount = 1
                    }
                    lastKeyPressTime = currentTime

                    if (keyPressCount >= 2) {
                        actionJob?.cancel()
                        finish()
                    } else {
                        actionJob = lifecycleScope.launch {
                            delay(300)
                            if (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                updateP1(p1Score.intValue + 1)
                            } else {
                                updateP2(p2Score.intValue + 1)
                            }
                        }
                    }
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    private fun syncScoreToPhone(p1: Int, p2: Int) {
        val putDataReq = PutDataMapRequest.create("/match_score").run {
            dataMap.putInt("p1_score", p1)
            dataMap.putInt("p2_score", p2)
            dataMap.putLong("timestamp", System.currentTimeMillis())
            asPutDataRequest()
        }
        Wearable.getDataClient(this).putDataItem(putDataReq)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/match_score") {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val p1 = dataMapItem.dataMap.getInt("p1_score", p1Score.intValue)
                    val p2 = dataMapItem.dataMap.getInt("p2_score", p2Score.intValue)
                    p1Score.intValue = p1
                    p2Score.intValue = p2
                }
            }
        }
    }
}

@Composable
fun WearScoreScreen(
    p1Score: Int,
    p2Score: Int,
    canUndo: Boolean,
    isHighContrast: Boolean,
    onToggleContrast: () -> Unit,
    onUndo: () -> Unit,
    onP1Change: (Int) -> Unit,
    onP2Change: (Int) -> Unit
) {
    var elapsedTime by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000L)
            elapsedTime += 1L
        }
    }
    
    val minutes = elapsedTime / 60
    val seconds = elapsedTime % 60
    val timeString = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    val tennisScores = listOf("0", "15", "30", "40", "Ad")
    val getScoreString = { score: Int ->
        if (score < tennisScores.size) tennisScores[score] else tennisScores.last()
    }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        timeText = { 
            TimeText(
                timeTextStyle = androidx.wear.compose.material.TimeTextDefaults.timeTextStyle(
                    color = MaterialTheme.colors.onBackground
                )
            ) 
        },
        modifier = Modifier.background(MaterialTheme.colors.background)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        onToggleContrast()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Live Match • $timeString",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "☀",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                // Player 1 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "P1", 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(24.dp)
                    )
                    
                    Button(
                        onClick = { if (p1Score > 0) onP1Change(p1Score - 1) },
                        modifier = Modifier.width(36.dp).height(36.dp),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrement P1")
                    }
                    
                    Text(
                        text = getScoreString(p1Score), 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                    
                    Button(
                        onClick = { 
                            onP1Change(p1Score + 1)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.width(36.dp).height(36.dp),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increment P1")
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Player 2 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "P2", 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(24.dp)
                    )
                    
                    Button(
                        onClick = { if (p2Score > 0) onP2Change(p2Score - 1) },
                        modifier = Modifier.width(36.dp).height(36.dp),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrement P2")
                    }
                    
                    Text(
                        text = getScoreString(p2Score), 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                    
                    Button(
                        onClick = { 
                            onP2Change(p2Score + 1)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.width(36.dp).height(36.dp),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increment P2")
                    }
                }
                if (canUndo) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactButton(
                        onClick = onUndo,
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                }
            }
        }
    }
}
