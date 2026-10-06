package com.chris.whisperloom.whisper

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.io.File

/**
 * Kann dieses Geraet offline erkennen, und mit wie vielen Threads? Die reinen Funktionen
 * ([parseCpuInfo], [supportsWhisper], [performanceCoreCount], [fitsDevice]) sind JVM-testbar.
 */
object OfflineSupport {

    /**
     * libwhisperloom.so ist mit `-march=armv8.2-a+fp16+dotprod` gebaut: ohne FP16-Vektorarithmetik
     * (fphp) und DotProd (asimddp) gaebe es SIGILL. Betroffen sind nur Armv8.0-SoCs (Cortex-A53/A72,
     * Snapdragon 835 …) — fuer eine Diktier-App mit `small` ohnehin zu langsam.
     */
    val REQUIRED_CPU_FEATURES: Set<String> = setOf("fphp", "asimddp")

    /** CPU-Flags aus dem Text von /proc/cpuinfo ("Features : fp asimd …"), ueber alle Kerne vereinigt. */
    fun parseCpuInfo(text: String): Set<String> =
        text.lineSequence()
            .filter { it.substringBefore(':').trim().equals("Features", ignoreCase = true) }
            .flatMap { it.substringAfter(':').trim().split(Regex("\\s+")).asSequence() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun supportsWhisper(features: Set<String>): Boolean = features.containsAll(REQUIRED_CPU_FEATURES)

    /** arm64-Geraet mit passender CPU; false -> nur Online-Modus anbieten. */
    val isSupported: Boolean by lazy {
        Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a" &&
            supportsWhisper(parseCpuInfo(readTextOrEmpty(File("/proc/cpuinfo"))))
    }

    /**
     * Performance-Kerne = Kerne, deren Maximaltakt ueber dem kleinsten liegt (big.LITTLE);
     * sind alle gleich schnell, zaehlen alle. Leere Liste -> 0.
     */
    fun performanceCoreCount(maxFreqKhz: List<Long>): Int {
        if (maxFreqKhz.isEmpty()) return 0
        val min = maxFreqKhz.min()
        val faster = maxFreqKhz.count { it > min }
        return if (faster == 0) maxFreqKhz.size else faster
    }

    /** whisper-Threads: Performance-Kerne, mindestens 2, hoechstens 6 (mehr als Big-Cores bremst). */
    fun threadCount(): Int {
        val cpus = Runtime.getRuntime().availableProcessors()
        val freqs = (0 until cpus).mapNotNull {
            readTextOrEmpty(File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")).trim().toLongOrNull()
        }
        val cores = if (freqs.size == cpus) performanceCoreCount(freqs) else cpus
        return cores.coerceIn(2, 6)
    }

    fun totalRamBytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.totalMem
    }

    /**
     * Unter 3 GB Geraetespeicher wird Offline gar nicht angeboten (UX-Spec §2 Schritt 1): small
     * (~430 MB) laeuft im IME-Prozess neben der Ziel-App — auf 2-GB-Geraeten schiesst der LMK ihn ab.
     */
    const val MIN_DEVICE_RAM_BYTES = 3L shl 30

    /** 10 % Toleranz: ein "6-GB-Geraet" meldet als totalMem meist nur ~5,6 GiB (Kernel-Reserven). */
    fun fitsDevice(totalRamBytes: Long, model: OfflineModel): Boolean =
        totalRamBytes >= model.minDeviceRamBytes / 10 * 9

    /** Geraet hat genug RAM fuer Offline ueberhaupt (gleiche 10-%-Toleranz wie [fitsDevice]). */
    fun deviceFits(totalRamBytes: Long): Boolean = totalRamBytes >= MIN_DEVICE_RAM_BYTES / 10 * 9

    fun fitsDevice(context: Context, model: OfflineModel): Boolean = fitsDevice(totalRamBytes(context), model)

    /** Vor grossen Downloads: laeuft das gerade ueber mobile Daten? (UX-Spec Dialog D2) */
    fun isMeteredNetwork(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

    private fun readTextOrEmpty(f: File): String = runCatching { f.readText() }.getOrDefault("")
}
