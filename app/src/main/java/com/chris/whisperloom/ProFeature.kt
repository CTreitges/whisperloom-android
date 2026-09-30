package com.chris.whisperloom

import androidx.annotation.StringRes

/**
 * Pro-/Entwickler-Funktionen, die in „Erweitert“ freigeschaltet werden ([Prefs.isEnabled]).
 * Neue Pro-Funktion = neuer Eintrag; Schalter und Hub-Unterzeile iterieren [entries].
 */
enum class ProFeature(@StringRes val title: Int, @StringRes val sub: Int, @StringRes val hubLabel: Int) {
    WIDGETS(R.string.pro_widgets, R.string.pro_widgets_sub, R.string.pro_widgets_hub),
    PROMPT(R.string.prompt_enable, R.string.prompt_enable_sub, R.string.settings_prompt_sub),
    SERVER_MODELS(R.string.pro_server_models, R.string.pro_server_models_sub, R.string.pro_server_models),
}
