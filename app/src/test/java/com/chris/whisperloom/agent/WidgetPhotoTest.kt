package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Ablage der Profil-Fotos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetPhotoTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun nurSchlichteDateinamenSindGueltig() {
        assertTrue(WidgetPhoto.isValidName("3f2a-1759140000.png"))
        assertFalse(WidgetPhoto.isValidName(""))
        assertFalse(WidgetPhoto.isValidName("."))
        assertFalse(WidgetPhoto.isValidName(".."))
        assertFalse(WidgetPhoto.isValidName("../whisperloom.xml"))
        assertFalse(WidgetPhoto.isValidName("a/b.png"))
        assertFalse(WidgetPhoto.isValidName("a\\b.png"))
        assertFalse(WidgetPhoto.isValidName("a\u0000.png"))
    }

    @Test fun fotosLiegenInFilesDirNichtImCache() {
        val f = WidgetPhoto.file(ctx, "a.png")!!
        assertEquals(File(ctx.filesDir, "widget_icons"), f.parentFile)
        assertNull(WidgetPhoto.file(ctx, "../a.png"))
    }

    @Test fun loeschenRaeumtDieDateiWeg() {
        val f = WidgetPhoto.file(ctx, "a.png")!!.apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        WidgetPhoto.delete(ctx, "a.png")
        assertFalse(f.exists())
        WidgetPhoto.delete(ctx, "a.png") // fehlt schon: kein Fehler
    }

    @Test fun loeschenVerlaesstNieDenOrdner() {
        val nachbar = File(ctx.filesDir, "nachbar.txt").apply { writeText("bleibt") }
        File(ctx.filesDir, WidgetPhoto.DIR).mkdirs()
        WidgetPhoto.delete(ctx, "../nachbar.txt")
        assertTrue(nachbar.isFile)
    }
}
