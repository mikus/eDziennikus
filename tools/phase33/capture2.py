#!/usr/bin/env python3
"""capture2.py <label> — the surfaces the first 13-state set never reached.

Covers: the 3 md_* colours on Behaviour, row_announcements_item on Notice board,
md_black_1000 on the login chooser, fragment_debug's OutlinedBox TextInputLayout, plus
Homework / Teachers / Notifications / a message detail / the profile dialog / Settings root.
Same discipline as capture.py: assert the state, refuse transient snackbars, abort loudly.

Writes into the SAME /tmp/n5a/<label> directory as capture.py and never clears it - capture.py
is the only script in the chain that does.
"""
import re, subprocess, sys, os, time

from capture import surface_pixel

ADB = "/Users/mikolaj.olszewski/Library/Android/sdk/platform-tools/adb -s emulator-5554".split()
PKG = "eu.mikus.edziennik"
OUT = None

def set_out(label, clear=False):
    # Point this module's captures at /tmp/n5a/<label>. Only the FIRST script in a run clears it.
    global OUT
    OUT = f"/tmp/n5a/{label}"
    os.makedirs(OUT, exist_ok=True)
    if clear:
        for f in os.listdir(OUT):
            os.remove(f"{OUT}/{f}")
    return OUT

def sh(*a, timeout=120):
    return subprocess.run(ADB + list(a), capture_output=True, text=True, timeout=timeout).stdout

def tap(x, y, wait=1.5):
    sh("shell", "input", "tap", str(x), str(y)); time.sleep(wait)

def key(k, wait=1.5):
    sh("shell", "input", "keyevent", k); time.sleep(wait)

def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return sh("shell", "cat", "/sdcard/ui.xml")

def texts():
    return [m.group(1) for m in re.finditer(r'text="([^"]+)"', dump())]

def node(want, exact=True):
    for m in re.finditer(r'<node[^>]*>', dump()):
        n = m.group(0)
        t = re.search(r'text="([^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if t and b and ((t.group(1) == want) if exact else (want in t.group(1))):
            x1, y1, x2, y2 = map(int, b.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def taptext(want, wait=2.5, exact=True, required=True):
    c = node(want, exact)
    if not c:
        if required: die(f"cannot find {want!r}")
        return False
    tap(c[0], c[1], wait); return True

def die(msg):
    print(f"ABORT: {msg}"); print("  visible:", texts()[:14]); sys.exit(1)

def relaunch():
    sh("shell", "am", "force-stop", PKG); time.sleep(1.5)
    sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity"); time.sleep(8)

def drawer_open():
    for _ in range(3):
        t = texts()
        if "Home page" in t and "Notice board" in t:
            return
        if "Synchronise all" in t:
            relaunch(); continue
        tap(74, 2256, 2.0)
    die("drawer would not open")

def go(row):
    drawer_open()
    if not node(row):
        for _ in range(3):
            sh("shell", "input", "swipe", "400", "1900", "400", "1000", "300"); time.sleep(1.2)
            if node(row): break
    taptext(row); time.sleep(2.0)

# expect_surface is a measured (x, y, "#rrggbb") triple pinning the THEME of the captured frame;
# see the note above shot() in capture.py. No single point is theme surface on every screen, so the
# triples are derived from a baseline capture in a later step - every call site passes None for now.
def shot(name, expect, expect_surface=None):
    t = texts()
    for e in expect:
        if not any(e in x for x in t):
            die(f"state {name}: expected {e!r} not present")
    for bad in ("Brak internetu", "nie znaleziono", "Gotowe"):
        if any(bad in x for x in t):
            die(f"state {name}: transient {bad!r} in frame - re-run")
    png = subprocess.run(ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=120).stdout
    if expect_surface:
        x, y, want = expect_surface
        got = surface_pixel(png, x, y)
        r, g, b = int(got[1:3], 16), int(got[3:5], 16), int(got[5:7], 16)
        lum = (r + g + b) / 3.0
        # Luminance, not an exact colour: the point is to catch a frame captured in the wrong theme,
        # and an exact match would break whenever the app's data changes what sits at (x,y).
        ok = lum >= 140 if want == "light" else lum <= 115
        if not ok:
            die(f"state {name}: surface at ({x},{y}) is {got} (lum {lum:.0f}), "
                f"expected a {want} theme - wrong theme captured")
    open(f"{OUT}/{name}.png", "wb").write(png)
    print(f"  captured {name}")

def main():
    set_out(sys.argv[1])

    relaunch()

    go("Homework");      shot("14-homework", ["Homework"])
    go("Behaviour");     shot("15-behaviour", ["Behaviour"])
    go("Notice board");  shot("16-announcements", ["Notice board"])

    drawer_open()
    if not node("Teachers"):
        taptext("More")
    taptext("Teachers");  shot("17-teachers", ["Teachers"])

    go("Notifications"); shot("18-notifications", ["Notifications"])

    # Debug: reached through the bottom sheet, and it holds an OutlinedBox TextInputLayout
    go("Home page")
    tap(1006, 2263, 3.0)
    taptext("Debugging", 4.0); shot("19-debug", ["Run command"])

    # A message detail is NOT captured: it fetches the body over the network, so with the network
    # frozen it raises an error snackbar. No renamed layout is involved either - messages_list_item
    # is the list, already covered by 06-messages in the first set.

    # the profile config dialog, via a long press in the profile list
    tap(1006, 220, 3.0)
    c = node("Stanisław Olszewski")
    if not c: die("profile row not found")
    sh("shell", "input", "motionevent", "DOWN", str(c[0]), str(c[1])); sh("shell", "sleep", "1")
    sh("shell", "input", "motionevent", "UP", str(c[0]), str(c[1])); time.sleep(4)
    shot("22-profile-dialog", ["Profile name"])
    taptext("CLOSE", 2.5, required=False)
    relaunch()

    # the login chooser - md_black_1000 lives on this screen
    tap(1006, 220, 3.0)
    taptext("Add a new student", 5.0)
    shot("23-login-chooser", ["e-register"])
    key("KEYCODE_BACK", 4.0)
    relaunch()

    go("Settings");      shot("24-settings", ["Appearance"])

    # OUT is shared with capture.py and never cleared here, so this is the running total
    n = len(os.listdir(OUT))
    print(f"{sys.argv[1]}: {n} states now in {OUT}")

if __name__ == "__main__":
    main()
