# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Szkolny.eu (`pl.szczodrzynski.edziennik`) is an Android app that aggregates multiple Polish school e-diary backends — Librus Synergia, Vulcan UONET+, Mobidziennik, USOS, Podlasie — behind a single UI. README is in Polish. Licensed GPLv3 with an addendum forbidding redistribution of compiled builds through Google Play or any store hosting the official app.

The canonical upstream is `szkolny-eu/szkolny-android`; this checkout may be a fork or downstream copy. CI workflows reference the upstream repo by full path (see CI section).

## Build & toolchain

Gradle wrapper (`./gradlew`) is the entry point. **JDK 17 required** (CI uses Temurin 17). AGP 8.6.1, Kotlin 1.9.20, `compileSdk`/`targetSdk` 35, `minSdk` 16.

| Task | Purpose |
|---|---|
| `./gradlew assembleUnofficialDebug` | Local-dev APK — the only debug variant that builds (see below) |
| `./gradlew assembleUnofficialRelease` | Local release build, no signing keys needed for the binary |
| `./gradlew assembleOfficialRelease` | Production APK (used by `release.yml`) — signed |
| `./gradlew bundlePlayRelease` | Google Play AAB (used by `push-main.yml`) — signed |
| `./gradlew lint` | Android lint (release builds skip lint by config) |
| `./gradlew clean` | Clean build outputs |

**Flavor & variant rules** (`app/build.gradle`):
- Three flavors: `unofficial` (default), `official`, `play`. Per the README, `official` and `play` are reserved for official releases and require signing material.
- `variantFilter` deliberately ignores every non-`unofficial` debug variant. **`./gradlew assembleDebug` produces nothing useful** — use `assembleUnofficialDebug`.
- `play` flavor uses `src/play/java` (Firebase Messaging enabled); `unofficial` and `official` share `src/play-not/java` (no Google Mobile Services).

**Signed-release outputs** land in `app/release/` as `Edziennik_<versionName>_<flavor>.{apk,aab}` — a custom `rename<Task>` task is registered as a finalizer of every `assemble*Release` / `bundle*Release` / `sign*Release` and copies+renames the output.

**There are no tests in this repo.** Neither `app/src/test/` nor `app/src/androidTest/` exist. Don't propose running test tasks; they have no source to compile.

## Architecture

Single Gradle module `:app`. All code under `app/src/main/java/pl/szczodrzynski/edziennik/`.

### Multi-backend e-diary layer (`data/api/edziennik/`)
The core abstraction. Each subpackage implements one backend:
- `librus/`, `vulcan/`, `mobidziennik/`, `podlasie/`, `usos/`, `demo/` — concrete providers
- `template/` — boilerplate for new providers
- `EdziennikTask.kt` — task orchestrator entry point at this layer
- `ProfileArchiver.kt` — cross-provider profile archiving
- `helper/` — shared helpers used by multiple providers

When changing provider behavior, scope changes to the provider's subpackage; cross-cutting changes to the task contract belong in `helper/` or the database layer.

### Persistence
Single Room database `AppDb` (`data/db/`). Migration schemas are committed under `app/schemas/pl.szczodrzynski.edziennik.data.db.AppDb/` — every schema change requires a new committed JSON schema and a written migration. Two kapt processors generate DAO code: `androidx.room:room-compiler` and `eu.szkolny.selective-dao:codegen` (the latter generates selective-update DAOs from annotations).

### UI
Feature-per-package under `ui/` (agenda, grades, home, homework, messages, timetable, widgets, etc.). Shared scaffolding in `ui/base/`, `ui/dialogs/`, `ui/views/`. Both **DataBinding and ViewBinding are enabled** — existing code mixes the two. AndroidX Navigation (`navigation-fragment-ktx`) is used for fragment graphs.

### Networking
- Provider-specific request/response models live alongside each provider under `data/api/`
- `network/` holds shared OkHttp/Retrofit setup; `network/cookie/` handles per-provider cookie jars
- Many providers are **HTML-scrape based** (hence `jsoup` + `jspoon` in deps)
- Debug builds include **Chucker** for in-app HTTP traffic inspection

### Background work
- `sync/` — sync logic driven by `androidx.work` (WorkManager)
- `receivers/` — broadcast receivers (boot, alarms)
- `data/firebase/` — FCM handling, only wired up on the `play` flavor

### Native code
`app/src/main/cpp/` (CMake): `aes.{c,h}`, `base64.cpp`, `szkolny-signing.cpp`. Used for crypto and API request signing. Build needs `-std=c++11`; `android.ndk.suppressMinSdkVersionError=21` is set in `gradle.properties`.

### Version metadata
`app/git-info.gradle` runs at Gradle configure time and uses JGit to inject git metadata (hash, branch, tag, dirty flag, rev-count) into `BuildConfig.GIT_INFO`. The `unofficial` flavor appends `${gitInfo.versionSuffix}` to its `versionName` so the running version reflects the branch and dirty state. Gradle configuration cache is intentionally disabled because this script reads live git state.

## CI / release pipeline

Workflows in `.github/workflows/`:
- `_build.yml` — reusable workflow definition. **All other workflows `uses:` the upstream copy at `szkolny-eu/szkolny-android/.github/workflows/_build.yml@develop`, not the local file.** Editing the local copy alone does not change actual CI behavior — coordinate any change with the upstream repo.
- `push-main.yml` — push to `main` → Play AAB build & upload
- `release.yml` — `v*.*` tag → APK release to SSH/GitHub/Firebase/Discord
- `schedule-dispatch.yml` — nightly cron (23:30 UTC) checks for new commits and triggers a nightly build if any

Python helpers under `.github/utils/` handle version bumping, signing config, changelog extraction, artifact discovery, DB persistence, and Discord posting (deps: `python-dotenv`, `pycryptodome`, `mysql-connector-python`, `requests`).

## Constraints to keep in mind

- **`minSdk = 16` (Android 4.1)**: guard newer APIs with `Build.VERSION.SDK_INT` / `@RequiresApi`. Core library desugaring is enabled, so `java.time` and streams are fine without checks.
- **R8 full mode is off** (`android.enableR8.fullMode=false`). Don't rely on aggressive shrinking in release builds.
- **`android.enableJetifier=true`** is on for transitively-pulled legacy support libs.
- **All commit messages in this repo follow `[Area] Title` convention** (e.g., `[UI] …`, `[API/Librus] …`, `[Actions] …`, `[Gradle] …`). Match the prefix style when adding commits.
