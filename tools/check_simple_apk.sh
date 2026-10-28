#!/usr/bin/env bash
# Prueft den Manifest-Schnitt der Simple-APK (Konzept WhisperLoom Simple §11.1): nur RECORD_AUDIO + INTERNET
# (plus das automatische DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION), keine Komponenten der Haupt-App
# (Bedienungshilfe, schwebender Knopf, Widget, Teilen-Ziel, Sprachauftrag) und keine Native-Bibliotheken.
#
# Aufruf: tools/check_simple_apk.sh <datei.apk> <pfad-zu-aapt2>
set -euo pipefail

apk=${1:?APK fehlt}
aapt2=${2:?aapt2 fehlt}

perms=$("$aapt2" dump permissions "$apk" | grep '^uses-permission:' | sed "s/.*name='\([^']*\)'.*/\1/" | sort)
expected=$(printf '%s\n' android.permission.INTERNET android.permission.RECORD_AUDIO \
  com.chris.whisperloom.simple.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | sort)
if [ "$perms" != "$expected" ]; then
  echo "::error::Simple-APK hat unerwartete Rechte:"; echo "$perms"; exit 1
fi

hits=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk" \
  | grep -c -E 'AccessibilityService|FloatingMicService|appwidget|ShareTranscribe|VoiceTask|ModelDownloadService' || true)
if [ "$hits" != "0" ]; then
  echo "::error::Simple-APK enthaelt Komponenten der Haupt-App ($hits Treffer im Manifest)"; exit 1
fi

if unzip -l "$apk" | grep -qE '\.so$'; then
  echo "::error::Simple-APK enthaelt Native-Bibliotheken:"; unzip -l "$apk" | grep -E '\.so$'; exit 1
fi

echo "$apk: Rechte, Komponenten und Bibliotheken wie vorgesehen"
