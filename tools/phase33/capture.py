#!/usr/bin/env python3
"""capture.py <label> — drive the app to each of the 14 spec §10 states and screencap.

Every state is VERIFIED by an expected-text assertion before the screenshot is taken, so a
mis-navigation aborts loudly instead of silently producing a false pixel diff later.
Assumes airplane mode ON (frozen data) and portrait, rotation locked.

Importable: nothing runs at import time, so capture2.py and diff.py can pull `surface_pixel`
and `decode_png` (and the navigation helpers) out of here.
"""
import re, struct, subprocess, sys, os, time, zlib

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

def node(want):
    """centre of the first node whose text == want, else None"""
    for m in re.finditer(r'<node[^>]*>', dump()):
        n = m.group(0)
        t = re.search(r'text="([^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if t and b and t.group(1).lower() == want.lower():
            x1, y1, x2, y2 = map(int, b.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def taptext(want, wait=2.5, required=True):
    c = node(want)
    if not c:
        if required: die(f"cannot find tappable text {want!r}")
        return False
    tap(c[0], c[1], wait); return True

def die(msg):
    print(f"ABORT: {msg}"); print("  visible:", texts()[:14]); sys.exit(1)

def decode_png(data, max_row=None):
    """(w, h, bpp, rows) for an 8-bit non-interlaced RGB/RGBA PNG - `adb exec-out screencap -p`.

    Pure stdlib on purpose: Pillow is not installed and PEP 668 blocks `pip install`. `rows` holds
    the unfiltered pixel rows 0..max_row inclusive (all h rows when max_row is None); w/h are always
    the true image dimensions.
    """
    pos, idat, w, h, bpp = 8, [], None, None, None
    while pos + 8 <= len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, depth, ct, _comp, _filt, interlace = struct.unpack(">IIBBBBB", chunk[:13])
            if depth != 8 or interlace != 0 or ct not in (2, 6):
                raise ValueError(f"unsupported PNG: depth={depth} colour_type={ct} interlace={interlace}")
            bpp = 4 if ct == 6 else 3
        elif typ == b"IDAT":
            idat.append(chunk)
        elif typ == b"IEND":
            break
        pos += 12 + ln
    if w is None:
        raise ValueError("PNG has no IHDR")

    raw = zlib.decompress(b"".join(idat))
    stride = w * bpp
    rows = h if max_row is None else min(h, max_row + 1)
    out = bytearray()
    prev = bytearray(stride)
    i = 0
    for _y in range(rows):
        f = raw[i]; i += 1
        line = bytearray(raw[i:i + stride]); i += stride
        if f:                                 # 0 = None, nothing to undo
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                if f == 1:   line[x] = (line[x] + a) & 255
                elif f == 2: line[x] = (line[x] + b) & 255
                elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
                elif f == 4:
                    p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                    pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                    line[x] = (line[x] + pr) & 255
                else:
                    raise ValueError(f"bad PNG filter type {f} on row {_y}")
        out += line
        prev = line
    return w, h, bpp, bytes(out)

def surface_pixel(png, x, y):
    """the "#rrggbb" of a single pixel of a screencap PNG"""
    w, h, bpp, rows = decode_png(png, max_row=y)
    if not (0 <= x < w and 0 <= y < h):
        raise ValueError(f"({x},{y}) is outside the {w}x{h} screenshot")
    k = y * w * bpp + x * bpp
    return "#%02x%02x%02x" % (rows[k], rows[k + 1], rows[k + 2])

def drawer_open():
    """open the nav-row drawer from any state"""
    for _ in range(3):
        t = texts()
        if "Home page" in t and "Notice board" in t:
            return
        if "Synchronise all" in t:            # profile list showing - collapse via scrim
            tap(1030, 700, 1.5); continue
        tap(74, 2256, 2.0)                    # hamburger
    die("drawer would not open")

# expect_surface is a measured (x, y, "#rrggbb") triple that pins the THEME of the captured frame -
# the text assertions alone cannot tell light from dark, which is exactly how the missing OK tap in
# the theme dialog hid for a whole phase. There is no single point that is theme surface on every
# screen: sampling (540,1400) across the dark set gives #1d1f26 on Home, #0d0e12 on Timetable,
# #76ff03 on Attendance and #ffffff on 12-lab (itself a dark state). The triples are therefore
# derived from a baseline capture in a later step; until then every call site passes None.
def shot(name, expect, expect_surface=None):
    """assert the state, then capture"""
    t = texts()
    for e in expect:
        if not any(e.lower() in x.lower() for x in t):
            die(f"state {name}: expected text {e!r} not present")
    # a transient snackbar in frame would diff against a later run that did not have one
    for bad in ("Brak internetu", "nie znaleziono", "Gotowe"):
        if any(bad in x for x in t):
            die(f"state {name}: transient snackbar/status {bad!r} in frame - re-run")
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
    print(f"  captured {name} ({len(png)} bytes)")

def relaunch():
    sh("shell", "am", "force-stop", PKG); time.sleep(1.5)
    sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity"); time.sleep(8)

def go(row, expect_after=None):
    """open the drawer and tap a nav row, scrolling the drawer if the row is below the fold"""
    drawer_open()
    if not node(row):
        # expanding "More" pushes the bottom rows (Notifications/Settings/Lab) off-screen
        for _ in range(3):
            sh("shell", "input", "swipe", "400", "1900", "400", "1000", "300"); time.sleep(1.2)
            if node(row):
                break
    taptext(row); time.sleep(1.5)

def main():
    set_out(sys.argv[1], clear=True)

    relaunch()
    shot("01-home-dark", ["lucky number"], expect_surface=(540, 1400, "dark"))

    go("Timetable", None);   shot("03-timetable", ["Timetable"])
    go("Agenda", None);      shot("04-agenda", ["Agenda"])
    go("Grades", None);      shot("05-grades", ["Grades"])
    go("Messages", None);    shot("06-messages", ["Inbox", "Search"])

    # 07-compose-dark is deliberately NOT captured: with the network frozen the composer cannot
    # load recipients and raises an error snackbar, so it is non-deterministic. Its FILLED
    # TextInputLayout (RichTextFieldBridge.kt:94) is checked by hand in Task 6 instead.

    go("Attendance", None);  shot("08-attendance", ["Attendance"])

    drawer_open()
    if not node("Notes"):
        taptext("More")
    taptext("Notes");        shot("09-notes", ["Notes"])

    go("Lab", None);         shot("12-lab", ["CLICK ME"])

    go("Home page", None)
    tap(1006, 2263, 3.0)                      # sheet button
    shot("13b-sheet-dark", ["Synchronise"], expect_surface=(540, 1400, "dark"))
    key("KEYCODE_BACK", 2.5)

    tap(1006, 220, 3.0)                       # toolbar avatar -> drawer + profile list
    shot("11-drawer", ["Add a new student", "Synchronise all"])
    # A scrim tap closes the drawer but leaves profileSelectionOpen set, so reopening shows the
    # profile list again. Relaunching is the only fully deterministic reset.
    relaunch()

    # Settings -> theme dialog (a MaterialAlertDialog), then switch to light
    go("Settings", None)
    taptext("Theme", 2.5)
    shot("10-settings-dialog", ["Theme"])
    taptext("Light", 6.0)
    # The radio row only moves ThemeChooserDialog's Compose state; the commit (config.ui.theme,
    # Themes.themeInt, recreate()) is in onPositiveClick(), so without OK the choice is discarded
    # on relaunch() and every *-light state below is captured in dark.
    taptext("OK", 6.0)                        # recreate()
    time.sleep(4)

    relaunch()
    shot("02-home-light", ["lucky number"], expect_surface=(540, 1400, "light"))
    tap(1006, 2263, 3.0)
    shot("13a-sheet-light", ["Synchronise"], expect_surface=(540, 1400, "light"))
    key("KEYCODE_BACK", 2.5)

    # restore dark for the next run's starting conditions
    go("Settings", None)
    taptext("Theme", 2.5)
    taptext("Dark", 6.0)
    taptext("OK", 6.0)
    time.sleep(4)
    relaunch()

    n = len(os.listdir(OUT))
    print(f"{sys.argv[1]}: {n} states captured")
    if n != 13:
        print("WARNING: expected 13"); sys.exit(1)

if __name__ == "__main__":
    main()
