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

    const val ROUTE_HOME = "home"
    const val ROUTE_SETUP = "setup"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_AGENT = "agent"

    /** Home (H): Notification-Tipp. */
    fun home(ctx: Context): Intent = intent(ctx, ROUTE_HOME)

    /** Einrichtungs-Assistent (W), optional direkt auf Schritt [step] (1..7). */
    fun setup(ctx: Context, step: Int? = null): Intent =
        intent(ctx, ROUTE_SETUP).apply {
            if (step != null) putExtra(EXTRA_STEP, step)
        }

    /** Einstellungen (E): IME-Zahnrad. */
    fun settings(ctx: Context): Intent = intent(ctx, ROUTE_SETTINGS)

    /** Erweiterte Optionen (Sprachauftrag): Widget-Tipp, solange nichts eingerichtet oder erlaubt ist. */
    fun agent(ctx: Context): Intent = intent(ctx, ROUTE_AGENT)

    private fun intent(ctx: Context, route: String): Intent =
        Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            // Aufrufer sind Services (kein Activity-Kontext) -> eigener Task noetig.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
