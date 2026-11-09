# Testing Rules

These rules apply to every test. The Kotest-specific ones (DescribeSpec style, `sut()`, `checkAll`, Turbine, random
fixtures) apply to the JVM unit tests in `app/src/test/`. Instrumented tests run on JUnit4 with a Compose rule, because
DescribeSpec does not run on a device, and follow the general rules: effective, stable, single aspect, black box.

Pick the cheapest test type that proves the behaviour. Code that needs no Android context gets a unit test. Code that
needs a `Context`, and UI behaviour such as callbacks and semantics, gets an instrumented test. How a piece of UI looks
gets a screenshot test.

- **Effective Tests**: Tests must not produce false passes. A test must only pass when the code it claims to test is
  actually performing correctly.
- **Stable Tests**: Tests must not produce false failures, which degrade our trust in them and hence reduce their
  utility. A test must only fail when there is a bug in the code it tests.
- **Single Aspect per Test**: Each test covers exactly one conceptual aspect of the subject's behavior, so that when
  something is wrong only the tests covering that aspect fail. Multiple assertions in one test can be appropriate when
  they all support the same aspect, but their presence is a smell — they often signal that two independent aspects are
  being conflated, in which case the test should be split. The word "and" (or any similar conjunction) is a similar
  smell, e.g. a test like `it("throws X and does not produce side effect Y")` should be split into `it("throws X")` and
  `it("does not produce side effect Y")`. Neither of the split tests should incidentally verify the other, e.g. the test
  for "does not produce side effect Y" should not fail if the code fails to throw X, and vice versa.
- **DescribeSpec Style**: Use Kotest's DescribeSpec style. Each block type has one job: `describe` names a
  behavior/aspect, `context` states a precondition, `it` states an assertion. Don't cross lanes — preconditions in
  `describe`, assertions in `context`, or preconditions in `it` are all smells. Don't duplicate `describe` or `context`
  blocks: when multiple tests share the same behavior and preconditions, group them as sibling `it` blocks under one
  `context` (or one `describe`, if no `context` applies) rather than repeating the parent structure.
    - A `describe` block names a public behavior or aspect of the subject — what callers see when they invoke a
      function or read a property. Use plain English from the caller's perspective (`describe("observing the foos by
      flow")`, `describe("adding foos")`) — not a code identifier verbatim (`describe("foosFlow")`,
      `describe("setLocation")`). Domain vocabulary that happens to overlap with an identifier is fine when it reads as
      natural English (`describe("the location flow")`). DO NOT encode preconditions in a `describe` label (that's what
      `context` is for); all `context` variations for the same behavior belong under the same `describe`.
    - Inside `describe` blocks, use `context` to specify preconditions that apply to tests. Each `context` encodes
      exactly one precondition; when tests share multiple preconditions, nest `context` blocks rather than conjoining
      preconditions in a single description. The word "and" (or any similar conjunction) in a `context` description is
      the same smell described for `it` — e.g. `context("when the connection is bandwidth-constrained and the user has
      no access")` should be split into an outer `context("when the connection is bandwidth-constrained")` with a
      nested inner `context("when the user has no access")`. Place each `it` under the innermost `context` whose
      preconditions all apply, so shared preconditions are stated once at the level they apply.
    - Inside `context` blocks, use `it` to state what the subject does (the assertion or expected outcome).
      Preconditions belong in `context`, not in `it`; a "when X" clause in an `it` description (e.g. `it("throws X when
      condition Y holds")`) is a smell signalling a missing `context` — lift it into `context("when condition Y
      holds")`. When no precondition applies, omit `context` and place `it` directly under `describe`. A `describe` may
      contain multiple `context` blocks; a `context` (or a `describe` without `context`) may contain multiple `it`
      blocks.
    - Each test must be self-contained: perform all setup, exercise, and teardown within the test itself, and do *not*
      reference instance state on the test class even if that state is immutable. This keeps tests parallelizable and
      flexible over time. The exception is process-wide state a test cannot scope to itself: `Dispatchers.setMain` and
      the `android.util.Log` mock may be set up and restored in `beforeSpec`/`afterSpec`. For example:
      ```kotlin
      class FooRepository(
          private val maxFoos: Int,
          private val backingStore: FooStore,
      ) {

          val foosFlow: Flow<Collection<Foo>>

          suspend fun addFoo(foo: Foo)
      }

      class FooRepositoryTest : DescribeSpec({

          // Create the subject under test, aka the "sut". Parameter defaults must be neutral
          // (see "Neutral `sut()` defaults" below), so each test passes only the inputs it cares
          // about and everything else stays neutral. Tests that need to drive behavior through a
          // specific input declare it locally and pass it in:
          //   val backingStore = TestFooStore()
          //   val subject = sut(backingStore = backingStore)
          // Tests that don't need to override any inputs call sut() with no arguments:
          //   val subject = sut()
          fun sut(
              maxFoos: Int = randomInt(),
              backingStore: FooStore = TestFooStore(),
          ): FooRepository = FooRepository(maxFoos, backingStore)

          describe("observing the foos by flow") { // Good naming! describe("foosFlow") would be bad.
              context("when no foos have been added") {
                  it("emits an empty collection from the flow") {
                      // Put the self-contained test here
                  }
              }

              context("when foos are added") {
                  it("emits the collection of added foos") {
                      // Put the self-contained test here
                  }
              }
          }

          describe("adding foos") { // Good naming! describe("addFoo") would be bad!
              // and so on...
          }
      })
      ```
    - **Neutral `sut()` defaults**: A `sut()` factory's parameter defaults must not bake in any assumption that the
      test doesn't explicitly claim. Each test passes only the inputs constrained by its enclosing `context` path;
      everything else stays at its default. The "no unstated assumption" rule takes two forms depending on the
      parameter's kind:
        - **Value parameters** (primitives, data classes, value-typed inputs): default to a random fixture (e.g.
          `randomBoolean()`, `randomDuration()`, `randomFoo()`), never a pinned constant. Pinning a value silently
          locks in an assumption the test doesn't claim; randomizing surfaces accidental dependencies as flakiness.
        - **Collaborator parameters** (dependencies the subject delegates to, such as repositories, stores, clients):
          default to a fresh, neutral test double — typically a `Test*` fake in its as-constructed/empty state (e.g.
          `backingStore: FooStore = TestFooStore()`). The default must not pre-stage data, responses, or behavior
          that would satisfy any particular precondition; tests that need a specific state declare the fake locally,
          configure it, and pass it in.

      Both forms share the same effect: keep test bodies minimal, force authors to encode in `context` blocks what
      each test actually depends on, and ensure unstated assumptions surface as failures rather than silent passes.
    - **Use `checkAll` for invariants over a quantified domain**: When the `it` description names an invariant ("…
      regardless of X, Y, Z" — i.e. the assertion holds for every value of those inputs), enumerate the quantified
      domain with `checkAll` rather than relying on randomized `sut()` defaults. Randomized defaults are a safety net
      for unstated assumptions; `checkAll` is the explicit claim. Use `Exhaustive` (`.enum<T>()`, `.collection(…)`,
      `.of(…)`) when the domain is small and finite — every combination runs in a single test run, deterministically.
      Use `Arb` when the domain is open or large. The `it` description should state the invariant and name the
      dimensions being quantified over so the reader can verify the test body matches the claim.
- **Kotest Assertions**: Use Kotest assertions for clear, concise, expressive tests.
- **Turbine for Flows**: Use the Turbine library to test Kotlin Flows.
- **Black Box Testing**: Write "black box" style tests that do not assume any implementation details about the code
  under test. Tests should verify behavior through public APIs only, without relying on internal state, private methods,
  or specific implementation strategies.
- **Edge Case Coverage**: Thoroughly cover all edge cases, including boundary values, error conditions, and distinct
  input categories (e.g. empty vs non-empty collections, valid vs invalid inputs, minimum and maximum values).
- **Test Doubles**: Use an appropriate test double type for each need within a given test.
- **Fast Execution**: Keep unit tests fast (milliseconds) so developers run them frequently during development.
- **Custom Matchers**: Create custom Kotest matchers and corresponding extension functions when they improve test
  readability and reuse.
- **Test Fixture Factories**: Ensure each model a test needs has a test fixture factory function in a `RandomFixtures.kt`
  file in the model's package under `app/src/test/`. This project is a single module, so there are no `:testing`
  sub-modules:
    - Name the function `random` followed by the model name and specify the type explicitly, e.g.
      `fun randomUser(): User`
    - Implement the function by returning an instance of the model populated with random data. You may use other fixture
      functions from your module or from modules like `:test:core` to help build the model, e.g.:
      `fun randomUser(): User = User(id = randomId(), name = randomName(), email = randomEmail())`
    - Randomize **all** properties of a model, including enum/unit fields. A fixture that fixes any property to a
      constant (e.g. always using `.meters` for a `Distance`) is not truly random and will miss bugs that only surface
      with other values. Use `Arb.enum<Foo.Unit>()` (or equivalent)
      to pick the unit randomly, just as the magnitude is picked randomly.
    - Do not add function parameters that map to model properties; use the data class `copy`
      function for that, e.g. `randomUser().copy(email = "bob@example.com")`
    - Parameters may be used to tweak random fixture generation, e.g.
      `randomSentence(wordCount: IntRange): String`
    - You can use Kotest property-based generators to help generate random data, e.g.
      `Arb.email().next(TEST_RANDOM_SOURCE)`
    - ALWAYS use the `TEST_RANDOM` and `TEST_RANDOM_SOURCE` from `com.omnibus.omnibus.testing`. The seed changes per run
      and is printed to the test report as `TEST_SEED=`. To debug a failure, hard-code that seed in `TEST_SEED`.
    - A test that fails only for some random values has found either a bug or an assumption the test should state in a
      `context`. Fix whichever it is. Do not pin the value to make the failure go away.
    - Don't assert exact 8-bit channel equality on a colour converted from a random continuous value (hue, chroma,
      lightness). Float conversion lands a channel one step off for a few percent of values, so the test fails
      intermittently. Compare within a step, or use a fixed set of inputs with `Exhaustive`.
    - If you need lower-level primitive fixture types (e.g. `randomString()`) to help build higher level fixtures, look
      for them in `com.omnibus.omnibus.testing`. If they don't exist and are generally useful, add them there for others
      to use.

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
