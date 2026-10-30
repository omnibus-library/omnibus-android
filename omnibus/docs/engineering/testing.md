# Testing
* Non-Android Kotlin/KMP files should be tested using Kotest JUnit test runner based unit tests.
* Files/functions that require Android context should be tested via Android instrumentation testing.
* When possible, tests should be structured following Kotest' [Describe Spec](https://kotest.io/docs/framework/testing-styles.html#describe-spec)
## Instrumented tests must not depend on screen size

Instrumented tests run on whatever device is attached: a 320dp emulator in CI, a 411dp Pixel locally, a foldable. A test that only passes at one width fails somewhere else, usually as a timeout that hides the cause.

* Size test content as a fraction of the window (`fillMaxWidth(0.6f)`), not in fixed dp. A fixed `Modifier.size(600.dp)` is capped by the window's constraints, so on a narrow screen it silently does nothing.
* When a test needs an exact size regardless of the window, use `requiredWidth`/`requiredSize`, and assert on relative layout (gaps, alignment, ratios) instead of absolute pixel positions. Density rounding moves absolute values (`LibraryScreenTest.tc66`).
* When a test changes a size, assert that it changed before waiting for a reaction to it, so a no-op fails with a clear message.
* Run new instrumented tests on a small default AVD as well as a phone-sized one. CI creates its AVD with no hardware profile, which is 320x640 at 160dpi.
