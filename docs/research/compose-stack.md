> **Historisch — Recherche-Archiv, Stand 2026-09-06.** Keine gepflegte Referenz: maßgeblich sind Code und [CHANGELOG](../../CHANGELOG.md); Einordnung in der [Übersicht](README.md).

# WhisperLoom-Android — Build-Konfiguration für Jetpack Compose + Material 3

Stand: 2026-09-06 · Read-only-Recherche, nichts gebaut, nichts verändert.
Alle Versions-/Datumsangaben stammen aus den unten verlinkten Quellen (abgerufen 2026-09-06). Wo etwas nicht belegbar war, steht **[unsicher]**.

---

## 0. Kurzfazit

| Frage | Antwort |
|---|---|
| Kann der Stack (AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / compileSdk 35) bleiben? | **Nein**, nicht mit einer aktuellen Compose-BOM. Compose ≥ 1.12.0 (BOM 2026.08.00) verlangt **compileSdk 37 und AGP ≥ 9.2.0** ([compose-ui 1.12.0-alpha01](https://developer.android.com/jetpack/androidx/releases/compose-ui), [compose-runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime), [Compose-Compiler-Doku](https://developer.android.com/develop/ui/compose/compiler)). Lifecycle 2.11.0 und Navigation 2.10.0 tragen dieselbe Anforderung. |
| Empfohlene Ziel-Kombination | **AGP 9.4.0 · Gradle 9.6.1 · JDK 17 (bleibt) · compileSdk 37 · targetSdk 35 (bleibt) · minSdk 26 (bleibt) · Built-in-Kotlin 2.2.10 (von AGP gebündelt) · Compose-Compiler-Plugin 2.2.10 · Compose BOM 2026.08.00 (ui 1.12.0, material3 1.4.0) · activity-compose 1.13.0 · lifecycle 2.11.0 · Robolectric 4.16.1 (Tests auf SDK 35)** |
| Fallback (weniger invasiv, aber "alt") | AGP 8.13.x · Gradle 8.13 · Kotlin 2.2.21 · compileSdk 36 · BOM 2026.06.01 (ui 1.11.4) · lifecycle 2.10.0 — compileSdk-Bedarf von Compose 1.11 nicht belegbar **[unsicher]**, siehe §2.4. |
| Navigation-Lib? | Nein — für ~6 Screens reicht State-Navigation (`enum`/sealed class + `BackHandler` aus activity-compose). |
| Material-Icons? | **Keine** Icon-Library. `material-icons-extended` = 35,7 MB AAR, von Google als "no longer maintained or recommended" markiert. XML-Vektor-Icons von fonts.google.com einchecken. |
| R8/Minify? | **Ja, für Release aktivieren** (Compose wird ohne R8 sehr groß; Compose braucht keine eigenen Keep-Regeln, org.json ist Framework). |
| APK-Wachstum | grob: **+1–1,5 MB mit R8**, **+8–12 MB ohne R8** (Schätzung, siehe §6). |
| Robolectric + Compose auf aarch64 ohne Emulator | Funktioniert (JVM-Tests, `createComposeRule()`); Robolectric 4.16.1 unterstützt API 23–36, SDK 35 läuft mit JDK 17, SDK 36 bräuchte JDK 21 → **Tests auf `@Config(sdk=[35])` festnageln**. |

---

## 1. Ist-Stand im Repo (gelesen, nicht gebaut)

- `build.gradle.kts`: `com.android.application 8.7.3`, `org.jetbrains.kotlin.android 2.0.21` (`apply false`).
- `settings.gradle.kts`: `pluginManagement { google(); mavenCentral(); gradlePluginPortal() }`, `FAIL_ON_PROJECT_REPOS`, nur `:app`.
- `gradle/wrapper/gradle-wrapper.properties`: `gradle-8.9-bin.zip`.
- `gradle.properties`: `-Xmx4g`, caching, parallel, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`.
- `app/build.gradle.kts`: compileSdk/targetSdk 35, minSdk 26, `kotlinOptions { jvmTarget = "17" }`, `isMinifyEnabled=false` in beiden BuildTypes, Release-Signing aus `WHISPERLOOM_KEYSTORE*`-Env (PKCS12), einzige Dependency `junit:junit:4.13.2`.
- Activities erben direkt von `android.app.Activity`; Manifest-Theme `@style/AppTheme` = `@android:style/Theme.Material.Light.DarkActionBar`.
- `proguard-rules.pro`: leer (Kommentar).
- CI `.github/workflows/build.yml`: `actions/setup-java@v4` (temurin 17), `android-actions/setup-android@v3`, `testDebugUnitTest lintDebug`, `assembleDebug`, `assembleRelease` mit Keystore aus Secret.
- Unit-Tests: 7 reine JUnit-Tests unter `app/src/test/java/com/chris/whisperloom/`.

---

## 2. Toolchain-Entscheidung

### 2.1 Aktuelle Versionen (Quellen)

| Komponente | Neueste stabile Version | Datum | Quelle |
|---|---|---|---|
| Android Gradle Plugin | **9.4.0** | September 2026 | [AGP 9.4.0 Release Notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Gradle | 9.7.1 (9.6.1 = Juni 2026) | 2026-08-19 / 2026-06-26 | [gradle.org/releases](https://gradle.org/releases/) |
| Kotlin | 2.4.10 | 2026-07-14 | [kotlinlang.org/docs/releases](https://kotlinlang.org/docs/releases.html) |
| Compose BOM | 2026.08.00 (ui 1.12.0, material3 1.4.0) | Aug 2026 | [BOM-Mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [BOM-POM](https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom) |
| Compose UI/Foundation/Runtime | 1.12.0 | 2026-08-12 (Release-Notes) | [compose-ui](https://developer.android.com/jetpack/androidx/releases/compose-ui) |
| Material 3 | 1.4.0 | (Seite nennt 2026-08-26 als Datum des Eintrags; BOMs seit 2026.04.01 mappen 1.4.0) **[Datum unsicher]** | [compose-material3](https://developer.android.com/jetpack/androidx/releases/compose-material3) |
| androidx.activity | 1.13.0 | 2026-03-11 | [activity](https://developer.android.com/jetpack/androidx/releases/activity) |
| androidx.lifecycle | 2.11.0 | 2026-06-17 | [lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) |
| androidx.navigation | 2.10.0 | 2026-08-26 | [navigation](https://developer.android.com/jetpack/androidx/releases/navigation) |
| androidx.core / core-splashscreen | 1.19.0 / 1.2.0 | 2026-06-03 / – | [core](https://developer.android.com/jetpack/androidx/releases/core) |
| androidx.test core / ext:junit | 1.7.0 / 1.3.0 | 2025-07-30 | [test](https://developer.android.com/jetpack/androidx/releases/test) |
| Robolectric | 4.16.1 (4.17-beta-4 = Pre-Release) | 2026-01-21 (4.17-beta-4: 2026-08-23) | [GitHub Releases](https://github.com/robolectric/robolectric/releases) |

### 2.2 AGP ↔ Gradle ↔ JDK (developer.android.com, je Release-Notes-Seite)

| AGP | Gradle min | SDK Build Tools | JDK min | Max. API | Quelle |
|---|---|---|---|---|---|
| 8.7.x (aktuell) | 8.9 | 35.0.0 | 17 | 35 | (Ist-Stand Repo; Tabelle nicht erneut abgerufen) |
| 8.13 | 8.13 | 35.0.0 | 17 | 36.1 | [agp-8-13](https://developer.android.com/build/releases/agp-8-13-0-release-notes) |
| 9.0.x | 9.1.0 | 36.0.0 | 17 | – | [agp-9-0](https://developer.android.com/build/releases/agp-9-0-0-release-notes) |
| 9.1.x | 9.3.1 | 36.0.0 | 17 | 37.0 | [agp-9-1](https://developer.android.com/build/releases/agp-9-1-0-release-notes) |
| 9.2.x | 9.4.1 | 36.0.0 | 17 | 37.0 | [agp-9-2](https://developer.android.com/build/releases/agp-9-2-0-release-notes) |
| 9.3.x | 9.5.0 | 36.0.0 | 17 | 37 | [agp-9-3](https://developer.android.com/build/releases/agp-9-3-0-release-notes) |
| **9.4.0** | **9.6.0** | **36.0.0** | **17** | **37** | [agp-9-4](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |

Gradle 9.x läuft mit JDK 17–26 ([Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html)) → **JDK 17 bleibt**, CI unverändert.

### 2.3 Kotlin ↔ AGP ↔ Gradle (JetBrains-Matrix)

| KGP | Gradle (min–max, voll unterstützt) | AGP (min–max) |
|---|---|---|
| 2.0.20–2.0.21 (aktuell) | 6.8.3–8.8 | 7.1.3–8.5 |
| 2.2.0–2.2.10 | 7.6.3–8.14 | 7.3.1–8.10.0 |
| 2.2.20–2.2.21 | 7.6.3–8.14 | 7.3.1–8.11.1 |
| 2.3.20–2.3.21 | 7.6.3–9.3.0 | 8.2.2–9.0.0 |
| 2.4.0–2.4.10 | 7.6.3–9.5.0 | 8.5.2–9.1.0 |

Quelle: [kotlinlang.org/docs/gradle-configure-project](https://kotlinlang.org/docs/gradle-configure-project.html).

**Wichtig — Built-in Kotlin ab AGP 9.0:** "Android Gradle plugin 9.0 introduces built-in Kotlin support and enables it by default. That means you no longer have to apply the `org.jetbrains.kotlin.android` plugin" und "AGP 9.0 now has a runtime dependency on Kotlin Gradle plugin (KGP) 2.2.10 … if you use a KGP version lower than 2.2.10, Gradle will automatically upgrade" ([AGP 9.0 Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)). Das POM von `com.android.tools.build:gradle:9.4.0` deklariert weiterhin `kotlin-gradle-plugin 2.2.10` und `kotlin-stdlib 2.2.10` ([POM 9.4.0](https://dl.google.com/android/maven2/com/android/tools/build/gradle/9.4.0/gradle-9.4.0.pom); gleiches bei 9.2.0 und 9.0.1). Kotlin 2.3.0 markiert `org.jetbrains.kotlin.android` mit AGP ≥ 9 als deprecated ([Kotlin 2.3 Compatibility Guide, KT-81199](https://kotlinlang.org/docs/compatibility-guide-23.html)). Wird das alte Plugin trotzdem angewendet, bricht der Build: "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0" ([Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)). `kotlinOptions {}` entfällt; `jvmTarget` "defaults to `android.compileOptions.targetCompatibility`" (ebd.).

**Compose-Compiler-Plugin:** ab Kotlin 2.0 `org.jetbrains.kotlin.plugin.compose`, Version = Kotlin-Version ("When you use the Compose Compiler Gradle plugin, you don't have to check Compose to Kotlin compatibility" — [compose-kotlin](https://developer.android.com/jetpack/androidx/releases/compose-kotlin); Setup: [compose/compiler](https://developer.android.com/develop/ui/compose/compiler); Optionen: [kotlinlang compose-compiler-options](https://kotlinlang.org/docs/compose-compiler-options.html)). `composeOptions.kotlinCompilerExtensionVersion` wird nicht mehr gesetzt; `buildFeatures.compose = true` bleibt nötig.

### 2.4 Anforderungen der Compose-Bibliotheken (der eigentliche Zwang)

| Bibliothek | Anforderung | Quelle |
|---|---|---|
| Compose ui/foundation/runtime **1.12.0** (BOM 2026.08.00) | "Updated Compose `compileSdk` to API 37. This means that a minimum AGP version of 9.2.0 is required when using Compose." (1.12.0-alpha01, 2026-04-22) | [compose-ui](https://developer.android.com/jetpack/androidx/releases/compose-ui), [compose-runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime), [compose-foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation) |
| Compose 1.9.0 | Lint-Checks verlangen AGP ≥ 8.8.2 (oder `android.experimental.lint.version=8.8.2`) | [compose-runtime 1.9.0](https://developer.android.com/jetpack/androidx/releases/compose-runtime) |
| Compose 1.10.0-alpha01 | minSdk 21 → 23 | ebd. |
| Compose 1.9.0-beta01 / 1.10.0-alpha02 | "Projects released with Kotlin 2.0 require KGP 2.0.0 or newer to be consumed" | ebd. |
| Lifecycle **2.11.0** | "Updates to the Compose `compileSdk` require a minimum AGP version of `9.2.0`"; Compose UI ≥ 1.7.0 für `LocalLifecycleOwner` | [lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) |
| Navigation **2.10.0** | ab 2.10.0-alpha03: Compose compileSdk 37 / AGP ≥ 9.2.0; minSdk 24 | [navigation](https://developer.android.com/jetpack/androidx/releases/navigation) |
| Activity 1.11.0 | "Activity is now compiled with API 36." (1.12.x/1.13.0: keine neue Aussage; im Web tauchen Lint-Fehler "activity:1.12.4 … compile against version 36" auf) | [activity](https://developer.android.com/jetpack/androidx/releases/activity), [Suchtreffer](https://github.com/ankidroid/Anki-Android/issues/19002) |
| Compose 1.9–1.11 compileSdk | **[unsicher]** — die Release-Notes 1.9–1.11 enthalten keine "compileSdk 36"-Zeile (nur 1.12 → 37). Praktisch verlangen die zugehörigen AndroidX-Abhängigkeiten (activity ≥ 1.11, core ≥ 1.17/1.18 "compileSdk … API 36.1") compileSdk 36. Compose 1.8.x verlangte 35 (Lint-Meldung "animation-core-android:1.8.2 … version 35 or later", [Suchtreffer](https://github.com/ankidroid/Anki-Android/issues/19002)). |
| Kotlin-Metadaten | Compose 1.11.4/1.12.0, material3 1.4.0, lifecycle 2.11.0 sind gegen `kotlin-stdlib 2.1.20` (material3: 2.0.21) gebaut ([POM ui-android 1.12.0](https://dl.google.com/android/maven2/androidx/compose/ui/ui-android/1.12.0/ui-android-1.12.0.pom)). Kotlin liest Binaries "preferably (but we can't guarantee it) … forwards compatible with the next language release, but not later ones" ([Kotlin evolution principles](https://kotlinlang.org/docs/kotlin-evolution-principles.html)) → Kotlin 2.0.21 könnte 2.1-Metadaten lesen, ist aber nicht garantiert; mit Built-in-Kotlin 2.2.10 kein Thema. |

### 2.5 Optionen und Empfehlung

| Option | Konfiguration | Bewertung |
|---|---|---|
| **A — Bleiben** (AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, compileSdk 35) | Nur mit alter BOM ≤ 2025.06.x (Compose 1.8.x) möglich **[BOM-Nummer unsicher]**; material3 1.3.x; lifecycle ≤ 2.9; Robolectric ok | **Nicht empfohlen.** Man startet ein Redesign auf einem Stack, der ab Tag 1 >1 Jahr alt ist; jedes spätere Library-Update erzwingt ohnehin den Sprung auf AGP 9/compileSdk 37. Kotlin 2.0.21 liegt außerhalb der Matrix für Gradle 8.9 (nur bis 8.8 voll unterstützt). |
| **B — Voll-Upgrade (Empfehlung)** | AGP **9.4.0**, Gradle **9.6.1**, JDK 17, compileSdk **37**, targetSdk 35, Built-in-Kotlin **2.2.10** + `org.jetbrains.kotlin.plugin.compose` **2.2.10**, BOM **2026.08.00**, activity 1.13.0, lifecycle 2.11.0, Robolectric 4.16.1 | Alle aktuellen stabilen Artefakte ohne Version-Pinning nach unten. Google-getestete Kombination (AGP bündelt KGP 2.2.10). Änderungsaufwand im Build ist klein (Plugin-Zeile raus, `kotlinOptions` raus, Wrapper hoch, compileSdk hoch). |
| **B' — wie B, aber Kotlin 2.4.10** | `org.jetbrains.kotlin.plugin.compose 2.4.10` (zieht KGP 2.4.10 auf den Classpath und überschreibt damit das gebündelte 2.2.10) | Neueste Sprachfeatures, aber **außerhalb der JetBrains-Matrix** (KGP 2.4.x: AGP ≤ 9.1.0, Gradle ≤ 9.5.0) → Warnungen, evtl. Inkompatibilitäten. Erst wählen, wenn eine KGP-Version AGP 9.4 offiziell listet. |
| **C — Mittelweg** | AGP 8.13.x, Gradle 8.13, Kotlin 2.2.21, compileSdk 36, BOM **2026.06.01** (ui 1.11.4, material3 1.4.0), activity 1.13.0, lifecycle **2.10.0**, navigation ≤ 2.9.x | Bleibt auf Gradle 8 / KGP-Plugin-Modell; Kotlin 2.2.21 offiziell mit AGP ≤ 8.11.1 (8.13 wieder außerhalb). compileSdk-Bedarf von Compose 1.11 nicht belegt **[unsicher]**. Nur sinnvoll, wenn AGP 9 aus einem konkreten Grund nicht geht — ich sehe keinen (keine Drittanbieter-Plugins, keine Variant-API-Nutzung). |

**Empfehlung: Option B.** Begründung: (1) einzige Kombination, in der die neuesten stabilen Compose/Lifecycle/Activity-Artefakte ohne Downgrade funktionieren; (2) das Projekt hat keine Fremd-Plugins, keine KSP/kapt, kein NDK mehr → keiner der AGP-9-Breaking-Changes trifft (Variant-API, `PostProcessing`, Density-Splits, Wear-Embedding); (3) JDK 17 und die CI-Datei bleiben praktisch unverändert; (4) Built-in-Kotlin macht die KGP-Matrix-Frage für die Basis obsolet.

**AGP 9 Property-Defaults, die dieses Projekt berühren** ([AGP 9.0 Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)): `android.builtInKotlin=true` (gewollt), `android.newDsl=true` (ok, keine alte Variant-API im Build), `android.r8.strictFullModeForKeepRules=true` und `android.r8.optimizedResourceShrinking=true` (relevant sobald Minify an), `android.enableAppCompileTimeRClass=true` (kein `switch` auf R-IDs im Code → ok), `android.sdk.defaultTargetSdkToCompileSdkIfUnset=true` (targetSdk ist explizit gesetzt → keine Wirkung). Gradle 9: JVM 17+, eingebettetes Kotlin 2.2, `Project.exec/javaexec` entfernt (nicht genutzt), min. AGP 8.4 ([Gradle 9 Upgrade Guide](https://docs.gradle.org/current/userguide/upgrading_major_version_9.html)).

**SDK-Pakete in CI:** Platform 37 und Build-Tools 36.0.0 werden von Gradle automatisch nachgeladen, "as long as the corresponding SDK license agreements have already been accepted" ([studio/intro/update](https://developer.android.com/studio/intro/update)); `android-actions/setup-android@v3` akzeptiert Lizenzen — bestehender Workflow sollte reichen **[nicht getestet]**.

**targetSdk:** bleibt 35. Google-Play-Pflicht (API 36 ab 2026-08-31, [Play target-sdk](https://developer.android.com/google/play/requirements/target-sdk)) gilt nur für Play-Distribution; WhisperLoom wird als APK aus GitHub Actions verteilt. Ein targetSdk-36-Sprung ist ein eigenes Thema (Verhaltensänderungen), nicht Teil des Compose-Umbaus.

---

## 3. Compose BOM und Artefakte

BOM **2026.08.00** ([POM](https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom)) mappt u. a.:

| Artefakt | Version (aus BOM) | Verwendung | Empfehlung |
|---|---|---|---|
| `androidx.compose.ui:ui` | 1.12.0 | Kern | ja |
| `androidx.compose.material3:material3` | 1.4.0 | M3-Komponenten | ja |
| `androidx.compose.ui:ui-tooling-preview` | 1.12.0 | `@Preview`-Annotationen | ja (implementation) |
| `androidx.compose.ui:ui-tooling` | 1.12.0 | Preview-Renderer in Studio | ja, **debugImplementation** |
| `androidx.compose.ui:ui-test-junit4` | 1.12.0 | `createComposeRule()` | ja, testImplementation |
| `androidx.compose.ui:ui-test-manifest` | 1.12.0 | `ComponentActivity` für `createComposeRule()` | ja, **debugImplementation** |
| `androidx.compose.material:material-icons-core` | **1.7.8** (BOM pinnt; AAR 0,8 MB) | vereinzelte Icons | **nein** (s. u.) |
| `androidx.compose.material:material-icons-extended` | **1.7.8** (AAR **35,7 MB**, [HEAD](https://dl.google.com/android/maven2/androidx/compose/material/material-icons-extended-android/1.7.8/material-icons-extended-android-1.7.8.aar)) | alle Icons | **nein** |
| `androidx.compose.foundation/runtime/animation` | 1.12.0 | transitiv | nicht separat deklarieren |

Nicht in der BOM (eigene Versionen):

| Artefakt | Version | Quelle / Hinweis |
|---|---|---|
| `androidx.activity:activity-compose` | **1.13.0** | [activity](https://developer.android.com/jetpack/androidx/releases/activity); liefert `setContent`, `BackHandler`, `enableEdgeToEdge`, `LocalActivity` |
| `androidx.lifecycle:lifecycle-runtime-compose` | **2.11.0** | [lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle); `collectAsStateWithLifecycle`, `LifecycleEventEffect` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | **2.11.0** | ebd.; `viewModel()` — nur nötig, falls ViewModels eingeführt werden |
| `androidx.navigation:navigation-compose` | 2.10.0 | [navigation](https://developer.android.com/jetpack/androidx/releases/navigation) — **nicht empfohlen** für 6 Screens (s. u.) |
| `androidx.core:core-splashscreen` | 1.2.0 | [core](https://developer.android.com/jetpack/androidx/releases/core) — optional |
| `androidx.core:core-ktx` | 1.19.0 | seit 1.19.0 leeres Artefakt, APIs in `core` gemerged ([core](https://developer.android.com/jetpack/androidx/releases/core)); nicht nötig |

**Icons:** Die Compose-Doku sagt zur Material-Icons-Library: "this artifact is no longer maintained or recommended for use in your apps, as it contains an older look and feel and can also increase the build time of your apps *significantly*" und empfiehlt XML-Downloads von [fonts.google.com/icons](https://fonts.google.com/icons) ([compose/graphics/images/material](https://developer.android.com/develop/ui/compose/graphics/images/material)). Ohne R8 landet `material-icons-extended` komplett im DEX. → **Empfehlung: keine Icon-Library**; die ~8–12 benötigten Icons (Mikro, Settings, Zurück, Check, Warnung, Kopieren, Teilen …) als `res/drawable/ic_*.xml` einchecken und per `painterResource()` nutzen. Das passt auch zum bisherigen Stil des Projekts (eigene Drawables).

**Navigation:** Für ~6 Screens (Setup-Schritte, Settings, Share-Flow) reicht
```kotlin
enum class Screen { Welcome, Permissions, Api, Keyboard, Overlay, Done }
var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
BackHandler(enabled = screen != Screen.Welcome) { screen = screen.previous() }
```
Vorteile: keine zusätzliche Lib (navigation-compose zieht `kotlinx-serialization`, `savedstate`, `navigationevent` etc.), kein Route-String-Parsing, Back-Handling über `BackHandler` (activity-compose). Die Navigation-2-Seite trägt einen Maintenance-Mode-Hinweis, dessen Geltungsbereich aus dem Abruf nicht eindeutig war **[unsicher]**; Navigation 3 ist als eigenes Paket unterwegs, Stable-Status nicht verifiziert **[unsicher]**. Beides spricht dafür, jetzt keine Nav-Lib zu binden.

**Splash:** `core-splashscreen 1.2.0` ist optional. Der weiße Start-Flash lässt sich auch ohne Lib durch dunkles `android:windowBackground` im Manifest-Theme verhindern (§4). Da Android 12+ ohnehin einen System-Splash mit App-Icon zeigt, ist die Lib nur nötig, wenn Splash-Hintergrund/Icon auf API 26–30 gestaltet werden sollen.

---

## 4. Theme ohne AppCompat

Bausteine:
1. Activities erben von `androidx.activity.ComponentActivity` (kein AppCompat).
2. `enableEdgeToEdge(statusBarStyle, navigationBarStyle)` aus `androidx.activity` — Signatur `fun ComponentActivity.enableEdgeToEdge(statusBarStyle: SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT), navigationBarStyle: SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT))` ([EdgeToEdge-Referenz](https://developer.android.com/reference/androidx/activity/EdgeToEdge)). Für eine fest dunkle App `SystemBarStyle.dark(Color.TRANSPARENT)` — "dark" = dunkler Hintergrund, **helle Icons** ([SystemBarStyle-Referenz](https://developer.android.com/reference/androidx/activity/SystemBarStyle); Wortlaut konnte nicht zitiert werden, Seite lud nur die Navigation **[Formulierung unsicher, Semantik ist API-Standard]**). Edge-to-Edge ist ab targetSdk 35 auf Android 15 ohnehin erzwungen ([edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge)).
3. Manifest-Theme = Framework-Theme mit dunklem `windowBackground` (verhindert den weißen Frame vor dem ersten Compose-Frame) und `windowLightStatusBar=false`.
4. Compose-Theme mit `darkColorScheme(...)` fest, `isSystemInDarkTheme()` ignorieren, **kein** `dynamicDarkColorScheme` ([Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)).

Framework-Themes `Theme.Material.NoActionBar` / `Theme.DeviceDefault.NoActionBar` existieren seit API 21 ([R.style](https://developer.android.com/reference/android/R.style); Seite lud nur Index, Existenz ist API-Standard). `Theme.Material.NoActionBar` ist die bessere Wahl gegenüber `DeviceDefault` (OEM-Skins färben DeviceDefault um; für Compose irrelevant, aber der Window-Hintergrund/Fensterrahmen bleibt vorhersagbar).

**Hinweis Scope:** IME (`WhisperLoomInputMethodService`) und Overlay (`FloatingMicService`) bleiben View-basiert — Compose in einem `InputMethodService`/`WindowManager`-Overlay erfordert manuelle `LifecycleOwner`/`SavedStateRegistryOwner`/`ViewTreeLifecycleOwner`-Verkabelung an der `ComposeView`; das ist ein eigenes Thema und nicht Teil dieser Konfiguration.

---

## 5. Robolectric + Compose-UI-Tests auf der JVM

**Fakten**
- Robolectric **4.16.1** (2026-01-21) ist die neueste stabile Version; 4.16 "supports Android Baklava (SDK 36), and you need to use JDK 21 if running tests with SDK 36 target. It also removes support for Android L (SDK 21 and 22)" ([Releases](https://github.com/robolectric/robolectric/releases)). README: "supports running unit tests for 14 different versions of Android, ranging from M (API level 23) to Baklava (API level 36)" ([README](https://github.com/robolectric/robolectric/blob/master/README.md)). SDK 35 kam mit 4.14 (2024-11-15). SDK 37 erst in **4.17-beta** (Pre-Release, 2026-07/08).
- **Konsequenz:** compileSdk 37 ist für Robolectric egal; Tests laufen gegen `@Config(sdk = [35])` (= targetSdk) mit JDK 17. Default-SDK ist ohnehin "the `targetSdk` specified in your module's build.gradle" ([robolectric.org/configuring](https://robolectric.org/configuring/)) — trotzdem explizit setzen, damit ein späterer targetSdk-36-Sprung nicht stillschweigend JDK 21 verlangt.
- Gradle: `testOptions.unitTests.isIncludeAndroidResources = true` ist Pflicht ([Robolectric strategies](https://developer.android.com/training/testing/local-tests/robolectric), [robolectric.org/getting-started](https://robolectric.org/getting-started/)).
- Compose-Test-Artefakte: `ui-test-junit4` (test), `ui-test-manifest` (debugImplementation — "Needed for createComposeRule(), but not for createAndroidComposeRule<YourActivity>()", [compose/testing](https://developer.android.com/develop/ui/compose/testing)). Die BOM liefert die Versionen; `androidx.test:core 1.7.0`, `androidx.test.ext:junit 1.3.0` ([test](https://developer.android.com/jetpack/androidx/releases/test)).
- JDK 17+: robolectric.org getting-started nennt `--add-opens`-JVM-Flags "when running on Java 17 or higher" (Liste im Snippet unten; Praxisberichte kommen ohne aus — als Fallback bei `InaccessibleObjectException` einbauen) **[Notwendigkeit unsicher]**.
- Praxis (Community): `@RunWith(RobolectricTestRunner::class)` + `createComposeRule()` funktioniert auf der JVM ([kmpbits](https://www.kmpbits.com/posts/robolectric-compose/), [dev.to/pchmielowski](https://dev.to/pchmielowski/testing-jetpack-compose-without-emulator-or-device-1dni)).

**Fallen**
1. **LooperMode PAUSED** (Default seit 4.4): Main-Thread-Arbeit läuft nur, wenn Robolectric sie abspult. `createComposeRule()` synchronisiert selbst über `ComposeIdlingResource`; bei `LaunchedEffect`/Coroutinen zusätzlich `composeTestRule.waitForIdle()` bzw. `mainClock.advanceTimeBy()`; bei Bedarf `shadowOf(Looper.getMainLooper()).idle()`. Bekannte Issues: [#6018](https://github.com/robolectric/robolectric/issues/6018), [#7055 AppNotIdleException bei vielen Compose-Tests](https://github.com/robolectric/robolectric/issues/7055), [b/341880461 Hänger](https://issuetracker.google.com/issues/341880461) (Inhalt hinter Login, nicht lesbar **[unsicher]**).
2. **Resources**: ohne `isIncludeAndroidResources` schlägt jede `Theme`/`stringResource`-Nutzung fehl.
3. **Ältere Workaround-Annotation** `@Config(instrumentedPackages = ["androidx.loader.content"])` — stammt aus 2021, mit 4.16 vermutlich unnötig; nur bei `ClassCastException` in `androidx.loader` ergänzen.
4. **GraphicsMode**: Für Layout/Semantik-Tests reicht der Default; `@GraphicsMode(NATIVE)` nur für Bitmaps/Screenshots.
5. **android-all-Jars** werden beim ersten Testlauf von Maven Central geladen (~100 MB pro SDK) — CI braucht Netz; Gradle-Cache hilft.
6. **aarch64-VPS**: Robolectric ist reine JVM; 4.17-beta-2 nennt CONSCRYPT-Mode "for all platforms" — für Linux aarch64 keine bekannten Blocker, aber **ungetestet**.

---

## 6. R8/Minify und APK-Größe

**Empfehlung: Release mit `isMinifyEnabled = true` + `isShrinkResources = true`** ([shrink-code](https://developer.android.com/build/shrink-code)).

Pro: Compose/Material3 sind ohne Shrinker sehr groß (alle M3-Komponenten, Foundation, Animation im DEX); mit R8 bleibt nur der genutzte Teil. R8 full mode ist seit AGP 8.0 Default. Contra: obfuskierte Stacktraces → `mapping.txt` (liegt in `app/build/outputs/mapping/release/`) als CI-Artefakt sichern; etwas längere Release-Builds; erster Release-Build muss auf Gerät verifiziert werden (AP6).

Keep-Regeln:
- **Compose:** keine eigenen nötig — AARs liefern Consumer-Rules, die automatisch angewandt werden (shrink-code). Compose-Runtime 1.11+ erwähnt eine Annahme in ihren Default-Rules für die neue `SlotTable` ([compose-runtime 1.11.0-alpha04](https://developer.android.com/jetpack/androidx/releases/compose-runtime)) — nur relevant, wenn man das Experiment aktiv einschaltet.
- **org.json:** Framework-Klassen, nichts zu keepen. Keine kotlinx-serialization, keine Reflection, keine JNI mehr → `proguard-rules.pro` kann leer bleiben.
- **Services/Activities aus dem Manifest** (IME, Accessibility, FloatingMic): AGP generiert Keep-Regeln aus dem Manifest (aapt) — kein Handlungsbedarf.
- AGP 9: `android.r8.strictFullModeForKeepRules=true` (Default-Konstruktoren müssen explizit gekeept werden, falls Regeln existieren — hier keine) und Consumer-Rules mit globalen Optionen (`-dontobfuscate`) werden gefiltert ([AGP 9.0 Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)).

**Größenschätzung** (keine eigene Messung möglich — nichts gebaut):

| Szenario | Erwartetes Wachstum | Basis |
|---|---|---|
| Release **mit R8 full mode** + Resource-Shrinking | **ca. +0,8 bis +1,5 MB** | Google Sunflower: View-App 2.252 KB → +Compose 3.034 KB (+782 KB), Compose-only 2.966 KB ([compare-metrics](https://developer.android.com/develop/ui/compose/migrate/compare-metrics)); Community: "Compose adds about 1 MB of DEX" mit R8 ([kotlinlang-Slack](https://slack-chats.kotlinlang.org/t/507909/anyone-noticed-that-apk-size-has-increased-drastically-when-)) |
| Release/Debug **ohne R8** | **ca. +8 bis +12 MB** **[grobe Schätzung]** | ui 1.12 + foundation + runtime + animation + material3 1.4 + lifecycle + activity + kotlin-stdlib/coroutines unminifiziert im DEX; genaue Zahl erst per `assembleRelease` + APK Analyzer |
| Mit `material-icons-extended` ohne R8 | zusätzlich mehrere MB (AAR 35,7 MB) | [Maven HEAD](https://dl.google.com/android/maven2/androidx/compose/material/material-icons-extended-android/1.7.8/material-icons-extended-android-1.7.8.aar) |

Die bisherige App (nur Framework, kein AndroidX) ist vermutlich < 2 MB; relativ ist das Wachstum groß, absolut bleibt ein R8-Release bei ~3–4 MB **[unsicher, ungemessen]**. Verifikation nach dem Umbau: `./gradlew assembleRelease` und `unzip -l app-release.apk | sort -k1 -n | tail` bzw. `apkanalyzer apk file-size`.

---

## 7. Fertige Snippets (Option B)

### 7.1 `gradle/wrapper/gradle-wrapper.properties`
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
# AGP 9.4.0 verlangt Gradle >= 9.6.0 (https://developer.android.com/build/releases/agp-9-4-0-release-notes)
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```
(Alternativ `./gradlew wrapper --gradle-version 9.6.1` ausführen, sobald ein JDK da ist — aktualisiert auch `gradle-wrapper.jar`.)

### 7.2 `settings.gradle.kts` (unverändert nutzbar)
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WhisperLoom"
include(":app")
```

### 7.3 Root `build.gradle.kts`
```kotlin
// Top-level Build-Datei. Plugin-Versionen zentral, angewandt in :app.
plugins {
    // AGP 9.4.0 (Sep 2026): https://developer.android.com/build/releases/agp-9-4-0-release-notes
    id("com.android.application") version "9.4.0" apply false
    // Kotlin kommt seit AGP 9.0 built-in (KGP 2.2.10 laut AGP-POM) — KEIN org.jetbrains.kotlin.android mehr.
    // Compose-Compiler-Plugin: Version = Kotlin-Version des Builds.
    // https://developer.android.com/develop/ui/compose/compiler
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
```

### 7.4 `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
# AGP 9 Defaults (builtInKotlin=true, newDsl=true) bewusst NICHT überschrieben.
```

### 7.5 `app/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chris.whisperloom"
    // Compose 1.12 (BOM 2026.08.00) verlangt compileSdk 37 + AGP >= 9.2.0:
    // https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.0-alpha01
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chris.whisperloom"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("WHISPERLOOM_KEYSTORE") ?: "keystore/whisperloom-release.p12"
            val ks = file(ksPath)
            if (ks.exists()) {
                storeFile = ks
                storeType = "PKCS12"
                storePassword = System.getenv("WHISPERLOOM_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("WHISPERLOOM_KEY_ALIAS") ?: "whisperloom"
                keyPassword = System.getenv("WHISPERLOOM_KEY_PASSWORD")
                    ?: System.getenv("WHISPERLOOM_KEYSTORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 full mode (AGP-Default) + Resource-Shrinking: Compose/M3 ohne Shrinker = mehrere MB DEX.
            // https://developer.android.com/build/shrink-code
            isMinifyEnabled = true
            isShrinkResources = true
            val ks = file(System.getenv("WHISPERLOOM_KEYSTORE") ?: "keystore/whisperloom-release.p12")
            if (ks.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // kotlinOptions {} entfällt: Built-in-Kotlin nimmt jvmTarget = targetCompatibility (17).
    // https://developer.android.com/build/migrate-to-built-in-kotlin

    testOptions {
        unitTests {
            // Pflicht für Robolectric (Themes, Strings, ui-test-manifest-Activity).
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric auf JDK 17+ (https://robolectric.org/getting-started/) — nur bei
                // InaccessibleObjectException nötig; schadet sonst nicht.
                test.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                )
            }
        }
    }
}

dependencies {
    // Compose BOM 2026.08.00 -> ui 1.12.0, material3 1.4.0
    // https://developer.android.com/develop/ui/compose/bom/bom-mapping
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // setContent, BackHandler, enableEdgeToEdge — https://developer.android.com/jetpack/androidx/releases/activity
    implementation("androidx.activity:activity-compose:1.13.0")
    // collectAsStateWithLifecycle etc. — https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // Nur falls ViewModels eingeführt werden:
    // implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    // Optional: implementation("androidx.core:core-splashscreen:1.2.0")

    // Bewusst NICHT: material-icons-core/-extended (35,7 MB AAR, von Google nicht mehr empfohlen),
    // navigation-compose (State-Navigation reicht für ~6 Screens).

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // ComponentActivity für createComposeRule(): https://developer.android.com/develop/ui/compose/testing
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Robolectric 4.16.1: SDK 23–36; SDK 35 mit JDK 17 — https://github.com/robolectric/robolectric/releases
    testImplementation("org.robolectric:robolectric:4.16.1")
    // https://developer.android.com/jetpack/androidx/releases/test
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
}
```

Hinweis zur `unitTests.all { test -> … }`-Syntax: in der neuen AGP-9-DSL heißt die Property weiterhin `isIncludeAndroidResources`; sollte `all { test -> }` in Kotlin-DSL als `Action<Test>` nicht kompilieren, alternativ `unitTests.all { jvmArgs(...) }` (Receiver = `Test`) verwenden **[Syntax gegen AGP 9.4 nicht kompiliert]**.

### 7.6 `app/proguard-rules.pro`
```
# Compose/Material3 bringen ihre Consumer-Keep-Regeln in den AARs mit.
# org.json = Framework, keine Reflection, kein JNI mehr -> nichts zu schuetzen.
```

### 7.7 `AndroidManifest.xml` (nur Änderungen)
```xml
<application
    android:allowBackup="true"
    android:icon="@drawable/ic_launcher"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/AppTheme">
    <!-- Activities unverändert deklariert; sie erben jetzt von ComponentActivity (Code), nicht Activity. -->
```
Keine Manifest-Einträge für Compose nötig. `ui-test-manifest` merged seine `ComponentActivity` nur in den Debug-Test-Manifest.

### 7.8 `res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Framework-Theme (kein AppCompat). Fest dunkel: verhindert weissen Frame vor dem ersten Compose-Frame.
         Theme.Material.NoActionBar: API 21+ (https://developer.android.com/reference/android/R.style) -->
    <style name="AppTheme" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/kb_background</item>
        <item name="android:colorPrimary">@color/accent</item>
        <item name="android:colorAccent">@color/accent</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <!-- helle Icons auf dunklem Grund (API 23+ / 27+) -->
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="27">false</item>
        <!-- Kein Dynamic Color: das Framework-Theme wird von Compose ueberdeckt. -->
    </style>

    <!-- Tastatur-Utility-Buttons (IME bleibt View-basiert) — unveraendert. -->
    <style name="Key"> … </style>
</resources>
```
(`xmlns:tools="http://schemas.android.com/tools"` im `<resources>`-Tag ergänzen, oder das Attribut in `values-v27/themes.xml` auslagern.)

### 7.9 `ui/theme/Theme.kt`
```kotlin
package com.chris.whisperloom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Farben aus res/values/colors.xml uebernommen (kb_background, kb_surface, accent, recording ...).
private val Accent = Color(0xFF4C8DFF)
private val AccentPressed = Color(0xFF2F6FE0)
private val Recording = Color(0xFFFF4C4C)
private val Background = Color(0xFF1B1E24)
private val Surface = Color(0xFF2A2E37)
private val SurfaceHigh = Color(0xFF3A3F4B)
private val OnDark = Color(0xFFECEFF4)
private val OnDarkDim = Color(0xFF9AA0AD)

// Fest dunkel, KEIN dynamicDarkColorScheme (Entscheidung: Dynamic Color = nein).
// https://developer.android.com/develop/ui/compose/designsystems/material3
private val LoomColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentPressed,
    onPrimaryContainer = Color.White,
    secondary = OnDarkDim,
    onSecondary = Background,
    error = Recording,
    onError = Color.White,
    background = Background,
    onBackground = OnDark,
    surface = Surface,
    onSurface = OnDark,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnDarkDim,
    outline = OnDarkDim,
)

@Composable
fun WhisperLoomTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() bewusst ignoriert: App ist immer dunkel.
    MaterialTheme(
        colorScheme = LoomColors,
        typography = Typography(),
        content = content,
    )
}
```

### 7.10 Activity-Grundgerüst (z. B. `SettingsActivity`)
```kotlin
package com.chris.whisperloom

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chris.whisperloom.ui.theme.WhisperLoomTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // SystemBarStyle.dark(scrim): dunkle Leisten, helle Icons.
        // https://developer.android.com/reference/androidx/activity/EdgeToEdge
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            WhisperLoomTheme {
                SettingsScreen(prefs = Prefs(this))
            }
        }
    }
}
```
Innen `Scaffold { innerPadding -> … }` bzw. `Modifier.safeDrawingPadding()` verwenden, damit Inhalte nicht unter die Systemleisten rutschen.

### 7.11 Beispiel-Robolectric-Compose-Test — `app/src/test/java/com/chris/whisperloom/ui/SettingsScreenTest.kt`
```kotlin
package com.chris.whisperloom.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.16.1: SDK 35 laeuft mit JDK 17; SDK 36 braeuchte JDK 21 (Release-Notes 4.16).
// Explizit pinnen, damit ein spaeterer targetSdk-Sprung die Tests nicht still bricht.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun togglingLlmPolishShowsModelField() {
        compose.setContent {
            WhisperLoomTheme {
                SettingsScreen(state = fakeSettings(llmPolish = false), onEvent = {})
            }
        }
        compose.onNodeWithText("Nachbearbeitung durch LLM").assertIsDisplayed()
        compose.onNodeWithTag("switch_llm_polish").performClick()
        compose.waitForIdle() // PAUSED-Looper: Recomposition explizit abwarten
        compose.onNodeWithTag("field_llm_model").assertIsDisplayed()
    }
}
```
(`SettingsScreen`/`fakeSettings` sind Platzhalter für das kommende Compose-UI; `Modifier.testTag(...)` an den Elementen setzen.)

### 7.12 CI (`.github/workflows/build.yml`) — Minimaländerungen
- `actions/setup-java@v4` mit `java-version: '17'` bleibt (AGP 9.4 / Gradle 9.6: JDK 17 ok).
- `android-actions/setup-android@v3` bleibt; AGP lädt Platform 37 + Build-Tools 36.0.0 selbst nach (Lizenzen akzeptiert).
- Zusätzliches Artefakt für R8-Mapping:
```yaml
      - name: R8-Mapping sichern
        uses: actions/upload-artifact@v4
        with:
          name: whisperloom-release-mapping
          path: app/build/outputs/mapping/release/mapping.txt
          if-no-files-found: warn
```
- Optional `gradle/actions/setup-gradle@v4` für Gradle-Cache (Robolectric-android-all-Jars, Compose-AARs) — spart Minuten pro Lauf.

---

## 8. Migrations-Reihenfolge (wenn JDK/SDK installiert sind)

1. Wrapper auf 9.6.1, Root-Plugins auf AGP 9.4.0 + Compose-Plugin 2.2.10, `org.jetbrains.kotlin.android` entfernen, `kotlinOptions` entfernen, compileSdk 37.
   → `./gradlew --no-daemon help` (Konfiguration) und `./gradlew testDebugUnitTest` (bestehende 7 Tests grün — Refactor-Regel §7).
2. Compose-Dependencies + `buildFeatures.compose` + Theme.kt + themes.xml ergänzen; eine Activity auf `ComponentActivity`+`setContent` umstellen.
   → `assembleDebug`; Lint auf "requires compileSdk" prüfen.
3. Robolectric-Test aus 7.11 → `testDebugUnitTest` auf dem aarch64-VPS.
4. Release mit R8 → `assembleRelease`, APK-Größe messen, APK auf Gerät installieren; IME-Service, Accessibility-Service, Overlay und Share-Intent durchspielen (AP6: kein "fertig" ohne Gerätetest).
5. Vor Release-Tag: `git log`/`git diff` gegen Build-Stand (CLAUDE.md §7, WhisperLoom-Lesson 2026-08-20).

---

## 9. Risiken

1. **Kotlin 2.2.10 (gebündelt) vs. Compose 1.12 / Compose-Compiler-Kompatibilität** — Compose-Compiler-Plugin 2.2.10 ist offiziell an die Kotlin-Version gekoppelt; Compose-Runtime 1.12 gegen stdlib 2.1.20 gebaut → kompatibel. Ein Mindest-Compiler-Version-Check der Runtime ist nicht ausgeschlossen **[unsicher]**; Fehlerbild wäre `IncompatibleComposeRuntimeVersionException` beim App-Start.
2. **Kotlin > 2.2.10 (z. B. 2.4.10) mit AGP 9.4** liegt außerhalb der JetBrains-Matrix (AGP ≤ 9.1.0, Gradle ≤ 9.5.0).
3. **AGP-9-DSL-Syntax** (`unitTests.all`, `storeType`) gegen 9.4 nicht kompiliert — Snippets sind aus Doku abgeleitet, nicht gebaut.
4. **Robolectric-Hänger/Flakes** bei Compose-Tests mit Effekten/Coroutinen (Issues #7055, b/341880461) — Tests klein halten, `waitForIdle`/`mainClock` nutzen.
5. **R8 im Release**: erstmals Minify → Verhalten von IME/Accessibility/Overlay auf Gerät prüfen; `mapping.txt` archivieren.
6. **compileSdk-Bedarf Compose 1.9–1.11** (Fallback C) nicht belegt.
7. **SDK-Auto-Download in CI** für Platform 37/Build-Tools 36.0.0 vorausgesetzt, nicht getestet.
8. **Compose in IME/Overlay** ist mit dieser Konfiguration nicht abgedeckt (bewusst außerhalb des Scopes).

## 10. Offene Fragen

1. Kotlin: beim gebündelten 2.2.10 bleiben (Empfehlung) oder bewusst 2.4.10 pinnen (Sprachfeatures, aber außerhalb der Matrix)?
2. Soll targetSdk im Zuge des Umbaus auf 36 (Play-Regel) — mit Robolectric-Folge JDK 21 für SDK-36-Tests — oder bei 35 bleiben?
3. ViewModels (lifecycle-viewmodel-compose) einführen oder Prefs/State weiter direkt in den Screens halten?
4. Soll `core-splashscreen` genutzt werden (eigener Splash auf API 26–30) oder reicht das dunkle `windowBackground`?
5. Sollen Compose-UI-Tests in CI laufen (Netz für android-all-Jars, ~+1–2 Min) oder nur lokal?

---

## 11. Quellen

- AGP: [9.4.0](https://developer.android.com/build/releases/agp-9-4-0-release-notes) · [9.3.0](https://developer.android.com/build/releases/agp-9-3-0-release-notes) · [9.2.0](https://developer.android.com/build/releases/agp-9-2-0-release-notes) · [9.1.x](https://developer.android.com/build/releases/agp-9-1-0-release-notes) · [9.0.x](https://developer.android.com/build/releases/agp-9-0-0-release-notes) · [8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes) · [Übersicht](https://developer.android.com/build/releases/gradle-plugin) · [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) · [AGP 9.4.0 POM (KGP 2.2.10)](https://dl.google.com/android/maven2/com/android/tools/build/gradle/9.4.0/gradle-9.4.0.pom)
- Gradle: [Releases](https://gradle.org/releases/) · [Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html) · [Upgrading to 9](https://docs.gradle.org/current/userguide/upgrading_major_version_9.html)
- Kotlin: [Releases](https://kotlinlang.org/docs/releases.html) · [Gradle-Kompatibilität](https://kotlinlang.org/docs/gradle-configure-project.html) · [Compatibility Guide 2.3](https://kotlinlang.org/docs/compatibility-guide-23.html) · [Compose compiler options](https://kotlinlang.org/docs/compose-compiler-options.html) · [Evolution principles](https://kotlinlang.org/docs/kotlin-evolution-principles.html) · [JetBrains-Blog AGP 9](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
- Compose: [BOM-Mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) · [BOM 2026.08.00 POM](https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom) · [BOM 2026.06.01 POM](https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.06.01/compose-bom-2026.06.01.pom) · [compose-ui](https://developer.android.com/jetpack/androidx/releases/compose-ui) · [compose-runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime) · [compose-foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation) · [compose-material3](https://developer.android.com/jetpack/androidx/releases/compose-material3) · [compose-material](https://developer.android.com/jetpack/androidx/releases/compose-material) · [compose-kotlin](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) · [Compiler-Setup](https://developer.android.com/develop/ui/compose/compiler) · [Material 3 Theming](https://developer.android.com/develop/ui/compose/designsystems/material3) · [Material Icons Hinweis](https://developer.android.com/develop/ui/compose/graphics/images/material) · [Testing](https://developer.android.com/develop/ui/compose/testing) · [Compare metrics](https://developer.android.com/develop/ui/compose/migrate/compare-metrics)
- AndroidX: [activity](https://developer.android.com/jetpack/androidx/releases/activity) · [lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) · [navigation](https://developer.android.com/jetpack/androidx/releases/navigation) · [core](https://developer.android.com/jetpack/androidx/releases/core) · [test](https://developer.android.com/jetpack/androidx/releases/test) · [EdgeToEdge](https://developer.android.com/reference/androidx/activity/EdgeToEdge) · [SystemBarStyle](https://developer.android.com/reference/androidx/activity/SystemBarStyle) · [Edge-to-edge Guide](https://developer.android.com/develop/ui/views/layout/edge-to-edge) · [Splash-Migration](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) · [R.style](https://developer.android.com/reference/android/R.style)
- Robolectric: [GitHub Releases](https://github.com/robolectric/robolectric/releases) · [README](https://github.com/robolectric/robolectric/blob/master/README.md) · [Getting started](https://robolectric.org/getting-started/) · [Configuring](https://robolectric.org/configuring/) · [Android: Robolectric strategies](https://developer.android.com/training/testing/local-tests/robolectric) · [kmpbits Compose+Robolectric](https://www.kmpbits.com/posts/robolectric-compose/) · [dev.to pchmielowski](https://dev.to/pchmielowski/testing-jetpack-compose-without-emulator-or-device-1dni) · [Issue #7055](https://github.com/robolectric/robolectric/issues/7055) · [Issue #6018](https://github.com/robolectric/robolectric/issues/6018)
- R8/Größe: [Shrink code](https://developer.android.com/build/shrink-code) · [kotlinlang-Slack APK-Größe](https://slack-chats.kotlinlang.org/t/507909/anyone-noticed-that-apk-size-has-increased-drastically-when-) · [material-icons-extended 1.7.8 AAR](https://dl.google.com/android/maven2/androidx/compose/material/material-icons-extended-android/1.7.8/material-icons-extended-android-1.7.8.aar)
- Sonstiges: [Play target-sdk](https://developer.android.com/google/play/requirements/target-sdk) · [SDK-Auto-Download](https://developer.android.com/studio/intro/update) · [compileSdk-36-Lint-Meldungen (AnkiDroid)](https://github.com/ankidroid/Anki-Android/issues/19002)
