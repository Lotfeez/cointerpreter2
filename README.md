# CoInterpreter

A professional real-time speech-to-speech interpreter for Android, powered
by OpenAI's Realtime API. Two people speak different languages; CoInterpreter
listens, interprets meaning (not just words), and speaks the translation —
without ever becoming a participant in the conversation.

> **Build status at a glance:** the Android app, backend, and CI pipeline are
> complete and locally verified wherever this sandbox's tooling allowed
> (Kotlin compiler, Node.js). The Android SDK/Gradle distribution servers are
> not reachable from this sandbox, so **GitHub Actions is the designated
> environment that actually compiles the APK** — see `CI_BUILD_GUIDE.md` and
> `BUILD_REPORT.md` for the precise, honest breakdown of what has and has not
> been executed, and where.

## What it does

- **Conversation Mode** — automatic two-way interpretation between two
  languages; no "Speaker A / Speaker B" button.
- **One-Way Mode** — continuous interpretation of a speech, briefing, or
  lecture into one target language.
- **Professional Mode** — favors terminology precision, formal register,
  and a user-editable **session glossary** (e.g. `وزارة العمل` → `Ministry
  of Labour`) over conversational paraphrasing — built for government,
  diplomatic, legal, and technical settings.
- First-class support for **Arabic, English, and French**, including real
  bidirectional text handling (not "align right" RTL faking), plus an
  extended language catalog.

It deliberately does **not** try to be a chatbot: the interpreter
constitution baked into the system instructions (see `ARCHITECTURE.md`)
explicitly forbids answering questions, giving opinions, or adding
information that wasn't in the original speech.

## Repository layout

```
android-app/   Kotlin + Jetpack Compose Android app
backend/       Node.js + TypeScript backend (mints short-lived OpenAI credentials)
.github/       GitHub Actions workflows (Android build/test/lint, backend CI)
docs/          This README, ARCHITECTURE.md, PRIVACY.md, BUILD_REPORT.md, CI_BUILD_GUIDE.md
verification/  Real, executed test output produced while building this repo
```

## Architecture in one paragraph

The Android app never holds an OpenAI API key. It asks the backend for a
short-lived credential (`POST /v1/session-token`), the backend mints that
credential from OpenAI's `/v1/realtime/client_secrets` (or
`/v1/realtime/translations/client_secrets`) endpoint using a permanent key
that lives only in the backend's environment, and the app uses that
short-lived token to open a realtime WebSocket session directly to OpenAI.
The app talks to either of two interchangeable engines behind a
`RealtimeInterpreterEngine` interface — see `ARCHITECTURE.md` for the full
picture, including exactly why two engines exist and how bidirectional
Conversation Mode works without a bespoke language-ID model.

## Prerequisites

- **For the Android app:** JDK 17, Android SDK (API 35), a device or
  emulator running Android 8.0 (API 26) or newer. You do **not** need any
  of this to read the code — you need it to build/run the app yourself
  outside of CI.
- **For the backend:** Node.js 20+.
- **For live interpreting to actually work:** an OpenAI API key with access
  to the Realtime API (`gpt-realtime`, `gpt-realtime-translate`).

## Backend setup

```bash
cd backend
cp .env.example .env
# edit .env and set OPENAI_API_KEY=sk-...
npm install
npm run build
npm start        # listens on :8787 by default
```

Never commit `.env`. `OPENAI_API_KEY` is the only secret this project has;
it must exist **only** in this file (or your deployment platform's secret
store), never in the Android app.

### Backend endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/healthz` | Liveness probe. |
| `POST` | `/v1/session-token` | Body: `{ mode, engine, targetLanguage }`. Returns `{ ephemeralToken, expiresAt, engine }`. Rate-limited (default 30 req/min/IP). |

## Android setup

1. Open `android-app/` in Android Studio (or use the CLI below).
2. Point the app at your backend:
   - **Emulator against a locally-running backend:** the default
     `http://10.0.2.2:8787/` already works — `10.0.2.2` is the emulator's
     alias for your host machine's `localhost`.
   - **Physical device:** create `android-app/local.properties` (already
     gitignored) with:
     ```properties
     backendBaseUrlDebug=http://<your-machine-lan-ip>:8787/
     ```
   - **Production:** set `COINTERPRETER_BACKEND_BASE_URL_RELEASE` in
     `gradle.properties`, or pass `-PbackendBaseUrlRelease=...` at build
     time.
3. Build and install:
   ```bash
   cd android-app
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   (This step requires the Android SDK to be installed locally; if you'd
   rather not install it, use GitHub Actions — see `CI_BUILD_GUIDE.md`.)

## How to get an installable APK without installing anything locally

See **`CI_BUILD_GUIDE.md`** — the short version is: push this repository to
GitHub, open the "Android Build" workflow under Actions, wait for it to go
green, and download the `CoInterpreter-debug.apk` artifact.

## Using the app

1. Pick your two languages (Arabic/English/French are first-class; more are
   available in the extended catalog).
2. Pick a mode: Conversation, One-Way, or Professional.
3. In Professional Mode, optionally open the glossary editor and add
   preferred terminology before you start.
4. Press **Start Interpreting**, grant microphone access, and speak.
   CoInterpreter shows `LISTENING` → `INTERPRETING` → `SPEAKING` state and
   both the original and translated transcript live.
5. Interrupt at any time — CoInterpreter cancels its own playback and
   listens immediately (barge-in).
6. Press **Stop** to end the session; save or clear the transcript from
   there.

## Troubleshooting

- **Echo/feedback on speakerphone:** this is a known, documented hardware
  limitation of using one phone's mic and speaker simultaneously; a wired
  or Bluetooth headset materially improves full-duplex quality and is
  automatically preferred when connected (see `ARCHITECTURE.md` §5).
- **"Could not start a secure session":** check the backend is reachable at
  the configured `BACKEND_BASE_URL` and that `OPENAI_API_KEY` is set.
- **Reconnecting loops:** CoInterpreter uses bounded exponential backoff and
  gives up after a fixed number of attempts rather than retrying forever;
  if you see repeated `RECONNECTING`, check your network connection.

## Credential and cost notes

No pricing is hardcoded anywhere in this repository, since OpenAI's Realtime
API pricing changes independently of this app. `gpt-realtime` bills by
token; `gpt-realtime-translate` bills by audio duration. Consult OpenAI's
current pricing page before production use. Pressing Stop closes the
realtime session promptly; CoInterpreter does not leave hidden sessions
running.

## License / attribution

CoInterpreter is an independent app built on top of OpenAI's Realtime API.
It is not made, operated, or endorsed by OpenAI.
