package com.chris.whisperloom.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Laengen der Erklaer- und Anleitungstexte (Spec 3.7.1 §0/§8): kurz genug, um sie neben einer
 * Illustration zu lesen. Gezaehlt wird der sichtbare Text; jede Illustration hat einen Bildtext.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnleitungsTexteTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** Alle Strings der App (nonTransitiveRClass: nur die eigenen), Name -> sichtbarer Text. */
    private val texte: Map<String, String> =
        R.string::class.java.fields.associate { it.name to ctx.getString(it.getInt(null)) }

    /** Name -> Laenge aller Texte aus [auswahl], die laenger als [max] sind. */
    private fun zuLang(auswahl: Map<String, String>, max: Int) =
        auswahl.filterValues { it.length > max }.mapValues { it.value.length }

    @Test fun keinErklaertextIstLaengerAls200Zeichen() {
        // Ausnahmen: die Play-Pflichttexte der Prominent Disclosure bleiben wortgleich;
        // "Eigener Server" in der Hilfe darf als drei kurze Zeilen bis 220 Zeichen haben.
        val auswahl = texte.filterKeys { !it.startsWith("disclosure_") && it != "help_s4_body" }
        assertEquals(emptyMap<String, Int>(), zuLang(auswahl, 200))
        assertEquals(emptyMap<String, Int>(), zuLang(texte.filterKeys { it == "help_s4_body" }, 220))
    }

    @Test fun gekuerztesVokabularSagtBeiEinemBegriffWird() {
        // Plural statt String: "1 von 3 Begriffen werden" war falsch. Als <plurals> faellt der Text
        // aus [texte] heraus, deshalb die 200-Zeichen-Grenze hier noch einmal.
        val res = ctx.resources
        val eins = res.getQuantityString(R.plurals.vocab_usage_truncated, 1, 1, 3)
        val mehr = res.getQuantityString(R.plurals.vocab_usage_truncated, 2, 2, 3)
        assertTrue(eins, eins.startsWith("1 von 3 Begriffen wird mitgeschickt:"))
        assertTrue(mehr, mehr.startsWith("2 von 3 Begriffen werden mitgeschickt:"))
        assertEquals(emptyMap<String, Int>(), zuLang(mapOf("one" to eins, "other" to mehr), 200))
    }

    @Test fun tutorialSeitenHabenHoechstens180Zeichen() {
        val seiten = texte.filterKeys { it.matches(Regex("tutorial_(pro_)?p\\d+_body")) }
        assertEquals("4 Einsteiger- und 6 Pro-Widgets-Seiten", 10, seiten.size)
        assertEquals(emptyMap<String, Int>(), zuLang(seiten, 180))
    }

    @Test fun kurztexteUnterIllustrationenUndImAssistentenHabenHoechstens160Zeichen() {
        val kurz = texte.filterKeys {
            it.matches(Regex("help_(s\\d|widgets)_intro|help_s5_body|welcome_body|setup_s\\d[ab]?_body|a11y_description"))
        }
        assertEquals(emptyMap<String, Int>(), zuLang(kurz, 160))
    }

    @Test fun jedeIllustrationHatEinenKurzenBildtext() {
        // ill_tutorial_x -> tutorial_img_x (Bestand), sonst ill_x -> img_x.
        val illustrationen = R.drawable::class.java.fields.map { it.name }.filter { it.startsWith("ill_") }
        assertEquals(22, illustrationen.size)
        val bildtexte = illustrationen.associateWith { ill ->
            val name = if (ill.startsWith("ill_tutorial_")) ill.replace("ill_tutorial_", "tutorial_img_") else ill.replace("ill_", "img_")
            texte[name]
        }
        assertEquals("ohne Bildtext", emptyList<String>(), bildtexte.filterValues { it == null }.keys.toList())
        // Die neuen Bildtexte (img_*) sind kurz; tutorial_img_share/_result sind aeltere, laengere Texte.
        assertEquals(emptyMap<String, Int>(), zuLang(texte.filterKeys { it.startsWith("img_") }, 120))
    }
}
