package com.chris.whisperloom.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri

/** System-Intents des Assistenten (Spec §2.2) — an einer Stelle, damit E3 dieselben nutzt. */
object SystemIntents {

    fun overlay(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${ctx.packageName}".toUri())

    fun accessibility(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun inputMethods(): Intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${ctx.packageName}".toUri())

    /** false, wenn das System keinen Empfaenger hat (Aufrufer zeigt Snackbar). */
    fun open(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun showImePicker(ctx: Context) {
        ctx.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
}

/** Die Activity hinter einem (Compose-)Context — fuer Berechtigungs-Rationale. */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
