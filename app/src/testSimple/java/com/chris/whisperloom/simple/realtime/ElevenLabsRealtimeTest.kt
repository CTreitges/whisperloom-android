package com.chris.whisperloom.simple.realtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/** ElevenLabs-Protokoll (Konzept §7.1, Stichprobe): URL, Audio-Nachricht, Deutung der Server-Nachrichten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ElevenLabsRealtimeTest {

    @Test fun urlTraegtModellTokenFormatSpracheUndCommitStrategie() {
        val url = ElevenLabsRealtime.url("wss://api.elevenlabs.io", "tok 1", RealtimeOptions(vadSilenceSeconds = 3.0))
        assertTrue(url.startsWith("wss://api.elevenlabs.io/v1/speech-to-text/realtime?"))
        assertTrue(url.contains("model_id=scribe_v2_realtime"))
        assertTrue(url.contains("token=tok+1"))
        assertTrue(url.contains("audio_format=pcm_16000"))
        assertTrue(url.contains("language_code=de"))
        assertTrue(url.contains("commit_strategy=vad"))
        assertTrue(url.contains("vad_silence_threshold_secs=3.0"))
        assertFalse(url.contains("no_verbatim"))
    }

    @Test fun manuellKeytermsUndNoVerbatim() {
        val url = ElevenLabsRealtime.url(
            "wss://api.elevenlabs.io/", "t",
            RealtimeOptions(vadSilenceSeconds = null, keyterms = listOf("Anna", "Müller"), noVerbatim = true),
        )
        assertTrue(url.contains("commit_strategy=manual"))
        assertFalse(url.contains("vad_silence"))
        assertTrue(url.contains("keyterms=Anna&keyterms=M%C3%BCller"))
        assertTrue(url.contains("no_verbatim=true"))
        assertFalse("kein doppelter Schraegstrich", url.contains("io//v1"))
    }

    @Test fun audioNachrichtIstBase64MitSampleRateUndCommit() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val json = JSONObject(ElevenLabsRealtime.audioMessage(pcm, 16_000, commit = true))
        assertEquals("input_audio_chunk", json.getString("message_type"))
        assertEquals(Base64.getEncoder().encodeToString(pcm), json.getString("audio_base_64"))
        assertEquals(16_000, json.getInt("sample_rate"))
        assertTrue(json.getBoolean("commit"))
        assertFalse(JSONObject(ElevenLabsRealtime.audioMessage(pcm, 16_000, commit = false)).getBoolean("commit"))
    }

    @Test fun serverNachrichtenWerdenGedeutet() {
        assertEquals(RealtimeEvent.Started, ElevenLabsRealtime.parse("""{"message_type":"session_started","session_id":"x"}"""))
        assertEquals(RealtimeEvent.Partial("Hallo"), ElevenLabsRealtime.parse("""{"message_type":"partial_transcript","text":"Hallo"}"""))
        assertEquals(RealtimeEvent.Committed("Hallo."), ElevenLabsRealtime.parse("""{"message_type":"committed_transcript","text":"Hallo."}"""))
        assertEquals(
            RealtimeEvent.Error("quota_exceeded", "Guthaben leer"),
            ElevenLabsRealtime.parse("""{"message_type":"quota_exceeded","error":"Guthaben leer"}"""),
        )
        assertEquals(RealtimeEvent.Ignored, ElevenLabsRealtime.parse("""{"message_type":"committed_transcript_with_timestamps"}"""))
        assertEquals(RealtimeEvent.Ignored, ElevenLabsRealtime.parse("kein json"))
    }
}
