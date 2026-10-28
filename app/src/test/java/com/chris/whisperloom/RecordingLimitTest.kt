package com.chris.whisperloom

import com.chris.whisperloom.RecordingLimit.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingLimitTest {

    private val min = 60_000L

    @Test fun onlineHinweisWarnungEnde() {
        assertEquals(Stage.OK, RecordingLimit.stage(10 * min - 1, limited = true))
        assertEquals(Stage.HINT, RecordingLimit.stage(10 * min, limited = true))
        assertEquals(Stage.HINT, RecordingLimit.stage(11 * min + 39_999, limited = true))
        assertEquals("Warnung bei 11:40", Stage.WARN, RecordingLimit.stage(11 * min + 40_000, limited = true))
        assertEquals(Stage.WARN, RecordingLimit.stage(12 * min - 1, limited = true))
        assertEquals("Senden bei 12:00", Stage.STOP, RecordingLimit.stage(12 * min, limited = true))
    }

    @Test fun offlineOhneGrenze() {
        assertEquals(Stage.OK, RecordingLimit.stage(60 * min, limited = false))
    }

    @Test fun widgetNotbremseOhneHinweis() {
        fun widget(ms: Long) = RecordingLimit.stage(ms, limited = true, stopMs = 5 * min, hintMs = null)
        assertEquals(Stage.OK, widget(4 * min + 39_999))
        assertEquals("Warnung bei 4:40", Stage.WARN, widget(4 * min + 40_000))
        assertEquals(Stage.STOP, widget(5 * min))
    }
}
