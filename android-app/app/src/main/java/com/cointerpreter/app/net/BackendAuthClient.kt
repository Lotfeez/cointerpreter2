package com.cointerpreter.app.net

import com.cointerpreter.app.BuildConfig
import com.cointerpreter.app.model.InterpreterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The ONLY place in the Android app that talks to the CoInterpreter backend.
 * It never sees a permanent OpenAI API key (spec §15): the backend holds
 * that secret and returns a short-lived `client_secret.value` minted via
 * OpenAI's `POST /v1/realtime/client_secrets` (or `/v1/realtime/translations/
 * client_secrets` for the translation endpoint), which this client passes
 * straight to [com.cointerpreter.app.engine.RealtimeInterpreterEngine.start].
 */
class BackendAuthClient(
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SessionTokenRequest(
        val mode: String,
        val engine: String,
        val targetLanguage: String,
    )

    @Serializable
    data class SessionTokenResponse(
        val ephemeralToken: String,
        val expiresAt: Long,
        val engine: String,
    )

    sealed class Result {
        data class Success(val response: SessionTokenResponse) : Result()
        data class Failure(val kind: FailureKind, val message: String) : Result()
    }

    enum class FailureKind { NETWORK, BACKEND_UNREACHABLE, HTTP_ERROR, MALFORMED_RESPONSE }

    /**
     * Requests one short-lived credential scoped to a single translation
     * direction/engine. Conversation Mode calls this twice (once per
     * direction) via [com.cointerpreter.app.engine.ConversationDirectionCoordinator].
     */
    suspend fun requestSessionToken(
        mode: InterpreterMode,
        engineId: String,
        targetLanguageCode: String,
    ): Result = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            SessionTokenRequest.serializer(),
            SessionTokenRequest(mode = mode.name, engine = engineId, targetLanguage = targetLanguageCode),
        )
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/session-token")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Failure(
                        FailureKind.HTTP_ERROR,
                        "Backend returned HTTP ${response.code}",
                    )
                }
                val text = response.body?.string()
                    ?: return@withContext Result.Failure(FailureKind.MALFORMED_RESPONSE, "Empty response body")
                val parsed = runCatching { json.decodeFromString(SessionTokenResponse.serializer(), text) }
                    .getOrElse {
                        return@withContext Result.Failure(FailureKind.MALFORMED_RESPONSE, "Could not parse response")
                    }
                Result.Success(parsed)
            }
        } catch (io: IOException) {
            Result.Failure(FailureKind.BACKEND_UNREACHABLE, io.message ?: "Backend unreachable")
        }
    }
}
