package com.chris.whisperloom.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer

/**
 * Ablage und Import der Profil-Fotos. Pixel (runde Ecken transparent) prueft nur das Geraet:
 * Robolectric zeichnet hier im LEGACY-Grafikmodus nicht wirklich (NATIVE scheitert auf aarch64).
 */
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

    @Test fun ladenFindetDieGespeicherteDatei() {
        val f = WidgetPhoto.file(ctx, "b.png")!!.apply { parentFile!!.mkdirs() }
        f.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertNotNull(WidgetPhoto.load(ctx, "b.png"))
        f.delete()
    }

    @Test fun ohneDateiGibtEsKeinBildStattEinesFehlers() {
        // Robolectric (LEGACY) erfaende sonst ein Bild auch fuer eine fehlende Datei — das Widget
        // muss aber aufs Mikrofon zurueckfallen koennen.
        assertNull(WidgetPhoto.load(ctx, "fehlt.png"))
        assertNull(WidgetPhoto.load(ctx, "../whisperloom.xml"))
        File(ctx.filesDir, "${WidgetPhoto.DIR}/ordner.png").mkdirs()
        assertNull("Ein Ordner ist kein Bild", WidgetPhoto.load(ctx, "ordner.png"))
    }

    // --- Import aus dem Photo Picker ------------------------------------------------

    @Test fun abtastrateHaeltDieKurzeSeiteUeberDerZielgroesse() {
        assertEquals(8, WidgetPhoto.sampleSize(4000, 3000, 192)) // 3000/8 = 375, /16 = 187 waere zu klein
        assertEquals(8, WidgetPhoto.sampleSize(3000, 4000, 192))
        assertEquals(2, WidgetPhoto.sampleSize(384, 1000, 192))
        assertEquals(1, WidgetPhoto.sampleSize(383, 1000, 192))
        assertEquals("Kleine Bilder werden nie weiter verkleinert", 1, WidgetPhoto.sampleSize(100, 50, 192))
    }

    @Test fun zuschnittIstDasMittlereQuadrat() {
        assertEquals(WidgetPhoto.Square(left = 500, top = 0, size = 3000), WidgetPhoto.cropRect(4000, 3000))
        assertEquals(WidgetPhoto.Square(left = 0, top = 500, size = 3000), WidgetPhoto.cropRect(3000, 4000))
        assertEquals(WidgetPhoto.Square(left = 0, top = 0, size = 192), WidgetPhoto.cropRect(192, 192))
        assertEquals(WidgetPhoto.Square(left = 1, top = 0, size = 3), WidgetPhoto.cropRect(5, 3))
    }

    @Test fun exifDrehungInGrad() {
        assertEquals(90, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_ROTATE_90)) // 6
        assertEquals(180, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(90, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(270, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_TRANSVERSE))
        assertEquals(180, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(0, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(0, WidgetPhoto.rotationFor(ExifInterface.ORIENTATION_UNDEFINED))
    }

    /** Ein echtes PNG ausserhalb des Foto-Ordners, wie es der Picker als Uri liefert. */
    private fun bild(width: Int, height: Int): Uri {
        val f = File(ctx.cacheDir, "picker-$width-$height.png")
        f.outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    private fun fotoOrdner(): List<String> = File(ctx.filesDir, WidgetPhoto.DIR).list()?.toList().orEmpty()

    @Test fun ablegenErzeugtEinSymbolImFotoOrdner() {
        // Ab Android 9 dekodiert ImageDecoder. Robolectric (LEGACY) kann ihn nur mit ByteBuffer-
        // Quellen, nicht mit Uri/Datei — deshalb hier Dekodieren und Ablegen getrennt.
        val bytes = File(bild(1600, 1200).path!!).readBytes()
        val bmp = WidgetPhoto.decodeSource(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
        assertEquals("Verkleinert dekodiert (1200/4 >= 192)", 400 to 300, bmp.width to bmp.height)

        val name = WidgetPhoto.store(ctx, "p1") { bmp }
        assertNotNull(name)
        assertTrue("Dateiname beginnt mit der Profil-Id: $name", name!!.startsWith("p1-"))
        assertTrue(name.endsWith(".png"))
        assertTrue(WidgetPhoto.isValidName(name))
        assertEquals(listOf(name), fotoOrdner())
        assertNotNull("Das Widget kann es laden", WidgetPhoto.load(ctx, name))
    }

    @Test fun ablegenOhneBildHinterlaesstNichts() {
        assertNull(WidgetPhoto.store(ctx, "p1") { null })
        assertNull(WidgetPhoto.store(ctx, "p1") { error("kaputt") })
        assertTrue(fotoOrdner().isEmpty())
    }

    @Config(sdk = [27])
    @Test fun importUnterAndroid9UeberBitmapFactory() {
        val name = WidgetPhoto.import(ctx, bild(300, 400), "p2")
        assertNotNull(name)
        assertTrue(name!!.startsWith("p2-"))
        assertEquals(listOf(name), fotoOrdner())
        assertNotNull(WidgetPhoto.load(ctx, name))
    }

    @Test fun unlesbaresBildErgibtNullUndKeineDatei() {
        assertNull(WidgetPhoto.import(ctx, Uri.parse("content://com.example.gibtsnicht/bild/1"), "p3"))
        assertNull(WidgetPhoto.import(ctx, Uri.fromFile(File(ctx.cacheDir, "fehlt.png")), "p3"))
        val keinBild = File(ctx.cacheDir, "text.png").apply { writeText("kein Bild") }
        assertNull(WidgetPhoto.import(ctx, Uri.fromFile(keinBild), "p3"))
        assertTrue("Kein halbes Foto bleibt liegen: ${fotoOrdner()}", fotoOrdner().isEmpty())
    }

    @Test fun loeschenVerlaesstNieDenOrdner() {
        val nachbar = File(ctx.filesDir, "nachbar.txt").apply { writeText("bleibt") }
        File(ctx.filesDir, WidgetPhoto.DIR).mkdirs()
        WidgetPhoto.delete(ctx, "../nachbar.txt")
        assertTrue(nachbar.isFile)
    }
}
