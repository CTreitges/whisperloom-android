package com.chris.whisperloom.overlay

import com.chris.whisperloom.RefineMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modus-Kreuz am schwebenden Knopf (docs/design/bubble-textfeld.md §2/§3) — Android-frei, JVM-testbar.
 *
 * Nach der Halte-Zeit liegen die Modi als Bogen um den Druckpunkt, dazu das ✕ (verwerfen). Gewaehlt
 * wird nach **Richtung**: das Ziel, dessen Richtung vom Druckpunkt aus der Fingerrichtung am naechsten
 * liegt, sobald der Finger die Einrast-Schwelle verlassen hat. So gilt dieselbe Auswahl, wenn der Bogen
 * am Rand gedreht, gestaucht oder verschoben ist, und das naeher liegende ✕ gewinnt nicht wegen seines
 * kuerzeren Radius. Alle Masse in dp, gerechnet in Bildschirm-Pixeln (y nach unten).
 */
object ModeCross {

    /** Druckpunkt → Mitte eines Modus. */
    const val RADIUS_DP = 112f

    /** Obergrenze, wenn der Bogen am Rand gestaucht wird (kurzer Daumenweg bleibt). */
    const val MAX_RADIUS_DP = 140f

    /** Druckpunkt → Mitte des ✕: naeher als die Modi, aber jenseits der Einrast-Schwelle. */
    const val DISCARD_RADIUS_DP = 96f

    const val PILL_W_DP = 72f
    const val PILL_H_DP = 44f
    const val DISCARD_DP = 52f

    /** Kleinster Mittenabstand benachbarter Modi bei gestauchtem Bogen (Pillenbreite + Luft). */
    const val MIN_GAP_DP = 80f

    /** Abstand jedes Ziels zum sicheren Bereich (ohne Status- und Navigationsleiste). */
    const val MARGIN_DP = 8f

    /** Fingerweg ab Druckpunkt, ab dem ein Ziel einrastet — wie die Tastatur (DictationGesture). */
    const val ARM_DP = 56f

    /** Eingerastet loest ein Ziel erst unter ARM − HYSTERESE (radial). */
    const val HYSTERESIS_DP = 12f

    /** Ein anderes Ziel gewinnt erst, wenn die Fingerrichtung um so viel naeher an ihm liegt. */
    const val ANGLE_HYSTERESIS_DEG = 6f

    /** Jedes Ziel liegt mindestens so weit vom Druckpunkt (sonst waere es nicht waehlbar). */
    const val MIN_REACH_DP = 80f

    /** Kleinster Richtungsabstand zweier Ziele vom Druckpunkt aus. */
    const val MIN_SEPARATION_DEG = 20f

    /** Deckkraft des Kreuzes, solange der Finger auf dem Knopf liegt: kaum sichtbar. */
    const val FAINT_ALPHA = 0.15f

    /** Fingerweg, ab dem das Kreuz voll deckend ist — vor der Einrast-Schwelle, damit man liest, bevor etwas einrastet. */
    const val FULL_ALPHA_DP = 32f

    /** Effektivpegel je 20-ms-Rahmen (≈ −36 dBFS), ab dem der Rahmen als Sprache zaehlt. */
    const val SPEECH_RMS = 0.015f

    /** So viel Sprache muss eine Aufnahme enthalten; kurze Knackser (Haptik, Finger) bleiben darunter. */
    const val SPEECH_MIN_MS = 200

    private const val FRAME_SAMPLES = 320 // 20 ms bei 16 kHz (AudioRecorder.SAMPLE_RATE)

    /** Nur in Ecken, wenn mit [MAX_RADIUS_DP] keine Lage passt. */
    const val CORNER_MAX_RADIUS_DP = 220f

    /** Spannweiten des Bogens, breiteste zuerst. */
    private val SPANS = listOf(180f, 150f, 120f, 90f)

    /** Lage des ✕ relativ zur Bogenmitte: gegenueber, sonst seitlich (am Rand). */
    private val DISCARD_OFFSETS = listOf(180f, 135f, 225f, 90f, 270f)

    sealed interface CrossTarget {
        data class Mode(val mode: RefineMode) : CrossTarget
        data object Discard : CrossTarget
    }

    data class Point(val x: Float, val y: Float)

    /** Sicherer Bereich in Bildschirm-Pixeln (ohne Status- und Navigationsleiste). */
    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int)

    data class Placed(val target: CrossTarget, val point: Point)

    /** Ziele in Bildschirm-Pixeln; Modi im Uhrzeigersinn in der Reihenfolge von `modes`, zuletzt das ✕. */
    data class Layout(val targets: List<Placed>) {
        fun pointOf(target: CrossTarget): Point? = targets.firstOrNull { it.target == target }?.point
    }

    /**
     * Lage der Ziele um den Druckpunkt ([cx], [cy]). Der Bogen oeffnet nach oben; passt das nicht, probiert
     * die Logik weitere Spannweiten, Richtungen und ✕-Lagen durch und nimmt die erste, bei der alle Ziele —
     * notfalls gemeinsam verschoben — in [area] liegen, sich nicht ueberlappen, weit genug vom Druckpunkt
     * liegen und in klar verschiedene Richtungen zeigen. Passt nichts, wird die erste Lage geklemmt.
     */
    fun layout(cx: Float, cy: Float, area: Area, modes: List<RefineMode>, density: Float): Layout {
        // Ecke: Passt keine Lage mit MIN_SEPARATION_DEG, gilt die mit dem groessten Richtungsabstand —
        // mindestens zweimal die Winkel-Hysterese, sonst waere ein Ziel nicht waehlbar.
        var best: List<Placed>? = null
        var bestSeparation = 2 * ANGLE_HYSTERESIS_DEG
        for (maxRadius in listOf(MAX_RADIUS_DP, CORNER_MAX_RADIUS_DP)) {
            for (span in SPANS) {
                for (facing in directions(cx, cy, area)) {
                    for (offset in DISCARD_OFFSETS) {
                        val raw = arc(cx, cy, modes, facing, span, facing + offset, density, maxRadius) ?: continue
                        val placed = shiftInside(raw, area, density) ?: continue
                        val separation = separation(placed, cx, cy, density) ?: continue
                        if (separation >= MIN_SEPARATION_DEG) return Layout(placed)
                        if (separation >= bestSeparation) {
                            best = placed
                            bestSeparation = separation
                        }
                    }
                }
            }
        }
        best?.let { return Layout(it) }
        val fallback = requireNotNull(arc(cx, cy, modes, 90f, 180f, 270f, density, maxRadius = Float.MAX_VALUE))
        return Layout(fallback.map { clampInside(it, area, density) })
    }

    /**
     * Ziel in Fingerrichtung oder null. [current] ist das zuletzt eingerastete Ziel (fuer die Hysterese),
     * ([downX], [downY]) der Druckpunkt.
     */
    fun pick(
        fingerX: Float, fingerY: Float, downX: Float, downY: Float,
        layout: Layout, current: CrossTarget?, density: Float,
    ): CrossTarget? {
        val arm = ARM_DP * density
        val travel = hypot(fingerX - downX, fingerY - downY)
        val currentPoint = current?.let { layout.pointOf(it) }
        if (travel < (if (currentPoint != null) arm - HYSTERESIS_DP * density else arm)) return null
        val finger = direction(downX, downY, fingerX, fingerY)
        fun off(p: Point) = angleBetween(finger, direction(downX, downY, p.x, p.y))
        val nearest = layout.targets.minBy { off(it.point) }
        if (currentPoint == null || nearest.target == current) return nearest.target
        return if (off(nearest.point) + ANGLE_HYSTERESIS_DEG <= off(currentPoint)) nearest.target else current
    }

    /** Deckkraft des Kreuzes: [FAINT_ALPHA] bis zum Touch-Slop, dann linear bis 1 bei [FULL_ALPHA_DP]. */
    fun alpha(distancePx: Float, slopPx: Float, density: Float): Float {
        val full = FULL_ALPHA_DP * density
        if (distancePx <= slopPx) return FAINT_ALPHA
        if (distancePx >= full || full <= slopPx) return 1f
        return FAINT_ALPHA + (1f - FAINT_ALPHA) * (distancePx - slopPx) / (full - slopPx)
    }

    /**
     * Enthaelt die Aufnahme Sprache? Mindestens [SPEECH_MIN_MS] in 20-ms-Rahmen mit Effektivpegel ab
     * [SPEECH_RMS]. Raumrauschen bleibt unter dem Pegel, ein Knacks (Haptik, Finger) unter der Dauer.
     */
    fun hasSpeech(samples: FloatArray): Boolean {
        val needed = SPEECH_MIN_MS / 20
        var frames = 0
        var start = 0
        while (start + FRAME_SAMPLES <= samples.size) {
            var sum = 0.0
            for (i in start until start + FRAME_SAMPLES) sum += samples[i].toDouble() * samples[i]
            if (sqrt(sum / FRAME_SAMPLES) >= SPEECH_RMS && ++frames >= needed) return true
            start += FRAME_SAMPLES
        }
        return false
    }

    // --- Lage --------------------------------------------------------------------------------

    /** Richtung von (x0, y0) nach (x, y) in Grad, mathematisch (0 = rechts, 90 = oben). */
    private fun direction(x0: Float, y0: Float, x: Float, y: Float): Float =
        Math.toDegrees(atan2((y0 - y).toDouble(), (x - x0).toDouble())).toFloat()

    private fun angleBetween(a: Float, b: Float): Float {
        val d = abs((a - b) % 360f)
        return if (d > 180f) 360f - d else d
    }

    /** Bogenmitten: oben zuerst, dann nach Naehe zur Richtung Druckpunkt → Mitte des Bereichs. */
    private fun directions(cx: Float, cy: Float, area: Area): List<Float> {
        val toCenter = direction(cx, cy, (area.left + area.right) / 2f, (area.top + area.bottom) / 2f)
        val others = (0 until 8).map { it * 45f }.filter { it != 90f }.sortedBy { angleBetween(it, toCenter) }
        return listOf(90f) + others
    }

    /**
     * Modi von links (facing + span/2) im Uhrzeigersinn. Der Radius waechst bei gestauchtem Bogen, bis
     * Nachbarn [MIN_GAP_DP] auseinander liegen; braeuchte das mehr als [maxRadius], null.
     */
    private fun arc(
        cx: Float, cy: Float, modes: List<RefineMode>, facing: Float, span: Float, discardAngle: Float,
        density: Float, maxRadius: Float,
    ): List<Placed>? {
        val n = modes.size
        val step = if (n > 1) span / (n - 1) else 0f
        val needed = if (n > 1) MIN_GAP_DP / (2f * sin(Math.toRadians(step / 2.0)).toFloat()) else 0f
        if (needed > maxRadius) return null
        val radius = maxOf(RADIUS_DP, needed) * density
        val placed = modes.mapIndexed { i, mode ->
            Placed(CrossTarget.Mode(mode), polar(cx, cy, radius, facing + span / 2f - i * step))
        }
        return placed + Placed(CrossTarget.Discard, polar(cx, cy, DISCARD_RADIUS_DP * density, discardAngle))
    }

    private fun polar(cx: Float, cy: Float, radius: Float, angleDeg: Float): Point {
        val a = Math.toRadians(angleDeg.toDouble())
        return Point(cx + radius * cos(a).toFloat(), cy - radius * sin(a).toFloat())
    }

    private fun halfSize(target: CrossTarget, density: Float): Pair<Float, Float> =
        if (target == CrossTarget.Discard) {
            DISCARD_DP / 2f * density to DISCARD_DP / 2f * density
        } else {
            PILL_W_DP / 2f * density to PILL_H_DP / 2f * density
        }

    /** Alle Ziele gemeinsam so verschieben, dass sie mit Rand in [area] liegen; null, wenn das nicht geht. */
    private fun shiftInside(placed: List<Placed>, area: Area, density: Float): List<Placed>? {
        val m = MARGIN_DP * density
        val left = placed.minOf { it.point.x - halfSize(it.target, density).first }
        val right = placed.maxOf { it.point.x + halfSize(it.target, density).first }
        val top = placed.minOf { it.point.y - halfSize(it.target, density).second }
        val bottom = placed.maxOf { it.point.y + halfSize(it.target, density).second }
        if (right - left > area.right - area.left - 2 * m || bottom - top > area.bottom - area.top - 2 * m) return null
        val dx = maxOf(0f, area.left + m - left) - maxOf(0f, right - (area.right - m))
        val dy = maxOf(0f, area.top + m - top) - maxOf(0f, bottom - (area.bottom - m))
        return placed.map { it.copy(point = Point(it.point.x + dx, it.point.y + dy)) }
    }

    /**
     * Kleinster Richtungsabstand zweier Ziele vom Druckpunkt aus; null, wenn ein Ziel zu nah am
     * Druckpunkt liegt oder zwei sich ueberlappen.
     */
    private fun separation(placed: List<Placed>, cx: Float, cy: Float, density: Float): Float? {
        val reach = MIN_REACH_DP * density
        if (placed.any { hypot(it.point.x - cx, it.point.y - cy) < reach }) return null
        val angles = placed.map { direction(cx, cy, it.point.x, it.point.y) }
        var smallest = 360f
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                smallest = minOf(smallest, angleBetween(angles[i], angles[j]))
                val (aw, ah) = halfSize(placed[i].target, density)
                val (bw, bh) = halfSize(placed[j].target, density)
                val apartX = abs(placed[i].point.x - placed[j].point.x) >= aw + bw
                val apartY = abs(placed[i].point.y - placed[j].point.y) >= ah + bh
                if (!apartX && !apartY) return null
            }
        }
        return smallest
    }

    private fun clampInside(p: Placed, area: Area, density: Float): Placed {
        val m = MARGIN_DP * density
        val (hw, hh) = halfSize(p.target, density)
        val x = p.point.x.coerceIn(area.left + m + hw, maxOf(area.left + m + hw, area.right - m - hw))
        val y = p.point.y.coerceIn(area.top + m + hh, maxOf(area.top + m + hh, area.bottom - m - hh))
        return p.copy(point = Point(x, y))
    }
}
