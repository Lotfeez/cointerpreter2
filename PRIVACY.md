# CoInterpreter — Privacy

This document describes what CoInterpreter's shipped code actually does, not
an aspirational policy. It is also shown, in shorter form, in the app's
Privacy screen (`ui/privacy/PrivacyScreen.kt`).

## What is transmitted to OpenAI

While an interpreting session is active, your microphone audio is streamed
as PCM16 audio frames to OpenAI's Realtime API (over a WebSocket connection
authenticated with a short-lived token — see ARCHITECTURE.md) in order to
produce interpreted speech and a transcript. Audio capture stops the
instant you press Stop or mute the microphone; no audio is captured before
you press Start.

## What is transmitted to the CoInterpreter backend

Only: the selected interpreting mode, the chosen engine identifier, and the
target language code, in order to request a short-lived OpenAI credential.
No audio, no transcript content, and no glossary content is ever sent to
the CoInterpreter backend — the backend's only job is minting the ephemeral
token; your device talks to OpenAI's Realtime API directly after that.

## What is stored locally on your device

- App settings (default languages, mode, voice, theme, etc.) via Jetpack
  DataStore.
- Your session glossary, if you choose to keep entries (`add`/`edit` persist
  to a local JSON file; `clear` deletes it).
- Any transcript you explicitly choose to save (`Save transcript`), as a
  local JSON file under the app's private storage.

## What is NOT stored

- CoInterpreter never stores an OpenAI API key on the device, at any time,
  in any form. The device only ever holds a short-lived ephemeral token
  that expires on its own.
- CoInterpreter does not record or retain raw audio after a session ends.
  Audio is streamed and discarded; nothing is written to disk unless you
  explicitly save a transcript (text only, never audio).
- Transcripts are not uploaded to any cloud service operated by
  CoInterpreter. They stay on-device unless you manually export/share them
  yourself through the OS share sheet (not currently wired up in this
  build; transcripts currently live only in local app storage).
- No advertising SDKs, no behavioral analytics SDKs, no telemetry beyond
  what the OS itself may collect.

## When the microphone is active

Only between pressing Start and pressing Stop (or muting). Android's system
microphone-in-use indicator is visible for the app's entire foreground (and,
if backgrounded mid-session, foreground-service) lifetime — CoInterpreter
does not attempt to hide or suppress that indicator.

## Logs

CoInterpreter does not write transcript text, glossary content, or audio to
Logcat. The backend does not log request bodies, transcript content, or
audio; the only server-side logging is a generic error-category message
(see `backend/src/middleware/errorHandler.ts`) with upstream error bodies
sanitized before they are ever written anywhere.

## How to delete your data

- **Session transcript:** "Clear transcript" in the session screen, or
  delete individual saved transcripts from Settings.
- **Glossary:** "Clear all" in the glossary editor.
- **Settings:** uninstalling the app removes all local DataStore/file
  storage, since none of it is backed up to a CoInterpreter-operated
  server.

## Third parties

CoInterpreter sends audio to OpenAI, L.L.C. to perform interpretation.
OpenAI's own handling of that audio is governed by OpenAI's own privacy
policy and API data-usage terms, not by CoInterpreter. CoInterpreter is an
independent app; it is not made, operated, or endorsed by OpenAI.
