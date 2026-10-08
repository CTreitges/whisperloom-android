package com.chris.whisperloom.ime

import com.chris.whisperloom.ime.DictationSession.Length
import com.chris.whisperloom.ime.DictationSession.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationSessionTest {

    private fun festgestellt() = DictationSession().apply {
        hold()
        lock()
    }

    @Test fun haltenFeststellenPausierenWeiter() {
        val s = DictationSession()
        assertFalse(s.isOpen)
        assertTrue(s.hold())
        assertEquals(Phase.HOLDING, s.phase)
        assertFalse("Beim Halten gibt es keine eigenen Tasten", s.withoutFinger)
        assertTrue(s.lock())
        assertTrue(s.isLocked)
        assertTrue(s.pause())
        assertTrue(s.isPaused)
        assertTrue(s.withoutFinger)
        assertTrue(s.resume())
        assertEquals(Phase.LOCKED, s.phase)
    }

    @Test fun pauseNurAusDemFestgestelltenZustand() {
        val s = DictationSession()
        assertFalse("Ohne Diktat", s.pause())
        s.hold()
        assertFalse("Beim Halten liegt der Finger auf der Taste", s.pause())
        assertEquals(Phase.HOLDING, s.phase)
        s.lock()
        s.pause()
        assertFalse("Schon pausiert", s.pause())
        assertEquals(Phase.PAUSED, s.phase)
    }

    @Test fun weiterNurAusDerPause() {
        val s = DictationSession()
        assertFalse(s.resume())
        s.hold()
        assertFalse(s.resume())
        s.lock()
        assertFalse("Laeuft schon", s.resume())
        assertEquals(Phase.LOCKED, s.phase)
    }

    @Test fun feststellenNurAusDemHalten() {
        val s = DictationSession()
        assertFalse(s.lock())
        assertEquals(Phase.NONE, s.phase)
        s.hold()
        s.lock()
        s.pause()
        assertFalse("Aus der Pause fuehrt nur Weiter zurueck", s.lock())
        assertEquals(Phase.PAUSED, s.phase)
    }

    @Test fun haltenStartetNichtsUeberEinemOffenenDiktat() {
        val s = festgestellt()
        s.pause()
        assertFalse("Eine neue Aufnahme wuerde das pausierte Diktat ueberschreiben", s.hold())
        assertEquals(Phase.PAUSED, s.phase)
    }

    @Test fun sendenUndVerwerfenAusJedemZustand() {
        for (vorbereiten in listOf<DictationSession.() -> Unit>(
            { hold() },
            { hold(); lock() },
            { hold(); lock(); pause() },
            { hold(); lock(); update(DictationSession.MAX_MS, limited = true); cap() },
        )) {
            val s = DictationSession().apply(vorbereiten)
            assertTrue(s.isOpen)
            s.end()
            assertEquals(Phase.NONE, s.phase)
            assertFalse(s.capped)
            assertEquals(0L, s.recordedMs)
            assertEquals(Length.OK, s.length)
        }
    }

    @Test fun dieAufnahmezeitStehtInDerPauseUndLaeuftDanachWeiter() {
        val s = festgestellt()
        s.update(5_000, limited = true)
        s.pause()
        assertEquals(5_000L, s.recordedMs)
        s.resume()
        s.update(7_500, limited = true)
        s.pause()
        s.resume()
        s.update(9_000, limited = true)
        assertEquals("Ueber zwei Pausen summiert", 9_000L, s.recordedMs)
    }

    @Test fun laengenStufenNurBeiOnlineErkennung() {
        assertEquals(Length.OK, DictationSession.length(DictationSession.LONG_MS - 1, limited = true))
        assertEquals(Length.LONG, DictationSession.length(DictationSession.LONG_MS, limited = true))
        assertEquals(Length.LONG, DictationSession.length(DictationSession.MAX_MS - 1, limited = true))
        assertEquals(Length.MAX, DictationSession.length(DictationSession.MAX_MS, limited = true))
        assertEquals("Offline gibt es keine Obergrenze", Length.OK, DictationSession.length(60 * 60_000L, limited = false))
    }

    @Test fun derDeckelLiegtUnterDerGrenzeDerAnbieter() {
        // OpenAI: 25 MB; 16 kHz Mono PCM16 = 32 000 Byte je Sekunde.
        val grenzeMs = 25_000_000L * 1000 / 32_000
        assertTrue(DictationSession.MAX_MS < grenzeMs)
        assertTrue(DictationSession.LONG_MS < DictationSession.MAX_MS)
    }

    @Test fun hoechstlaengePausiertUndSperrtWeiter() {
        val s = festgestellt()
        assertEquals(Length.MAX, s.update(DictationSession.MAX_MS, limited = true))
        assertTrue(s.cap())
        assertTrue(s.isPaused)
        assertTrue(s.capped)
        assertFalse("Weiter ist gesperrt", s.resume())
        assertEquals(Phase.PAUSED, s.phase)
    }

    @Test fun deckelNurAusDerLaufendenAufnahme() {
        val s = DictationSession()
        s.hold()
        assertFalse("Beim Halten gibt es keine Pause", s.cap())
        assertFalse(s.capped)
        s.lock()
        s.pause()
        assertFalse("Schon pausiert: der Nutzer darf noch weiter", s.cap())
        assertFalse(s.capped)
    }
}
