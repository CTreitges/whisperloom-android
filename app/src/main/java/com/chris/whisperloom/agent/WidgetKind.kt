package com.chris.whisperloom.agent

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.chris.whisperloom.R

/** Wer ein Feature sieht: alle Nutzer oder nur mit freigeschalteten Pro-Funktionen. */
enum class Tier { NORMAL, PRO }

/**
 * Widget-Typen. Gespeichert wird [key] (stabil), nie der Enum-Name — wie bei [SpeechPause].
 * Neuer Typ = neuer Eintrag (plus eigener Provider); das Widget-Menue zeigt ihn dann von selbst.
 */
enum class WidgetKind(
    val key: String,
    val tier: Tier,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
) {
    VOICE_COMMAND("voice_command", Tier.PRO, R.string.kind_voice_command, R.string.kind_voice_command_sub, R.drawable.ic_mic);

    companion object {
        /** Unbekannt oder fehlend = [VOICE_COMMAND]: alle Profile bis 3.7.0 sind Sprach-Command-Widgets. */
        fun fromKey(key: String?): WidgetKind = entries.firstOrNull { it.key == key } ?: VOICE_COMMAND

        fun of(tier: Tier): List<WidgetKind> = entries.filter { it.tier == tier }
    }
}
