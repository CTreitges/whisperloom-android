#!/usr/bin/env bash
# Prueft die Paket-ID eines fertigen Builds: GitHub-/F-Droid-APK = com.chris.whisperloom, Play-AAB = com.whisperloom.
# Wichtig vor dem ERSTEN Play-Upload: die erste hochgeladene ID gehoert der App fuer immer.
#
# Aufruf: tools/check_package_id.sh <datei.apk|datei.aab> <erwartete-id>
# .aab braucht BUNDLETOOL (Pfad zum bundletool-all-*.jar), .apk braucht AAPT2 (Pfad zu aapt2).
set -euo pipefail

file=${1:?Datei fehlt}
expected=${2:?erwartete Paket-ID fehlt}

case "$file" in
  *.aab)
    bt() { java -jar "${BUNDLETOOL:?BUNDLETOOL nicht gesetzt}" dump manifest --bundle "$file" --xpath "$1"; }
    actual=$(bt /manifest/@package)
    info="versionCode $(bt /manifest/@android:versionCode), versionName $(bt /manifest/@android:versionName)"
    ;;
  *.apk)
    actual=$("${AAPT2:?AAPT2 nicht gesetzt}" dump packagename "$file")
    info="APK"
    ;;
  *)
    echo "::error::unbekannter Dateityp: $file"
    exit 2
    ;;
esac

if [ "$actual" != "$expected" ]; then
  echo "::error::$file hat Paket-ID '$actual', erwartet '$expected'"
  exit 1
fi
echo "$file: Paket-ID $actual ($info)"
