package com.chris.whisperloom.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Erkannte Sprache aus der Antwort von POST /audio/transcriptions. org.json gibt es auf der JVM
 * nur ueber Robolectric — deshalb nicht in [TranscriptionRequestTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranscriptionResponseTest {

    private fun detected(body: String) = TranscriptionRequest.detectedLanguage(JSONObject(body))

    /** OpenAI gpt-transcribe meldet `languages: [{code}]` statt `language` (API-Referenz, Stand 2026-10-28). */
    @Test fun gptTranscribeMeldetDieSpracheAlsListe() {
        assertEquals("de", detected("""{"text":"Hallo Welt","languages":[{"code":"de"}]}"""))
    }

    @Test fun beiMehrerenSprachenZaehltDieErste() {
        assertEquals("en", detected("""{"text":"Meeting um zehn","languages":[{"code":"en"},{"code":"de"}]}"""))
    }

    /** Unsicher erkannt: OpenAI liefert eine leere Liste. */
    @Test fun leereListeHeisstKeineSprache() {
        assertNull(detected("""{"text":"…","languages":[]}"""))
        assertNull(detected("""{"text":"…","languages":[{"code":""}]}"""))
    }

    /** verbose_json und eigene Server (Whisper-Familie): wie bisher das Feld `language`. */
    @Test fun verboseJsonLiefertLanguage() {
        assertEquals("german", detected("""{"text":"Hallo","language":"german"}"""))
        assertEquals("de", detected("""{"text":"Hallo","language":"de"}"""))
    }

    @Test fun ohneSprachfeldNull() {
        assertNull(detected("""{"text":"Hallo"}"""))
        assertNull(detected("""{"text":"Hallo","language":""}"""))
    }
}
