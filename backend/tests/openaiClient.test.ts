import { test } from "node:test";
import assert from "node:assert/strict";
import { HttpOpenAiRealtimeGateway, OpenAiGatewayError } from "../src/lib/openaiClient.js";
import type { AppConfig } from "../src/lib/config.js";

const baseConfig: AppConfig = {
  port: 8787,
  openAiApiKey: "sk-test-fake-key-never-a-real-secret",
  openAiApiBaseUrl: "https://api.openai.com",
  allowedOrigins: ["http://localhost:8787"],
  sessionTokenRateLimitPerMinute: 30,
};

function fakeFetch(status: number, jsonBody: unknown): typeof fetch {
  return (async () =>
    ({
      ok: status >= 200 && status < 300,
      status,
      json: async () => jsonBody,
      text: async () => JSON.stringify(jsonBody),
    }) as Response) as unknown as typeof fetch;
}

test("mintSessionToken calls the standard client_secrets endpoint for gpt-realtime", async () => {
  let capturedUrl = "";
  let capturedBody: any = null;
  const fetchSpy: typeof fetch = (async (url: any, init: any) => {
    capturedUrl = String(url);
    capturedBody = JSON.parse(init.body);
    return { ok: true, status: 200, json: async () => ({ value: "ek_abc123", expires_at: 1234567890 }) } as Response;
  }) as unknown as typeof fetch;

  const gateway = new HttpOpenAiRealtimeGateway(baseConfig, fetchSpy);
  const result = await gateway.mintSessionToken({
    mode: "PROFESSIONAL",
    engine: "gpt-realtime",
    targetLanguageCode: "en",
    safetyIdentifier: "abc",
  });

  assert.equal(capturedUrl, "https://api.openai.com/v1/realtime/client_secrets");
  assert.equal(capturedBody.session.model, "gpt-realtime");
  assert.equal(capturedBody.session.type, "realtime");
  assert.equal(result.ephemeralToken, "ek_abc123");
  assert.equal(result.engine, "gpt-realtime");
});

test("mintSessionToken calls the dedicated translations endpoint and sets target language for gpt-realtime-translate", async () => {
  let capturedUrl = "";
  let capturedBody: any = null;
  const fetchSpy: typeof fetch = (async (url: any, init: any) => {
    capturedUrl = String(url);
    capturedBody = JSON.parse(init.body);
    return { ok: true, status: 200, json: async () => ({ value: "ek_translate_1", expires_at: 999 }) } as Response;
  }) as unknown as typeof fetch;

  const gateway = new HttpOpenAiRealtimeGateway(baseConfig, fetchSpy);
  const result = await gateway.mintSessionToken({
    mode: "CONVERSATION",
    engine: "gpt-realtime-translate",
    targetLanguageCode: "ar",
    safetyIdentifier: "abc",
  });

  assert.equal(capturedUrl, "https://api.openai.com/v1/realtime/translations/client_secrets");
  assert.equal(capturedBody.session.model, "gpt-realtime-translate");
  assert.equal(capturedBody.session.audio.output.language, "ar");
  assert.equal(result.ephemeralToken, "ek_translate_1");
});

test("mintSessionToken accepts the nested client_secret response shape too", async () => {
  const gateway = new HttpOpenAiRealtimeGateway(
    baseConfig,
    fakeFetch(200, { client_secret: { value: "ek_nested", expires_at: 42 } }),
  );
  const result = await gateway.mintSessionToken({
    mode: "PROFESSIONAL",
    engine: "gpt-realtime",
    targetLanguageCode: "en",
    safetyIdentifier: "abc",
  });
  assert.equal(result.ephemeralToken, "ek_nested");
  assert.equal(result.expiresAt, 42);
});

test("mintSessionToken throws OpenAiGatewayError on a non-2xx upstream response, without leaking the raw body", async () => {
  const gateway = new HttpOpenAiRealtimeGateway(
    baseConfig,
    fakeFetch(401, { error: { message: "Incorrect API key provided: sk-verysecret1234567890" } }),
  );
  await assert.rejects(
    () =>
      gateway.mintSessionToken({
        mode: "PROFESSIONAL",
        engine: "gpt-realtime",
        targetLanguageCode: "en",
        safetyIdentifier: "abc",
      }),
    (err: unknown) => {
      assert.ok(err instanceof OpenAiGatewayError);
      assert.equal(err.upstreamStatus, 401);
      assert.ok(!err.message.includes("sk-verysecret1234567890"), "error message must not leak the upstream secret text");
      return true;
    },
  );
});

test("mintSessionToken throws on a malformed 200 response instead of returning undefined fields", async () => {
  const gateway = new HttpOpenAiRealtimeGateway(baseConfig, fakeFetch(200, { unexpected: "shape" }));
  await assert.rejects(() =>
    gateway.mintSessionToken({
      mode: "PROFESSIONAL",
      engine: "gpt-realtime",
      targetLanguageCode: "en",
      safetyIdentifier: "abc",
    }),
  );
});
