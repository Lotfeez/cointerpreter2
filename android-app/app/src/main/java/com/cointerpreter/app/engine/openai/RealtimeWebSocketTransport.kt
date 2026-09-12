package com.cointerpreter.app.engine.openai

import android.util.Base64
import com.cointerpreter.app.engine.EngineException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Thin, engine-agnostic WebSocket connection to an OpenAI Realtime endpoint.
 *
 * DOCUMENTATION ASSUMPTION (see ARCHITECTURE.md): CoInterpreter connects
 * over WebSocket rather than WebRTC. OpenAI's own guidance recommends WebRTC
 * for client-side/browser apps (~100ms) vs WebSocket (~200ms, "server-to-
 * server"). CoInterpreter is a native Android app, not a browser, and
 * getting a correct, secure WebRTC media stack onto Android means bundling
 * the full libwebrtc AAR and implementing SDP/ICE negotiation against the
 * `/v1/realtime/calls` endpoint. That is a legitimate future optimization
 * (tracked behind this same [com.cointerpreter.app.engine.RealtimeInterpreterEngine]
 * interface) but was judged too large a surface to implement and verify
 * without a live OpenAI credential in this environment. WebSocket streaming
 * of raw PCM16 frames is fully documented, uses the same ephemeral-token
 * authentication model, and the ~100ms latency delta is acceptable for a
 * consecutive/short-utterance interpreting UX. Swapping to WebRTC later
 * requires only a new class implementing the same engine interface.
 */
internal class RealtimeWebSocketTransport(
    private val url: String,
    private val ephemeralToken: String,
    private val safetyIdentifier: String? = null,
    private val client: OkHttpClient = defaultClient(),
) {
    private val socketRef = AtomicReference<WebSocket?>(null)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Connects and exposes every received text frame as a raw JSON string. */
    fun connect(): Flow<String> = callbackFlow {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $ephemeralToken")
        if (safetyIdentifier != null) {
            requestBuilder.header("OpenAI-Safety-Identifier", safetyIdentifier)
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection established; callers observe this via the
                // session.created / translation-session-started event on
                // the message stream rather than here, keeping a single
                // source of truth for "are we really ready".
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val statusCode = response?.code
                val kind = when (statusCode) {
                    401, 403 -> EngineException.Kind.AUTH
                    429 -> EngineException.Kind.RATE_LIMITED
                    in 500..599 -> EngineException.Kind.SERVER_OUTAGE
                    else -> EngineException.Kind.TRANSPORT
                }
                close(EngineException("WebSocket failure: ${t.message}", kind, t))
            }
        }

        val socket = client.newWebSocket(requestBuilder.build(), listener)
        socketRef.set(socket)

        awaitClose {
            socket.close(1000, "client_stop")
            socketRef.set(null)
        }
    }

    fun send(text: String): Boolean = socketRef.get()?.send(text) ?: false

    fun sendAudio(pcm16: ByteArray) {
        val b64 = Base64.encodeToString(pcm16, Base64.NO_WRAP)
        send(json.encodeToString(InputAudioBufferAppendEvent.serializer(), InputAudioBufferAppendEvent(audio = b64)))
    }

    fun close() {
        socketRef.getAndSet(null)?.close(1000, "client_stop")
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // streaming socket, no read timeout
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
