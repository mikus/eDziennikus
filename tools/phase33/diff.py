#!/usr/bin/env python3
"""diff.py <a> <b> — pixel-compare two capture sets (directories) or two single screenshots.

Both arguments must be the same kind. A bare capture label is accepted as well as a path, so
`diff.py setA setB` still means /tmp/n5a/setA vs /tmp/n5a/setB.

Exits non-zero when any pair differs, so it can gate a phase.
"""
import os, sys

from capture import decode_png       # same directory; one PNG decoder for the whole toolchain

SKIP_ROWS = 100     # crop the status bar: its clock (y=53..83) changes between captures

def resolve(arg):
    """the path as given, or the /tmp/n5a capture set of that name"""
    for p in (arg, f"/tmp/n5a/{arg}"):
        if os.path.exists(p):
            return p
    sys.exit(f"no such file or directory: {arg}")

def compare(pa, pb):
    """(differing pixels below the status bar, one-line verdict); -1 pixels means size mismatch"""
    w1, h1, b1, x = decode_png(open(pa, "rb").read())
    w2, h2, b2, y = decode_png(open(pb, "rb").read())
    if (w1, h1, b1) != (w2, h2, b2):
        return -1, f"SIZE {w1}x{h1}x{b1} vs {w2}x{h2}x{b2}"
    n = sum(1 for k in range(SKIP_ROWS * w1 * b1, len(x), b1) if x[k:k + 3] != y[k:k + 3])
    return n, "IDENTICAL" if n == 0 else f"{n} PIXELS DIFFER"

def pairs_for(A, B):
    """[(name, pathA, pathB)] for two directories or two files, or None if the sets do not match"""
    if os.path.isdir(A) != os.path.isdir(B):
        sys.exit(f"cannot compare a directory with a file: {A} / {B}")
    if not os.path.isdir(A):
        a, b = os.path.basename(A), os.path.basename(B)
        return [(a if a == b else f"{a} vs {b}", A, B)]

    sa = sorted(f for f in os.listdir(A) if f.endswith(".png"))
    sb = sorted(f for f in os.listdir(B) if f.endswith(".png"))
    if not sa or not sb:
        # two equally truncated sets would otherwise compare IDENTICAL and pass the gate
        print(f"EMPTY SET: {len(sa)} .png in {A}, {len(sb)} in {B}")
        return None
    if sa != sb:
        print("SLOT MISMATCH")
        print("  only in A:", sorted(set(sa) - set(sb)))
        print("  only in B:", sorted(set(sb) - set(sa)))
        return None
    return [(name, f"{A}/{name}", f"{B}/{name}") for name in sa]

def main():
    if len(sys.argv) != 3:
        sys.exit("usage: diff.py <dirA> <dirB> | <a.png> <b.png>")
    pairs = pairs_for(resolve(sys.argv[1]), resolve(sys.argv[2]))
    if pairs is None:
        print("RESULT: FAIL")
        return 1

    bad = 0
    for name, pa, pb in pairs:
        n, verdict = compare(pa, pb)
        print(f"{name}: {verdict}")
        if n != 0:
            bad += 1
    print(f"compared {len(pairs)} state(s), {bad} differ")
    print("RESULT:", "PASS" if bad == 0 else f"FAIL ({bad} states)")
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main())
