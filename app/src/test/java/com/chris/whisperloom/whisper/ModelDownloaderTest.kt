package com.chris.whisperloom.whisper

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Tests gegen einen lokalen JDK-HttpServer, der Range-Requests (206 + Content-Range) kann und sich
 * fuer die Fehlerfaelle verstellen laesst. Reines JVM ohne Robolectric — der Downloader ist Android-frei.
 */
class ModelDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val data = ByteArray(300_000) { (it * 31 + it / 7).toByte() }

    private lateinit var server: HttpServer
    private lateinit var store: ModelStore

    /** Laedt von seiner eigenen URL — der Pfad weicht bewusst vom Dateinamen ab (wie die gepinnten HF-URLs). */
    private lateinit var model: OfflineModel

    // Server-Verhalten je Test
    private var rangeSupported = true
    private var slowChunkMs = 0L
    private var holdMs = 0L // Server "haengt": so lange keine Antwort-Header
    private var statusOverride = 0
    private var body: ByteArray = data
    private var truncateFirstResponseAt = -1 // erste Antwort nach so vielen Bytes beenden (Netzabbruch)
    private val ranges = mutableListOf<String?>() // Range-Header je Request
    private val paths = mutableListOf<String>() // angefragte Pfade
    private val statuses = mutableListOf<Int>()

    @Before fun setUp() {
        store = ModelStore(File(tmp.root, "models"))
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/whisper/resolve/main/ggml-test.bin") { ex -> handle(ex) }
        server.createContext("/litert/resolve/0123abc/text-test.litertlm") { ex -> handle(ex) }
        server.start()
        model = OfflineModel(
            id = "test", label = "Test", fileName = "ggml-test.bin",
            url = "http://127.0.0.1:${server.address.port}/whisper/resolve/main/ggml-test.bin",
            bytes = data.size.toLong(), sha256 = sha256(data), approxRamBytes = 0, minDeviceRamBytes = 0,
        )
    }

    @After fun tearDown() {
        server.stop(0)
    }

    private fun handle(ex: HttpExchange) {
        val range = ex.requestHeaders.getFirst("Range")
        val first = synchronized(ranges) { ranges.add(range); ranges.size == 1 }
        synchronized(paths) { paths.add(ex.requestURI.path) }
        runCatching {
            if (holdMs > 0) Thread.sleep(holdMs)
            if (statusOverride != 0) {
                synchronized(statuses) { statuses.add(statusOverride) }
                ex.sendResponseHeaders(statusOverride, -1)
                ex.close()
                return
            }
            var from = 0
            var status = 200
            if (rangeSupported && range != null && range.startsWith("bytes=")) {
                from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                if (from >= body.size) {
                    ex.sendResponseHeaders(416, -1)
                    ex.close()
                    return
                }
                status = 206
                ex.responseHeaders.add("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
            }
            synchronized(statuses) { statuses.add(status) }
            val slice = body.copyOfRange(from, body.size)
            val cut = if (first && truncateFirstResponseAt >= 0) truncateFirstResponseAt else slice.size
            // Abgeschnittene Antwort als chunked (Laenge 0): der Client bekommt ein sauberes, aber zu kurzes Ende.
            ex.sendResponseHeaders(status, if (cut < slice.size) 0L else slice.size.toLong())
            ex.responseBody.use { out ->
                var off = 0
                while (off < cut) {
                    val n = minOf(16 * 1024, cut - off)
                    out.write(slice, off, n)
                    out.flush()
                    off += n
                    if (slowChunkMs > 0) Thread.sleep(slowChunkMs)
                }
            }
        }
    }

    private fun seenRanges() = synchronized(ranges) { ranges.toList() }
    private fun seenStatuses() = synchronized(statuses) { statuses.toList() }

    private fun downloader(retries: Int = 3, freeSpace: (File) -> Long = { it.usableSpace }, readTimeoutMs: Int = 30_000) = ModelDownloader(
        readTimeoutMs = readTimeoutMs,
        retries = retries,
        backoffMs = 5,
        progressIntervalMs = 50,
        freeSpace = freeSpace,
    )

    @Test fun kompletterDownloadWirdVerifiziertUndUmbenannt() {
        val progress = mutableListOf<Long>()
        val done = downloader().download(model, store) { bytes, total, _ ->
            assertEquals(model.bytes, total)
            progress.add(bytes)
        }
        assertTrue(done)
        assertTrue(store.isInstalled(model))
        assertArrayEquals(data, store.file(model).readBytes())
        assertFalse(store.partFile(model).exists())
        assertEquals(model.bytes, progress.last())
        assertEquals(progress, progress.sorted()) // monoton
        assertEquals(listOf<String?>(null), seenRanges()) // erster Versuch ohne Range
    }

    @Test fun cancelMittendrinLaesstTeildateiStehen() {
        // Review TST-4: Abbruch an den Fortschritt binden, nicht an die Wanduhr (kalter JIT/CI-Last).
        // Der erste Fortschritts-Aufruf kommt nach >= 50 ms mitten im Strom (19 Stuecke x 20 ms Server-Pause).
        slowChunkMs = 20
        val seen = AtomicLong()
        val done = downloader().download(model, store, isCancelled = { seen.get() > 0 }) { bytes, _, _ -> seen.set(bytes) }
        assertFalse(done)
        val part = store.partFile(model)
        assertTrue(part.exists())
        assertTrue("0 < ${part.length()} < ${data.size}", part.length() in 1 until data.size.toLong())
        assertFalse(store.file(model).exists())
        assertFalse(store.isInstalled(model))
    }

    @Test fun cancelImBackoffStartetKeinenWeiterenVersuch() {
        statusOverride = 503
        val done = downloader().download(model, store, isCancelled = { seenStatuses().isNotEmpty() })
        assertFalse(done)
        assertEquals(listOf(503), seenStatuses()) // kein zweiter Request nach dem Abbruch
        assertFalse(store.partFile(model).exists())
    }

    @Test fun cancelBeiHaengenderVerbindungIstEinAbbruchKeinNetzfehler() {
        // Review NAT-2: Nutzer tippt "Abbrechen", waehrend der Server nicht liefert. Erst der
        // Read-Timeout weckt uns — das muss als Abbruch (false) enden, nicht als DownloadException,
        // sonst zeigt der Dienst "Fehlgeschlagen" und behaelt die Teildatei.
        holdMs = 3_000
        val start = System.nanoTime()
        val done = downloader(readTimeoutMs = 200).download(model, store, isCancelled = { true })
        assertFalse(done)
        assertTrue("kam nach ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start < 2_500_000_000L)
    }

    @Test fun resumeSetztMitRangeFortUndPrueftDieGesamtsumme() {
        // Teildatei mit den ersten 100 000 korrekten Bytes (Zustand nach einem Abbruch)
        store.ensureDir()
        store.partFile(model).writeBytes(data.copyOfRange(0, 100_000))
        val progress = mutableListOf<Long>()
        assertTrue(downloader().download(model, store) { bytes, _, _ -> progress.add(bytes) })
        assertEquals(listOf("bytes=100000-"), seenRanges())
        assertEquals(listOf(206), seenStatuses())
        assertTrue(progress.first() >= 100_000L)
        assertArrayEquals(data, store.file(model).readBytes())
        assertTrue(store.isInstalled(model))
    }

    @Test fun serverOhneRangeStartetVonVorn() {
        rangeSupported = false
        store.ensureDir()
        store.partFile(model).writeBytes(data.copyOfRange(0, 50_000))
        assertTrue(downloader().download(model, store))
        assertEquals(listOf("bytes=50000-"), seenRanges()) // Range wurde angefragt ...
        assertEquals(listOf(200), seenStatuses()) // ... der Server kann es nicht -> von vorn
        assertArrayEquals(data, store.file(model).readBytes())
        assertTrue(store.isInstalled(model))
    }

    @Test fun kaputteTeildateiFaelltBeiDerPruefsummeAufUndWirdVerworfen() {
        store.ensureDir()
        store.partFile(model).writeBytes(ByteArray(10_000) { 9 })
        val e = assertThrows(DownloadException::class.java) { downloader().download(model, store) }
        assertEquals(DownloadException.Kind.CHECKSUM, e.kind)
        assertFalse(e.retryable)
        assertFalse(store.partFile(model).exists())
        assertFalse(store.file(model).exists())
        // naechster Versuch laedt von vorn und klappt
        assertTrue(downloader().download(model, store))
        assertEquals(listOf("bytes=10000-", null), seenRanges())
        assertTrue(store.isInstalled(model))
    }

    @Test fun checksumMismatchLoeschtDateiUndMeldetFehler() {
        val wrong = model.copy(sha256 = "0".repeat(64))
        val e = assertThrows(DownloadException::class.java) { downloader().download(wrong, store) }
        assertEquals(ModelDownloader.MSG_CHECKSUM, e.message)
        assertEquals(DownloadException.Kind.CHECKSUM, e.kind)
        assertFalse(e.retryable)
        assertFalse(store.partFile(wrong).exists())
        assertFalse(store.file(wrong).exists())
    }

    @Test fun netzabbruchWirdWiederholtUndFortgesetzt() {
        truncateFirstResponseAt = 120_000
        assertTrue(downloader(retries = 2).download(model, store))
        assertEquals(listOf(null, "bytes=120000-"), seenRanges())
        assertEquals(listOf(200, 206), seenStatuses())
        assertArrayEquals(data, store.file(model).readBytes())
    }

    @Test fun http503NachAllenVersuchenRetryable() {
        statusOverride = 503
        val e = assertThrows(DownloadException::class.java) { downloader(retries = 2).download(model, store) }
        assertTrue(e.retryable)
        assertEquals(DownloadException.Kind.NETWORK, e.kind)
        assertEquals(ModelDownloader.MSG_NET, e.message)
        assertEquals(3, seenRanges().size) // 1 Versuch + 2 Wiederholungen
    }

    @Test fun http404NichtRetryable() {
        statusOverride = 404
        val e = assertThrows(DownloadException::class.java) { downloader().download(model, store) }
        assertFalse(e.retryable)
        assertEquals("Server antwortet mit HTTP 404", e.message)
        assertEquals(1, seenRanges().size)
    }

    @Test fun falscheDateigroesseVomServer() {
        body = data + ByteArray(10)
        val e = assertThrows(DownloadException::class.java) { downloader().download(model, store) }
        assertFalse(e.retryable)
        assertTrue(e.message!!, e.message!!.contains("Dateigröße"))
        assertFalse(store.partFile(model).exists())
    }

    @Test fun ohneSpeicherplatzKeinRequest() {
        val e = assertThrows(DownloadException::class.java) { downloader(freeSpace = { 0L }).download(model, store) }
        assertEquals(DownloadException.Kind.STORAGE, e.kind)
        assertFalse(e.retryable)
        assertEquals(ModelDownloader.MSG_STORAGE, e.message)
        assertTrue(seenRanges().isEmpty())
    }

    @Test fun textmodellLaedtVonSeinerUrlUnterSeinemDateinamen() {
        val text = model.copy(
            id = "text_test", fileName = "text-test.litertlm",
            url = "http://127.0.0.1:${server.address.port}/litert/resolve/0123abc/text-test.litertlm",
            extraDiskBytes = 1_000,
        )
        assertTrue(downloader().download(text, store))
        assertEquals(listOf("/litert/resolve/0123abc/text-test.litertlm"), synchronized(paths) { paths.toList() })
        assertArrayEquals(data, File(store.dir, "text-test.litertlm").readBytes())
        assertTrue(store.isInstalled(text))
        assertFalse(store.isInstalled(model))
    }

    @Test fun platzpruefungRechnetDenZusatzplatzMit() {
        val text = model.copy(extraDiskBytes = 1_000)
        val e = assertThrows(DownloadException::class.java) {
            downloader(freeSpace = { text.bytes + text.extraDiskBytes - 1 }).download(text, store)
        }
        assertEquals(DownloadException.Kind.STORAGE, e.kind)
        assertTrue(seenRanges().isEmpty())
        assertTrue(downloader(freeSpace = { text.bytes + text.extraDiskBytes }).download(text, store))
    }

    @Test fun bereitsInstalliertOhneRequest() {
        store.ensureDir()
        store.file(model).writeBytes(data)
        assertTrue(downloader().download(model, store))
        assertTrue(seenRanges().isEmpty())
    }

    @Test fun vollstaendigeTeildateiWirdNurNochVerifiziert() {
        // Absturz zwischen Schreiben und Umbenennen: alles da, nur der Name fehlt
        store.ensureDir()
        store.partFile(model).writeBytes(data)
        assertTrue(downloader().download(model, store))
        assertTrue(seenRanges().isEmpty())
        assertTrue(store.isInstalled(model))
    }

    @Test fun contentRangeTotal() {
        assertEquals(190_000L, ModelDownloader.contentRangeTotal("bytes 1000-189999/190000"))
        assertEquals(-1L, ModelDownloader.contentRangeTotal("bytes 0-99/*"))
        assertEquals(-1L, ModelDownloader.contentRangeTotal(null))
    }

    private companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
