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

When adding or changing a feature, check how iOS does it first and match it unless there's an Android-specific reason not to. Read the files on GitHub, or clone the repository if you need to search it.

## Layout

```
omnibus/
├── app/src/main/java/com/omnibus/omnibus/   # App source (UI theme lives in ui/theme/)
├── app/src/test/                            # JVM unit tests
├── app/src/androidTest/                     # Instrumented tests
├── docs/engineering/                        # Engineering guidelines (read these)
└── gradle/libs.versions.toml                # Version catalog
```

## Commands

Run from the `omnibus/` directory:

```bash
./gradlew assembleDebug          # Build a debug APK
./gradlew test                   # Run JVM unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires a device/emulator)
./gradlew lint                   # Run Android lint
```

## Engineering Guidelines

Follow the documents in `docs/engineering/`:

- [Architecture](docs/engineering/architecture.md): MVVM, uni-directional data flow (state flows up from data layers to the UI, events flow down from the UI to data layers), and standard concurrency best practices.
- [Feature Layers](docs/engineering/feature-layers.md): Screen (Compose) → ViewModel → Repository → Data sources, with conventions per layer. The login feature is the reference implementation.
- [Commits](docs/engineering/commits.md): Never commit directly to `main`; work on a branch and merge via pull request. Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#specification).
- [KDocs](docs/engineering/kdocs.md): Document public APIs with KDoc. Keep descriptions concise and avoid restating what is obvious from the signature.
- [Testing](docs/engineering/testing.md): Unit test non-Android Kotlin/KMP code with Kotest on the JUnit runner, preferring the [Describe Spec](https://kotest.io/docs/framework/testing-styles.html#describe-spec) style. Test code that needs an Android `Context` with instrumented tests in `app/src/androidTest/`.

## Conventions

- Add new dependencies through the version catalog (`libs.versions.toml`) rather than hard-coding coordinates in `build.gradle.kts`.
- Build UI with Compose; put theme-related code in `ui/theme/`.
- Add or update tests alongside behavior changes, following the testing guideline above, and make sure `./gradlew test` passes before finishing.
