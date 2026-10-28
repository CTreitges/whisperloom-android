# Zweckbestimmung

**WhisperLoom ist ein allgemeines Diktat-Werkzeug mit barrierearmer Bedienung: Es wandelt gesprochene Sprache in Text um und fügt ihn in andere Apps ein.**

Die Zweckbestimmung entscheidet, ob eine App als Medizinprodukt gilt (MDR Art. 2 Nr. 12). Dazu zählen nicht nur der Code, sondern auch App-Texte, Store-Beschreibung, Changelogs, Patchnotes, Anleitung, Kurzbefehle und die Namen von Modi und Profilen. Allgemeine Spracherkennung ohne medizinischen Zweck ist kein Medizinprodukt ([MDCG 2019-11 Rev. 1](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en), S. 35). Damit das so bleibt, gilt für alle Texte die Wortliste unten.

## Regeln

- WhisperLoom beschreibt, **was die Bedienung leistet**, nicht, für wen sie gedacht ist oder welche Einschränkung sie ausgleicht.
- Keine Mess-, Fortschritts- oder Übungsfunktionen (z. B. Verständlichkeit bewerten, Sprechen trainieren, Verlauf als Fortschritt zeigen).
- Modi und Profile heißen nach ihrer Funktion. Der vereinfachte Modus heißt **„Einfache Bedienung“**.
- Neue Texte in Einfacher Sprache.

## Wortliste

| Erlaubt (funktional) | Verboten (medizinisch oder zielgruppenbezogen) |
|---|---|
| Diktat, Diktieren, Spracheingabe, Sprache in Text | Therapie, therapeutisch, Behandlung |
| Einfache Bedienung, einfacher Modus | für Menschen mit Behinderung, behindertengerecht |
| barrierearm, ohne Ziehen, ohne Halten, Tippen statt Halten | Aphasie, Dysarthrie, Sprachstörung, Sprechstörung |
| große Schrift, große Knöpfe, Vorlesen, Töne, Vibration | Patient, Patientin, Diagnose, medizinisch, klinisch |
| langsam sprechen, längere Pausen, Pausen erlaubt | Rehabilitation, Reha, Logopädie |
| Bedienungshilfe, TalkBack, Switch Access, Voice Access | Übung, Training, Fortschritt (im Sinne von Lernfortschritt) |
| Vertrauensperson richtet ein, Einrichtungsdatei | Pflege, Betreuung (als Zweck der App) |

## Prüfung

Vor jedem Release, und wenn Texte dazukommen:

```
git grep -n -i -E "behinder|therapie|therapeut|aphasie|dysarthrie|sprachstörung|sprechstörung|patient|diagnose|medizinisch|klinisch|rehabilitation|logopäd" -- app/src/main/res fastlane CHANGELOG.md docs/ANLEITUNG.md
```

Erwartung: keine Treffer außer den bekannten technischen Stellen unten. „Übung“, „Training“ und „Fortschritt“ prüft man von Hand, weil sie auch harmlos vorkommen (z. B. Download-Fortschritt, „Inhalte zum Training nutzen“ beim Gemini-Hinweis).

Stand 2026-10-28 (Einführung dieser Liste), bewusst offen gelassen:

- `CHANGELOG.md` 3.x: „Diagnose am Rechner: `adb shell …`“ — Fehlersuche, kein medizinischer Bezug.
- `docs/ANLEITUNG.md` Anbieterwahl: „Für medizinische Diktate gibt es ‚Scribe v2 Medical‘“ — beschreibt ein Modell des Anbieters ElevenLabs. Vor einer Store-Beschreibung mit Barrierefreiheits-Bezug neutral fassen oder streichen.
