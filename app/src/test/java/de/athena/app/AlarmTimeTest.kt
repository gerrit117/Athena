package de.athena.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AlarmTimeTest {
    private fun epoch(s: String)=Instant.parse(s).toEpochMilli()
    @Test fun nextOccurrenceIsTomorrowOnceTodaysTimeHasPassed() {
        assertEquals(epoch("2026-11-03T04:00:00Z"),nextAlarmTime(5,0,"Europe/Berlin",epoch("2026-11-02T05:00:00Z")))
    }
    @Test fun springGapMovesMissingWallTimeForward() {
        assertEquals(epoch("2026-03-29T01:30:00Z"),nextAlarmTime(2,30,"Europe/Berlin",epoch("2026-03-28T22:00:00Z")))
    }
    @Test fun autumnFoldCanChooseTheSecondStillFutureOccurrence() {
        assertEquals(epoch("2026-10-25T01:30:00Z"),nextAlarmTime(2,30,"Europe/Berlin",epoch("2026-10-25T00:45:00Z")))
    }
    @Test fun midnightAndExactCurrentMinuteAreStrictlyFuture() {
        assertEquals(epoch("2026-11-03T00:00:00Z"),nextAlarmTime(0,0,"UTC",epoch("2026-11-02T00:00:00Z")))
    }
    @Test fun dailyAlarmNeverRingsTwiceDuringTheAutumnFold() {
        assertEquals(epoch("2026-10-26T01:30:00Z"),nextDailyAlarmTime(2,30,"Europe/Berlin",epoch("2026-10-25T00:30:01Z")))
    }
    @Test fun weeklyAlarmSkipsDaysAndRepeatsOneWeekLater() {
        assertEquals(epoch("2026-11-09T04:00:00Z"),nextAlarmTime(5,0,"Europe/Berlin",epoch("2026-11-02T05:00:00Z"),listOf(1)))
    }
    @Test fun weekdayAlarmSkipsTheWeekend() {
        assertEquals(epoch("2026-11-09T04:00:00Z"),nextDailyAlarmTime(5,0,"Europe/Berlin",epoch("2026-11-06T04:00:01Z"),listOf(1,2,3,4,5)))
    }
}
