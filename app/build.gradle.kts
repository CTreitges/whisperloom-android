plugins {
    id("com.android.application")
    // Compose-Compiler-Plugin (Kotlin selbst kommt built-in mit AGP 9).
    id("org.jetbrains.kotlin.plugin.compose")
}

// Native-Build (whisper.cpp via CMake/NDK) nur, wenn whisperloom.skipNative NICHT gesetzt ist.
// Der aarch64-Dev-VPS hat kein NDK (gibt es nur fuer x86_64-Hosts) und setzt die Property in
// ~/.gradle/gradle.properties: Kotlin, Tests und ein APK ohne .so bauen trotzdem. CI (x86_64) baut komplett.
val skipNative = providers.gradleProperty("whisperloom.skipNative").isPresent

android {
    namespace = "com.chris.whisperloom"
    // Compose 1.12 (BOM 2026.08.00) verlangt compileSdk 37 + AGP >= 9.2.0:
    // https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.0-alpha01
    compileSdk = 37
    if (!skipNative) {
        // NDK r28c: 16-KB-Page-Alignment per Default, stable, AGP-9.x-Default (research/whisper-cpp.md §3.1)
        ndkVersion = "28.2.13676358"
    }

    defaultConfig {
        applicationId = "com.chris.whisperloom"
        minSdk = 26
        // targetSdk 36 (Android 16): Pflicht fuer NEUE Play-Store-Apps/-Updates seit 31.08.2026
        // (developer.android.com/google/play/requirements/target-sdk). API 35 wird fuer Neu-Uploads
        // nicht mehr akzeptiert. compileSdk 37 deckt das ab. Laufzeit-Verhalten von Android 16
        // (erzwungenes edge-to-edge — App nutzt enableEdgeToEdge — u.a.) am Geraet gegenpruefen.
        targetSdk = 36
        versionCode = 9
        versionName = "3.5.0"

        if (!skipNative) {
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++17"
                    arguments += listOf(
                        "-DANDROID_STL=c++_static", // eine .so, kein libc++_shared.so
                        "-DANDROID_PLATFORM=android-26",
                    )
                    targets += "whisperloom"
                }
            }
            // Nur arm64: reale Zielgeraete. x86_64 nur fuer Emulator-Tests (CMake laesst dann GGML_CPU_ARM_ARCH weg).
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    if (!skipNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        packaging {
            // unkomprimiert + zip-aligned (AGP >= 8.5.1) -> 16-KB-Page-Size-Geraete
            jniLibs {
                useLegacyPackaging = false
            }
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME fuer Home-Fusszeile und Ueber-Sheet (AGP 9: Default aus).
        buildConfig = true
    }

    // Lint-ERRORS brechen den Build (CI-Schritt "Unit-Tests + Lint" ist sonst nur scheinbar gruen);
    // Warnungen scheitern nie (Default), die Reports bleiben erhalten.
    lint {
        abortOnError = true
    }

    signingConfigs {
        create("release") {
            // Keystore + Passwort kommen aus Umgebungsvariablen (CI: aus GitHub-Secrets).
            // Kein Secret im Repo. Fehlt der Keystore lokal, bleibt release unsigniert.
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
            if (!skipNative) {
                // whisper.cpp PR #3913: AGP-Debug laesst den Native-Build unoptimiert -> Erkennung unbrauchbar langsam.
                externalNativeBuild {
                    cmake {
                        arguments += "-DCMAKE_BUILD_TYPE=Release"
                    }
                }
            }
        }
        release {
            // R8 (Full Mode = AGP-Default) + Resource-Shrinking: Compose/M3 ohne Shrinker = mehrere MB DEX.
            // Stacktraces entschluesseln: app/build/outputs/mapping/release/mapping.txt
            // https://developer.android.com/build/shrink-code
            isMinifyEnabled = true
            isShrinkResources = true
            val ks = file(System.getenv("WHISPERLOOM_KEYSTORE") ?: "keystore/whisperloom-release.p12")
            if (ks.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (!skipNative) {
                // .so bleibt gestrippt; Symboltabelle separat unter app/build/outputs/native-debug-symbols
                ndk {
                    debugSymbolLevel = "SYMBOL_TABLE"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // kotlinOptions {} entfaellt: Built-in-Kotlin nimmt jvmTarget = targetCompatibility (17).
    // https://developer.android.com/build/migrate-to-built-in-kotlin

    testOptions {
        unitTests {
            // Pflicht fuer Robolectric (Themes, Strings, ui-test-manifest-Activity).
            isIncludeAndroidResources = true
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
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // kotlinx-coroutines (StateFlow fuer ModelDownloads) kommt transitiv ueber Compose (core 1.9.0) —
    // bewusst nicht doppelt deklariert.
    // Bewusst NICHT: material-icons-core/-extended (35,7 MB AAR, von Google nicht mehr empfohlen),
    // navigation-compose (State-Navigation reicht fuer ~6 Screens). Icons als eigene res/drawable/ic_*.xml.

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // ComponentActivity fuer createComposeRule(): https://developer.android.com/develop/ui/compose/testing
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Robolectric 4.16.1: SDK 23–36; SDK 35 laeuft mit JDK 17 — https://github.com/robolectric/robolectric/releases
    testImplementation("org.robolectric:robolectric:4.16.1")
    // https://developer.android.com/jetpack/androidx/releases/test
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.work:work-testing:2.11.2")
}
