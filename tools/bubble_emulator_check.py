#!/usr/bin/env python3
"""Prueft den schwebenden Knopf am laufenden Emulator oder Testgeraet (UX-Spec §6.22).

    py -3 -X utf8 tools/bubble_emulator_check.py visibility [--serial S] [--no-build] [--avd wl35]
                                                            [--pid-file DATEI] [--shots ORDNER]
    py -3 -X utf8 tools/bubble_emulator_check.py gestures [wie visibility]   # Ziehen, Halten + Wischen, ✕, Bilder
    py -3 -X utf8 tools/bubble_emulator_check.py disclosure --shots ORDNER   # Hinweis vorab als Bild
    py -3 -X utf8 tools/bubble_emulator_check.py stop --pid-file DATEI       # Emulator per gespeicherter PID

`visibility` baut das Debug-APK ohne NDK, installiert es, setzt Overlay-Recht und Bedienungshilfe per adb
und spielt die Faelle an der Debug-Pruefseite `BubbleCheckActivity` durch (Feld A, Feld B, Passwortfeld).
Sichtbar heisst: das Fenster `WhisperLoomBubble` hat in `dumpsys window` die View-Sichtbarkeit 0x0.
Jeder Fall gibt eine Zeile "ok"/"FEHLER" aus; Exit 0 nur, wenn alle ok sind.

Laeuft kein Geraet, startet das Skript das AVD (`--avd`) und haengt dessen PID als JSON-Zeile an
`--pid-file` an; `stop` beendet genau diesen Emulator (adb emu kill, sonst die gespeicherte PID).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PKG = "com.chris.whisperloom"
A11Y = f"{PKG}/{PKG}.a11y.TextInserterAccessibilityService"
CHECK_ACTIVITY = f"{PKG}/{PKG}.debug.BubbleCheckActivity"
WINDOW_TITLE = "WhisperLoomBubble"  # FloatingMicService.WINDOW_TITLE
CANCEL_MARGIN_DP = 96  # BubblePosition.CANCEL_MARGIN_DP
CANCEL_SIZE_DP = 72  # BubblePosition.CANCEL_SIZE_DP
MAX_SHOT_PX = 1200


# --- reine Auswertung (ohne Geraet testbar) ------------------------------------------------


@dataclass(frozen=True)
class Bounds:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def center(self) -> tuple[int, int]:
        return (self.left + self.right) // 2, (self.top + self.bottom) // 2


def bubble_visible(dumpsys_window: str) -> bool | None:
    """View-Sichtbarkeit des Knopf-Fensters: True = 0x0, False = sonst (GONE 0x8), None = kein Fenster."""
    blocks = re.split(r"\n(?=\s*Window #\d+)", dumpsys_window)
    for block in blocks:
        if WINDOW_TITLE not in block.split("\n", 1)[0]:
            continue
        m = re.search(r"mViewVisibility=0x([0-9a-fA-F]+)", block)
        if m:
            return int(m.group(1), 16) == 0
    return None


def bubble_frame(dumpsys_window: str) -> Bounds | None:
    """Rahmen des Knopf-Fensters in Bildschirm-Pixeln (`frame=[l,t][r,b]`). Ein Overlay einer anderen
    App fehlt im `uiautomator dump` des aktiven Fensters, deshalb aus `dumpsys window`."""
    blocks = re.split(r"\n(?=\s*Window #\d+)", dumpsys_window)
    for block in blocks:
        if WINDOW_TITLE not in block.split("\n", 1)[0]:
            continue
        m = re.search(r"\bframe=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block)
        if m:
            return Bounds(*(int(x) for x in m.groups()))
    return None


CROSS_TITLE = "WhisperLoomModeCross"  # ModeCrossWindow.WINDOW_TITLE


def cross_shown(dumpsys_window: str) -> bool:
    """Ob das Modus-Kreuz-Fenster gerade angehaengt ist."""
    return any(CROSS_TITLE in b.split("\n", 1)[0] for b in re.split(r"\n(?=\s*Window #\d+)", dumpsys_window))


def pref_value(prefs_xml: str, key: str) -> str | None:
    """Wert eines Eintrags aus shared_prefs/*.xml (string oder boolean), sonst None."""
    m = re.search(rf'<string name="{re.escape(key)}">([^<]*)</string>', prefs_xml)
    if m:
        return m.group(1)
    m = re.search(rf'<boolean name="{re.escape(key)}" value="(true|false)"\s*/>', prefs_xml)
    return m.group(1) if m else None


def right_mode(prefs_xml: str) -> str:
    """Ziel rechts im nach oben offenen Bogen: das letzte Ziel — "prompt", wenn eingeschaltet, sonst "summarize"."""
    return "prompt" if pref_value(prefs_xml, "refine_prompt_enabled") == "true" else "summarize"


def node_bounds(ui_xml: str, resource_id: str) -> Bounds | None:
    """Bounds des Knotens mit resource-id aus einem `uiautomator dump`."""
    for m in re.finditer(r"<node\b[^>]*>", ui_xml):
        node = m.group(0)
        if f'resource-id="{resource_id}"' not in node:
            continue
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            return Bounds(*(int(x) for x in b.groups()))
    return None


def cancel_target_center(width: int, height: int, density_dpi: int) -> tuple[int, int]:
    """Mitte des Ziels unten (CancelTarget: 96 dp ueber dem Rand, 72 dp gross)."""
    d = density_dpi / 160
    size = round(CANCEL_SIZE_DP * d)
    top = height - round(CANCEL_MARGIN_DP * d) - size
    return width // 2, top + size // 2


def parse_wm_size(text: str) -> tuple[int, int]:
    """`wm size` -> (Breite, Hoehe); "Override size" hat Vorrang."""
    sizes = dict(re.findall(r"(Physical|Override) size: (\d+x\d+)", text))
    w, h = (sizes.get("Override") or sizes["Physical"]).split("x")
    return int(w), int(h)


def parse_wm_density(text: str) -> int:
    dens = dict(re.findall(r"(Physical|Override) density: (\d+)", text))
    return int(dens.get("Override") or dens["Physical"])


def sdk_dir(repo: Path = REPO, env: dict | None = None) -> Path:
    """SDK aus local.properties (sdk.dir, escaped), sonst ANDROID_HOME, sonst %LOCALAPPDATA%/Android/Sdk."""
    env = os.environ if env is None else env
    props = repo / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line[len("sdk.dir="):].replace("\\:", ":").replace("\\\\", "\\"))
    if env.get("ANDROID_HOME"):
        return Path(env["ANDROID_HOME"])
    return Path(env.get("LOCALAPPDATA", "")) / "Android" / "Sdk"


def emulator_env(sdk: Path, env) -> dict[str, str]:
    """Umgebung fuer den Emulator: SDK-adb vorn im PATH. Ein aelteres adb im PATH startet sonst einen
    eigenen Server, der den des SDK abwirft ("server version doesn't match", Geraet bleibt offline)."""
    out = dict(env)
    out["ANDROID_SDK_ROOT"] = str(sdk)
    out["ANDROID_HOME"] = str(sdk)
    out["PATH"] = str(sdk / "platform-tools") + os.pathsep + env.get("PATH", "")
    return out


def attached_devices(adb_devices: str) -> list[str]:
    """Seriennummern im Zustand "device" aus `adb devices`."""
    out = []
    for line in adb_devices.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            out.append(parts[0])
    return out


# --- Geraet --------------------------------------------------------------------------------


class Device:
    def __init__(self, adb: Path, serial: str):
        self.adb = adb
        self.serial = serial

    def run(self, *args: str, check: bool = True, timeout: int = 60) -> str:
        r = subprocess.run(
            [str(self.adb), "-s", self.serial, *args],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout,
        )
        if check and r.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip() or r.stdout.strip()}")
        return r.stdout

    def sh(self, cmd: str, check: bool = True) -> str:
        return self.run("shell", cmd, check=check)

    def visible(self) -> bool | None:
        return bubble_visible(self.sh("dumpsys window windows"))

    def wait_visible(self, want: bool, timeout: float = 6.0) -> bool:
        """Wartet, bis der Knopf [want] ist; liefert den letzten Stand == want."""
        end = time.monotonic() + timeout
        while True:
            now = self.visible()
            if now == want or time.monotonic() > end:
                return now == want
            time.sleep(0.4)

    def ui_bounds(self, resource_id: str) -> Bounds | None:
        self.sh("uiautomator dump /sdcard/wl_ui.xml", check=False)
        return node_bounds(self.sh("cat /sdcard/wl_ui.xml", check=False), resource_id)

    def tap_id(self, resource_id: str) -> None:
        b = self.ui_bounds(resource_id)
        if b is None:
            raise RuntimeError(f"nicht gefunden: {resource_id}")
        x, y = b.center
        self.sh(f"input tap {x} {y}")

    def ime_shown(self) -> bool:
        return "mInputShown=true" in self.sh("dumpsys input_method", check=False)

    def wait_ime(self, want: bool, timeout: float = 6.0) -> bool:
        end = time.monotonic() + timeout
        while self.ime_shown() != want:
            if time.monotonic() > end:
                return False
            time.sleep(0.4)
        return True

    def screenshot(self, path: Path) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        r = subprocess.run([str(self.adb), "-s", self.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=60)
        path.write_bytes(r.stdout)
        shrink(path)
        return path


def shrink(path: Path) -> None:
    """Bild auf hoechstens 1200 px Kantenlaenge (Kontextbudget beim Lesen)."""
    from PIL import Image

    with Image.open(path) as im:
        im.thumbnail((MAX_SHOT_PX, MAX_SHOT_PX))
        im.save(path)


def adb_path() -> Path:
    exe = "adb.exe" if os.name == "nt" else "adb"
    return sdk_dir() / "platform-tools" / exe


def ensure_device(args) -> Device:
    adb = adb_path()
    listing = subprocess.run([str(adb), "devices"], capture_output=True, text=True).stdout
    serials = attached_devices(listing)
    if args.serial:
        if args.serial not in serials:
            sys.exit(f"Geraet {args.serial} nicht verbunden")
        return Device(adb, args.serial)
    if serials:
        return Device(adb, serials[0])
    return Device(adb, start_emulator(adb, args))


def start_emulator(adb: Path, args) -> str:
    exe = "emulator.exe" if os.name == "nt" else "emulator"
    emu = sdk_dir() / "emulator" / exe
    flags = 0x00000008 | 0x00000200 if os.name == "nt" else 0  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    log = open(Path(args.pid_file).with_suffix(".emulator.log") if args.pid_file else os.devnull, "w")
    p = subprocess.Popen(
        [str(emu), "-avd", args.avd, "-no-snapshot-save", "-no-audio", "-no-boot-anim"],
        stdout=log, stderr=subprocess.STDOUT, creationflags=flags, env=emulator_env(sdk_dir(), os.environ),
    )
    if args.pid_file:
        with open(args.pid_file, "a", encoding="utf-8") as f:
            f.write(json.dumps({"pid": p.pid, "kind": "emulator", "host": "local", "name": exe,
                                "note": f"{args.avd} bubble_emulator_check",
                                "ts": time.strftime("%Y-%m-%dT%H:%M:%S%z")}) + "\n")
    print(f"Emulator {args.avd} gestartet, PID {p.pid}")
    end = time.monotonic() + 300
    while time.monotonic() < end:
        if p.poll() is not None:
            sys.exit(f"Emulator beendet (Exit {p.returncode}) — Log neben {args.pid_file}")
        serials = attached_devices(subprocess.run([str(adb), "devices"], capture_output=True, text=True).stdout)
        if serials:
            d = Device(adb, serials[0])
            if d.sh("getprop sys.boot_completed", check=False).strip() == "1":
                return serials[0]
        time.sleep(3)
    sys.exit("Emulator nicht in 300 s gebootet")


def stop(args) -> int:
    """Beendet den zuletzt gespeicherten Emulator: erst adb emu kill, dann die gespeicherte PID."""
    rows = [json.loads(l) for l in Path(args.pid_file).read_text(encoding="utf-8").splitlines() if l.strip()]
    emus = [r for r in rows if r.get("kind") == "emulator"]
    adb = adb_path()
    for serial in attached_devices(subprocess.run([str(adb), "devices"], capture_output=True, text=True).stdout):
        if serial.startswith("emulator-"):
            subprocess.run([str(adb), "-s", serial, "emu", "kill"], capture_output=True)
    for r in emus:
        pid = int(r["pid"])
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
        else:
            try:
                os.kill(pid, 15)
            except ProcessLookupError:
                pass
        print(f"Emulator-PID {pid} beendet (oder schon weg)")
    return 0


# --- Ablauf ----------------------------------------------------------------------------------


def build_and_install(d: Device, build: bool) -> None:
    if build:
        gradlew = REPO / ("gradlew.bat" if os.name == "nt" else "gradlew")
        subprocess.run([str(gradlew), "-q", "-Pwhisperloom.skipNative=true", "assembleDebug"], cwd=REPO, check=True)
    apk = REPO / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    d.run("install", "-r", "-g", str(apk), timeout=300)


def prepare(d: Device) -> None:
    d.sh(f"appops set {PKG} SYSTEM_ALERT_WINDOW allow")
    d.sh("settings put secure show_ime_with_hard_keyboard 1")
    # Ziehen ohne Halten: Halte-Zeit hochsetzen, damit `input swipe` nicht als Halten zaehlt.
    d.sh("settings put secure long_press_timeout 1500")
    # `install -r` beendet den Prozess; die Bedienungshilfe steht danach unter "Crashed services" und
    # bindet erst nach Aus/Ein neu (derselbe Wert in den Settings loest nichts aus).
    set_a11y(d, False)
    set_a11y(d, True)


def set_a11y(d: Device, on: bool) -> None:
    services = A11Y if on else '""'
    d.sh(f"settings put secure enabled_accessibility_services {services}")
    d.sh(f"settings put secure accessibility_enabled {1 if on else 0}")
    time.sleep(2)


def open_check_page(d: Device, start_bubble: bool = False, bg: str | None = None) -> None:
    extra = " --ez start_bubble true" if start_bubble else ""
    if bg:
        extra += f" --es bg {bg}"
    d.sh(f"am start -W -n {CHECK_ACTIVITY}{extra}")
    time.sleep(1.5)


def focus_field(d: Device, rid: str) -> bool:
    d.tap_id(f"{PKG}:id/{rid}")
    return d.wait_ime(True)


def close_keyboard(d: Device) -> bool:
    d.sh("input keyevent KEYCODE_BACK")
    return d.wait_ime(False)


def drop_on_target(d: Device) -> None:
    b = bubble_frame(d.sh("dumpsys window windows"))
    if b is None:
        raise RuntimeError("Knopf-Fenster nicht gefunden")
    w, h = parse_wm_size(d.sh("wm size"))
    tx, ty = cancel_target_center(w, h, parse_wm_density(d.sh("wm density")))
    bx, by = b.center
    d.sh(f"input swipe {bx} {by} {tx} {ty} 900")
    time.sleep(1)


def visibility(args) -> int:
    d = ensure_device(args)
    shots = Path(args.shots) if args.shots else None
    build_and_install(d, build=not args.no_build)
    prepare(d)
    results: list[tuple[str, bool]] = []

    def case(name: str, ok: bool, shot: str | None = None) -> None:
        results.append((name, ok))
        print(f"{'ok    ' if ok else 'FEHLER'} {name}")
        if shot and shots:
            d.screenshot(shots / shot)

    try:
        open_check_page(d, start_bubble=True)
        d.sh("input keyevent KEYCODE_HOME")
        case("Home: unsichtbar", d.wait_visible(False), "vis-home.png")

        open_check_page(d)
        ime = focus_field(d, "check_field_a")
        case("Textfeld + Tastatur: sichtbar", ime and d.wait_visible(True), "vis-field.png")

        ime_closed = close_keyboard(d)
        case("Tastatur zu: unsichtbar", ime_closed and d.wait_visible(False), "vis-keyboard-closed.png")

        ime = focus_field(d, "check_field_pw")
        case("Passwortfeld: unsichtbar", ime and d.wait_visible(False), "vis-password.png")

        focus_field(d, "check_field_a")
        shown = d.wait_visible(True)
        drop_on_target(d)
        gone = d.wait_visible(False)
        close_keyboard(d)
        focus_field(d, "check_field_a")
        same = d.wait_visible(False, timeout=3)
        focus_field(d, "check_field_b")
        other = d.wait_visible(True)
        case("Ablegen: unsichtbar bis anderes Feld", shown and gone and same and other, "vis-after-drop.png")

        set_a11y(d, False)
        open_check_page(d)
        focus_field(d, "check_field_a")
        case("Bedienungshilfe aus: unsichtbar", d.wait_visible(False), "vis-a11y-off.png")
    finally:
        set_a11y(d, True)
        d.sh("settings put secure long_press_timeout 400", check=False)

    failed = [n for n, ok in results if not ok]
    print(f"{len(results) - len(failed)}/{len(results)} ok")
    return 1 if failed else 0


def read_prefs(d: Device) -> str:
    return d.sh(f"run-as {PKG} cat shared_prefs/whisperloom.xml", check=False)


def frame_center(d: Device) -> tuple[int, int] | None:
    b = bubble_frame(d.sh("dumpsys window windows"))
    return b.center if b else None


def hold_at(d: Device, x: int, y: int) -> None:
    """Finger aufsetzen und ueber die Halte-Zeit (500 ms, siehe gestures) liegen lassen."""
    d.sh(f"input motionevent DOWN {x} {y}")
    time.sleep(1.2)


def slide(d: Device, x0: int, y0: int, x1: int, y1: int, steps: int = 6) -> None:
    for i in range(1, steps + 1):
        d.sh(f"input motionevent MOVE {x0 + (x1 - x0) * i // steps} {y0 + (y1 - y0) * i // steps}")


def gestures(args) -> int:
    """A9/A10: Ziehen vor der Haltezeit verschiebt, Halten + Wischen setzt den Modus, ✕ setzt nichts; Bilder."""
    d = ensure_device(args)
    shots = Path(args.shots)
    build_and_install(d, build=not args.no_build)
    prepare(d)
    results: list[tuple[str, bool]] = []

    def case(name: str, ok: bool) -> None:
        results.append((name, ok))
        print(f"{'ok    ' if ok else 'FEHLER'} {name}")

    try:
        open_check_page(d, start_bubble=True, bg="dark")
        focus_field(d, "check_field_a")
        d.wait_visible(True)
        w, h = parse_wm_size(d.sh("wm size"))
        reach = 110 * parse_wm_density(d.sh("wm density")) // 160  # weiter als die Einrast-Schwelle (56 dp)
        mode_before = pref_value(read_prefs(d), "refine_mode")

        # Sofortiges Ziehen (Halte-Zeit 1500 ms aus prepare): verschiebt nur, in die Mitte ueber der Tastatur.
        start = frame_center(d)
        tx, ty = w // 2, h * 3 // 8
        if start:
            d.sh(f"input swipe {start[0]} {start[1]} {tx} {ty} 400")
        time.sleep(1.5)
        moved = frame_center(d)
        case(
            "Ziehen vor der Haltezeit verschiebt nur",
            moved is not None and abs(moved[0] - tx) < 60 and abs(moved[1] - ty) < 60
            and pref_value(read_prefs(d), "refine_mode") == mode_before and not cross_shown(d.sh("dumpsys window windows")),
        )
        cx, cy = moved or (tx, ty)

        d.sh("settings put secure long_press_timeout 500")
        time.sleep(1)
        expected = right_mode(read_prefs(d))

        # Halten: Kreuz kaum sichtbar; nach rechts wischen: deutlich; loslassen setzt den Modus.
        hold_at(d, cx, cy)
        opened = cross_shown(d.sh("dumpsys window windows"))
        d.screenshot(shots / "menu-faint-dark.png")
        slide(d, cx, cy, cx + reach, cy)
        d.screenshot(shots / "menu-visible-dark.png")
        d.sh(f"input motionevent UP {cx + reach} {cy}")
        time.sleep(1.5)
        mode_right = pref_value(read_prefs(d), "refine_mode")
        case(f"Halten + Wischen nach rechts setzt Modus ({mode_right}, erwartet {expected})", opened and mode_right == expected)

        # Halten + ✕ (nach unten): setzt nichts, Knopf bleibt an seiner Stelle.
        hold_at(d, cx, cy)
        slide(d, cx, cy, cx, cy + reach)
        d.sh(f"input motionevent UP {cx} {cy + reach}")
        time.sleep(1.5)
        still = frame_center(d)
        case(
            "Halten + ✕ setzt nichts",
            pref_value(read_prefs(d), "refine_mode") == expected
            and still is not None and abs(still[0] - cx) < 10 and abs(still[1] - cy) < 10,
        )

        # Heller Grund: Ruhe-Knopf, Kreuz deutlich (nach oben links), zurueck in die Mitte = nichts gewaehlt.
        open_check_page(d, bg="light")
        focus_field(d, "check_field_a")
        d.wait_visible(True)
        time.sleep(1)
        d.screenshot(shots / "idle-light.png")
        hold_at(d, cx, cy)
        slide(d, cx, cy, cx - reach * 7 // 10, cy - reach * 7 // 10)
        d.screenshot(shots / "menu-visible-light.png")
        slide(d, cx - reach * 7 // 10, cy - reach * 7 // 10, cx, cy)
        d.sh(f"input motionevent UP {cx} {cy}")
        time.sleep(1.5)
        case("Kreuz aufgezogen und zurueck: Modus bleibt", pref_value(read_prefs(d), "refine_mode") == expected)
    finally:
        d.sh("settings put secure long_press_timeout 400", check=False)

    failed = [n for n, ok in results if not ok]
    print(f"{len(results) - len(failed)}/{len(results)} ok")
    return 1 if failed else 0


def disclosure(args) -> int:
    """Hinweis vor dem Einschalten der Bedienungshilfe (Assistent Schritt 5) als Bild."""
    d = ensure_device(args)
    set_a11y(d, False)
    d.sh(f"am start -W -n {PKG}/.SetupActivity --es route setup --ei step 5")
    time.sleep(2)
    d.sh("uiautomator dump /sdcard/wl_ui.xml", check=False)
    ui = d.sh("cat /sdcard/wl_ui.xml", check=False)
    m = re.search(r'<node\b[^>]*text="Bedienungshilfe aktivieren"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', ui)
    if not m:
        print("FEHLER Knopf 'Bedienungshilfe aktivieren' nicht gefunden")
        return 1
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    d.sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
    time.sleep(1.5)
    out = d.screenshot(Path(args.shots) / "disclosure.png")
    print(f"ok     {out}")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["visibility", "gestures", "disclosure", "stop"])
    ap.add_argument("--serial")
    ap.add_argument("--avd", default="wl35")
    ap.add_argument("--no-build", action="store_true")
    ap.add_argument("--pid-file", default=str(Path.home() / ".claude/backups/wl-bubble-textfeld-work/pids.jsonl"))
    ap.add_argument("--shots", default=str(Path.home() / ".claude/backups/wl-bubble-textfeld-work/shots"))
    args = ap.parse_args(argv)
    return {"visibility": visibility, "gestures": gestures, "disclosure": disclosure, "stop": stop}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
