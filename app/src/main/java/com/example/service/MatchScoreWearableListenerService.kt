package com.example.service

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class MatchScoreWearableListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/match_score") {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val p1 = dataMapItem.dataMap.getInt("p1_score")
                    val p2 = dataMapItem.dataMap.getInt("p2_score")
                    Log.d("WearSync", "Received match score from watch: P1=$p1, P2=$p2")
                    
                    com.example.data.repository.TennisRepository.updateScoreFromWatch(p1, p2)
                }
            }
        }
    }
}
