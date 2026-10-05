# Testing
* Non-Android Kotlin/KMP files should be tested using Kotest JUnit test runner based unit tests.
* Files/functions that require Android context should be tested via Android instrumentation testing.
* When possible, tests should be structured following Kotest' [Describe Spec](https://kotest.io/docs/framework/testing-styles.html#describe-spec)