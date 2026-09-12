kotlinc version used:

This directory records a REAL execution of CoInterpreter's Android-free
(pure Kotlin/JVM) production logic, run directly in the sandbox that built
this repository -- not a projection of expected results.

- PureLogicTests.kt is a standalone assertion harness (no JUnit dependency,
  since Maven Central / JCenter were not reachable from the sandbox; only
  npm, PyPI, crates.io, and GitHub release assets were reachable).
- It was compiled together with the *actual* production files
  app/src/main/java/com/cointerpreter/app/model/Language.kt,
  .../state/SessionState.kt, and .../engine/InterpreterConstitution.kt
  (copied verbatim, not reimplemented), using the official Kotlin 2.0.20
  compiler downloaded from JetBrains' GitHub release assets.
- pure_logic_test_output.txt is the unedited stdout of that run: 38/38
  checks passed.

This covers every state-machine transition table entry, the "never answer
instead of translate" constitution requirement (TEST 2 in the original
spec), Professional Mode addendum, glossary injection/omission, and
RTL/language lookups.

It does NOT cover anything requiring the Android SDK (Compose UI, AudioRecord/
AudioTrack, OkHttp WebSocket wire behaviour, DataStore, the real OpenAI
service). Those are covered by the JUnit test suite under
android-app/app/src/test, which requires Gradle + the Android SDK and is
executed in CI by .github/workflows/android-build.yml, not in this sandbox.
See BUILD_REPORT.md for the full breakdown of tested vs. untested items.
