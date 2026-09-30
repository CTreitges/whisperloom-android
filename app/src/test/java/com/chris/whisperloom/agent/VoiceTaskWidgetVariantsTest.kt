package com.chris.whisperloom.agent

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Die drei Layout-Varianten und das Profil je Instanz: was welches Widget zeigt, wie die
 * Varianten beim Wiederverwenden der View sauber bleiben und welcher Tipp zu welchem Widget gehoert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskWidgetVariantsTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(ctx)

    // Mit Server: ohne waere ein Widget im Ruhezustand "Server fehlt" statt bereit.
    private val einkauf = WidgetProfile(
        id = "p1", name = "Einkauf", icon = ProfileIcon.BuiltIn("shopping_cart"),
        serverUrl = TEST_SERVER_URL, serverToken = TEST_SERVER_TOKEN,
    )
    private val mitFoto = einkauf.copy(icon = ProfileIcon.Photo("p1-1.png"))

    @Before fun leeren() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun variante(
        layout: WidgetLayout,
        profile: WidgetProfile = WidgetProfile.DEFAULT,
        foto: Bitmap? = null,
        state: VoiceTaskState = VoiceTaskState.READY,
        message: String = "",
    ): RemoteViews = VoiceTaskWidgetView.build(ctx, layout, profile, foto, state, 0, message, null)

    private fun angewendet(views: RemoteViews): View = views.apply(ctx, FrameLayout(ctx))

    private fun foto(): Bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)

    private fun cd(state: VoiceTaskState, elapsed: Long = 0) =
        VoiceTaskWidgetView.contentDescription(ctx, state, elapsed, "")

    /** Welche Drawable-Ressource das Symbol zeigt (RemoteViews ruft setImageResource). */
    private fun ressource(wurzel: View): Int =
        ReflectionHelpers.getField(wurzel.findViewById<ImageView>(R.id.widget_icon), "mResource")

    private fun sichtbarkeit(wurzel: View, id: Int): Int = wurzel.findViewById<View>(id).visibility

    /** Aus welcher Ressource der Hintergrund stammt (RemoteViews ruft setBackgroundResource). */
    private fun hintergrund(wurzel: View): Int = shadowOf(wurzel.background).createdFromResId

    /** So faerbt ImageView.setColorFilter(int) — der Weg, den RemoteViews fuer das Symbol nimmt. */
    private fun faerbung(farbe: Int): ColorFilter = PorterDuffColorFilter(ctx.getColor(farbe), PorterDuff.Mode.SRC_ATOP)

    private fun symbolfarbe(wurzel: View): ColorFilter? = wurzel.findViewById<ImageView>(R.id.widget_icon).colorFilter

    // --- Die Varianten ------------------------------------------------------

    @Test fun jedeVarianteLaesstSichInJedemZustandAnwenden() {
        WidgetLayout.entries.forEach { layout ->
            VoiceTaskState.entries.forEach { state ->
                val wurzel = angewendet(variante(layout, einkauf, state = state))
                assertEquals(VoiceTaskWidgetView.contentDescription(ctx, einkauf, state, 0, ""), wurzel.contentDescription)
                assertEquals(View.VISIBLE, sichtbarkeit(wurzel, R.id.widget_icon))
                assertEquals(View.GONE, sichtbarkeit(wurzel, R.id.widget_photo))
                val name = wurzel.findViewById<TextView>(R.id.widget_name)
                if (layout == WidgetLayout.ICON) assertNull("1x1 zeigt keinen Namen", name)
                else assertEquals("$layout/$state", "Einkauf", name.text.toString())
            }
        }
    }

    @Test fun ohneEigenenNamenStehtSprachauftragDa() {
        val wurzel = angewendet(variante(WidgetLayout.ROW))
        assertEquals(ctx.getString(R.string.widget_label), wurzel.findViewById<TextView>(R.id.widget_name).text.toString())
        // Ohne eigenen Namen klingt TalkBack wie vor den Profilen.
        assertEquals(cd(VoiceTaskState.READY), wurzel.contentDescription)
    }

    @Test fun derBildschirmleserNenntDenProfilnamen() {
        val text = VoiceTaskWidgetView.contentDescription(ctx, einkauf, VoiceTaskState.RECORDING, 7_000, "")
        assertTrue(text.startsWith("Einkauf"))
        assertTrue(text.contains(cd(VoiceTaskState.RECORDING, 7_000)))
    }

    private fun dp(wert: Float) = (wert * ctx.resources.displayMetrics.density).roundToInt()

    /**
     * Robolectric (LEGACY-Grafik) misst jede Textzeile mit fester Hoehe (35 px, gleich bei jeder
     * Schriftgroesse) und bricht nicht um. Deshalb bekommt jede sichtbare Textzeile hier ihr echtes
     * Mass — Roboto braucht mit Schriftpolster hoechstens 1,33 x Schriftgroesse je Zeile — und
     * alles andere (Polster, Abstaende, Symbolgroesse, Verschachtelung) wird echt gemessen.
     */
    private fun echteTexthoehen(v: View) {
        if (v is TextView && v.visibility != View.GONE) {
            assertTrue("Text ohne Zeilengrenze", v.maxLines in 1..2)
            v.layoutParams = v.layoutParams.apply { height = ceil(v.maxLines * v.textSize * ZEILE).toInt() }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) echteTexthoehen(v.getChildAt(i))
    }

    @Test fun jedeVariantePasstInIhreIdealgroesse() {
        WidgetLayout.entries.forEach { layout ->
            val wurzel = angewendet(variante(layout, einkauf, state = VoiceTaskState.ERROR, message = "Server nicht erreichbar"))
            echteTexthoehen(wurzel)
            wurzel.measure(
                View.MeasureSpec.makeMeasureSpec(dp(layout.w), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            assertTrue("$layout braucht ${wurzel.measuredHeight}px, hat ${dp(layout.h)}px", wurzel.measuredHeight <= dp(layout.h))
        }
    }

    @Test fun inBereitUndFehlerStehtDasSymbolDesProfils() {
        assertEquals(R.drawable.ic_shopping_cart, ressource(angewendet(variante(WidgetLayout.STACK, einkauf))))
        assertEquals(
            R.drawable.ic_shopping_cart,
            ressource(angewendet(variante(WidgetLayout.STACK, einkauf, state = VoiceTaskState.ERROR))),
        )
        assertEquals(R.drawable.ic_send, ressource(angewendet(variante(WidgetLayout.STACK, einkauf, state = VoiceTaskState.SENT))))
    }

    @Test fun ohneServerStehtDasServerSymbolStattDesFotos() {
        WidgetLayout.entries.forEach { layout ->
            val wurzel = angewendet(variante(layout, mitFoto, foto(), VoiceTaskState.NO_SERVER))
            assertEquals("$layout", R.drawable.ic_dns, ressource(wurzel))
            assertEquals(View.GONE, sichtbarkeit(wurzel, R.id.widget_photo))
        }
    }

    @Test fun ohneServerLiegtDerHintergrundWieBereitUndDasSymbolIstGedaempft() {
        WidgetLayout.entries.forEach { layout ->
            val bereit = angewendet(variante(layout, einkauf))
            val ohneServer = angewendet(variante(layout, einkauf, state = VoiceTaskState.NO_SERVER))
            assertEquals("$layout", R.drawable.widget_bg_ready, hintergrund(bereit))
            assertEquals("$layout: kein Fehler-Rot, nur ein Hinweis", hintergrund(bereit), hintergrund(ohneServer))
            assertEquals("$layout", faerbung(R.color.loom_outline), symbolfarbe(ohneServer))
            assertEquals("$layout", faerbung(R.color.loom_primary), symbolfarbe(bereit))
        }
    }

    @Test fun einFotoVerdraengtDasSymbolAuchBeimWiederverwenden() {
        // Der Launcher recycelt die View bei gleichem Layout (reapply): jede Sichtbarkeit muss
        // bei jedem Zeichnen neu gesetzt werden, sonst bleibt das vorige Bild stehen.
        val wurzel = angewendet(variante(WidgetLayout.STACK, einkauf))
        variante(WidgetLayout.STACK, mitFoto, foto()).reapply(ctx, wurzel)
        assertEquals(View.GONE, sichtbarkeit(wurzel, R.id.widget_icon))
        assertEquals(View.VISIBLE, sichtbarkeit(wurzel, R.id.widget_photo))
        assertNotNull(wurzel.findViewById<ImageView>(R.id.widget_photo).drawable)

        variante(WidgetLayout.STACK, einkauf).reapply(ctx, wurzel)
        assertEquals(View.VISIBLE, sichtbarkeit(wurzel, R.id.widget_icon))
        assertEquals(View.GONE, sichtbarkeit(wurzel, R.id.widget_photo))
    }

    @Test fun waehrendDerAufnahmeStehtDasStoppSymbolStattDesFotos() {
        val wurzel = angewendet(variante(WidgetLayout.STACK, mitFoto, foto()))
        variante(WidgetLayout.STACK, mitFoto, foto(), VoiceTaskState.RECORDING).reapply(ctx, wurzel)
        assertEquals(View.GONE, sichtbarkeit(wurzel, R.id.widget_photo))
        assertEquals(View.VISIBLE, sichtbarkeit(wurzel, R.id.widget_icon))
        assertEquals(R.drawable.ic_stop, ressource(wurzel))
    }

    @Test fun abAndroid12WaehltDasSystemWieWidgetLayouts() {
        // Mit dem echten AOSP-Code (versteckte API, per Reflexion): die Groessen-Map muss genau
        // die Idealgroessen tragen, die WidgetLayouts.pick annimmt.
        val views = VoiceTaskWidgetView.views(ctx, manager, 1, WidgetProfile.DEFAULT, null, VoiceTaskState.READY, 0, "")
        fun systemWahl(w: Float, h: Float): Int = ReflectionHelpers.callInstanceMethod<RemoteViews>(
            views, "getRemoteViewsToApply",
            ReflectionHelpers.ClassParameter.from(Context::class.java, ctx),
            ReflectionHelpers.ClassParameter.from(SizeF::class.java, SizeF(w, h)),
        ).layoutId
        var w = 30f
        while (w <= 600f) {
            var h = 30f
            while (h <= 300f) {
                assertEquals("bei ${w}x$h", WidgetLayouts.pick(w, h).layoutRes, systemWahl(w, h))
                h += 7f
            }
            w += 7f
        }
        // Ohne gemeldete Groesse nimmt das System die kleinste Variante.
        assertEquals(R.layout.widget_task_icon, views.layoutId)
    }

    // --- Echte Widgets mit Profilen -----------------------------------------

    /** Pro Widgets an, Mikrofon erlaubt, das Standardprofil mit Server — alle Widgets bereit. */
    private fun bereitMachen() {
        serverEinrichten(ctx)
        shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun widget(): Int = shadowOf(manager).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)

    @Test fun zweiWidgetsZeigenIhrJeweiligesProfil() {
        bereitMachen()
        val store = WidgetProfileStore(ctx)
        store.save(einkauf)
        val eins = widget()
        val zwei = widget()
        store.bind(eins, einkauf.id)

        VoiceTaskWidgetView.push(ctx, VoiceTaskState.READY)

        val a = shadowOf(manager).getViewFor(eins)
        val b = shadowOf(manager).getViewFor(zwei)
        assertEquals(VoiceTaskWidgetView.contentDescription(ctx, einkauf, VoiceTaskState.READY, 0, ""), a.contentDescription)
        assertEquals(cd(VoiceTaskState.READY), b.contentDescription)
        assertEquals(R.drawable.ic_shopping_cart, ressource(a))
        assertEquals(R.drawable.ic_mic, ressource(b))
    }

    @Test fun einGespeichertesFotoErscheintEinFehlendesFaelltAufsMikrofonZurueck() {
        bereitMachen()
        val store = WidgetProfileStore(ctx)
        store.save(mitFoto)
        val id = widget()
        store.bind(id, mitFoto.id)

        VoiceTaskWidgetView.push(ctx, VoiceTaskState.READY)
        var wurzel = shadowOf(manager).getViewFor(id)
        assertEquals("Datei fehlt: kein Loch, sondern das Mikrofon", View.GONE, sichtbarkeit(wurzel, R.id.widget_photo))
        assertEquals(R.drawable.ic_mic, ressource(wurzel))

        val datei = WidgetPhoto.file(ctx, "p1-1.png")!!
        datei.parentFile!!.mkdirs()
        datei.outputStream().use { foto().compress(Bitmap.CompressFormat.PNG, 100, it) }
        VoiceTaskWidgetView.push(ctx, VoiceTaskState.READY)
        wurzel = shadowOf(manager).getViewFor(id)
        assertEquals(View.VISIBLE, sichtbarkeit(wurzel, R.id.widget_photo))
        assertEquals(View.GONE, sichtbarkeit(wurzel, R.id.widget_icon))
        datei.delete()
    }

    @Test fun jedesWidgetHatSeinenEigenenTipp() {
        val eins = VoiceTaskWidgetView.tapIntent(ctx, TapIntent.START, 1)!!
        val zwei = VoiceTaskWidgetView.tapIntent(ctx, TapIntent.START, 2)!!
        val a = shadowOf(eins).savedIntent
        val b = shadowOf(zwei).savedIntent
        assertFalse("Sonst bestimmte das zuletzt gezeichnete Widget den Tipp aller", a.filterEquals(b))
        assertEquals(1, a.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
        assertEquals(2, b.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
        assertTrue(shadowOf(eins).isActivity)
    }

    @Test fun beendenBleibtEinDienstAufrufFuerAlleWidgets() {
        val eins = VoiceTaskWidgetView.tapIntent(ctx, TapIntent.STOP, 1)!!
        val zwei = VoiceTaskWidgetView.tapIntent(ctx, TapIntent.STOP, 2)!!
        assertTrue(shadowOf(eins).isForegroundService)
        assertEquals(VoiceTaskService.ACTION_STOP, shadowOf(eins).savedIntent.action)
        assertTrue("Jedes Widget beendet dieselbe Aufnahme", shadowOf(eins).savedIntent.filterEquals(shadowOf(zwei).savedIntent))
    }

    // --- Unter Android 12 ---------------------------------------------------

    @Test @Config(sdk = [30])
    fun unterAndroid12EntscheidenDieGemeldetenSpannen() {
        val einsZuEins = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 57)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 127)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 51)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 102)
        }
        assertEquals(LegacyLayouts(WidgetLayout.ICON, WidgetLayout.ROW), VoiceTaskWidgetView.legacyLayouts(einsZuEins))
        assertEquals(LegacyLayouts(WidgetLayout.STACK, WidgetLayout.STACK), VoiceTaskWidgetView.legacyLayouts(Bundle()))
    }

    @Test @Config(sdk = [30])
    fun unterAndroid12ZeigenZweiWidgetsIhreNamen() {
        bereitMachen()
        val store = WidgetProfileStore(ctx)
        store.save(einkauf)
        val eins = widget()
        val zwei = widget()
        store.bind(eins, einkauf.id)

        VoiceTaskWidgetView.push(ctx, VoiceTaskState.READY)

        // Ohne gemeldete Groesse: STACK, also mit Namen.
        fun name(id: Int) = shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.widget_name).text.toString()
        assertEquals("Einkauf", name(eins))
        assertEquals(ctx.getString(R.string.widget_label), name(zwei))
    }

    private companion object {
        /** Hoechste Zeilenhoehe je Schriftgroesse (Roboto, mit Schriftpolster, eine Zeile). */
        const val ZEILE = 1.33f
    }
}
