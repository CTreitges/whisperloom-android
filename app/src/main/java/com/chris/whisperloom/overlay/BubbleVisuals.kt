package com.chris.whisperloom.overlay

/**
 * Reine (Android-freie) Beschreibung, wie der Knopf bzw. die IME-Mikro-Taste in einem
 * Zustand aussieht (UX-Spec §5.1, Tabelle "Vier Zustaende"). Die Zuordnung auf Drawables,
 * Farben und Strings (R.*) passiert erst im Service — so bleibt die Logik JVM-testbar.
 */
data class BubbleVisual(
    val fill: Fill,
    val ring: Ring,
    val icon: Icon,
    val iconTint: IconTint,
    /** 1,0 normal; 0,5 waehrend SENDING. */
    val iconAlpha: Float,
    val label: Label,
    val labelStyle: LabelStyle,
    val description: Description,
) {
    /** Fuellung des Kreises. Reihenfolge = Level in `mic_button_bg.xml` (IME). */
    enum class Fill { SURFACE, RECORDING, PRIMARY_CONTAINER, ERROR_CONTAINER }

    /** Ring um den Kreis. PULSE animiert, RECORDING_STATIC ist der Reduce-Motion-Ersatz. */
    enum class Ring { PRIMARY, PULSE, RECORDING_STATIC, ARC, ERROR }

    enum class Icon { MIC, STOP, REPLAY, CONTENT_COPY }

    enum class IconTint { PRIMARY, ON_RECORDING, ON_PRIMARY_CONTAINER, ON_ERROR_CONTAINER }

    /** TIMER = "● m:ss" (Text kommt aus BubbleUi.timerText), Rest sind feste Strings. */
    enum class Label { NONE, TIMER, SENDING, REFINING, RETRY_HINT, COPIED }

    /** Pillen-Grund + Textfarbe des Labels. */
    enum class LabelStyle { NEUTRAL, RECORDING, ERROR }

    /** contentDescription (cd_bubble_*). */
    enum class Description { IDLE, RECORDING, SENDING, REFINING, ERROR }
}

object BubbleVisuals {

    /** Icon-Deckkraft waehrend der Uebertragung. */
    const val SENDING_ICON_ALPHA = 0.5f

    /** Fuellung des IDLE-Kreises (surfaceContainer @ 96 %). */
    const val IDLE_FILL_ALPHA = 0.96f

    /**
     * @param copiedHint IDLE-Hinweis "Kopiert — einfuegen" (nur ohne Bedienungshilfe, 2 s nach dem Senden)
     * @param reduceMotion Animator-Dauer-Skalierung 0: statischer Ring statt Puls
     * @param refining SENDING in der Textverbesserung: das Label nennt den Ausweg (Tippen = ohne KI)
     */
    fun visualFor(
        state: BubbleState,
        copiedHint: Boolean = false,
        reduceMotion: Boolean = false,
        refining: Boolean = false,
    ): BubbleVisual = when (state) {
        BubbleState.IDLE -> BubbleVisual(
            fill = BubbleVisual.Fill.SURFACE,
            ring = BubbleVisual.Ring.PRIMARY,
            icon = if (copiedHint) BubbleVisual.Icon.CONTENT_COPY else BubbleVisual.Icon.MIC,
            iconTint = BubbleVisual.IconTint.PRIMARY,
            iconAlpha = 1f,
            label = if (copiedHint) BubbleVisual.Label.COPIED else BubbleVisual.Label.NONE,
            labelStyle = BubbleVisual.LabelStyle.NEUTRAL,
            description = BubbleVisual.Description.IDLE,
        )
        BubbleState.RECORDING -> BubbleVisual(
            fill = BubbleVisual.Fill.RECORDING,
            ring = if (reduceMotion) BubbleVisual.Ring.RECORDING_STATIC else BubbleVisual.Ring.PULSE,
            icon = BubbleVisual.Icon.STOP,
            iconTint = BubbleVisual.IconTint.ON_RECORDING,
            iconAlpha = 1f,
            label = BubbleVisual.Label.TIMER,
            labelStyle = BubbleVisual.LabelStyle.RECORDING,
            description = BubbleVisual.Description.RECORDING,
        )
        BubbleState.SENDING -> BubbleVisual(
            fill = BubbleVisual.Fill.PRIMARY_CONTAINER,
            ring = BubbleVisual.Ring.ARC,
            icon = BubbleVisual.Icon.MIC,
            iconTint = BubbleVisual.IconTint.ON_PRIMARY_CONTAINER,
            iconAlpha = SENDING_ICON_ALPHA,
            label = if (refining) BubbleVisual.Label.REFINING else BubbleVisual.Label.SENDING,
            labelStyle = BubbleVisual.LabelStyle.NEUTRAL,
            description = if (refining) BubbleVisual.Description.REFINING else BubbleVisual.Description.SENDING,
        )
        BubbleState.ERROR -> BubbleVisual(
            fill = BubbleVisual.Fill.ERROR_CONTAINER,
            ring = BubbleVisual.Ring.ERROR,
            icon = BubbleVisual.Icon.REPLAY,
            iconTint = BubbleVisual.IconTint.ON_ERROR_CONTAINER,
            iconAlpha = 1f,
            label = BubbleVisual.Label.RETRY_HINT,
            labelStyle = BubbleVisual.LabelStyle.ERROR,
            description = BubbleVisual.Description.ERROR,
        )
    }
}
