import "dotenv/config";

/**
 * Centralized, validated environment configuration. Fails fast and loudly
 * if OPENAI_API_KEY is missing rather than silently starting a backend that
 * can never mint a real session token (spec §15).
 */
export interface AppConfig {
  port: number;
  openAiApiKey: string;
  openAiApiBaseUrl: string;
  allowedOrigins: string[];
  sessionTokenRateLimitPerMinute: number;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const openAiApiKey = env.OPENAI_API_KEY?.trim() ?? "";
  const port = Number.parseInt(env.PORT ?? "8787", 10);
  const openAiApiBaseUrl = (env.OPENAI_API_BASE_URL?.trim() || "https://api.openai.com").replace(/\/$/, "");
  const allowedOrigins = (env.ALLOWED_ORIGINS ?? "http://localhost:8787")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  const sessionTokenRateLimitPerMinute = Number.parseInt(env.SESSION_TOKEN_RATE_LIMIT_PER_MINUTE ?? "30", 10);

  return { port, openAiApiKey, openAiApiBaseUrl, allowedOrigins, sessionTokenRateLimitPerMinute };
}

export function assertRuntimeConfigForServing(config: AppConfig): void {
  // Deliberately NOT thrown at module import time, and NOT required for
  // `npm run build` / CI (spec §11 of the follow-up prompt: a debug APK and
  // this backend's build/tests must not require a real OpenAI key). It IS
  // required the moment someone actually calls /v1/session-token.
  if (!config.openAiApiKey) {
    throw new MissingApiKeyError();
  }
}

export class MissingApiKeyError extends Error {
  constructor() {
    super("OPENAI_API_KEY is not configured on the server.");
    this.name = "MissingApiKeyError";
  }
}
