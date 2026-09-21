package com.chris.whisperloom.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Das Widget lebt fast vollstaendig im Manifest und in der Provider-XML. Kein Robolectric-Test
 * deckt das ab — also hier, gegen die Dateien selbst. Jede dieser Zeilen war eine bewusste
 * Entscheidung, die sich beim naechsten Aufraeumen sonst lautlos verliert.
 */
class VoiceTaskManifestTest {

    private fun datei(vararg kandidaten: String): File =
        kandidaten.map { File(it) }.first { it.exists() }

    private fun manifest(): File = datei("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")

    private fun block(tag: String, name: String): String {
        val text = manifest().readText()
        val start = text.indexOf("""android:name=".$name"""")
        assertTrue("$name steht nicht im Manifest", start > 0)
        val anfang = text.lastIndexOf("<$tag", start)
        return text.substring(anfang, text.indexOf(">", start).let { text.indexOf("</$tag>", anfang).takeIf { e -> e in 0..(it + 4000) } ?: it })
    }

    private fun providerXml(): String =
        datei("src/main/res/xml/widget_task_info.xml", "app/src/main/res/xml/widget_task_info.xml").readText()

    @Test fun dasWidgetIstAlsEmpfaengerRegistriert() {
        val receiver = block("receiver", "agent.VoiceTaskWidget")
        assertTrue(receiver.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(receiver.contains("""android:resource="@xml/widget_task_info""""))
    }

    @Test fun derAufnahmeDienstIstEinMikrofonDienst() {
        assertTrue(block("service", "agent.VoiceTaskService").contains("""android:foregroundServiceType="microphone""""))
    }

    @Test fun dasTrampolinIstUnsichtbarUndTauchtNichtInDerUebersichtAuf() {
        val activity = block("activity", "agent.VoiceTaskTrampolineActivity")
        assertTrue(activity.contains("""android:theme="@android:style/Theme.Translucent.NoTitleBar""""))
        assertTrue(activity.contains("""android:excludeFromRecents="true""""))
        assertTrue(activity.contains("""android:taskAffinity="""""))
    }

    @Test fun dasTrampolinSetztKeinNoHistory() {
        // noHistory wuerde onActivityResult killen und jeden Permission-Dialog still unbrauchbar machen.
        assertFalse(block("activity", "agent.VoiceTaskTrampolineActivity").contains("noHistory"))
    }

    @Test fun dasWidgetPolltNicht() {
        assertTrue("""updatePeriodMillis="0" fehlt""", providerXml().contains("""android:updatePeriodMillis="0""""))
    }

    @Test fun dieVorschauIstEinLayoutKeinBild() {
        // previewImage ist seit Android 12 abgekuendigt und zeigt in der Auswahl nichts Echtes.
        assertTrue(providerXml().contains("""android:previewLayout="@layout/widget_task_preview""""))
        assertFalse("android:previewImage darf nicht gesetzt sein", providerXml().contains("android:previewImage"))
    }

    @Test fun dasWidgetNenntSeineWunschgroesse() {
        listOf("targetCellWidth", "targetCellHeight", "maxResizeWidth", "maxResizeHeight").forEach {
            assertTrue("$it fehlt", providerXml().contains("android:$it"))
        }
    }

    @Test fun derOffeneAuftragBleibtAufDemGeraet() {
        // Enthaelt Transkript und Aufnahme — nichts davon gehoert in ein Cloud-Backup.
        val backup = datei("src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/backup_rules.xml").readText()
        val extraction = datei("src/main/res/xml/data_extraction_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(backup.contains("""path="whisperloom_agent.xml""""))
        assertTrue(backup.contains("""path="voice_task.pcm""""))
        assertTrue(extraction.substringAfter("<cloud-backup").substringBefore("</cloud-backup>").contains("""path="whisperloom_agent.xml""""))
        assertTrue(extraction.substringAfter("<device-transfer").substringBefore("</device-transfer>").contains("""path="voice_task.pcm""""))
    }
}
