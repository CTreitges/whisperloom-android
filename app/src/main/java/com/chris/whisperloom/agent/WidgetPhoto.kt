package com.chris.whisperloom.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Eigene Fotos fuer Widget-Profile, abgelegt unter `filesDir/widget_icons/` (nicht im Cache:
 * das System raeumt ihn bei Platzmangel weg, das Widget zeigte dann ein Loch). Ein Profil
 * speichert nur den Dateinamen ([ProfileIcon.Photo]).
 */
object WidgetPhoto {

    const val DIR = "widget_icons"

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
}
