package com.chris.whisperloom.whisper

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Download fehlgeschlagen. [retryable] = true: ein neuer Versuch setzt an der liegen gebliebenen
 * Teildatei an (Netz-/Serverfehler). false: die Teildatei wurde verworfen (Pruefsumme, Groesse,
 * HTTP-4xx) oder der Nutzer muss erst etwas tun (Speicherplatz).
 */
class DownloadException(
    message: String,
    val retryable: Boolean,
    val kind: Kind = Kind.OTHER,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** Grobe Einordnung, damit der Dienst die passenden Texte (err_*) zeigen kann. */
    enum class Kind { NETWORK, CHECKSUM, STORAGE, OTHER }
}

/**
 * Laedt ein Modell von seiner [OfflineModel.url] per HttpURLConnection in den [ModelStore] — reines
 * Kotlin, ohne Android:
 *  - Teildatei `<datei>.part`; Fortsetzung per `Range: bytes=n-` (206 + Content-Range), antwortet der
 *    Server mit 200, wird von vorn begonnen
 *  - SHA-256 streamend beim Schreiben; vorhandene Teilbytes werden vorher eingerechnet
 *  - fertig nur bei Groesse == Katalog UND Pruefsumme == Katalog, dann atomares Umbenennen
 *  - Platzpruefung vorab: Restgroesse plus [OfflineModel.extraDiskBytes] (Cache eines Textmodells)
 *  - Netzfehler: bis zu [retries] Wiederholungen mit verdoppelndem Backoff, jeweils fortgesetzt
 *  - [ProgressListener] hoechstens alle [progressIntervalMs]
 */
class ModelDownloader(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val retries: Int = 3,
    private val backoffMs: Long = 2_000,
    private val progressIntervalMs: Long = 250,
    /** Freier Platz im Zielordner — injizierbar fuer Tests. */
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    fun interface ProgressListener {
        /** [bytesPerSec] = Rate seit dem letzten Aufruf (0 beim Abschluss-Aufruf). */
        fun onProgress(bytes: Long, total: Long, bytesPerSec: Long)
    }

    /**
     * Blockierend — aus einem Hintergrund-Thread aufrufen.
     * @return true = Datei vollstaendig und verifiziert; false = ueber [isCancelled] abgebrochen,
     *   die Teildatei bleibt fuer eine spaetere Fortsetzung liegen.
     * @throws DownloadException bei Netz-, Server-, Speicher- oder Pruefsummenfehlern
     */
    fun download(
        model: OfflineModel,
        store: ModelStore,
        isCancelled: () -> Boolean = { false },
        onProgress: ProgressListener = ProgressListener { _, _, _ -> },
    ): Boolean {
        if (store.isInstalled(model)) return true
        if (!store.ensureDir()) throw DownloadException(MSG_STORAGE, retryable = false, kind = DownloadException.Kind.STORAGE)
        val part = store.partFile(model)
        var attempt = 0
        while (true) {
            try {
                return tryOnce(model, store, isCancelled, onProgress)
            } catch (e: DownloadException) {
                if (!e.retryable && e.kind != DownloadException.Kind.STORAGE) part.delete()
                throw e
            } catch (e: IOException) {
                // Abbruch waehrend read() haengt (Server liefert nicht): erst der Read-Timeout bringt uns
                // hierher — dann ist das ein Abbruch, kein Netzfehler.
                if (isCancelled()) return false
                if (isOutOfSpace(e)) {
                    throw DownloadException(MSG_STORAGE, retryable = false, kind = DownloadException.Kind.STORAGE, cause = e)
                }
                if (attempt >= retries) {
                    throw DownloadException(MSG_NET, retryable = true, kind = DownloadException.Kind.NETWORK, cause = e)
                }
                Thread.sleep(backoffMs shl attempt)
                attempt++
                if (isCancelled()) return false
            }
        }
    }

    private fun tryOnce(
        model: OfflineModel,
        store: ModelStore,
        isCancelled: () -> Boolean,
        onProgress: ProgressListener,
    ): Boolean {
        val part = store.partFile(model)
        val target = store.file(model)
        var existing = if (part.isFile) part.length() else 0L
        if (existing > model.bytes) {
            part.delete() // groesser als das Ziel: kann nicht zu dieser Datei gehoeren
            existing = 0L
        }
        if (freeSpace(store.dir) < model.bytes + model.extraDiskBytes - existing) {
            throw DownloadException(MSG_STORAGE, retryable = false, kind = DownloadException.Kind.STORAGE)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        if (existing > 0) FileInputStream(part).use { feed(it, digest) }

        if (existing < model.bytes) {
            val conn = open(model, existing)
            try {
                var resume = existing > 0
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        val total = contentRangeTotal(conn.getHeaderField("Content-Range"))
                        if (total != model.bytes) throw DownloadException(MSG_SIZE.format(total), retryable = false)
                    }
                    HttpURLConnection.HTTP_OK -> {
                        val length = conn.contentLengthLong
                        if (length > 0 && length != model.bytes) throw DownloadException(MSG_SIZE.format(length), retryable = false)
                        if (resume) { // Server kann kein Range -> von vorn
                            digest.reset()
                            existing = 0L
                            resume = false
                        }
                    }
                    // Teildatei passt nicht zum Server: verwerfen, Neustart ueber den Retry-Pfad
                    416 -> { part.delete(); throw IOException("HTTP 416") }
                    429, in 500..599 -> throw IOException("HTTP $code")
                    else -> throw DownloadException(MSG_HTTP.format(code), retryable = false)
                }
                if (!copy(conn, part, resume, existing, model.bytes, digest, isCancelled, onProgress)) return false
            } finally {
                conn.disconnect()
            }
        }
        finish(part, target, model, digest)
        return true
    }

    /** @return false = abgebrochen (Teildatei bleibt) */
    private fun copy(
        conn: HttpURLConnection,
        part: File,
        append: Boolean,
        startBytes: Long,
        total: Long,
        digest: MessageDigest,
        isCancelled: () -> Boolean,
        onProgress: ProgressListener,
    ): Boolean {
        var bytes = startBytes
        var lastAt = System.nanoTime()
        var lastBytes = bytes
        val buf = ByteArray(64 * 1024)
        FileOutputStream(part, append).use { out ->
            conn.inputStream.use { input ->
                while (true) {
                    if (isCancelled()) {
                        conn.disconnect() // sofort weg, sonst liest close() den Rest nach
                        return false
                    }
                    val n = input.read(buf)
                    if (n < 0) break
                    if (bytes + n > total) throw DownloadException(MSG_SIZE.format(bytes + n), retryable = false)
                    out.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    bytes += n
                    val now = System.nanoTime()
                    val elapsedMs = (now - lastAt) / 1_000_000
                    if (elapsedMs >= progressIntervalMs) {
                        onProgress.onProgress(bytes, total, (bytes - lastBytes) * 1000 / elapsedMs)
                        lastAt = now
                        lastBytes = bytes
                    }
                }
            }
        }
        if (bytes < total) throw IOException("Verbindung endete vorzeitig ($bytes von $total Bytes)")
        onProgress.onProgress(bytes, total, 0)
        return true
    }

    private fun finish(part: File, target: File, model: OfflineModel, digest: MessageDigest) {
        if (part.length() != model.bytes) throw DownloadException(MSG_SIZE.format(part.length()), retryable = false)
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(model.sha256, ignoreCase = true)) {
            throw DownloadException(MSG_CHECKSUM, retryable = false, kind = DownloadException.Kind.CHECKSUM)
        }
        target.delete()
        if (!part.renameTo(target)) {
            throw DownloadException(MSG_RENAME, retryable = false, kind = DownloadException.Kind.STORAGE)
        }
    }

    private fun open(model: OfflineModel, existing: Long): HttpURLConnection {
        // Jedes Mal die Katalog-URL: der CDN-Link hinter der Weiterleitung ist signiert und laeuft ab.
        val conn = URL(model.url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true // huggingface.co -> 302 -> CDN
        conn.setRequestProperty("Accept-Encoding", "identity") // keine transparente Kompression, Content-Length soll stimmen
        if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
        return conn
    }

    private fun feed(input: InputStream, digest: MessageDigest) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return
            digest.update(buf, 0, n)
        }
    }

    private fun isOutOfSpace(e: IOException): Boolean =
        e.message?.let { it.contains("ENOSPC") || it.contains("No space left", ignoreCase = true) } ?: false

    companion object {
        const val MSG_NET = "Netzwerkfehler beim Laden"
        const val MSG_CHECKSUM = "Datei beschädigt — erneut laden"
        const val MSG_STORAGE = "Nicht genug Speicherplatz"
        const val MSG_SIZE = "Server liefert eine andere Dateigröße (%d Bytes) als erwartet"
        const val MSG_HTTP = "Server antwortet mit HTTP %d"
        const val MSG_RENAME = "Modell-Datei konnte nicht gespeichert werden"

        /** "bytes 1000-189999/190000" -> 190000; fehlt oder "*" -> -1. */
        fun contentRangeTotal(header: String?): Long =
            header?.substringAfter('/', "")?.trim()?.toLongOrNull() ?: -1L
    }
}
