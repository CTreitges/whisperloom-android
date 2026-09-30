package com.chris.whisperloom

import android.content.Context
import android.content.Intent
import com.chris.whisperloom.ui.MainActivity

/**
 * Zentrale Navigation aus Overlay, IME und Notification in die App (UX-Spec §1.2).
 * Ziel ist immer die [MainActivity]; sie liest `route` und `step` (auch in onNewIntent).
 */
object AppNav {
    const val EXTRA_ROUTE = "route"
    const val EXTRA_STEP = "step"

    /** Profil-Id zur Route [ROUTE_WIDGETS]: dessen Editor oeffnet sich. */
    const val EXTRA_PROFILE = "profile"

    const val ROUTE_HOME = "home"
    const val ROUTE_SETUP = "setup"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_ADVANCED = "advanced"

    /** Frueherer Name von [ROUTE_ADVANCED] (bis 3.7.0) — gilt weiter fuer schon erzeugte Intents. */
    const val ROUTE_AGENT = "agent"
    const val ROUTE_WIDGETS = "widgets"

    /** Home (H): Notification-Tipp. */
    fun home(ctx: Context): Intent = intent(ctx, ROUTE_HOME)

    /** Einrichtungs-Assistent (W), optional direkt auf Schritt [step] (1..7). */
    fun setup(ctx: Context, step: Int? = null): Intent =
        intent(ctx, ROUTE_SETUP).apply {
            if (step != null) putExtra(EXTRA_STEP, step)
        }

    /** Einstellungen (E): IME-Zahnrad. */
    fun settings(ctx: Context): Intent = intent(ctx, ROUTE_SETTINGS)

    /** "Erweitert": Widget-Tipp, solange Pro Widgets aus sind. */
    fun advanced(ctx: Context): Intent = intent(ctx, ROUTE_ADVANCED)

    /** Widget-Menue, Tab "Pro Widgets": Widget-Tipp ohne Mikrofon, Aufnahme-Notification. */
    fun proWidgets(ctx: Context): Intent = intent(ctx, ROUTE_WIDGETS)

    /** Wie [proWidgets], dazu oeffnet sich der Editor von [profileId]: Widget-Tipp ohne Server. */
    fun widgetProfile(ctx: Context, profileId: String): Intent =
        proWidgets(ctx).putExtra(EXTRA_PROFILE, profileId)

    private fun intent(ctx: Context, route: String): Intent =
        Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            // Aufrufer sind Services (kein Activity-Kontext) -> eigener Task noetig.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
