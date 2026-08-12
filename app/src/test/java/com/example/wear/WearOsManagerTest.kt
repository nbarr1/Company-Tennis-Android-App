package com.example.wear

import com.example.scoring.TennisPoint
import org.junit.Assert.*
import org.junit.Test

class WearOsManagerTest {

    @Test
    fun tennisPointToInt_mapsEveryPointToItsWireIndex() {
        assertEquals(0, tennisPointToInt(TennisPoint.LOVE))
        assertEquals(1, tennisPointToInt(TennisPoint.P15))
        assertEquals(2, tennisPointToInt(TennisPoint.P30))
        assertEquals(3, tennisPointToInt(TennisPoint.P40))
        assertEquals(4, tennisPointToInt(TennisPoint.AD))
    }

    @Test
    fun tennisPointToInt_isTheInverseOfTheWatchsIntToPointMapping() {
        // MatchScoreWearableListenerService/TennisRepository.updateScoreFromWatch map raw watch
        // ints back to TennisPoint via 0->LOVE,1->P15,2->P30,3->P40,4->AD. Both directions must
        // agree or the phone<->watch score sync will silently desync.
        val watchIntToPoint = mapOf(
            0 to TennisPoint.LOVE,
            1 to TennisPoint.P15,
            2 to TennisPoint.P30,
            3 to TennisPoint.P40,
            4 to TennisPoint.AD
        )
        watchIntToPoint.forEach { (int, point) ->
            assertEquals(int, tennisPointToInt(point))
            assertEquals(point, watchIntToPoint[tennisPointToInt(point)])
        }
    }
}
