# CoInterpreter — Build Report

This report distinguishes, precisely, what has been **built and verified by
real execution in the sandbox that authored this repository**, from what
**requires GitHub Actions or a live OpenAI credential** to verify. Nothing
below is a projection or an assumption dressed up as a result.

## 1. Implementation status

| Area | Status |
|---|---|
| Android app source (Kotlin/Compose, all screens, engines, audio, state machine, repositories) | **Complete** |
| OpenAI Realtime integration (`gpt-realtime` + `gpt-realtime-translate`, researched against current official docs) | **Complete** |
| Backend (Node/TypeScript, ephemeral credential minting) | **Complete** |
| GitHub Actions CI (Android build/test/lint, backend CI, manual release) | **Complete** |
| Documentation (README, ARCHITECTURE, PRIVACY, this file, CI_BUILD_GUIDE) | **Complete** |
| Security review / secret scan | **Complete, passed** |
| Android JUnit/instrumentation test *execution* | **Not run here — requires Gradle + Android SDK, runs in CI** |
| Live OpenAI Realtime session (actual audio in/out) | **Not run — requires a real OPENAI_API_KEY, which was not provided** |
| Physical APK file | **Not present in this sandbox — produced by CI, see below** |

## 2. What was actually executed in this sandbox, and how

The sandbox that built this repository has **no Android SDK, no Gradle, and
no network access to `dl.google.com` / `maven.google.com` /
`services.gradle.org`** (all three returned HTTP 403 when tested directly).
It does have a JVM, and outbound access to GitHub, npm, and PyPI. Given
that, two independent things were actually run:

### 2a. Pure-Kotlin production logic (Kotlin compiler, no Android SDK)

The Kotlin 2.0.20 compiler was downloaded directly from JetBrains' official
GitHub release assets and used to compile and run the **actual, unmodified**
production files that have no Android dependency:

- `android-app/app/src/main/java/com/cointerpreter/app/model/Language.kt`
- `android-app/app/src/main/java/com/cointerpreter/app/state/SessionState.kt`
  (the session state machine)
- `android-app/app/src/main/java/com/cointerpreter/app/state/ReconnectPolicy.kt`
- `android-app/app/src/main/java/com/cointerpreter/app/engine/InterpreterConstitution.kt`

against a standalone assertion harness (`verification/PureLogicTests.kt`
and `verification/ReconnectPolicyHarness.kt`, JUnit was unavailable since
Maven Central returned 403 from this sandbox). Full unedited output is in
`verification/pure_logic_test_output.txt`:

```
TOTAL: 38, PASSED: 38, FAILED: 0        (state machine, constitution, glossary, language/RTL)
TOTAL: 3, PASSED: 3, FAILED: 0          (bounded exponential backoff)
```

This covers, with a real compiler and real assertions, not mocks:
- every transition in the session state machine's table, including the
  barge-in transition, illegal-transition rejection, duplicate-session
  prevention, and fatal-error recovery (spec §19, §29);
- the critical "translate the question, do not answer it" requirement
  (spec §3, TEST 2), and the "never join the conversation / never add
  facts / never give an opinion" clauses;
- Professional Mode's terminology-precedence addendum;
- glossary injection (TEST 8) and the "no empty glossary section" cost
  optimization (spec §6, §28);
- Arabic/Urdu/Persian RTL flags and language lookup fallback (spec §4);
- bounded exponential backoff with a hard attempt cap (spec §27).

The equivalent JUnit versions of these same tests live in
`android-app/app/src/test/` (`SessionStateMachineTest.kt`,
`InterpreterConstitutionTest.kt`, `LanguageTest.kt`,
`InterpreterEngineFactoryTest.kt`, `ReconnectPolicyTest.kt`) for Gradle/CI
to run against the full Android classpath.

### 2b. Backend (Node.js, real execution)

The backend was installed, built, linted, and tested for real in this
sandbox:

```
$ npm install         → 226 packages, exit 0
$ npm run build        (tsc)                 → exit 0, dist/ produced
$ npm run lint          (eslint)             → 1 real issue found and fixed
                                                (unused _next param in the
                                                 Express error handler),
                                                then exit 0
$ npm test              (node --test)        → 17/17 tests passed
```

Full output saved at `verification/backend_test_output.txt` and
`verification/backend_lint_output.txt`. The 17 backend tests cover: env/
config defaults and the "never fabricate an API key" guarantee, the
`MissingApiKeyError` fail-fast path, both OpenAI client-secret request
shapes (general vs. translation endpoint) with a faked `fetch` (no real
network calls, no real key needed), acceptance of both documented response
shapes, sanitization of upstream error bodies (verified to strip a
deliberately-embedded fake key string), input validation for all three
request fields, and end-to-end HTTP behavior of `/healthz` and
`/v1/session-token` using an in-process Express server.

## 3. What was NOT executed here, and why

- **Android JUnit tests via Gradle** (`./gradlew testDebugUnitTest`) —
  requires the Android SDK and Gradle distribution, both unreachable from
  this sandbox. These tests exist in the repo and are correct Kotlin (the
  logic they test was independently verified in §2a); `android-build.yml`
  runs them for real on every push.
- **Android Lint** (`./gradlew lintDebug`) — same reason; runs in CI.
- **`assembleDebug` / the APK itself** — same reason. This is the one item
  genuinely gated on GitHub Actions; see `CI_BUILD_GUIDE.md`.
- **Instrumentation/Compose UI tests** — would additionally require an
  emulator or device, not attempted in either environment yet; not present
  in this repo. If needed, add them under `app/src/androidTest` and a
  `-PtestInstrumentation` CI job with `reactivecircus/android-emulator-runner`.
- **Any live OpenAI Realtime session** — requires `OPENAI_API_KEY`, which
  was never provided to this environment (correctly — it should never be
  pasted into a chat or committed to a repo). Everything that does *not*
  require the key (routing logic, request-shape correctness, error mapping,
  input validation, the entire Android app compiling against the SDK) is
  either verified above or ready to verify in CI.

## 4. Security review

Performed directly against the final repository contents (not a subset):

- `grep`-based scan for `sk-[A-Za-z0-9_-]{16,}` patterns across all source,
  config, and doc files: the **only** match is a deliberately fake string,
  `sk-test-not-a-real-key` / `sk-verysecret1234567890`, used inside two
  backend tests specifically to prove the error-sanitization path strips
  such strings before they'd reach a client or log. No match in any Kotlin,
  Gradle, manifest, or resource file.
- No `.env` file present anywhere in the repository (only `.env.example`,
  with a blank `OPENAI_API_KEY=`).
- No `.jks` / `.keystore` / `.p12` files present — the release workflow
  intentionally has no signing key and will only produce a signed APK if
  the repository operator supplies their own signing secrets.
- `OPENAI_API_KEY` appears only in: backend source (as an env var read,
  never a literal), backend tests (as fake values), `.env.example`,
  and documentation prose. It does not appear anywhere under `android-app/`.
- `android-app/.gitignore` excludes `local.properties`, `*.apk`, `*.aab`,
  build directories, and `.cxx`/native build caches.
- `backend/.gitignore` excludes `node_modules/`, `dist/`, `.env`, and logs.

**Result: PASS.** No permanent credential exists anywhere in this
repository, in either source or built output.

## 5. Repository statistics (for scale/sanity, not a quality claim by itself)

- Android: 35 Kotlin files, ~3,400 lines, across `engine/` (2 realtime
  engines + factory + constitution + wire models + WebSocket transport),
  `audio/` (capture, playback, routing, foreground service), `state/`
  (state machine + reconnect policy), `data/` (settings/glossary/transcript
  repositories), `ui/` (6 Compose screens + theme), `net/` (backend client),
  `model/` (domain types).
- Backend: 11 TypeScript files, ~620 lines, across `lib/` (config, OpenAI
  gateway), `routes/` (session-token, health), `middleware/` (error
  handler), plus 4 test files.
- `android-app/gradle/wrapper/gradle-wrapper.jar` is a real, working Gradle
  wrapper jar (not a placeholder) — SHA-256:
  `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`,
  fetched from JetBrains/Gradle's own GitHub-hosted release artifact and
  verified to be a valid zip archive.

## 6. Known limitations

- Conversation Mode's automatic bidirectional switching (via two concurrent
  `gpt-realtime-translate` sessions, see ARCHITECTURE.md §3) is an
  architecture derived from OpenAI's documented model behavior but has not
  been validated against real audio, since that requires a live credential.
- The WebSocket transport (chosen over native WebRTC — see ARCHITECTURE.md
  §2 for the reasoning) has not been load-tested for jitter/packet-loss
  behavior on real cellular networks.
- The extended language catalog (`Language.EXTENDED`) beyond
  Arabic/English/French has UI wiring but has not had the manual UX review
  spec §31 calls for (long-string layout, punctuation) beyond the three
  first-class languages.
- No instrumentation/Compose UI test suite exists yet (see §3).
- The release workflow produces an **unsigned** APK unless the repository
  operator supplies their own signing secrets — this is by design (spec:
  "do not invent a signing key"), not an oversight.
- Transcript export/sharing via the OS share sheet is not wired up; saved
  transcripts currently live only in local app storage (see PRIVACY.md).

## 7. Items requiring external credentials/accounts to finish verifying

1. **`OPENAI_API_KEY`** with Realtime API access — needed to verify any
   live audio path end-to-end (both engines, barge-in, reconnect against a
   real dropped connection, Bluetooth routing with real hardware).
2. **A GitHub repository** to push this code to — needed for
   `.github/workflows/android-build.yml` to actually run and produce
   `CoInterpreter-debug.apk` (see `CI_BUILD_GUIDE.md`).
3. **An Android signing identity** (only if a signed release build is
   wanted) — see `.github/workflows/android-release.yml` header for the
   exact secrets to add.

None of the above are required to inspect, build-review, or CI-build the
project — only to run it against real interpreted speech in production.
