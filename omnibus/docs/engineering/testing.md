# Testing
* Non-Android Kotlin/KMP files should be tested using Kotest JUnit test runner based unit tests.
* Files/functions that require Android context should be tested via Android instrumentation testing.
* When possible, tests should be structured following Kotest' [Describe Spec](https://kotest.io/docs/framework/testing-styles.html#describe-spec)
## Screenshot tests

Tests whose only job is to check that a piece of UI looks right are screenshot tests, not instrumented tests. They render a `@Preview` composable on the JVM with Layoutlib (no emulator) and compare it with a reference image.

* Put them in `app/src/screenshotTest/kotlin/`, mirroring the package of the composable. Mark each `@Preview` function with `@PreviewTest`. Use `@PreviewLightDark` for light and dark, and wrap the content in a `Surface(color = MaterialTheme.colorScheme.background)`, because a preview has no ground of its own.
* Record or update references with `./gradlew updateDebugScreenshotTestDefaultTestSuite`, then run `./gradlew testDebugScreenshotTestDefaultTestSuite` to check them. Run them as two separate invocations. References are in `app/src/screenshotTestDefaultDebug/reference/` and are committed; review the diff of an updated image like code. The HTML report is in `app/build/reports/tests/`.
* Renaming a `@PreviewTest` function orphans its reference image, so re-record after a rename.
* A screenshot is not an assertion about behaviour. Callbacks, semantics, and whether something renders at all stay in instrumented tests, and text formatting belongs in a unit test of the formatter.

## Instrumented tests must not depend on screen size

Instrumented tests run on whatever device is attached: a 320dp emulator in CI, a 411dp Pixel locally, a foldable. A test that only passes at one width fails somewhere else, usually as a timeout that hides the cause.

* Never pin a screen size. Fill the window (`fillMaxSize()`) or size test content as a fraction of it (`fillMaxWidth(0.6f)`), not in fixed dp. A fixed `Modifier.size(600.dp)` is capped by the window's constraints, so on a narrow screen it silently does nothing, and `requiredWidth(390.dp)` overflows a 320dp screen instead.
* Assert relative layout (alignment, ordering, no overlap, ratios, one element filling another) instead of absolute positions or sizes that depend on the window. Density rounding also moves absolute values.
* Choose content that behaves the same at every width. For example, a title meant to wrap and ellipsize should be long enough to do so even in the widest cell.
* When a test changes a size, assert that it changed before waiting for a reaction to it, so a no-op fails with a clear message.
* Run new instrumented tests on a small default AVD as well as a phone-sized one. CI creates its AVD with no hardware profile, which is 320x640 at 160dpi.

## Reopening a DataStore in a test

To test that a value survives a restart, close the first DataStore and open a second on the same file. Join the first scope (`dataStoreScope.coroutineContext.job.cancelAndJoin()`) before reopening. `cancel()` alone returns before the file is released, so the reopen sometimes fails with "multiple DataStores active for the same file", mostly on loaded CI runners.
