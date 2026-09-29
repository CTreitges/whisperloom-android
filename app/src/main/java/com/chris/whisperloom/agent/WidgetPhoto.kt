package com.chris.whisperloom.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import java.io.File

/**
 * Eigene Fotos fuer Widget-Profile, abgelegt unter `filesDir/widget_icons/` (nicht im Cache:
 * das System raeumt ihn bei Platzmangel weg, das Widget zeigte dann ein Loch). Ein Profil
 * speichert nur den Dateinamen ([ProfileIcon.Photo]).
 */
object WidgetPhoto {

    const val DIR = "widget_icons"

    /** Kantenlaenge des gespeicherten Kreises: 192 x 192 x 4 Byte, rund 147 KB je Foto. */
    const val SIZE = 192

    /**
     * Nur ein schlichter Dateiname, kein Pfad: ein kaputter Eintrag darf nie auf eine Datei
     * ausserhalb des Ordners zeigen — geloescht wird ueber genau diesen Namen.
     */
    fun isValidName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

    /** Die Datei zum Namen, null bei unbrauchbarem Namen. Existenz wird nicht geprueft. */
    fun file(ctx: Context, name: String): File? =
        if (isValidName(name)) File(File(ctx.filesDir, DIR), name) else null

    /**
     * Das Foto zum Zeichnen; null, wenn die Datei fehlt oder nicht lesbar ist — das Widget zeigt
     * dann das Standardsymbol statt eines Lochs. Die Datei ist beim Import schon rund und klein.
     */
    fun load(ctx: Context, name: String): Bitmap? {
        val f = file(ctx, name)?.takeIf { it.isFile } ?: return null
        return runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
    }

    fun delete(ctx: Context, name: String) {
        file(ctx, name)?.let { runCatching { it.delete() } }
    }

    /**
     * Ein Bild aus dem Photo Picker uebernehmen: mittleres Quadrat, rund, [SIZE] Pixel, als PNG
     * unter `<profileId>-<zeit>.png`. Sofort im Picker-Callback aufrufen — die Leseerlaubnis
     * fuer [uri] gilt nur voruebergehend. Keine Berechtigung, kein FileProvider noetig.
     *
     * @return der Dateiname fuer [ProfileIcon.Photo], null wenn das Bild nicht lesbar war
     *   (etwa HEIC unter Android 9) — die Oberflaeche meldet das, abgestuerzt wird nie.
     */
    @WorkerThread
    fun import(ctx: Context, uri: Uri, profileId: String): String? = store(ctx, profileId) { decodeUri(ctx, uri) }

    /**
     * Ein dekodiertes Bild rund zuschneiden und ablegen. [decode] darf werfen oder null liefern;
     * dann gibt es null und keine halbe Datei.
     */
    @VisibleForTesting
    internal fun store(ctx: Context, profileId: String, decode: () -> Bitmap?): String? {
        val name = "$profileId-${System.currentTimeMillis()}.png"
        val target = file(ctx, name) ?: return null
        val saved = runCatching {
            val icon = roundIcon(decode() ?: return null)
            target.parentFile?.mkdirs()
            target.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.getOrDefault(false)
        if (saved) return name
        runCatching { target.delete() }
        return null
    }

    /**
     * Verkleinert dekodieren, damit ein 50-MP-Foto nicht den Speicher sprengt. Ab Android 9
     * dreht und liest [ImageDecoder] selbst (EXIF, HEIF); darunter BitmapFactory plus EXIF von Hand.
     */
    private fun decodeUri(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return decodeSource(ImageDecoder.createSource(cr, uri))
        // Nur die Masse: decodeStream liefert dabei immer null, ausgewertet werden die Options.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, SIZE) }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val exif = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = rotationFor(exif)
        if (degrees == 0) return bmp
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** SOFTWARE, weil sich eine HARDWARE-Bitmap weder auf einen Canvas zeichnen noch speichern liesse. */
    @RequiresApi(Build.VERSION_CODES.P)
    @VisibleForTesting
    internal fun decodeSource(source: ImageDecoder.Source): Bitmap =
        ImageDecoder.decodeBitmap(source) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val sample = sampleSize(w, h, SIZE)
            d.setTargetSize(w / sample, h / sample)
        }

    /** Mittleres Quadrat, auf [SIZE] skaliert und rund ausgeschnitten (Ecken transparent). */
    private fun roundIcon(src: Bitmap): Bitmap {
        val sq = cropRect(src.width, src.height)
        val scale = SIZE.toFloat() / sq.size
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setTranslate(-sq.left.toFloat(), -sq.top.toFloat())
                postScale(scale, scale)
            })
        }
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        Canvas(out).drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, paint)
        return out
    }

    /**
     * Groesste Zweierpotenz, bei der die kurze Seite noch mindestens [target] Pixel hat —
     * BitmapFactory rundet `inSampleSize` ohnehin auf Zweierpotenzen ab.
     */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        val shortSide = minOf(width, height)
        var sample = 1
        while (shortSide / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Das groesste mittige Quadrat eines Bildes. */
    fun cropRect(width: Int, height: Int): Square {
        val size = minOf(width, height)
        return Square(left = (width - size) / 2, top = (height - size) / 2, size = size)
    }

    /**
     * Drehung in Grad fuer einen EXIF-Orientierungswert. Gespiegelte Varianten zaehlen nur mit
     * ihrer Drehung — im runden Symbol faellt eine Spiegelung nicht auf.
     */
    fun rotationFor(exifOrientation: Int): Int = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    data class Square(val left: Int, val top: Int, val size: Int)
}
