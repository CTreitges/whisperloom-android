package com.chris.whisperloom.agent

import android.content.Context
import com.chris.whisperloom.Prefs

const val TEST_SERVER_URL = "https://bridge.example.de"
const val TEST_SERVER_TOKEN = "geheim"

/**
 * Pro Widgets an und ein brauchbarer Server im Profil [profileId] (Standard: das Standardprofil).
 * Seit 3.7.1 hat jedes Widget seinen eigenen Server — die globalen Einstellungen gibt es nicht mehr.
 */
fun serverEinrichten(
    ctx: Context,
    profileId: String = WidgetProfile.DEFAULT_ID,
    url: String = TEST_SERVER_URL,
    token: String = TEST_SERVER_TOKEN,
): WidgetProfile {
    Prefs(ctx).proWidgetsEnabled = true
    val store = WidgetProfileStore(ctx)
    val p = checkNotNull(store.get(profileId)) { "Profil $profileId fehlt" }.copy(serverUrl = url, serverToken = token)
    store.save(p)
    return p
}
