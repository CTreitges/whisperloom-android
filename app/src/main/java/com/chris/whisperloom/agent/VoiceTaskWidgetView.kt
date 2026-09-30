package com.chris.whisperloom.agent

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import com.chris.whisperloom.R

/**
 * Zeichnet das Widget. Das Widget haelt keinen Zustand — hier wird aus [VoiceTaskState] und
 * ein paar Zahlen eine komplette [RemoteViews] gebaut und ueber den AppWidgetManager an ALLE
 * Instanzen geschickt.
 *
 * Ein laufender Auftrag ist global (ein Auftrag zur Zeit, alle Widgets zeigen ihn), der
 * Ruhezustand und das Aussehen gelten je Instanz: jedes Widget ist nur mit dem Server seines
 * Profils bereit ([VoiceTaskWidget.idle]) und zeigt Namen und Symbol seines Profils
 * ([WidgetProfileStore.forWidget]) in der Variante, die zu seiner Groesse passt ([WidgetLayouts]).
 *
 * Die Entscheidung, was ein Tipp bedeutet, faellt in [VoiceTaskUi.tap]; hier wird sie nur in
 * den passenden PendingIntent uebersetzt.
 */
object VoiceTaskWidgetView {

    /** Standardprofil in der Variante STACK, ohne Instanz — die Optik vor den Profilen. */
    fun build(ctx: Context, state: VoiceTaskState, elapsedMs: Long = 0, message: String = ""): RemoteViews =
        build(
            ctx, WidgetLayout.STACK, WidgetProfile.DEFAULT, null, state, elapsedMs, message,
            tapIntent(ctx, VoiceTaskUi.tap(state), AppWidgetManager.INVALID_APPWIDGET_ID),
        )

    /**
     * Eine Variante fuer ein Profil. [photo] nur, wenn das Profil ein Foto hat; gezeigt wird es
     * nur in bereit/Fehler ([WidgetLayouts.showsProfileIcon]).
     *
     * Setzt JEDE veraenderliche Eigenschaft, auch die scheinbar unveraenderten: der Launcher
     * recycelt die View bei gleichem Layout und spielt nur die neuen Aktionen darauf ab
     * (`AppWidgetHostView` → `reapply`). Was hier fehlt, bliebe vom vorigen Bild stehen — etwa
     * ein Foto, obwohl gerade aufgenommen wird.
     */
    fun build(
        ctx: Context,
        layout: WidgetLayout,
        profile: WidgetProfile,
        photo: Bitmap?,
        state: VoiceTaskState,
        elapsedMs: Long,
        message: String,
        click: PendingIntent?,
    ): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout.layoutRes)
        v.setInt(R.id.widget_root, "setBackgroundResource", background(state))
        val showPhoto = photo != null && WidgetLayouts.showsProfileIcon(state)
        v.setViewVisibility(R.id.widget_icon, if (showPhoto) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.widget_photo, if (showPhoto) View.VISIBLE else View.GONE)
        if (showPhoto) v.setImageViewBitmap(R.id.widget_photo, photo)
        v.setImageViewResource(R.id.widget_icon, icon(state, profile))
        v.setInt(R.id.widget_icon, "setColorFilter", ctx.getColor(iconColor(state)))
        if (layout != WidgetLayout.ICON) {
            v.setTextViewText(R.id.widget_name, profile.displayName(ctx))
            v.setTextColor(R.id.widget_name, ctx.getColor(textColor(state)))
        }
        v.setTextColor(R.id.widget_status, ctx.getColor(textColor(state)))
        v.setTextViewText(R.id.widget_status, status(ctx, state, elapsedMs, message))
        v.setContentDescription(R.id.widget_root, contentDescription(ctx, profile, state, elapsedMs, message))
        if (click != null) v.setOnClickPendingIntent(R.id.widget_root, click)
        return v
    }

    /**
     * Alle Instanzen des Widgets neu zeichnen. Ohne Widget auf dem Startbildschirm folgenlos.
     *
     * Ueber die Ids statt `updateAppWidget(ComponentName, …)`: nur so bekommt jede Instanz ihr
     * eigenes Profil — und nur diesen Weg bildet Robolectric ab.
     */
    fun push(ctx: Context, state: VoiceTaskState, elapsedMs: Long = 0, message: String = "") {
        val ids = VoiceTaskWidget.placedIds(ctx)
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(ctx)
        pushTo(ctx, manager, ids, state, elapsedMs, message)
    }

    /**
     * Bestimmte Instanzen zeichnen, jede mit ihrem Profil. Wer "bereit" zeichnet, bekommt je
     * Instanz den Ruhezustand ihres Profils ([VoiceTaskWidget.idle]) — ein Widget ohne Server
     * zeigt dann "Server fehlt". Ein Foto wird nur geladen, wenn es gerade zu sehen ist, und je
     * Aufruf nur einmal — auch wenn mehrere Widgets es teilen.
     */
    fun pushTo(
        ctx: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        state: VoiceTaskState,
        elapsedMs: Long = 0,
        message: String = "",
    ) {
        val store = WidgetProfileStore(ctx)
        val photos = HashMap<String, Bitmap?>()
        for (id in ids) {
            val profile = store.forWidget(id)
            val shown = VoiceTaskWidget.idle(state, profile)
            val photoName = (profile.icon as? ProfileIcon.Photo)?.fileName
                ?.takeIf { WidgetLayouts.showsProfileIcon(shown) }
            val photo = photoName?.let { name ->
                if (name !in photos) photos[name] = WidgetPhoto.load(ctx, name)
                photos[name]
            }
            manager.updateAppWidget(id, views(ctx, manager, id, profile, photo, shown, elapsedMs, message))
        }
    }

    /**
     * Ab Android 12 alle drei Varianten als Groessen-Map — das System waehlt je nach Groesse,
     * auch beim Ziehen, ohne dass die App gefragt wird. Darunter waehlt [WidgetLayouts.legacy]
     * aus den gemeldeten Spannen, je eine Variante fuer Hoch- und Querformat.
     * Der Tipp ist je Instanz ein eigener PendingIntent und steckt in jeder Variante.
     */
    @VisibleForTesting
    internal fun views(
        ctx: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        profile: WidgetProfile,
        photo: Bitmap?,
        state: VoiceTaskState,
        elapsedMs: Long,
        message: String,
    ): RemoteViews {
        val click = tapIntent(ctx, VoiceTaskUi.tap(state), widgetId)
        fun variant(layout: WidgetLayout) = build(ctx, layout, profile, photo, state, elapsedMs, message, click)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(WidgetLayout.entries.associate { SizeF(it.w, it.h) to variant(it) })
        }
        val pick = legacyLayouts(manager.getAppWidgetOptions(widgetId) ?: Bundle.EMPTY)
        return if (pick.portrait == pick.landscape) variant(pick.portrait)
        else RemoteViews(variant(pick.landscape), variant(pick.portrait))
    }

    @VisibleForTesting
    internal fun legacyLayouts(options: Bundle): LegacyLayouts = WidgetLayouts.legacy(
        minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
        minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
    )

    fun status(ctx: Context, state: VoiceTaskState, elapsedMs: Long, message: String): String = when (state) {
        VoiceTaskState.READY -> ctx.getString(R.string.widget_ready)
        VoiceTaskState.RECORDING -> VoiceTaskUi.timerText(elapsedMs)
        // Mit Grund: ein Versuch ist gescheitert, der naechste folgt — und ein Tipp sendet sofort.
        VoiceTaskState.WORKING ->
            if (message.isBlank()) ctx.getString(R.string.widget_working)
            else ctx.getString(R.string.widget_working_retry, reason(ctx, message))
        VoiceTaskState.SENT ->
            if (message.isBlank()) ctx.getString(R.string.widget_sent)
            else ctx.getString(R.string.widget_sent_raw)
        VoiceTaskState.NO_MIC -> ctx.getString(R.string.widget_no_mic)
        VoiceTaskState.OFF -> ctx.getString(R.string.widget_off)
        VoiceTaskState.NO_SERVER -> ctx.getString(R.string.widget_no_server)
        VoiceTaskState.ERROR -> ctx.getString(R.string.widget_error_retry, reason(ctx, message))
    }

    fun contentDescription(ctx: Context, state: VoiceTaskState, elapsedMs: Long, message: String): String = when (state) {
        VoiceTaskState.READY -> ctx.getString(R.string.cd_widget_ready)
        VoiceTaskState.RECORDING -> ctx.getString(R.string.cd_widget_recording, VoiceTaskUi.timerText(elapsedMs))
        VoiceTaskState.WORKING ->
            if (message.isBlank()) ctx.getString(R.string.cd_widget_working)
            else ctx.getString(R.string.cd_widget_working_retry, reason(ctx, message))
        VoiceTaskState.SENT ->
            if (message.isBlank()) ctx.getString(R.string.cd_widget_sent)
            else ctx.getString(R.string.cd_widget_sent_raw, message)
        VoiceTaskState.NO_MIC -> ctx.getString(R.string.cd_widget_no_mic)
        VoiceTaskState.OFF -> ctx.getString(R.string.widget_off)
        VoiceTaskState.NO_SERVER -> ctx.getString(R.string.cd_widget_no_server)
        VoiceTaskState.ERROR -> ctx.getString(R.string.cd_widget_error, reason(ctx, message))
    }

    /**
     * Mit eigenem Profilnamen nennt TalkBack ihn zuerst ("Einkauf: Sprachauftrag aufnehmen") —
     * sonst klingen zwei Widgets gleich. Ohne Namen bleibt es beim Text ohne Profile.
     */
    fun contentDescription(
        ctx: Context,
        profile: WidgetProfile,
        state: VoiceTaskState,
        elapsedMs: Long,
        message: String,
    ): String {
        val text = contentDescription(ctx, state, elapsedMs, message)
        return if (profile.name.isEmpty()) text else ctx.getString(R.string.cd_widget_named, profile.name, text)
    }

    /** Ohne eigene Meldung bleibt es bei einem allgemeinen Hinweis statt einer leeren Zeile. */
    private fun reason(ctx: Context, message: String): String =
        message.ifBlank { ctx.getString(R.string.kb_error) }

    private fun background(state: VoiceTaskState): Int = when (state) {
        VoiceTaskState.RECORDING -> R.drawable.widget_bg_recording
        VoiceTaskState.WORKING -> R.drawable.widget_bg_working
        VoiceTaskState.SENT -> R.drawable.widget_bg_sent
        VoiceTaskState.ERROR, VoiceTaskState.NO_MIC -> R.drawable.widget_bg_error
        VoiceTaskState.READY, VoiceTaskState.OFF, VoiceTaskState.NO_SERVER -> R.drawable.widget_bg_ready
    }

    /** In bereit/Fehler das Symbol des Profils; hat es ein Foto, steht hier (unsichtbar) das Mikrofon. */
    private fun icon(state: VoiceTaskState, profile: WidgetProfile): Int = when (state) {
        VoiceTaskState.RECORDING -> R.drawable.ic_stop
        VoiceTaskState.WORKING, VoiceTaskState.SENT -> R.drawable.ic_send
        VoiceTaskState.NO_MIC, VoiceTaskState.OFF -> R.drawable.ic_mic_off
        VoiceTaskState.NO_SERVER -> R.drawable.ic_dns
        VoiceTaskState.READY, VoiceTaskState.ERROR ->
            WidgetIcons.of((profile.icon as? ProfileIcon.BuiltIn)?.key ?: WidgetIcons.DEFAULT_KEY).drawable
    }

    private fun iconColor(state: VoiceTaskState): Int = when (state) {
        VoiceTaskState.RECORDING -> R.color.loom_recording
        VoiceTaskState.WORKING -> R.color.loom_secondary
        VoiceTaskState.SENT -> R.color.loom_success
        VoiceTaskState.ERROR, VoiceTaskState.NO_MIC -> R.color.loom_error
        VoiceTaskState.OFF, VoiceTaskState.NO_SERVER -> R.color.loom_outline
        VoiceTaskState.READY -> R.color.loom_primary
    }

    private fun textColor(state: VoiceTaskState): Int = when (state) {
        VoiceTaskState.RECORDING -> R.color.loom_recordingText
        VoiceTaskState.WORKING -> R.color.loom_onSecondaryContainer
        VoiceTaskState.SENT -> R.color.loom_onSuccessContainer
        VoiceTaskState.ERROR, VoiceTaskState.NO_MIC -> R.color.loom_onErrorContainer
        VoiceTaskState.READY, VoiceTaskState.OFF, VoiceTaskState.NO_SERVER -> R.color.loom_onSurfaceVariant
    }

    /**
     * Start und Wiederholung laufen ueber die Trampolin-Activity: damit ist die App beim
     * Start des Mikrofon-Dienstes real im Vordergrund, und die Hintergrund-Beschraenkungen
     * ab Android 14 koennen gar nicht erst greifen (die scheitern sonst STILL — ohne
     * Exception, nur ohne Ton). Das Beenden darf direkt an den Dienst gehen: der laeuft
     * zu dem Zeitpunkt bereits im Vordergrund — und jedes Widget beendet dieselbe Aufnahme.
     *
     * Mit gueltiger [widgetId] traegt die Trampolin-Intent die Instanz in `data`: sonst hielte
     * das System die Tipps zweier Widgets fuer denselben PendingIntent (Extras zaehlen beim
     * Vergleich nicht), und das zuletzt gezeichnete Widget bestimmte das Profil aller.
     *
     * [TapIntent.NONE] ist der einzige Zustand ohne Tippflaeche — den vergibt [VoiceTaskUi.tap]
     * nicht, er bleibt als Rueckfallwert fuer eine Intent ohne Angabe.
     */
    @VisibleForTesting
    internal fun tapIntent(ctx: Context, intent: TapIntent, widgetId: Int): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return when (intent) {
            TapIntent.NONE -> null
            TapIntent.STOP -> PendingIntent.getForegroundService(
                ctx, REQ_STOP,
                Intent(ctx, VoiceTaskService::class.java).setAction(VoiceTaskService.ACTION_STOP),
                flags,
            )
            else -> PendingIntent.getActivity(
                ctx, REQ_TRAMPOLINE + intent.ordinal,
                VoiceTaskTrampolineActivity.intent(ctx, intent, widgetId),
                flags,
            )
        }
    }

    private const val REQ_STOP = 71
    private const val REQ_TRAMPOLINE = 80
}
