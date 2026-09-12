import type { AppConfig } from "./config.js";
import type { EngineId, InterpreterMode } from "../model/sessionModes.js";

/**
 * The ONLY module in this backend that sends the permanent OPENAI_API_KEY
 * anywhere. It calls OpenAI's official ephemeral-credential endpoints
 * (documented at developers.openai.com as of Sep 2026 -- see
 * ARCHITECTURE.md "Documentation assumptions") and returns only the
 * short-lived client secret to its caller. The permanent key never appears
 * in the function's return value.
 *
 * Two endpoints are used depending on which engine the Android app asked
 * for:
 *  - `POST /v1/realtime/client_secrets` for the general-purpose gpt-realtime
 *    model (Professional Mode).
 *  - `POST /v1/realtime/translations/client_secrets` for the dedicated
 *    gpt-realtime-translate model (Conversation / One-Way Mode).
 */

export interface MintedSessionToken {
  ephemeralToken: string;
  expiresAt: number;
  engine: EngineId;
}

export interface OpenAiRealtimeGateway {
  mintSessionToken(params: {
    mode: InterpreterMode;
    engine: EngineId;
    targetLanguageCode: string;
    safetyIdentifier: string;
  }): Promise<MintedSessionToken>;
}

type FetchLike = typeof fetch;

export class HttpOpenAiRealtimeGateway implements OpenAiRealtimeGateway {
  constructor(
    private readonly config: AppConfig,
    private readonly fetchImpl: FetchLike = fetch,
  ) {}

  async mintSessionToken(params: {
    mode: InterpreterMode;
    engine: EngineId;
    targetLanguageCode: string;
    safetyIdentifier: string;
  }): Promise<MintedSessionToken> {
    const { engine, targetLanguageCode, safetyIdentifier } = params;

    const isTranslateEngine = engine === "gpt-realtime-translate";
    const path = isTranslateEngine ? "/v1/realtime/translations/client_secrets" : "/v1/realtime/client_secrets";

    const body = isTranslateEngine
      ? {
          session: {
            model: engine,
            audio: { output: { language: targetLanguageCode } },
          },
        }
      : {
          session: {
            type: "realtime",
            model: engine,
          },
        };

    const response = await this.fetchImpl(`${this.config.openAiApiBaseUrl}${path}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.config.openAiApiKey}`,
        "Content-Type": "application/json",
        "OpenAI-Safety-Identifier": safetyIdentifier,
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const text = await response.text().catch(() => "");
      throw new OpenAiGatewayError(response.status, sanitizeUpstreamError(text));
    }

    const json = (await response.json()) as {
      value?: string;
      expires_at?: number;
      client_secret?: { value?: string; expires_at?: number };
    };

    // The GA client-secrets response nests the ephemeral value under
    // `client_secret` for /v1/realtime/client_secrets, but the translation
    // endpoint has been observed to return it at the top level in some
    // OpenAI GA snapshots. Accept either shape defensively rather than
    // crashing the whole session-start flow if OpenAI's field nesting
    // shifts slightly between snapshots (documented ARCHITECTURE.md
    // assumption -- re-verify against OpenAI's changelog if this throws).
    const value = json.client_secret?.value ?? json.value;
    const expiresAt = json.client_secret?.expires_at ?? json.expires_at;

    if (!value || !expiresAt) {
      throw new OpenAiGatewayError(502, "Malformed client secret response from OpenAI");
    }

    return { ephemeralToken: value, expiresAt, engine };
  }
}

export class OpenAiGatewayError extends Error {
  constructor(
    public readonly upstreamStatus: number,
    message: string,
  ) {
    super(message);
    this.name = "OpenAiGatewayError";
  }
}

/** Strips anything that looks like it could contain request/account details before it reaches our own error responses or logs (spec §15 "sanitize server errors"). */
function sanitizeUpstreamError(text: string): string {
  if (!text) return "OpenAI request failed";
  return text.length > 200 ? "OpenAI request failed" : "OpenAI request failed (see server logs for detail category only)";
}
