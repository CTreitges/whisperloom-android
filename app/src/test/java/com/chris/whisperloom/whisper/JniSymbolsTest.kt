package com.chris.whisperloom.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lokal gibt es kein NDK, der Linker prueft die JNI-Symbole also erst in CI. Dieser Test liest
 * WhisperLib.kt und whisper_jni.cpp und gleicht Namen, Praefix (Paket + Objektname) und
 * Parameterzahl in beide Richtungen ab — derselbe Abgleich wie tools/check_jni_symbols.py.
 * Zusaetzlich (nur hier) die Parametertypen in Reihenfolge: JNI-Kurznamen kodieren keine Typen,
 * ein verrutschter Parameter faellt sonst nicht einmal dem Linker auf, erst zur Laufzeit.
 */
class JniSymbolsTest {

    private fun source(vararg candidates: String): String =
        candidates.map { File(it) }.first { it.exists() }.readText()

    private val kt = source(
        "src/main/java/com/chris/whisperloom/whisper/WhisperLib.kt",
        "app/src/main/java/com/chris/whisperloom/whisper/WhisperLib.kt",
    )
    private val cpp = source("src/main/cpp/whisper_jni.cpp", "app/src/main/cpp/whisper_jni.cpp")

    private fun params(list: String) = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Kotlin-Typ -> JNI-Typ; Nullbarkeit aendert den JNI-Typ nicht. */
    private val jniType = mapOf(
        "Long" to "jlong", "Int" to "jint", "Boolean" to "jboolean",
        "String" to "jstring", "FloatArray" to "jfloatArray",
    )

    private val prefix: String
    private val ktFuns: Map<String, List<String>>  // Name -> JNI-Typen der Parameter
    private val cppFuns: Map<String, List<String>> // Symbol -> JNI-Typen ohne JNIEnv*, jobject

    init {
        val pkg = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE).find(kt)!!.groupValues[1]
        val obj = Regex("^\\s*(?:\\w+\\s+)*object\\s+(\\w+)", RegexOption.MULTILINE).find(kt)!!.groupValues[1]
        // JNI-Mangling: "." -> "_", "_" -> "_1"
        prefix = "Java_" + "$pkg.$obj".replace("_", "_1").replace('.', '_') + "_"
        ktFuns = Regex("external\\s+fun\\s+(\\w+)\\s*\\(([^)]*)\\)").findAll(kt).associate { m ->
            m.groupValues[1] to params(m.groupValues[2]).map { p ->
                val type = p.substringAfter(':').trim().removeSuffix("?")
                jniType[type] ?: type
            }
        }
        cppFuns = Regex("JNIEXPORT\\s+\\w+\\s+JNICALL\\s+(Java_\\w+)\\s*\\(([^)]*)\\)").findAll(cpp).associate { m ->
            m.groupValues[1] to params(m.groupValues[2]).drop(2).map { it.split(Regex("\\s+")).first() }
        }
    }

    @Test fun praefixEntsprichtPaketUndObjekt() {
        assertEquals("Java_com_chris_whisperloom_whisper_WhisperLib_", prefix)
        assertTrue(cppFuns.isNotEmpty())
        for (symbol in cppFuns.keys) assertTrue("$symbol ohne Praefix $prefix", symbol.startsWith(prefix))
    }

    @Test fun jedeExternalFunHatGenauEinSymbolMitGleicherParameterzahl() {
        assertTrue(ktFuns.isNotEmpty())
        assertEquals(ktFuns.keys, cppFuns.keys.map { it.removePrefix(prefix) }.toSet())
        for ((name, types) in ktFuns) assertEquals("Parameterzahl von $name", types.size, cppFuns[prefix + name]?.size)
    }

    @Test fun parametertypenStimmenInReihenfolgeUeberein() {
        for ((name, types) in ktFuns) assertEquals("Parametertypen von $name", types, cppFuns[prefix + name])
        // Stichprobe, damit der Abgleich nicht leer durchlaeuft
        assertEquals(
            listOf("jlong", "jint", "jstring", "jstring", "jint", "jboolean", "jboolean", "jfloatArray"),
            ktFuns["fullTranscribe"],
        )
    }

    @Test fun zeitstempelKommenAusDemJniParameter() {
        // Fest no_timestamps=true schneidet Audio > 30 s alle 30 s mitten im Wort (WhisperContext.useTimestamps).
        assertFalse(Regex("no_timestamps\\s*=\\s*true").containsMatchIn(cpp))
        assertTrue(Regex("no_timestamps\\s*=\\s*timestamps\\b").containsMatchIn(cpp))
    }

    @Test fun keinAssetLaderMehr() {
        // Modelle kommen nur per Download; ein Asset-Loader wuerde wieder ein Modell im APK nahelegen.
        assertFalse(cpp.contains("asset_manager"))
        assertFalse(ktFuns.containsKey("initContextFromAsset"))
    }
}
