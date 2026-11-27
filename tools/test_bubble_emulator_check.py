"""Tests der reinen Auswertung in bubble_emulator_check.py (ohne Geraet).

    py -3 -X utf8 -m unittest tools/test_bubble_emulator_check.py
"""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bubble_emulator_check as c  # noqa: E402

DUMPSYS = """WINDOW MANAGER WINDOWS (dumpsys window windows)
  Window #3 Window{a1b2 u0 com.android.launcher3/.Launcher}:
    mDisplayId=0 rootTaskId=1 mSession=Session{x}
    mViewVisibility=0x0 mHaveFrame=true
  Window #4 Window{c3d4 u0 WhisperLoomBubble}:
    mDisplayId=0 mSession=Session{y}
    mViewVisibility=0x8 mHaveFrame=true
"""


class BubbleVisibleTest(unittest.TestCase):
    def test_gone_is_false(self):
        self.assertIs(c.bubble_visible(DUMPSYS), False)

    def test_visible_is_true(self):
        self.assertIs(c.bubble_visible(DUMPSYS.replace("mViewVisibility=0x8", "mViewVisibility=0x0")), True)

    def test_other_windows_do_not_count(self):
        # Nur das Launcher-Fenster (0x0): kein Knopf-Fenster -> None, nicht "sichtbar".
        launcher_only = DUMPSYS.split("  Window #4")[0]
        self.assertIsNone(c.bubble_visible(launcher_only))

    def test_invisible_0x4_is_false(self):
        self.assertIs(c.bubble_visible(DUMPSYS.replace("mViewVisibility=0x8", "mViewVisibility=0x4")), False)


class NodeBoundsTest(unittest.TestCase):
    XML = (
        '<hierarchy><node index="0" text="" resource-id="com.chris.whisperloom:id/check_field_a" '
        'class="android.widget.EditText" bounds="[48,300][1032,420]" />'
        '<node index="1" text="" resource-id="com.chris.whisperloom:id/bubble" bounds="[900,1500][1080,1680]" /></hierarchy>'
    )

    def test_finds_center(self):
        b = c.node_bounds(self.XML, "com.chris.whisperloom:id/check_field_a")
        self.assertEqual(c.Bounds(48, 300, 1032, 420), b)
        self.assertEqual((540, 360), b.center)

    def test_missing_is_none(self):
        self.assertIsNone(c.node_bounds(self.XML, "com.chris.whisperloom:id/check_field_b"))


class GeometryTest(unittest.TestCase):
    def test_cancel_target_center_420dpi(self):
        # 420 dpi = 2,625: Groesse 189 px, Rand 252 px -> top 2400-252-189 = 1959, Mitte 1959+94
        self.assertEqual((540, 2053), c.cancel_target_center(1080, 2400, 420))

    def test_wm_size_override_wins(self):
        self.assertEqual((1080, 2400), c.parse_wm_size("Physical size: 1080x2400\n"))
        self.assertEqual((720, 1600), c.parse_wm_size("Physical size: 1080x2400\nOverride size: 720x1600\n"))

    def test_wm_density(self):
        self.assertEqual(420, c.parse_wm_density("Physical density: 420\n"))
        self.assertEqual(320, c.parse_wm_density("Physical density: 420\nOverride density: 320\n"))


class DevicesTest(unittest.TestCase):
    def test_only_ready_devices(self):
        out = "List of devices attached\nemulator-5554\tdevice\nR58M\tunauthorized\n\n"
        self.assertEqual(["emulator-5554"], c.attached_devices(out))

    def test_none(self):
        self.assertEqual([], c.attached_devices("List of devices attached\n\n"))


class SdkDirTest(unittest.TestCase):
    def test_local_properties_unescaped(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "local.properties").write_text("sdk.dir=C\\:\\\\Users\\\\X\\\\Sdk\n", encoding="utf-8")
            self.assertEqual(Path("C:\\Users\\X\\Sdk"), c.sdk_dir(Path(tmp), env={}))

    def test_android_home_then_localappdata(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(Path("/opt/sdk"), c.sdk_dir(Path(tmp), env={"ANDROID_HOME": "/opt/sdk"}))
            self.assertEqual(Path("/home/u/AppData") / "Android" / "Sdk", c.sdk_dir(Path(tmp), env={"LOCALAPPDATA": "/home/u/AppData"}))


if __name__ == "__main__":
    unittest.main()
