# eDziennikus smoke test

A minimal manual checklist for verifying the app works end-to-end after meaningful changes. Designed to be re-run quickly (~5 minutes) at risk checkpoints during cleanups, bugfixes, and refactors.

## When to run it

- **Required** during fork cleanup: after commits #9 (Firebase stripped, build restored — first runnable state under new identity, covers all changes from #1–#9 including the HIGH-risk #8 UI cleanup) and #13 (cleanup complete). See [the fork-customization design](superpowers/specs/2026-05-12-fork-customization-design.md) for context.
- **Strongly recommended** after any commit that touches: login flow, the Librus API client, the database schema, the navigation drawer, the profile model.
- **Optional but useful** after any bugfix that targets a path the smoke test covers.

Note: commits #3–#8 of the fork cleanup leave the build unbuildable (Firebase plugin validates `google-services.json` against `applicationId` mismatch). The app can't be installed to test during that range — the first runnable smoke test waits until the build is restored at commit #9.

## Prerequisites

- A test device or emulator with the freshly-built debug APK installed (`./gradlew assembleMainDebug` then `./gradlew installMainDebug` or sideload)
- A test Librus account, or a real account being used **read-only** (don't dismiss notifications, mark messages read, or change any state)
- (Optional) a set of pre-cleanup baseline screenshots for visual comparison — taken once before commit #1

## The checks

| # | Step | Expected | Notes |
|---|---|---|---|
| 1 | Open the app drawer on the device | `eDziennikus` appears with the placeholder icon (until a custom icon is designed) | After commit #12 the icon should be the "eD" placeholder; before #12, expect the original Szkolny.eu icon. |
| 2 | Tap the icon | Login screen (or onboarding flow on first install) opens without crash | |
| 3 | Choose login type if prompted | Only Librus options are offered (no Vulcan/Mobidziennik/Podlasie/USOS) | After commit #8 the picker may be skipped entirely if there's only one option. |
| 4 | Enter test Librus credentials and submit | Login completes, lands on home / dashboard | Watch logcat for any non-Librus class references that throw `ClassNotFoundException`. |
| 5 | Open the **Grades** tab | At least one grade item renders, or an empty-state if account has none | UI should match pre-cleanup baseline screenshot. |
| 6 | Open the **Messages** tab | List renders without crash (may be empty for test account) | |
| 7 | Open the **Timetable** tab | At least one day of the current week renders | |
| 8 | Open **Settings → Profile** (or equivalent navigation) | Shows the logged-in account; only Librus-type profiles appear in any profile-switcher UI | |
| 9 | Open **About / Info** screen | Displays the new branding ("eDziennikus", fork attribution after commit #12) | Pre-commit-#12: expect the original Szkolny.eu about text. |
| 10 | Log out, then log back in | Both transitions succeed without crash | |

## What to do if a check fails

1. Note the commit you're on (`git -C development log --oneline -1`).
2. Note which check failed and what you saw (screenshot or logcat snippet).
3. If it's a regression introduced by the current commit:
   - For low-risk commits (#1, #2, #9–#12): investigate and fix before moving on.
   - For commit #8 (HIGH risk): expect this — that's why this checkpoint exists.
4. If it's a pre-existing issue (the same check failed on the pre-cleanup baseline):
   - Add to the bugfix backlog, don't block cleanup on it.

## Limitations honest with yourself about

- This checklist verifies **happy path only**. It catches crashes and gross visual breakage; it does not verify the correctness of grade calculations, message threading, attendance tracking, etc.
- Without prior test snapshots, "did behavior X subtly regress" is unanswerable. Some regressions will slip through.
- The smoke test should grow over time. Add a check when a bug surfaces that the existing checks would have caught — that's how the suite earns its keep.
