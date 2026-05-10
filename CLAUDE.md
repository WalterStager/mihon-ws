# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build (installs on connected device/emulator)
./gradlew assembleDebug

# Release build (no telemetry/updater by default)
./gradlew assembleRelease

# Release with telemetry + updater (matches CI)
./gradlew assembleRelease -Pinclude-telemetry -Penable-updater

# Run all unit tests
./gradlew testDebugUnitTest

# Run unit tests for a single module
./gradlew :domain:testDebugUnitTest

# Run a single test class
./gradlew :domain:testDebugUnitTest --tests "tachiyomi.domain.manga.interactor.FetchIntervalTest"

# Check code style (ktlint via Spotless)
./gradlew spotlessCheck

# Auto-fix code style
./gradlew spotlessApply

# Verify SQLDelight migrations are valid
./gradlew verifySqlDelightMigration
```

Build variants: `debug` (applicationId `.dev` suffix), `release`, `foss` (no telemetry/updater), `preview`, `benchmark`.

Gradle properties that toggle features:
- `-Pinclude-telemetry` — Firebase Crashlytics/Analytics
- `-Penable-updater` — in-app update checker
- `-Pdisable-code-shrink` — skip R8 minification
- `-Pinclude-dependency-info` — embed dependency metadata

## Module Architecture

Multi-module Gradle project. Dependency direction: `app` → `domain`/`data`/`presentation-core` → `core:common`/`source-api`.

| Module | Purpose |
|---|---|
| `:app` | Android application, UI screens (`eu.kanade.tachiyomi.ui.*`), feature code (`mihon.feature.*`) |
| `:domain` | Business logic, use-case interactors, repository interfaces, domain models |
| `:data` | SQLDelight database, repository implementations, data models |
| `:core:common` | Shared utilities, preferences, migrations framework |
| `:core:archive` | Archive file reading (ZIP/RAR/CBZ/CBR) |
| `:presentation-core` | Shared Compose components and theming |
| `:presentation-widget` | Home screen widget |
| `:source-api` | KMP module — public API for content sources (extensions) |
| `:source-local` | Local source (files on device) |
| `:i18n` | String resources via Moko Resources |
| `:telemetry` | Firebase wrapper (conditionally included) |
| `:macrobenchmark` | Macrobenchmark tests |

## Key Architectural Patterns

**DI — Injekt:** Uses `uy.kohesive.injekt` (not Hilt/Koin). Singletons registered in `AppModule` and `DomainModule`. Retrieve via `Injekt.get<T>()` or `by injectLazy<T>()`.

**Navigation — Voyager:** Screens implement `cafe.adriel.voyager.core.screen.Screen`. Tab-level navigation uses `TabNavigator`; modal/push navigation uses `Navigator`. The five bottom-nav tabs are `LibraryTab`, `UpdatesTab`, `HistoryTab`, `BrowseTab`, `MoreTab` — all wired in `HomeScreen`.

**State — Voyager ScreenModel:** ViewModels are `cafe.adriel.voyager.core.model.ScreenModel`. State flows via `StateFlow`/`SharedFlow` from coroutines.

**Database — SQLDelight:** Schemas in `data/src/main/sqldelight/tachiyomi/`. Generated `Database` class accessed via `AppModule`. Schema migrations in `migrations/` numbered sequentially (`.sqm` files). Run `verifySqlDelightMigration` after any schema change.

**Preferences:** Typed preference wrappers in `core:common`. Each feature area has its own `*Preferences` class (e.g., `LibraryPreferences`, `ReaderPreferences`). Retrieved via Injekt.

**Dual package names:** Legacy code lives under `eu.kanade.tachiyomi.*` / `eu.kanade.domain.*`; newer code migrates to `mihon.*` / `tachiyomi.*` (without the `eu.kanade` prefix). Both coexist.

**Source extensions:** External sources are APKs loaded at runtime via `ExtensionManager`. The `source-api` module defines the `Source`/`CatalogueSource` interfaces that extensions implement.

**Coil 3:** Image loading throughout. Custom fetchers (`MangaCoverFetcher`, `BufferedSourceFetcher`) and keyers registered in `App.kt`.

## Linting

Spotless enforces ktlint on all `.kt` and `.kts` files. CI runs `spotlessCheck` — always run `spotlessApply` before committing to avoid CI failures.