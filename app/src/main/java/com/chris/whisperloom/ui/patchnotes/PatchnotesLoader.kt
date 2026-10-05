package com.chris.whisperloom.ui.patchnotes

import android.content.res.AssetManager

/**
 * Liest die Patchnotes aus den App-Assets. Gradle kopiert sie beim Build hinein
 * (Task `copy<Variant>PatchnotesAssets`): `patchnotes/CHANGELOG.md` und `patchnotes/highlights/<versionCode>.txt`.
 * Eine IOException geht an den Aufrufer.
 */
object PatchnotesLoader {

    private const val DIR = "patchnotes"

    fun load(assets: AssetManager): List<Release> {
        val changelog = read(assets, "$DIR/CHANGELOG.md")
        // assets.list sortiert lexikografisch (10.txt vor 4.txt) — merge braucht die versionCode-Reihenfolge.
        val files = assets.list("$DIR/highlights").orEmpty()
            .filter { it.endsWith(".txt") }
            .sortedBy { it.removeSuffix(".txt").toIntOrNull() ?: 0 }
            .mapNotNull { PatchnotesParser.parseHighlights(read(assets, "$DIR/highlights/$it")) }
        return PatchnotesParser.merge(PatchnotesParser.parseChangelog(changelog), files)
    }

    private fun read(assets: AssetManager, path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
