# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

eDziennikus (`eu.mikus.edziennik`) is an Android app for a single Polish e-diary backend, derived from the upstream Szkolny.eu codebase. README is in Polish. Licensed GPLv3 with an addendum forbidding redistribution of compiled builds through Google Play or any store hosting the official app.

The canonical upstream is `szkolny-eu/szkolny-android`; this checkout is a fork (`mikus/eDziennikus`) that has been narrowed to a single backend, repackaged under `eu.mikus.edziennik`, and rewired to its own CI (see CI section). The Librus provider code (`data/api/edziennik/librus/`) is the only live e-diary backend; a `demo/` provider exists for offline screenshots and tests.

## Build & toolchain

Gradle wrapper (`./gradlew`) is the entry point. **JDK 17 required** (CI uses Temurin 17). Gradle 9.5.0, AGP 8.13.2, Kotlin 2.3.20, `compileSdk`/`targetSdk` 35, `minSdk` 23.

| Task | Purpose |
|---|---|
| `./gradlew assembleDebug` | Local-dev APK |
| `./gradlew assembleRelease` | Production APK (signed if signing config present) |
| `./gradlew bundleRelease` | AAB build (signed if signing config present) |
| `./gradlew lint` | Android lint (release builds skip lint by config) |
| `./gradlew clean` | Clean build outputs |

**Variant rules** (`app/build.gradle`):
- **No product flavors.** The fork ships a single sideload-distribution binary (GitHub Releases), so the upstream `unofficial`/`official`/`play` trio was collapsed. `./gradlew assembleDebug` produces a usable artifact at `app/build/outputs/apk/debug/app-debug.apk` (no more `variantFilter` trap).
- The runtime `BuildConfig.FLAVOR` string is hard-coded to `"main"` via `buildConfigField` so it keeps a stable value after the flavors were collapsed. Its only consumers are `BuildManager.kt:47` (`val buildFlavor = BuildConfig.FLAVOR`) and the diagnostic log line at `BuildManager.kt:248`. There is no `X-AppFlavor` header anywhere in the tree, and nothing keys a cache on it.
- `versionName` in `defaultConfig` is appended with `gitInfo.versionSuffix` so running builds always reflect branch and dirty state.

**Signed-release outputs** land in `app/release/` as `Edziennik_<versionName>.{apk,aab}` — a custom `rename<Task>` task is registered as a finalizer of `assembleRelease` / `bundleRelease` / `signReleaseBundle` and copies+renames the output.

The `app/src/test/` source set is wired up for JVM unit tests on the JUnit Platform (JUnit 5 / Jupiter + Vintage). `app/src/androidTest/` (instrumented tests) is **not yet wired up** — defer until an instrumented test is genuinely needed. See "Testing & quality bar" for the policy.

## Architecture

Single Gradle module `:app`. All code under `app/src/main/java/eu/mikus/edziennik/`.

### E-diary backend (`data/api/edziennik/`)
The provider abstraction is kept from upstream even though the fork ships a single backend, so the multi-provider scaffolding can be re-used if more providers come back later.
- `librus/` — the only live provider, mostly HTML-scrape backed
- `demo/` — offline sample provider, useful for screenshots and tests
- `EdziennikTask.kt` — task orchestrator entry point at this layer
- `ProfileArchiver.kt` — profile archiving (still parameterised over provider IDs)
- `helper/` — shared helpers

When changing Librus behavior, scope changes to `librus/`; cross-cutting changes to the task contract belong in `helper/` or the database layer.

### Persistence
Single Room database `AppDb` (`data/db/`). Two kapt processors generate DAO code: `androidx.room:room-compiler` and `eu.szkolny.selective-dao:codegen` (the latter generates selective-update DAOs from annotations).

> **⚠ There are no Room migrations, and bumping the DB version destroys user data.**
> `AppDb.kt:46` is `version = 1`, `AppDb.kt:101` builds with `.fallbackToDestructiveMigration()`, the tree
> contains **zero** Room `Migration` objects and no `addMigrations(...)` call, and
> `app/schemas/eu.mikus.edziennik.data.db.AppDb/` holds exactly one file, `1.json` — there is no "past"
> snapshot to migrate from. Bumping the version therefore **drops and recreates every table**: the build
> stays green and every user silently loses their local diary.
>
> Changing the schema is a **flag-and-confirm** change (see "Safe-change rules"). Doing it safely means
> first replacing `.fallbackToDestructiveMigration()` with real `Migration` objects wired through
> `addMigrations(...)`, which is its own piece of work — not a step inside a feature.

### UI
Feature-per-package under `ui/` (agenda, grades, home, homework, messages, timetable, widgets, etc.). Shared scaffolding in `ui/base/`, `ui/dialogs/`, `ui/views/`.

**Jetpack Compose is the larger and growing surface** (`compose = true`, compose-bom, material3, activity-compose, lifecycle-runtime-compose; 83 files import `androidx.compose.runtime.Composable` against 63 that import a generated `eu.mikus.edziennik.databinding.*Binding`). **DataBinding is off** (`app/build.gradle:56`, `dataBinding = false`); ViewBinding is still on and still hosts the older screens. See "UI: Compose and ViewBinding".

**There is no fragment Navigation graph.** The only navigation artifact is `androidx.navigation:navigation-compose:2.9.5`, and its only consumer is the login flow — `ui/login/LoginNavHost.kt`, whose entry composable is `LoginRoot` (`:42`). `NavHostFragment`, `navigation-fragment-ktx` and `res/navigation/` do not exist. The main content host swaps fragments by hand — `MainActivity.kt:976-977` does `transaction.replace(R.id.fragment, fragment)` + `commitAllowingStateLoss()` — with `NavStackPolicy.kt` as the back-stack authority.

### Networking
- Provider-specific request/response models live alongside each provider under `data/api/`
- `network/` holds `SSLProviderInstaller.kt` and `cookie/` (per-provider cookie jars). The shared OkHttp client is assembled in `App.kt:138-168`, not here.
- **Retrofit is declared but unused** — `retrofit2` has zero hits anywhere under `app/src`. Don't reach for it as if it were the established idiom; the providers are HTML-scrape based (hence `jsoup` + `jspoon`).
- **Chucker ships in release builds too.** It is declared with plain `implementation` (`app/build.gradle:214`), not `debugImplementation`, and there is no no-op release variant. It is gated at *runtime* instead, by **both** flags: `App.kt:160-161` nests `if (enableChucker)` inside `if (devMode)`, so the interceptor needs `devMode && enableChucker`. `enableChucker` defaults to `devMode` (`App.kt:220`, `config.enableChucker ?: devMode`), and setting `config.enableChucker` cannot override a false `devMode`.

### Background work
- `sync/` — sync logic driven by `androidx.work` (WorkManager)
- `receivers/` — broadcast receivers (boot, alarms)

### Native code
None. The fork has no native sources or NDK dependencies — the upstream
`szkolny-signing` JNI library (used to sign requests to szkolny.eu's
now-removed API) was deleted along with SzkolnyApi.
`utils/AppCertificateReader.kt` survives as a pure-Kotlin helper that
reads the APK's signing certificate; `BuildManager.kt:76` compares its
MD5 against a known hash to set `isSigned`.

### Version metadata
`app/git-info.gradle` runs at Gradle configure time and uses JGit to inject git metadata (hash, branch, tag, dirty flag, rev-count) into `BuildConfig.GIT_INFO`. `defaultConfig.versionName` appends `${gitInfo.versionSuffix}` so the running version reflects the branch and dirty state. Because this script reads live git state at configure time it is not configuration-cache safe — but note the cache is simply **never opted into**: nothing in `gradle.properties`, `settings.gradle` or either `build.gradle` sets `org.gradle.configuration-cache`. Don't enable it without dealing with this script first.

## Coding conventions

**Language**: Kotlin for all new code. ~34 Java files still exist (`utils/Utils.java`, `utils/Anim.java`, `utils/Colors.java`, `utils/models/Date.java`, `ui/announcements/AnnouncementsAdapter.java`, a minority of `data/db/dao/` (7 of 31) and `data/db/entity/` (6 of 32) — both layers are majority Kotlin — and 4 of the 11 widget classes) and may be edited in place, but don't write new `.java` files. **Don't opportunistically rewrite Java to Kotlin** as a side-effect of unrelated work — that's a refactor and must follow the refactor rule under "Testing & quality bar".

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
- New async code uses Kotlin coroutines. The **legacy** idiom, still used by adapters and Home cards, implements `CoroutineScope` directly with `private val job = Job()` + `override val coroutineContext get() = job + Dispatchers.Main`, then `launch { withContext(Dispatchers.IO) { ... } }` — see [AttendanceAdapter.kt:49-51](app/src/main/java/eu/mikus/edziennik/ui/attendance/AttendanceAdapter.kt).
- **New screens should prefer a ViewModel** exposing `StateFlow`, collected with `collectAsStateWithLifecycle()`. That is what every *stateful* migrated screen does; see `AttendanceViewModel.kt`. (`ui/settings/LicensesActivity.kt` is the exception — a static list, no ViewModel.)
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

## UI: Compose and ViewBinding

**DataBinding is off** — `app/build.gradle:56` sets `dataBinding = false`, and no `<layout>`-wrapped XML or `DataBindingUtil` call remains. Ignore any older note that says otherwise.

Two live styles, and the split is deliberate:

- **Compose is the default for new screens and the larger surface** (83 files import `androidx.compose.runtime.Composable` vs 63 importing a generated `eu.mikus.edziennik.databinding.*Binding`). The house shape is a Fragment that owns a `ComposeView` and calls `setAppThemeContent { … }`, a ViewModel exposing `StateFlow`, and `collectAsStateWithLifecycle()` in the composable. `AttendanceFragment.kt` + `AttendanceViewModel.kt` + `AttendanceScreen.kt` is the canonical trio.
- **ViewBinding still hosts the older screens.** Inflate via the generated `Fragment<Name>Binding.inflate(...)`; bind to a property named `b`; return `b.root` from `onCreateView`. If the Fragment outlives its view, null the binding in `onDestroyView`.

**When editing an existing screen, match its style.** Don't migrate a ViewBinding screen to Compose as a side-effect of unrelated work — that's a refactor and falls under the TDD-for-refactors rule below.

**Don't reintroduce a `<merge>`-rooted include.** ViewBinding *generates* a field for one, but `bind()` resolves that field via `findViewById`, and a `<merge>` leaves no view with that id — so the field is null and it throws `NullPointerException: Missing required view with ID: …`. This crashed the note dialog until `5c8bd79e` gave the header a real root; see the comment at `res/layout/note_dialog_header.xml:7-11`. No `<merge>`-rooted layout remains today. A probe showing the field exists does not show it resolves.

**Compose UI tests cannot run here.** `createComposeRule` cannot be hosted under Robolectric 4.14.1 + compose-bom 2026.06.00, and there is no `app/src/androidTest/` — see the note at `ShellPolicyTest.kt:23-26`. `androidx.compose.ui:ui-test-junit4` *is* on the test classpath (`app/build.gradle:276`) and is vestigial; the comment above it at `:275` claiming these tests run is stale. So anything observable only in a composition is an emulator smoke, not a gate — say so in a plan rather than implying a test covers it.

## Testing & quality bar

**Test source set wired**: `app/src/test/` runs JVM unit tests on the JUnit Platform. The default engine is **Jupiter (JUnit 5)** — `org.junit.jupiter.api.Test`, `kotlin.test.assertEquals`, etc. The **Vintage** engine is also on the classpath so JUnit 4 tests (currently just Robolectric, which has no first-party Jupiter runner) run side-by-side.

Dependencies in `app/build.gradle`:
- `org.junit:junit-bom:5.13.4` aligns Jupiter / Platform / Vintage versions.
- `org.junit.jupiter:junit-jupiter` (API + engine via BOM), `org.junit.platform:junit-platform-launcher`, `org.junit.vintage:junit-vintage-engine`.
- `org.jetbrains.kotlin:kotlin-test-junit5` for Kotlin-friendly assertions.
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2` — pinned to match `kotlinx-coroutines-android:1.10.2` (`app/build.gradle:253-254`); bump the two together. `runTest`, `runCurrent`, `advanceUntilIdle` and virtual-time `withTimeoutOrNull` are all available.
- `io.mockk:mockk:1.13.13` for Kotlin-friendly mocking.
- `org.robolectric:robolectric:4.14.1` for Android-framework fakes on the JVM (first stable with SDK 35 support).

We deliberately do **not** depend on `androidx.test:core` or `androidx.test.ext:junit`. The original reason was a manifest-merger failure: they declare `minSdkVersion=19` against a then-`minSdk=16` app. **That reason has expired** — `minSdk` is 23, and the highest declared value among those artifacts is 19 (`androidx.test:monitor`; `core`/`runner`/`annotation` declare 14), so none of them would fail the merge now. The exclusion is therefore a live decision to re-take, not a constraint: adopting them would mean adding deps and re-checking the merged test manifest, so treat it as its own change rather than a cleanup. Use `org.robolectric.RuntimeEnvironment.getApplication()` instead of `ApplicationProvider.getApplicationContext()`. A unit-test-only manifest at `app/src/test/AndroidManifest.xml` re-declares the `tools:overrideLibrary` directives needed for the test variant (the main manifest's overrides don't flow into the unit-test merged manifest).

Robolectric tests can instantiate the production `App.onCreate()` directly — there's no native library load to fake out anymore (the `szkolny-signing` JNI dep was deleted along with SzkolnyApi). Tests that just need an `Application` instance can either let the real `App` boot, or swap in stock `android.app.Application::class` via `@Config(application = …)` if they want isolation from app-wide singletons. See `RobolectricSmokeTest.kt` for the canonical shape.

**Running tests:**
- `./gradlew test` — all JVM unit tests (debug + release variants).
- `./gradlew :app:testDebugUnitTest` — debug-variant tests only (faster local loop).

**Configuration in `app/build.gradle`** (`android.testOptions.unitTests`):
- `includeAndroidResources = true` — Robolectric needs Android resources on the test classpath.
- `returnDefaultValues = true` — un-mocked Android-framework calls return defaults instead of throwing.
- `all { useJUnitPlatform() }` — run via the JUnit Platform (Jupiter is default; Vintage is on the classpath for JUnit 4 / Robolectric).

**Policy** (now actionable):

- **New features → TDD.** Write the failing test first; implementation follows. Prefer Jupiter (`org.junit.jupiter.api.Test`) for non-Android tests; reach for Robolectric only when the unit under test genuinely touches `android.*`.
- **Refactors → characterize first.** Before changing the structure of existing code, write tests that pin down its current observable behavior. Refactor against a green bar. **Refactor PRs that don't add coverage for the touched area should be rejected**, including Java → Kotlin migrations and ViewBinding → Compose moves.
- **Bug fixes → reproduce first.** Failing test that reproduces the bug, then fix.

**Definition of done for any change**:
- `./gradlew assembleDebug` builds clean.
- `./gradlew lint` produces no new warnings for touched files (release builds skip lint by config, but local runs should still be clean).
- `./gradlew test` passes. Tests for the touched area exist.
- No new `AsyncTask`, no new direct `Log.d` calls, no hardcoded user-facing strings.

`app/src/androidTest/` (instrumented tests) is **not yet wired up** — defer until an instrumented test is genuinely needed (e.g., real-Activity tests that Robolectric can't fake). Adding it later will require the `android-junit5` plugin if you want Jupiter on instrumented tests; until then, the standard JUnit 4 idiom is the path of least resistance.

## Safe-change rules

These changes have hidden coordination cost or break things in non-obvious ways. **Flag and confirm before doing any of them**, even if the diff looks small:

- **`.github/workflows/build.yml` and `.github/workflows/release.yml`** — the fork's own CI. `build.yml` runs `./gradlew assembleDebug lint` on every push/PR and uploads nothing; `release.yml` triggers on `v*` tags and produces signed APKs. There is no upstream reusable workflow any more — edit the local files directly.
- **The Room schema and `AppDb` version** — see the warning under "Persistence". `AppDb` is `version = 1` with `.fallbackToDestructiveMigration()` and there are no `Migration` objects, so **bumping the version wipes every user's data while the build stays green**. Don't edit `app/schemas/…/1.json` either; Room regenerates it.
- **Provider request/response models under `data/api/<provider>/`** — fields are shaped by undocumented backend JSON/HTML. Don't rename or restructure without verifying against a captured response (Chucker is the standard tool; it is compiled into every build but only captures when `devMode` **and** `enableChucker` are both true — see Networking).
- **`eu.mikus.edziennik` application ID, signing config, version code/name in `app/build.gradle`** — release plumbing and sideload identity depend on exact values. Changing the application ID forces users to reinstall and loses their data.
- **Classes referenced from XML layouts (`<view class="…">` or custom view tags)** — Kotlin's rename refactor won't catch them. Grep `app/src/main/res/layout/` for the FQCN before renaming.
- **Adding new dependencies** — many providers parse HTML using `jsoup` + `jspoon` already in deps. Don't add a second HTML parser, JSON library, or networking layer without justifying why the existing one is insufficient.
- **`gradle.properties` flags** — `android.enableJetifier`, `android.enableR8.fullMode` are set deliberately. Don't flip them as "cleanup".

## CI / release pipeline

Workflows in `.github/workflows/` — both are self-contained for this fork (no upstream reusable workflow indirection):
- `build.yml` — push / PR to any branch → `./gradlew assembleDebug lint`. The smoke gate. It produces **no artifact**: there is no upload step, so don't expect to download an APK from a CI run. Note it also runs `lint`, so a new lint **error** fails CI even though the task table above lists lint separately. Warnings do not fail it — `warningsAsErrors` is not set (`app/build.gradle:73-78`).
- `release.yml` — `v*` tag (not `v*.*`) → `./gradlew assembleRelease` with a signing config materialised from repo secrets, attaches the signed APK to the GitHub Release.

There is no Play AAB upload, no nightly cron, and no Discord/Firebase distribution any more — those upstream paths were removed when the fork dropped its `play` and `official` flavors. Releases are sideload-only via GitHub Releases.

## Constraints to keep in mind

- **`minSdk = 23` (Android 6.0)**: guard newer APIs with `Build.VERSION.SDK_INT` / `@RequiresApi`. Core library desugaring is enabled, so `java.time` and streams are fine without checks. Note that guards for API levels **≤ 23 are now dead code** — `lintDebug` already reports them as `Unnecessary; SDK_INT is always >= 23`. There are 18 such comparisons left across 10 files (9 of them in `utils/PermissionChecker.java` alone); `MainActivity.kt:980` is the only one in `MainActivity`. Don't add new ones; removing the existing ones is a separate, test-backed change.
- **R8 full mode is off** (`android.enableR8.fullMode=false`). Don't rely on aggressive shrinking in release builds.
- **`android.enableJetifier=true`** is on for transitively-pulled legacy support libs.
- **All commit messages in this repo follow `[Area] Title` convention** (e.g., `[UI] …`, `[API/Librus] …`, `[Actions] …`, `[Gradle] …`). Match the prefix style when adding commits.
