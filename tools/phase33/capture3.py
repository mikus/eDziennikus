#!/usr/bin/env python3
"""capture3.py <label> — the manifest surfaces neither of the first two scripts reaches.

Covers the attendance details dialog, the Material date and time pickers,
dialog_generate_block_timetable, both code-built TextInputLayout modes (note editor DIALOG,
composer FILLED), a message attachment chip and its PopupMenu, the Lab profile dropdown's
PopupMenu, the crash screen, a tinted-light theme and an active text selection.

A thin client of capture.py: every helper is imported, none is re-implemented. Writes into the
SAME /tmp/n5a/<label> directory as the other two and never clears it — capture.py is the only
script in the chain that does. Clearing here would delete the other 22 states, and Gate 2 would
then compare two equally-truncated sets and report IDENTICAL.

Run it LAST: it is the only script that changes the theme, and its restore step at the bottom is
what puts the device back on Dark for the next run.
"""
import os, re, subprocess, sys, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from capture import (ADB, die, drawer_open, dump, go, key, node, relaunch, set_out, sh, shot,
                     surface_pixel, tap, taptext)

# Every state below is captured on profile 2. App.config.lastProfileId survives relaunch() (which is
# only force-stop + start), so a leaked profile switch would silently re-base every later state —
# hence the name goes into the assertions of every shot that can see the toolbar subtitle.
PROFILE = "Wojciech Olszewski"

FAB = (539, 2237)           # per-screen FAB
SHEET = (1006, 2263)        # bottom-sheet button


def longpress(x, y, hold=1.5, wait=3.5):
    """down, hold, up — `input tap` is far too short to trigger onLongClick"""
    sh("shell", "input", "motionevent", "DOWN", str(x), str(y)); time.sleep(hold)
    sh("shell", "input", "motionevent", "UP", str(x), str(y)); time.sleep(wait)


def longpress_text(want, **kw):
    c = node(want)
    if not c:
        die(f"cannot find {want!r} to long-press")
    longpress(c[0], c[1], **kw)


def node_lowest(want):
    """centre of the BOTTOM-MOST node whose text == want.

    Settings carries three identical "More" expanders and node()/taptext() always take the first;
    the dev crash action lives behind the last one.
    """
    best = None
    for m in re.finditer(r"<node[^>]*>", dump()):
        n = m.group(0)
        t = re.search(r'text="([^"]*)"', n)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if t and b and t.group(1).lower() == want.lower():
            x1, y1, x2, y2 = map(int, b.groups())
            if best is None or y2 > best[1]:
                best = ((x1 + x2) // 2, (y1 + y2) // 2)
    return best


def scroll_bottom(times=6):
    """fling the current list to its end.

    Over-scroll clamps, so repeating past the end is idempotent and the resulting offset is
    reproducible — unlike a single fling, which is not (measured: two identical `input swipe`
    calls from the same state left Settings 235k pixels apart). Any state whose frame shows a
    scrolled list must be anchored on this clamp, never on a bare fling.
    """
    for _ in range(times):
        sh("shell", "input", "swipe", "540", "1900", "540", "900", "200"); time.sleep(1.0)


def drag(x, y0, y1, steps=6):
    """scroll without momentum: a held pause before UP zeroes the velocity tracker, so the list
    moves by exactly the drag distance and stops. Deterministic, where a fling is not."""
    sh("shell", "input", "motionevent", "DOWN", str(x), str(y0))
    for i in range(1, steps + 1):
        sh("shell", "input", "motionevent", "MOVE", str(x), str(y0 + (y1 - y0) * i // steps))
    time.sleep(0.6)
    sh("shell", "input", "motionevent", "UP", str(x), str(y1))
    time.sleep(1.5)


def assert_highlight(out, name, inside, outside):
    """abort unless a text-selection highlight is actually in frame.

    The floating selection toolbar lives in its own window and never appears in a uiautomator
    dump, so shot()'s text assertions cannot tell a selected notice from an unselected one — a
    long-press that silently fails (as it does on Behaviour, see the note in main) would capture
    a plain dialog and pass. Compare a point inside the selected word against the dialog
    background on the same row instead. Deliberately a *difference*, not a colour: entry H is the
    highlight colour changing, so pinning today's value would abort every post-33 run.
    """
    png = open(f"{out}/{name}.png", "rb").read()
    got, bg = surface_pixel(png, *inside), surface_pixel(png, *outside)
    if got == bg:
        die(f"state {name}: no selection highlight at {inside} — it is {got}, the same as the "
            f"background at {outside}; the long-press did not take")


def main():
    # NO clear=True — see the module docstring.
    out = set_out(sys.argv[1])

    relaunch()

    # --- entry 3: the attendance details dialog (a ComposeDialog hosting a code-built MaterialButton)
    go("Attendance")
    taptext("List", 2.5)                      # pin the tab; Attendance restores the last one used
    taptext("Monday • Jun 22 • 13:25 • lesson 7", 3.0)
    shot("25-details-attendance", ["Attendance ID", "44034688", "NOTES", "CLOSE"])
    taptext("CLOSE", 2.5)

    # --- entry 9a: MaterialDatePicker, and entry 5: dialog_generate_block_timetable
    go("Timetable")
    tap(*SHEET, 3.0)
    taptext("Choose date", 3.5)
    shot("26-datepicker", ["SELECT DATE", "CANCEL", "OK"])
    taptext("CANCEL", 3.0)

    tap(*SHEET, 3.0)
    taptext("Save the timetable as an image", 3.5)
    shot("28-genblock", ["Range of the timetable", "For selected week", "Printable", "CANCEL"])
    taptext("CANCEL", 2.5)                    # never SAVE — it writes a file

    # --- entry 9b: MaterialTimePicker. Quiet hours, NOT the event dialog: EventManualDialog seeds
    # Time.getNow() and so renders a different frame every run. This one is pinned at 22:30.
    # The Settings list shows through beside the picker, so its offset is anchored on the bottom
    # clamp and then dragged back up by a fixed amount.
    go("Settings")
    scroll_bottom(5)
    drag(540, 900, 1500)
    taptext("Quiet hours", 3.0)
    taptext("Set start time", 3.5)
    shot("27-timepicker", ["SET START TIME", "22", "30", "CANCEL"])
    taptext("CANCEL", 2.5)                    # one CANCEL closes the picker AND the quiet-hours dialog

    # --- entry 13a: the note editor's code-built TextInputLayout, DIALOG mode
    drawer_open()
    if not node("Notes"):
        taptext("More")
    taptext("Notes", 3.0)
    tap(*FAB, 3.5)
    shot("29-noteeditor", ["Edit note", "Note text", "Color", "CANCEL"])
    taptext("CANCEL", 3.0)

    # --- entry 13b: the same widget in FILLED mode, in the composer.
    # The composer autofocuses "To", which raises the IME and with it a floating IME toolbar that
    # sits over the page — one BACK dismisses the keyboard and stays on the screen. Nothing here
    # touches the network: the recipient list is only fetched when the dropdown is opened.
    go("Messages")
    tap(*FAB, 5.0)
    key("KEYCODE_BACK", 2.5)                  # hide the IME, do not leave
    shot("30-compose", ["To", "Subject", "0/150", "33/20000", "Compose", PROFILE])
    key("KEYCODE_BACK", 3.0)                  # now leave

    # --- entry 6: a message showing an attachment chip. Already read (so the unread count does not
    # move) and its body is cached, so opening it does not hit the frozen network.
    taptext("Całoroczna zgoda na wycieczki, wyjścia obowiązująca w roku szkolnym 2026/2027", 5.0)
    shot("31-msg-attachment",
         ["12. Zgoda rodziców na wycieczki 2026.pdf", "Be Montessori Sekretariat", PROFILE])

    # --- entry 9c: the chip's PopupMenu. LONG-press only: a plain tap on the chip calls
    # downloadAttachment() (AttachmentsView.kt:53-60), and "Download again" does the same.
    longpress_text("12. Zgoda rodziców na wycieczki 2026.pdf")
    shot("32-attach-popup", ["Download again"])   # the popup is its own window; nothing else dumps
    key("KEYCODE_BACK", 2.5)
    key("KEYCODE_BACK", 3.0)

    # --- entry 9c: the Lab profile dropdown's PopupMenu. The Lab page shows through, so the list is
    # anchored on its bottom clamp. Do NOT tap a row — that navigates and the switch persists.
    # The row texts double as the profile/archive-state guard for the whole run.
    go("Lab")
    scroll_bottom(6)
    c = node(f"2 {PROFILE} archived false")
    if not c:
        die("Lab profile dropdown missing or not on profile 2 — a profile switch or archive leaked")
    tap(c[0], c[1], 3.0)
    shot("33-lab-dropdown", ["1 Jan Szkolny archived false", f"2 {PROFILE} archived false",
                             "3 Tomasz Olszewski archived false", "4 Stanisław Olszewski archived false"])
    key("KEYCODE_BACK", 2.5)

    # --- entry H: an active text selection, for android:textColorHighlight.
    # NOT the Behaviour notice the spec names first: row_notices_item's TextView is selectable in
    # XML, but NoticesAdapter.kt:87 then calls BetterLink.attach(), whose BetterLinkMovementMethod
    # extends LinkMovementMethod — canSelectArbitrarily() is false, so isTextSelectable() is false
    # and the long-press never starts a selection (verified on device: two selection attempts and
    # an unselected frame were pixel-identical). dialog_announcement.xml:14 is the same entry's
    # other selectable surface and gets no BetterLink, so selection works there.
    # The long-press point is over a plain word: land it on the date further up and Android's text
    # classifier swaps the toolbar for a date chip.
    go("Notice board")
    taptext("XI Pomorska Konferencja Montessori", 4.0)
    longpress(430, 1300)
    shot("36-selection", ["XI Pomorska Konferencja Montessori", "Pomorską Konferencję"])
    assert_highlight(out, "36-selection", inside=(470, 1247), outside=(800, 1247))
    key("KEYCODE_BACK", 2.5)                  # drop the selection
    taptext("OK", 3.0)

    # --- entries 4 and 4b: the crash screen. CrashActivity cannot be started with `am start` — it
    # finish()es without a crash-config intent and is exported="false" — so the dev action is the
    # only route. It is behind the LAST of Settings' three "More" expanders.
    go("Settings")
    scroll_bottom(5)
    more = node_lowest("More")
    if not more:
        die("Settings: no 'More' expander found for the dev crash action")
    tap(more[0], more[1], 3.0)
    taptext("Click to throw an unexpected exception", 6.0)
    shot("34-crash", ["RESTART APPLICATION", "ERROR DETAILS", "unexpected error"])
    relaunch()

    # --- entry 12: a tinted-light theme. The surface pin is the point of this state: the text
    # assertions cannot tell Amber from Dark, which is exactly how the missing OK tap hid before.
    go("Settings")
    taptext("Theme", 2.5)
    for _ in range(4):
        if node("Amber"):
            break
        sh("shell", "input", "swipe", "540", "1600", "540", "1100", "300"); time.sleep(1.2)
    taptext("Amber", 6.0)
    taptext("OK", 6.0)                        # the radio row alone only moves Compose state;
    time.sleep(4)                             # onPositiveClick() is what writes config.ui.theme
    relaunch()
    # Behaviour, NOT Home: Home is painted entirely by Compose, whose palette (appColorScheme) never
    # reads the XML theme, so Home-under-Amber is byte-identical to Home-under-Light and would prove
    # nothing. Behaviour is an XML RecyclerView whose row_notices_item is a 4dp MaterialCardView --
    # exactly the elevated MaterialShapeDrawable that manifest entry V-L's tint lands on.
    go("Behaviour")
    shot("35-amber", ["Behaviour"], expect_surface=(540, 1400, "light"))

    # --- restore Dark. capture.py's own restore runs at the end of the FIRST script and cannot undo
    # a theme set here; left as-is, every later run — pre33a, post33a, post33b — would open under
    # Amber, both sides would match, and Gate 2 would report IDENTICAL while AppTheme.Dark, the
    # theme this phase flips, was never pixel-verified.
    go("Settings")
    taptext("Theme", 2.5)
    taptext("Dark", 6.0)
    taptext("OK", 6.0)
    time.sleep(4)
    relaunch()
    png = subprocess.run(ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=120).stdout
    got = surface_pixel(png, 540, 1400)
    lum = sum(int(got[i:i + 2], 16) for i in (1, 3, 5)) / 3.0
    if lum > 115:
        die(f"theme restore failed: home surface is {got} (lum {lum:.0f}), expected Dark — the next "
            f"run would capture every 'dark' state on a tinted theme")
    go("Settings")
    if not node("Dark"):
        die("theme restore failed: Settings does not report Dark as the selected theme")

    n = len(os.listdir(out))
    print(f"{sys.argv[1]}: {n} states now in {out}")
    if n != 34:
        print("WARNING: expected 34 (13 + 9 + 12)")


if __name__ == "__main__":
    main()
