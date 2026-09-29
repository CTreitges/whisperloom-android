package com.chris.whisperloom.agent

import org.junit.Assert.assertEquals
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
        // APPWIDGET_UPDATE ist ein geschuetzter System-Broadcast — der Empfaenger braucht
        // (und bekommt) keine Tuer nach aussen.
        assertTrue("exported=\"false\" fehlt", receiver.contains("""android:exported="false""""))
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

    private fun dp(attribut: String): Int {
        val wert = Regex("""android:$attribut="(\d+)dp"""").find(providerXml())
        assertTrue("$attribut fehlt oder ist nicht in dp", wert != null)
        return wert!!.groupValues[1].toInt()
    }

    @Test fun dasWidgetIstVon1x1Bis4x2Ziehbar() {
        // Zellmasse laut Doku: hochkant (73n-16) x (118m-16) dp.
        assertTrue("1x1 (57 dp breit) muss erreichbar sein", dp("minResizeWidth") <= 73 - 16)
        assertTrue("1x1 (102 dp hoch) muss erreichbar sein", dp("minResizeHeight") <= 118 - 16)
        assertTrue("4 Spalten (276 dp) muessen passen", dp("maxResizeWidth") >= 73 * 4 - 16)
        assertTrue("5 Spalten (349 dp) nicht mehr", dp("maxResizeWidth") < 73 * 5 - 16)
        assertTrue("2 Zeilen (220 dp) muessen passen", dp("maxResizeHeight") >= 118 * 2 - 16)
        // Platziert wird weiter als 2x2 — Bestandswidgets aendern ihre Groesse nicht.
        assertEquals(110, dp("minWidth"))
        assertEquals(110, dp("minHeight"))
        assertTrue(providerXml().contains("""android:resizeMode="horizontal|vertical""""))
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

    @Test fun widgetProfileUndFotosBleibenAufDemGeraet() {
        // Wie die anderen App-Dateien: nichts davon in Cloud-Backup oder Geraete-Transfer.
        val backup = datei("src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/backup_rules.xml").readText()
        val extraction = datei("src/main/res/xml/data_extraction_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml").readText()
        val prefs = """<exclude domain="sharedpref" path="${WidgetProfileStore.FILE}.xml" />"""
        val fotos = """<exclude domain="file" path="${WidgetPhoto.DIR}" />"""
        listOf(
            backup,
            extraction.substringAfter("<cloud-backup").substringBefore("</cloud-backup>"),
            extraction.substringAfter("<device-transfer").substringBefore("</device-transfer>"),
        ).forEach {
            assertTrue("Profile nicht ausgeschlossen: $it", it.contains(prefs))
            assertTrue("Fotos nicht ausgeschlossen: $it", it.contains(fotos))
        }
    }
}
