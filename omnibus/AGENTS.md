# AGENTS.md

Guidance for AI coding agents working in this project.

## Project Overview

Omnibus is a single-module Android app (`:app`) written in Kotlin with Jetpack Compose and Material 3.

- Package / namespace: `com.omnibus.omnibus`
- Min SDK 24, target/compile SDK 37, Java 11 bytecode
- Dependencies and plugin versions are managed in `gradle/libs.versions.toml`

### Related Projects

This app is one client of [Omnibus](https://github.com/omnibus-library/omnibus), a self-hosted library server. That repository also contains the server and the other clients, which are the reference for behavior, design, and API usage:

| Path | What it is | Use it for |
|---|---|---|
| [`omnibus-ios/`](https://github.com/omnibus-library/omnibus/tree/main/omnibus-ios) | Native iOS client (SwiftUI) | Screen flows, copy, and UX parity. The closest analogue to this app. |
| [`frontend/`](https://github.com/omnibus-library/omnibus/tree/main/frontend) | Web client (Rust/Dioxus) | Behavior the native clients mirror. Design tokens live in `frontend/assets/atrium.css`. |
| [`server/`](https://github.com/omnibus-library/omnibus/tree/main/server) | API server (Rust) | Endpoints and request/response shapes (`server/src/backend/routes.rs`). |
| [`docs/design/atrium-design-system.md`](https://github.com/omnibus-library/omnibus/blob/main/docs/design/atrium-design-system.md) | Atrium design system | Palette, spacing, type, and motion. `ui/theme/` mirrors these. |

When adding or changing a feature, check how iOS does it first and match it unless there's an Android-specific reason not to. The design-system docs below are such a reason: where one applies, follow it over iOS. Read the files on GitHub, or clone the repository if you need to search it.

## Layout

```
omnibus/
├── app/src/main/java/com/omnibus/omnibus/   # App source (UI theme lives in ui/theme/)
├── app/src/test/                            # JVM unit tests
├── app/src/androidTest/                     # Instrumented tests
├── app/src/screenshotTest/                  # Compose preview screenshot tests
├── docs/conventions/                        # Rules for project documents, such as specs
├── docs/engineering/                        # Engineering guidelines (read these)
├── docs/features/                           # Feature specs, the source of truth for each feature
├── docs/design-system/                      # Design-system rules for UI components (read these)
└── gradle/libs.versions.toml                # Version catalog
```

## Commands

Run from the `omnibus/` directory:

```bash
./gradlew assembleDebug          # Build a debug APK
./gradlew test                   # Run JVM unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires a device/emulator)
./gradlew testDebugScreenshotTestDefaultTestSuite    # Run screenshot tests (no device needed)
./gradlew updateDebugScreenshotTestDefaultTestSuite  # Re-record screenshot references
./gradlew lint                   # Run Android lint
```

Releases are automated with release-please; see [Releases](../README.md#releases) in the repo README. Never edit `appVersionName`, `versionName` or `versionCode` in `app/build.gradle.kts` by hand.

## Specs

[Specs](docs/conventions/specs.md) sets the rules and template for specs. A feature with a spec in `docs/features/` is built to that spec: it wins over iOS and over any ticket. When code and spec disagree, fix one of them in the same PR and bump the spec's version.

## Engineering Guidelines

Follow the documents in `docs/engineering/`:

- [Architecture](docs/engineering/architecture.md): MVVM, uni-directional data flow (state flows up from data layers to the UI, events flow down from the UI to data layers), and standard concurrency best practices.
- [Feature Layers](docs/engineering/feature-layers.md): Screen (Compose) → ViewModel → Repository → Data sources, with conventions per layer. The login feature is the reference implementation.
- [Commits](docs/engineering/commits.md): Never commit directly to `main`; work on a branch and merge via pull request. Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#specification).
- [Pull Requests](docs/engineering/pull-requests.md): Each PR delivers one meaningful change to the user experience. Split features by user impact, never by architecture layer. Deliver a multi-PR feature as one linear `gh stack`.
- [Local Storage](docs/engineering/local-storage.md): Where data lives on the device. Data that belongs to a library goes in the Room database, scoped by library id; secrets, shared settings and binary caches stay outside it. Read it before storing anything new.
- [KDocs](docs/engineering/kdocs.md): Document public APIs with KDoc. Keep descriptions concise and avoid restating what is obvious from the signature.
- [Testing](docs/engineering/testing.md): Unit test non-Android Kotlin/KMP code with Kotest on the JUnit runner, preferring the [Describe Spec](https://kotest.io/docs/framework/testing-styles.html#describe-spec) style. Test code that needs an Android `Context` with instrumented tests in `app/src/androidTest/`. Check how a piece of UI looks with a screenshot test in `app/src/screenshotTest/`.

## Design System

Follow the documents in `docs/design-system/` when building or changing UI:

- [Bottom Sheets](docs/design-system/bottom-sheet.md): When to use a bottom sheet instead of a full screen, dialog, menu or snackbar, what content belongs on one, and how sheets behave, look and adapt to wide windows. Read it before adding a sheet or putting content in one.

## Conventions

- Add new dependencies through the version catalog (`libs.versions.toml`) rather than hard-coding coordinates in `build.gradle.kts`.
- Build UI with Compose; put theme-related code in `ui/theme/`.
- Add or update tests alongside behavior changes, following the testing guideline above, and make sure `./gradlew test` passes before finishing.
