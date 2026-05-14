# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

eDziennikus (`eu.mikus.edziennik`) is an Android app for the Librus Synergia e-diary, derived from the upstream Szkolny.eu codebase. README is in Polish. Licensed GPLv3 with an addendum forbidding redistribution of compiled builds through Google Play or any store hosting the official app.

The canonical upstream is `szkolny-eu/szkolny-android`; this checkout is a fork (`mikus/szkolny-android`) that has been narrowed to a single backend, repackaged under `eu.mikus.edziennik`, and rewired to its own CI (see CI section). The Librus provider code (`data/api/edziennik/librus/`) is the only live e-diary backend; a `demo/` provider exists for offline screenshots and tests.

## Build & toolchain

Gradle wrapper (`./gradlew`) is the entry point. **JDK 17 required** (CI uses Temurin 17). Gradle 9.5.0, AGP 8.13.2, Kotlin 2.3.20, `compileSdk`/`targetSdk` 35, `minSdk` 16.

| Task | Purpose |
|---|---|
| `./gradlew assembleDebug` | Local-dev APK |
| `./gradlew assembleRelease` | Production APK (signed if signing config present) |
| `./gradlew bundleRelease` | AAB build (signed if signing config present) |
| `./gradlew lint` | Android lint (release builds skip lint by config) |
| `./gradlew clean` | Clean build outputs |

**Variant rules** (`app/build.gradle`):
- **No product flavors.** The fork ships a single sideload-distribution binary (GitHub Releases), so the upstream `unofficial`/`official`/`play` trio was collapsed. `./gradlew assembleDebug` produces a usable artifact at `app/build/outputs/apk/debug/app-debug.apk` (no more `variantFilter` trap).
- The runtime `BuildConfig.FLAVOR` string is hard-coded to `"main"` via `buildConfigField` so the X-AppFlavor backend header and the config-sync cache key stay stable.
- `versionName` in `defaultConfig` is appended with `gitInfo.versionSuffix` so running builds always reflect branch and dirty state.

**Signed-release outputs** land in `app/release/` as `Edziennik_<versionName>.{apk,aab}` — a custom `rename<Task>` task is registered as a finalizer of `assembleRelease` / `bundleRelease` / `signReleaseBundle` and copies+renames the output.

The `app/src/test/` source set is wired up for JVM unit tests on the JUnit Platform (JUnit 5 / Jupiter + Vintage). `app/src/androidTest/` (instrumented tests) is **not yet wired up** — defer until an instrumented test is genuinely needed. See "Testing & quality bar" for the policy.

## Architecture

Single Gradle module `:app`. All code under `app/src/main/java/eu/mikus/edziennik/`.

### E-diary backend (`data/api/edziennik/`)
The provider abstraction is kept from upstream even though the fork ships a single backend, so the multi-provider scaffolding can be re-used if more providers come back later.
- `librus/` — the only live provider (Librus Synergia, mostly HTML-scrape backed)
- `demo/` — offline sample provider, useful for screenshots and tests
- `EdziennikTask.kt` — task orchestrator entry point at this layer
- `ProfileArchiver.kt` — profile archiving (still parameterised over provider IDs)
- `helper/` — shared helpers

When changing Librus behavior, scope changes to `librus/`; cross-cutting changes to the task contract belong in `helper/` or the database layer.

### Persistence
Single Room database `AppDb` (`data/db/`). Migration schemas are committed under `app/schemas/eu.mikus.edziennik.data.db.AppDb/` — every schema change requires a new committed JSON schema and a written migration. Two kapt processors generate DAO code: `androidx.room:room-compiler` and `eu.szkolny.selective-dao:codegen` (the latter generates selective-update DAOs from annotations).

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

### Native code
`app/src/main/cpp/` (CMake): `aes.{c,h}`, `base64.cpp`, `szkolny-signing.cpp`. Used for crypto and API request signing. Build needs `-std=c++11`; `android.ndk.suppressMinSdkVersionError=21` is set in `gradle.properties`.

### Version metadata
`app/git-info.gradle` runs at Gradle configure time and uses JGit to inject git metadata (hash, branch, tag, dirty flag, rev-count) into `BuildConfig.GIT_INFO`. `defaultConfig.versionName` appends `${gitInfo.versionSuffix}` so the running version reflects the branch and dirty state. Gradle configuration cache is intentionally disabled because this script reads live git state.

## Coding conventions

**Language**: Kotlin for all new code. Java files still exist (`utils/Utils.java`, `utils/Anim.java`, `utils/Colors.java`, `ui/announcements/AnnouncementsFragment.java`, several helpers) and may be edited in place, but don't write new `.java` files. **Don't opportunistically rewrite Java to Kotlin** as a side-effect of unrelated work — that's a refactor and must follow the refactor rule under "Testing & quality bar".

**File header**: New Kotlin files start with the copyright block already in use across the repo:
```kotlin
/*
 * Copyright (c) <Author> YYYY-M-D.
 */
```

**Naming**:
- Fragments end in `Fragment`, dialogs in `Dialog`, view models in `ViewModel`, renderers in `Renderer`, adapters in `Adapter`.
- View bindings are conventionally held in a property named `b` (`private val b: Fragment<Name>Binding`). Don't rename to `binding` in files that already use `b`.
- Per-file log tag: `companion object { private const val TAG = "<ClassName>" }`. Don't share a tag across files.

**Null safety**:
- Prefer non-null types and scope functions (`?.let`, `?.run`, `?:`) over `!!`.
- `!!` is acceptable only when surrounding code already proves non-null and a check would obscure intent. Never use `!!` just to silence the compiler.
- Treat platform types from Android APIs as nullable unless the doc explicitly guarantees otherwise.

**Concurrency**:
- New async code uses Kotlin coroutines. The repo idiom is to implement `CoroutineScope` directly on the Fragment/controller with `override val coroutineContext = Job() + Dispatchers.Main`, then `launch { withContext(Dispatchers.IO) { ... } }`. See [AgendaFragmentDefault.kt:43-52](app/src/main/java/eu/mikus/edziennik/ui/agenda/AgendaFragmentDefault.kt) for the canonical shape.
- **Don't add new `AsyncTask` or raw `Thread { }`.** They still exist in legacy paths and should be migrated when the surrounding code is already being touched (with tests — see refactor rule).
- Cancel the scope's `Job` in `onDestroyView` / `onCleared` when tied to a lifecycle.

**Logging**:
- Use `Utils.d(TAG, message)` (in `utils/Utils.java`). It forwards to `HyperLog` for persisted logs *and* Logcat. **Don't call `android.util.Log.d` directly** in new code — persisted logs are how user-submitted error reports become diagnosable.
- Don't leave commented-out `Log.d` lines behind (the codebase already has too many).

**Strings**:
- All user-facing strings live in `res/values/strings.xml` (Polish, default), with translations in `res/values-en/` and `res/values-de/`.
- **No hardcoded user-facing literals** in Kotlin/XML. Internal-only messages that are logged and never shown can be inline.
- When adding a string: add the Polish source first; English/German translations can follow later.

**Imports & style**: 4-space indent, no tabs. Wildcard imports are permitted (the codebase uses `kotlinx.coroutines.*`, `java.util.*`); follow the surrounding file. Trailing comma after the last constructor argument when multi-line. Use `data class` for value-like records (DTOs, UI state). Don't reformat unrelated lines while making functional changes.

**`when` and control flow**: Prefer expression-form `when` and `if` over statement-form when returning a value. Use `?.let { ... } ?: run { ... }` instead of `if (x != null) ... else ...` for nullable-driven branching.

## UI & view binding

ViewBinding and DataBinding are both enabled. ViewBinding is dominant (~6:1 by file count) and is the **default for new screens**.

- **New screens use ViewBinding.** Inflate via the generated `Fragment<Name>Binding.inflate(...)`; bind to a property named `b`; return `b.root` from `onCreateView`.
- **Use DataBinding only when the layout genuinely needs it** — two-way binding (`@={}`), `<data>` expressions evaluated by the layout, or BR-class observability. A single one-shot `@{viewModel.title}` you can do imperatively in Kotlin is **not** a justification.
- **When editing an existing screen, match its style.** Don't migrate DataBinding ↔ ViewBinding as a side-effect of unrelated work — it's a refactor and falls under the TDD-for-refactors rule below.
- If the Fragment outlives its view, null out the binding in `onDestroyView` to avoid leaks.

## Testing & quality bar

**Test source set wired**: `app/src/test/` runs JVM unit tests on the JUnit Platform. The default engine is **Jupiter (JUnit 5)** — `org.junit.jupiter.api.Test`, `kotlin.test.assertEquals`, etc. The **Vintage** engine is also on the classpath so JUnit 4 tests (currently just Robolectric, which has no first-party Jupiter runner) run side-by-side.

Dependencies in `app/build.gradle`:
- `org.junit:junit-bom:5.13.4` aligns Jupiter / Platform / Vintage versions.
- `org.junit.jupiter:junit-jupiter` (API + engine via BOM), `org.junit.platform:junit-platform-launcher`, `org.junit.vintage:junit-vintage-engine`.
- `org.jetbrains.kotlin:kotlin-test-junit5` for Kotlin-friendly assertions.
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4` — pinned to match the transitive coroutines version; bumping requires bumping coroutines first.
- `io.mockk:mockk:1.13.13` for Kotlin-friendly mocking.
- `org.robolectric:robolectric:4.14.1` for Android-framework fakes on the JVM (first stable with SDK 35 support).

We deliberately do **not** depend on `androidx.test:core` or `androidx.test.ext:junit` — both declare `minSdkVersion=19` and the production app is `minSdk=16`. Use `org.robolectric.RuntimeEnvironment.getApplication()` instead of `ApplicationProvider.getApplicationContext()`. A unit-test-only manifest at `app/src/test/AndroidManifest.xml` re-declares the `tools:overrideLibrary` directives needed for the test variant (the main manifest's overrides don't flow into the unit-test merged manifest).

Robolectric tests instantiate the production `App.onCreate()` by default, which calls `System.loadLibrary("szkolny-signing")` (native, won't load on the JVM). Use `@Config(application = android.app.Application::class)` to swap in stock Application — see `RobolectricSmokeTest.kt` for the canonical shape. Tests that genuinely need the real `App` will have to stub the JNI dependency.

**Running tests:**
- `./gradlew test` — all JVM unit tests (debug + release variants).
- `./gradlew :app:testDebugUnitTest` — debug-variant tests only (faster local loop).

**Configuration in `app/build.gradle`** (`android.testOptions.unitTests`):
- `includeAndroidResources = true` — Robolectric needs Android resources on the test classpath.
- `returnDefaultValues = true` — un-mocked Android-framework calls return defaults instead of throwing.
- `all { useJUnitPlatform() }` — run via the JUnit Platform (Jupiter is default; Vintage is on the classpath for JUnit 4 / Robolectric).

**Policy** (now actionable):

- **New features → TDD.** Write the failing test first; implementation follows. Prefer Jupiter (`org.junit.jupiter.api.Test`) for non-Android tests; reach for Robolectric only when the unit under test genuinely touches `android.*`.
- **Refactors → characterize first.** Before changing the structure of existing code, write tests that pin down its current observable behavior. Refactor against a green bar. **Refactor PRs that don't add coverage for the touched area should be rejected**, including Java → Kotlin migrations and DataBinding ↔ ViewBinding moves.
- **Bug fixes → reproduce first.** Failing test that reproduces the bug, then fix.

**Definition of done for any change**:
- `./gradlew assembleDebug` builds clean.
- `./gradlew lint` produces no new warnings for touched files (release builds skip lint by config, but local runs should still be clean).
- `./gradlew test` passes. Tests for the touched area exist.
- No new `AsyncTask`, no new direct `Log.d` calls, no hardcoded user-facing strings.

`app/src/androidTest/` (instrumented tests) is **not yet wired up** — defer until an instrumented test is genuinely needed (e.g., real-Activity tests that Robolectric can't fake). Adding it later will require the `android-junit5` plugin if you want Jupiter on instrumented tests; until then, the standard JUnit 4 idiom is the path of least resistance.

## Safe-change rules

These changes have hidden coordination cost or break things in non-obvious ways. **Flag and confirm before doing any of them**, even if the diff looks small:

- **`.github/workflows/build.yml` and `.github/workflows/release.yml`** — the fork's own CI. `build.yml` runs `assembleDebug` on every push/PR; `release.yml` triggers on `v*.*` tags and produces signed APKs. There is no upstream reusable workflow any more — edit the local files directly.
- **`app/schemas/eu.mikus.edziennik.data.db.AppDb/`** — these JSON files are *committed snapshots* of past Room schemas. Room uses them to verify migrations. Don't edit them. To change schema, bump `AppDb` version, write a new `Migration`, and let Room export the new snapshot on the next build.
- **Shipped Room `Migration` objects** — never edit a migration after it has shipped (a user already ran it). Add a new migration instead.
- **`app/src/main/cpp/`** (`aes.{c,h}`, `base64.cpp`, `szkolny-signing.cpp`) — native crypto and API request signing. Changes here can silently break provider authentication. Don't refactor unless the task explicitly requires it.
- **Provider request/response models under `data/api/<provider>/`** — fields are shaped by undocumented backend JSON/HTML. Don't rename or restructure without verifying against a captured response (Chucker on debug builds is the standard tool).
- **`eu.mikus.edziennik` application ID, signing config, version code/name in `app/build.gradle`** — release plumbing and sideload identity depend on exact values. Changing the application ID forces users to reinstall and loses their data.
- **Classes referenced from XML layouts (`<view class="…">` or custom view tags)** — Kotlin's rename refactor won't catch them. Grep `app/src/main/res/layout/` for the FQCN before renaming.
- **Adding new dependencies** — many providers parse HTML using `jsoup` + `jspoon` already in deps. Don't add a second HTML parser, JSON library, or networking layer without justifying why the existing one is insufficient.
- **`gradle.properties` flags** — `android.enableJetifier`, `android.enableR8.fullMode`, `android.ndk.suppressMinSdkVersionError` are set deliberately. Don't flip them as "cleanup".

## CI / release pipeline

Workflows in `.github/workflows/` — both are self-contained for this fork (no upstream reusable workflow indirection):
- `build.yml` — push / PR to any branch → `./gradlew assembleDebug`, uploads `app-debug.apk` as a workflow artifact. The smoke gate.
- `release.yml` — `v*.*` tag → `./gradlew assembleRelease` with a signing config materialised from repo secrets, attaches the signed APK to the GitHub Release.

There is no Play AAB upload, no nightly cron, and no Discord/Firebase distribution any more — those upstream paths were removed when the fork dropped its `play` and `official` flavors. Releases are sideload-only via GitHub Releases.

## Constraints to keep in mind

- **`minSdk = 16` (Android 4.1)**: guard newer APIs with `Build.VERSION.SDK_INT` / `@RequiresApi`. Core library desugaring is enabled, so `java.time` and streams are fine without checks.
- **R8 full mode is off** (`android.enableR8.fullMode=false`). Don't rely on aggressive shrinking in release builds.
- **`android.enableJetifier=true`** is on for transitively-pulled legacy support libs.
- **All commit messages in this repo follow `[Area] Title` convention** (e.g., `[UI] …`, `[API/Librus] …`, `[Actions] …`, `[Gradle] …`). Match the prefix style when adding commits.
