#!/usr/bin/env python3
"""
Phase 38 capture: the 7 light themes (subject) + Dark, Black/OLED, Red (control).

Two sets, deliberately. `tools/phase33/diff.py` exits non-zero when ANY pair differs, so running it
over the merged set reports FAIL(7) on a fully successful change. control/ is gated by diff.py;
subject/ is read by eye against the spec's PASS criterion.

Preconditions (capture.py's contract): airplane mode ON, portrait, rotation locked.
"""
import os, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "phase33"))
from capture import (ADB, node, taptext, tap, texts, die,  # noqa
                     surface_pixel, relaunch, sh, go)

# (id, chooser label, filename slug). Labels are the values-en strings EXACTLY - node() matches
# `t.group(1).lower() == want.lower()` (capture.py:48), so "Light" does not also match "Light blue".
# The slug is a separate field because theme_black is "Black / OLED" (strings.xml:1067), which
# contains a path separator and cannot go in a filename.
SUBJECT = [(0, "Light", "light"), (5, "Light yellow", "light-yellow"),
           (8, "Light blue", "light-blue"), (11, "Light purple", "light-purple"),
           (14, "Light red", "light-red"), (16, "Amber", "amber"),
           (17, "Light green", "light-green")]
CONTROL = [(1, "Dark", "dark"), (2, "Black / OLED", "black-oled"), (13, "Red", "red")]
LIGHT_IDS = {t[0] for t in SUBJECT}

# Red (id 13) is the one theme whose `isDark` flag and rendering disagree: Themes.kt:31 flags it
# `isDark = true`, so it takes the bar's dark arm, but its surface is `#ff5e5e` and its derived
# `onSurface` is the LIGHT ink `#181c21` (measured). Home samples `#e55757`, luminance 134 - inside
# capture.py's deliberate dead zone between light (>=140) and dark (<=115), so NEITHER bucket fits
# and any luminance assertion on it is wrong whichever way it is written. It is verified by the
# Settings read-back alone; as a control frame it is additionally pinned by diff.py, which requires
# it byte-identical across the two sides. This is the same disagreement the design records as the
# reason Red keeps white-on-#FF6C6C at 2.76:1.
LUMINANCE_UNCLASSIFIABLE = {13}

# Inside the bottom bar, above the gesture inset and well left of the centre-docked FAB.
# capture.py taps the hamburger at (74, 2256) and the sheet button at (1006, 2263), so the bar
# spans that row; x=270 sits between the hamburger and the FAB. 1080x2400 AVD.
BAR_PX = (270, 2263)

# Per-theme `colorScheme.surfaceContainer`, measured from the built code (Task 2 of the plan), not
# hand-derived: two independent derivations disagreed in the last bit on 3 of 7. This is the only
# per-theme discriminator the after side has - a luminance bucket cannot tell Amber from LightYellow
# from Light - so it is asserted, not printed.
AFTER_EXPECT = {0: "#f3f3f4", 5: "#f3f3d0", 8: "#cfe6f0", 11: "#eccff2",
                14: "#eccfd0", 16: "#f3cd58", 17: "#e7efd0"}


def out_dir(side, kind):
    d = os.path.join(HERE, side, kind)
    if os.path.isdir(d):                       # capture3.py deliberately does not clear; we do
        for f in os.listdir(d):
            os.remove(os.path.join(d, f))
    else:
        os.makedirs(d)
    return d


def screencap():
    return subprocess.run(ADB + ["exec-out", "screencap", "-p"],
                          capture_output=True, timeout=120).stdout


def dismiss_nag():
    """
    Clear the "problem with synchronization / app manager" dialog if it is up.

    It is a side effect of the airplane mode capture.py's contract requires: every sync fails, and
    the app offers to open battery-optimization settings. Left alone it blocks drawer_open() and
    would land in captured frames. "DON'T ASK AGAIN" is the correct button - "OK" leaves the app for
    system settings. Tapped by coordinate rather than taptext(): the dialog animates in, and a tap
    dispatched too early is swallowed.
    """
    for _ in range(3):
        c = node("DON'T ASK AGAIN")
        if not c:
            return
        tap(c[0], c[1], 3.0)
        time.sleep(1.5)
    if node("DON'T ASK AGAIN"):
        die("the sync/app-manager dialog will not dismiss - it would appear in captured frames")


def goto_theme_setting():
    relaunch()
    dismiss_nag()
    go("Settings", None)


def open_theme_dialog():
    goto_theme_setting()
    taptext("Theme", 2.5)


def pick(label):
    """select `label` in the chooser, scrolling until rendered, then COMMIT with OK"""
    open_theme_dialog()
    for _ in range(8):
        if node(label):
            break
        # capture3.py:219-222's proven swipe for this exact dialog. No priming tap: every
        # RadioRow's whole row is clickable (ThemeChooserDialog.kt:42), so a tap "inside the
        # list" selects a theme.
        sh("shell", "input", "swipe", "540", "1600", "540", "1100", "300")
        time.sleep(0.8)
    else:
        die(f"theme row {label!r} never became visible in the chooser")
    taptext(label, 6.0)
    # The radio row only moves ThemeChooserDialog's Compose state; the commit (config.ui.theme,
    # Themes.themeInt, recreate()) is in onPositiveClick(). Without OK the choice is DISCARDED on
    # the next relaunch and every frame below is captured in the previous theme.
    if not taptext("OK", 6.0, required=False):
        die(f"no OK button in the theme dialog for {label!r} - selection would be discarded")
    time.sleep(4)


def verify_applied(label, want_light):
    """
    Read back from Settings with the dialog CLOSED - capture3.py:250-252.

    `want_light=None` skips the luminance leg entirely - see LUMINANCE_UNCLASSIFIABLE.

    Reading it with the dialog OPEN is vacuous: ThemeChooserDialog.kt:38-44 composes a row for all
    18 themes on every open regardless of selection, and node() reads only text= and bounds=
    (capture.py:45-51), never the RadioButton's checked state. That check passes identically when
    OK was never committed - which is exactly the recorded capture-harness failure mode.

    The luminance leg (capture3.py:244-249) is the second half: a summary string can match while
    the frame is still rendered in the wrong family.
    """
    goto_theme_setting()
    if not node(label):
        die(f"theme read-back failed: Settings does not report {label!r} as the selected theme")
    relaunch()
    dismiss_nag()
    png = screencap()
    got = surface_pixel(png, 540, 1400)
    lum = sum(int(got[i:i + 2], 16) for i in (1, 3, 5)) / 3.0
    if want_light is None:
        print(f"    ({label}: home surface {got}, lum {lum:.0f} - luminance leg skipped, "
              f"read-back only)", flush=True)
        return
    if (lum < 140) if want_light else (lum > 115):
        die(f"{label}: home surface {got} (lum {lum:.0f}) is the wrong theme family")


def capture(side, kind, rows, screen, expect_px=None):
    d = out_dir(side, kind)
    results = []
    for tid, label, slug in rows:
        pick(label)
        verify_applied(label,
                       want_light=None if tid in LUMINANCE_UNCLASSIFIABLE else (tid in LIGHT_IDS))
        if screen == "messages":
            go("Messages", None)
            # FabAttentionEffect extends the FAB 1 s after the screen arms one and holds it 2 s
            # (AppBottomBar.kt:88-89). Capturing inside that window makes the frame
            # non-deterministic and an extended FAB can reach BAR_PX.
            time.sleep(4)
        t = texts()
        for bad in ("Brak internetu", "nie znaleziono", "Gotowe"):
            if any(bad in x for x in t):
                die(f"{label}: transient snackbar {bad!r} in frame - re-run")
        png = screencap()
        px = surface_pixel(png, *BAR_PX)
        name = f"{tid:02d}-{slug}-{screen}"
        if expect_px and tid in expect_px and px != expect_px[tid]:
            die(f"{name}: bar pixel {px}, expected {expect_px[tid]} - wrong theme, or BAR_PX is "
                f"outside the bar. Do not proceed; the after-comparison would measure nothing.")
        open(os.path.join(d, name + ".png"), "wb").write(png)
        print(f"  {name}: bar pixel {px}", flush=True)
        results.append((tid, label, px))
    with open(os.path.join(HERE, side, f"{kind}-pixels.txt"), "w") as f:
        for tid, label, px in results:
            f.write(f"{tid:02d} {label} {px}\n")
    return results


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("before", "after"):
        die("usage: capture38.py before|after")
    side = sys.argv[1]
    # before: every light bar must still be the hardcoded blue - the premise of the whole phase.
    # after: pass the per-theme surfaceContainer measured in Task 2, the only discriminator there is.
    expect = {tid: "#2196f3" for tid, *_ in SUBJECT} if side == "before" else AFTER_EXPECT
    print(f"== {side}: control (must be byte-identical across sides) ==", flush=True)
    c = capture(side, "control", CONTROL, "home")
    print(f"== {side}: subject, Home ==", flush=True)
    s = capture(side, "subject", SUBJECT, "home", expect_px=expect)
    print(f"== {side}: subject, Messages - one frame, the FAB-bearing check ==", flush=True)
    m = capture(side, "subject-fab", [SUBJECT[3]], "messages", expect_px=expect)
    pick("Dark"); verify_applied("Dark", want_light=False)   # restore starting conditions
    print("\nbar pixels:", flush=True)
    for tid, label, px in c + s + m:
        print(f"  {tid:>2} {label:<14} {px}", flush=True)


if __name__ == "__main__":
    main()
