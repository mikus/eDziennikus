#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Join a ThemeAttrProbe delta against Phase 33's attribute disposition table.

Usage:  python3 tools/phase33/check_disposition.py <delta.txt> <attr-disposition.tsv>

This is the machine half of the spec's §2.2 gate: *every one of the 155 rows gets
exactly one disposition*.  The table (docs/phase33/attr-disposition.tsv) carries the
human half - which §7 manifest entry owns a row, or the one-line reachability
disproof that clears it.

Delta format (one row per theme x attribute):

    ~ <theme>\t<attr>\t<old> -> <new>      attribute changed value
    + <theme>\t<attr>\t<new>               attribute newly resolvable
    - <theme>\t<attr>\t<old>               attribute no longer resolvable

Two things this deliberately does NOT do, because both have produced wrong answers
in this phase before:

  * It does not reconstruct changes by pairing '+' against '-'.  A changed row emits
    only '~'; pairing would under-report by exactly the changed count (1872 rows in
    the measured 33b delta).  All three prefixes are parsed on their own terms.

  * It does not key on the attribute name alone.  The key is (theme, attr) and the
    resolved value is kept, so a pin applied to AppTheme.Light but forgotten on
    AppTheme.Dark is caught - name-only keying cannot see that, because the name
    would still be "accounted for" by the theme that did get the pin.

Exit codes:
    0  every delta row is dispositioned and every assertion holds
    1  one or more violations (listed on stdout)
    2  the table itself is malformed or has a duplicate row, or the delta is malformed
"""

import sys
from collections import defaultdict

DISPOSITIONS = ("NAMED", "CLEARED", "PINNED", "TODO", "UNDISPOSITIONED")
REMOVED = "<removed>"


def die(msg):
    print("TABLE ERROR: " + msg)
    sys.exit(2)


def parse_delta(path):
    """-> {(theme, attr): (kind, old, new)}"""
    rows = {}
    with open(path, encoding="utf-8") as fh:
        for lineno, raw in enumerate(fh, 1):
            line = raw.rstrip("\n")
            if not line.strip():
                continue
            kind, sep, rest = line.partition(" ")
            if kind not in ("+", "-", "~") or not sep:
                print("DELTA ERROR: %s:%d: unrecognised prefix: %s" % (path, lineno, line))
                sys.exit(2)
            parts = rest.split("\t")
            if len(parts) != 3:
                print("DELTA ERROR: %s:%d: expected 3 tab-separated fields, got %d"
                      % (path, lineno, len(parts)))
                sys.exit(2)
            theme, attr, value = parts
            if kind == "~":
                if " -> " not in value:
                    print("DELTA ERROR: %s:%d: '~' row without ' -> ': %s"
                          % (path, lineno, line))
                    sys.exit(2)
                old, new = value.split(" -> ", 1)
            elif kind == "+":
                old, new = "<unset>", value
            else:
                old, new = value, REMOVED
            key = (theme, attr)
            if key in rows:
                print("DELTA ERROR: %s:%d: duplicate row for %s / %s"
                      % (path, lineno, theme, attr))
                sys.exit(2)
            rows[key] = (kind, old, new)
    return rows


def parse_table(path):
    """-> {attr: (disposition, owner_or_disproof, expected_after)}"""
    table = {}
    with open(path, encoding="utf-8") as fh:
        for lineno, raw in enumerate(fh, 1):
            line = raw.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 4:
                die("%s:%d: expected 4 tab-separated columns, got %d" % (path, lineno, len(parts)))
            attr, disp, owner, expected = (p.strip() for p in parts)
            if not attr:
                die("%s:%d: empty attribute name" % (path, lineno))
            if attr in table:
                die("%s:%d: duplicate row for %s" % (path, lineno, attr))
            if disp not in DISPOSITIONS:
                die("%s:%d: %s has unknown disposition %r (want one of %s)"
                    % (path, lineno, attr, disp, "/".join(DISPOSITIONS)))
            if disp in ("NAMED", "CLEARED", "PINNED") and not owner:
                die("%s:%d: %s is %s but column 3 is empty - a NAMED row needs its "
                    "§7 entry id and a CLEARED row needs its disproof"
                    % (path, lineno, attr, disp))
            table[attr] = (disp, owner, expected)
    return table


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    delta_path, table_path = sys.argv[1], sys.argv[2]

    delta = parse_delta(delta_path)
    table = parse_table(table_path)

    by_attr = defaultdict(list)          # attr -> [(theme, kind, old, new)]
    for (theme, attr), (kind, old, new) in delta.items():
        by_attr[attr].append((theme, kind, old, new))
    for rows in by_attr.values():
        rows.sort()

    violations = []
    notes = []

    # 1. Every attribute that moved must be dispositioned.
    for attr in sorted(by_attr):
        if attr not in table:
            violations.append(
                "UNDISPOSITIONED %s - moved on %d theme(s) (%s) but no row in %s"
                % (attr, len(by_attr[attr]),
                   ", ".join(t for t, _, _, _ in by_attr[attr][:4])
                   + ("..." if len(by_attr[attr]) > 4 else ""),
                   table_path))

    # 2-5. Per-table-row assertions.
    for attr in sorted(table):
        disp, owner, expected = table[attr]
        rows = by_attr.get(attr, [])

        if disp == "TODO":
            violations.append("UNDISPOSITIONED %s - disposition is TODO (%s)" % (attr, owner))
            continue
        if disp == "UNDISPOSITIONED":
            violations.append("UNDISPOSITIONED %s - %s" % (attr, owner))
            continue

        if disp == "PINNED":
            # Negative assertion: this is what actually verifies that 33a's pins took.
            if rows:
                violations.append(
                    "PIN FAILED %s - pinned by 33a but present in the delta on %d theme(s): %s"
                    % (attr, len(rows),
                       "; ".join("%s %s %s->%s" % (t, k, o, nv) for t, k, o, nv in rows[:3])
                       + ("; ..." if len(rows) > 3 else "")))
            continue

        if disp == "NAMED":
            if not rows:
                violations.append(
                    "PREDICTED BUT ABSENT %s - §7 entry %s names it, but it does not appear "
                    "in the delta" % (attr, owner))
                continue
            if expected:
                for theme, kind, old, new in rows:
                    if new != expected:
                        violations.append(
                            "WRONG VALUE %s/%s - §7 entry %s predicts %s, delta has %s"
                            % (theme, attr, owner, expected, new))
            continue

        # CLEARED - nothing further to assert.  A clearance that stopped moving is
        # stale bookkeeping, not a risk, so it is a note rather than a violation.
        if disp == "CLEARED" and not rows:
            notes.append("STALE CLEARANCE %s - dispositioned as CLEARED but absent from the delta"
                         % attr)

    counts = defaultdict(int)
    for disp, _, _ in table.values():
        counts[disp] += 1

    print("delta:  %s  (%d theme x attribute rows, %d distinct attributes, %d themes)"
          % (delta_path, len(delta), len(by_attr), len({t for t, _ in delta})))
    print("table:  %s  (%d rows: %s)"
          % (table_path, len(table),
             ", ".join("%s %d" % (d, counts[d]) for d in DISPOSITIONS if counts[d])))
    print("")

    for note in notes:
        print("note: " + note)
    if notes:
        print("")

    if violations:
        for v in violations:
            print(v)
        print("")
        print("FAIL: %d violation(s)" % len(violations))
        return 1

    print("OK: every delta row is dispositioned; %d pin(s) held; %d predicted row(s) present"
          % (counts["PINNED"], counts["NAMED"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
