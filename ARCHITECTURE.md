# CoInterpreter — Architecture

This document records the actual architecture implemented in this
repository, and the specific OpenAI documentation this implementation is
based on, so future changes can be checked against the same sources rather
than against tribal memory.

## 1. Documentation assumptions (OpenAI Realtime API, as of September 2026)

Everything below was verified against official OpenAI documentation
(developers.openai.com) during implementation, not assumed from older
tutorials. If OpenAI's API has changed since, re-verify against these same
pages before trusting this section:

- `developers.openai.com/api/docs/guides/realtime` — GA overview, ephemeral
  client secrets, WebRTC vs WebSocket vs SIP.
- `developers.openai.com/api/docs/guides/realtime-conversations` — session
  lifecycle, event flow.
- `developers.openai.com/api/docs/guides/realtime-transcription` — dedicated
  transcription sessions (`gpt-realtime-whisper`), `audio/pcm` @ 24kHz.
- `developers.openai.com/api/docs/guides/realtime-translation` — the
  dedicated translation endpoint and `gpt-realtime-translate` model.
- `developers.openai.com/api/docs/api-reference/realtime-sessions` —
  `RealtimeSessionClientSecret` / `RealtimeSessionCreateResponse` shapes.
- `developers.openai.com/api/docs/models/gpt-realtime`,
  `.../gpt-realtime-translate` — model capability pages.
- OpenAI Developers blog, "Developer notes on the Realtime API" — session
  duration limits (up to 60 minutes), token windows, sideband connections.

Key facts this implementation relies on:

1. **Ephemeral credentials.** The server mints a short-lived credential via
   `POST /v1/realtime/client_secrets` (general model) or
   `POST /v1/realtime/translations/client_secrets` (translation model),
   authenticated with the permanent `OPENAI_API_KEY` on the **server only**.
   The Android app receives only the resulting `value` (the ephemeral
   token) and uses it as a bearer token on the realtime WebSocket. Tokens
   are short-lived (documented default around one minute to *establish* a
   connection); the session itself can then run up to the documented
   60-minute session cap.
2. **Two realtime models, two endpoints.**
   - `gpt-realtime` (general-purpose): connect to
     `wss://api.openai.com/v1/realtime?model=gpt-realtime`. Accepts full
     custom `session.instructions`, voice selection, and tool use. This is
     the only engine that can carry [`InterpreterConstitution`](android-app/app/src/main/java/com/cointerpreter/app/engine/InterpreterConstitution.kt)
     and the session glossary.
   - `gpt-realtime-translate` (purpose-built): connect to
     `wss://api.openai.com/v1/realtime/translations?model=gpt-realtime-translate`.
     Auto-detects the source language across 70+ supported languages and
     translates into a single configured `session.audio.output.language`
     (13 supported output languages). It does **not** accept custom
     prompting, tools, or voice selection — voice dynamically follows the
     source speaker's tone instead. Structurally, it cannot "answer a
     question" or "join the conversation" because it has no channel to
     generate anything other than a translation of what it hears.
3. **GA event names.** `session.update`, `input_audio_buffer.append`,
   `input_audio_buffer.commit`, `input_audio_buffer.speech_started` /
   `speech_stopped`, `response.create`, `response.cancel`,
   `response.output_audio.delta` / `.done`,
   `response.output_audio_transcript.delta` / `.done`,
   `conversation.item.input_audio_transcription.delta` / `.completed`,
   `conversation.item.truncate` (barge-in), `error`. Audio configuration
   moved under `session.audio.{input,output}`, not top-level `voice`/
   `input_audio_format` fields from older preview APIs.
4. **Audio format.** 24kHz mono PCM16 (`audio/pcm`, `rate: 24000`) for both
   input and output. CoInterpreter's [`AudioCaptureEngine`](android-app/app/src/main/java/com/cointerpreter/app/audio/AudioCaptureEngine.kt)
   and [`AudioPlaybackEngine`](android-app/app/src/main/java/com/cointerpreter/app/audio/AudioPlaybackEngine.kt)
   both operate at this exact rate to avoid an unnecessary resample step.
5. **Safety identifier.** OpenAI recommends (does not require) an
   `OpenAI-Safety-Identifier` header on requests, sent server-side when
   minting a client secret. The backend sends a random, non-identifying
   hash — CoInterpreter has no user accounts, so there is no real user ID to
   pseudonymize.

## 2. Why WebSocket, not WebRTC (transport decision)

OpenAI recommends WebRTC for client-side/browser apps (~100ms) over
WebSocket (~200ms, described as "server-to-server"). CoInterpreter is a
**native Android app**, not a browser. A correct, secure native WebRTC
integration means bundling the full `libwebrtc` AAR and implementing
SDP/ICE negotiation against `/v1/realtime/calls`. That is a legitimate
future optimization — and the architecture below makes it a drop-in
replacement — but was judged too large a surface to implement *and verify*
without a live OpenAI credential in the environment this repository was
built in.

WebSocket streaming of raw PCM16 frames:
- Uses the exact same ephemeral-token authentication model.
- Is fully documented and stable.
- Costs roughly 100ms of extra round-trip latency, which is acceptable for
  a consecutive/short-utterance interpreting UX (this is not a sub-100ms
  gaming or lip-sync use case).

Swapping to WebRTC later requires only a new class implementing
[`RealtimeInterpreterEngine`](android-app/app/src/main/java/com/cointerpreter/app/engine/RealtimeInterpreterEngine.kt);
nothing else in the app changes. This is the entire point of that
interface existing.

## 3. Engine abstraction and mode → engine mapping

```
RealtimeInterpreterEngine (interface)
├── OpenAiGptRealtimeEngine       -- gpt-realtime, custom instructions, glossary
└── OpenAiRealtimeTranslateEngine -- gpt-realtime-translate, no custom prompting
```

`InterpreterEngineFactory` maps modes to engines:

| Mode | Engine | Why |
|---|---|---|
| Professional | `OpenAiGptRealtimeEngine` | Needs the full Interpreter Constitution + session glossary as an editable instruction, for terminology precedence (spec §25). |
| Conversation | `OpenAiRealtimeTranslateEngine` (×2, see below) | Lower latency, tone-matched voice, structurally cannot answer instead of translate. |
| One-Way | `OpenAiRealtimeTranslateEngine` (×1) | Same as above; single direction, single session. |

### Conversation Mode bidirectionality

`gpt-realtime-translate` only translates towards **one** configured target
language per session, and OpenAI's own guidance notes it "may not translate
audio that is already in the listener's selected output language."
[`ConversationDirectionCoordinator`](android-app/app/src/main/java/com/cointerpreter/app/engine/ConversationDirectionCoordinator.kt)
exploits this directly: it opens **two** translation sessions fed the same
microphone audio, one targeting language A and one targeting language B.
Whichever session actually emits `response.output_audio.delta` for a given
utterance is — by construction — the correct direction, so CoInterpreter
gets automatic bidirectional switching (spec §5A, TEST 7) without any
bespoke client-side language-ID model. The cost is double the concurrent
realtime sessions during Conversation Mode; this trade-off is deliberate
and documented rather than hidden.

## 4. Session lifecycle & state machine

[`SessionStateMachine`](android-app/app/src/main/java/com/cointerpreter/app/state/SessionState.kt)
is a pure function `(SessionState, SessionEvent) -> SessionState?` with no
I/O, so every legal and illegal transition is unit-testable without mocking
Android or the network (see `SessionStateMachineTest` and the 41
sandbox-executed checks in `verification/`). States:

```
Idle → RequestingPermission → Authenticating → Connecting → Ready
  → Listening → Interpreting → Speaking → Ready (loop)
Any active state → Reconnecting → Ready | Error
Any state (except Stopping/Stopped) → Error (on FatalError)
Any active state → Stopping → Stopped
```

Barge-in is modeled explicitly: `Speaking + SpeechDetected → Listening`
directly, skipping back through `Ready`. The session layer reacts to this
transition by calling `engine.cancelResponse()` (which sends
`response.cancel` + `conversation.item.truncate` with the exact played-back
audio duration) and `AudioPlaybackEngine.truncateAndFlush()` in the same
beat, keeping local playback state and server conversation state
synchronized (spec §12).

`SessionStateMachine.isSessionActive()` is the single predicate that gates
whether audio capture should be running, which prevents duplicate sessions
on screen rotation / recreation: the ViewModel survives configuration
changes (standard `ViewModel` lifecycle) and a second `start()` call is
simply rejected by the state machine (`Ready + StartRequested → null`).

### Reconnection

[`ReconnectPolicy`](android-app/app/src/main/java/com/cointerpreter/app/state/ReconnectPolicy.kt)
implements bounded exponential backoff (base 500ms, capped, max attempts)
per spec §27 — no endless aggressive retry loops.

## 5. Audio pipeline

- **Capture:** `AudioRecord` with `VOICE_COMMUNICATION` source, 24kHz mono
  PCM16, ~20ms frames streamed directly to the engine as
  `input_audio_buffer.append` events (base64-encoded). No separate
  speech-to-text pipeline is built client-side; transcription comes from
  the realtime session itself (`gpt-realtime-whisper` for the translation
  path, native transcription events for the general model), per spec §10.
- **Playback:** a single long-lived `AudioTrack` in streaming mode with
  `PERFORMANCE_MODE_LOW_LATENCY`, so response audio starts as soon as the
  first chunk arrives.
- **Routing:** [`AudioRouteManager`](android-app/app/src/main/java/com/cointerpreter/app/audio/AudioRouteManager.kt)
  requests `AUDIOFOCUS_GAIN`, sets `MODE_IN_COMMUNICATION` (enabling
  platform echo cancellation where the device supports it), and prefers
  Bluetooth SCO/BLE → wired headset → earpiece → speaker, using
  `setCommunicationDevice` on API 31+ and the legacy
  `startBluetoothSco`/`isSpeakerphoneOn` APIs below that.
- **Echo cancellation caveat (documented, not hidden):** CoInterpreter does
  not claim perfect echo cancellation on speakerphone. `MODE_IN_COMMUNICATION`
  enables the platform's built-in AEC on devices that implement it, but this
  is a real hardware/API limitation, and headphones materially improve
  full-duplex quality (see README troubleshooting).
- **Foreground service:** [`InterpretingForegroundService`](android-app/app/src/main/java/com/cointerpreter/app/audio/InterpretingForegroundService.kt)
  keeps the mic + socket alive if the app is backgrounded mid-session,
  required by Android 14+'s foreground-service-type rules. It never starts
  unless a session is active and is stopped immediately on session end.

## 6. Backend / authentication boundary

```
Android app  --(mode, engine, targetLanguage)-->  POST /v1/session-token
                                                          |
                                              backend validates input,
                                              rate-limits, holds
                                              OPENAI_API_KEY only here
                                                          |
                                              POST /v1/realtime[/translations]/client_secrets
                                              (Authorization: Bearer OPENAI_API_KEY)
                                                          |
Android app  <--(ephemeralToken, expiresAt)--  backend returns ONLY the ephemeral value
                                                          |
Android app  --(Authorization: Bearer ephemeralToken)-->  wss://api.openai.com/v1/realtime[...]
```

The permanent API key exists in exactly one place: the backend process's
environment, loaded from `.env` (never committed; see `.env.example`). It
is never logged, never returned in any HTTP response body, and never
reaches the APK in any form — verified by the repository secret scan in
`BUILD_REPORT.md`.

## 7. Security boundaries summary

- No permanent credential in Kotlin, resources, Gradle config, or the APK.
- Backend validates `mode`, `engine`, and `targetLanguage` against fixed
  allow-lists before calling OpenAI (defense in depth against a modified
  client).
- Backend rate-limits `/v1/session-token` (default 30 req/min/IP).
- Backend sanitizes upstream OpenAI error bodies before they reach the
  client or logs, so a leaked upstream message can't leak account details.
- No conversation audio or transcript content is logged anywhere in the
  backend or the Android app.
