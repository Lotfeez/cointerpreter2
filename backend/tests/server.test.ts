import { test } from "node:test";
import assert from "node:assert/strict";
import express from "express";
import { createSessionTokenHandler } from "../src/routes/sessionToken.js";
import { handleHealth } from "../src/routes/health.js";
import type { OpenAiRealtimeGateway } from "../src/lib/openaiClient.js";
import http from "node:http";

function buildTestApp(gateway: OpenAiRealtimeGateway) {
  const app = express();
  app.use(express.json());
  app.get("/healthz", handleHealth);
  app.post("/v1/session-token", createSessionTokenHandler(gateway));
  return app;
}

async function postJson(app: express.Express, path: string, body: unknown): Promise<{ status: number; body: any }> {
  const server = http.createServer(app);
  await new Promise<void>((resolve) => server.listen(0, resolve));
  const address = server.address();
  const port = typeof address === "object" && address ? address.port : 0;
  try {
    const res = await fetch(`http://127.0.0.1:${port}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const json = await res.json().catch(() => undefined);
    return { status: res.status, body: json };
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

const stubGateway: OpenAiRealtimeGateway = {
  async mintSessionToken() {
    return { ephemeralToken: "ek_stub_token", expiresAt: 1234, engine: "gpt-realtime" };
  },
};

test("GET /healthz returns 200 ok without leaking config", async () => {
  const app = express();
  app.get("/healthz", handleHealth);
  const server = http.createServer(app);
  await new Promise<void>((resolve) => server.listen(0, resolve));
  const address = server.address();
  const port = typeof address === "object" && address ? address.port : 0;
  const res = await fetch(`http://127.0.0.1:${port}/healthz`);
  const json = await res.json();
  assert.equal(res.status, 200);
  assert.deepEqual(json, { status: "ok" });
  await new Promise<void>((resolve) => server.close(() => resolve()));
});

test("POST /v1/session-token returns 200 and never echoes a raw OpenAI API key field", async () => {
  const app = buildTestApp(stubGateway);
  const { status, body } = await postJson(app, "/v1/session-token", {
    mode: "PROFESSIONAL",
    engine: "gpt-realtime",
    targetLanguage: "en",
  });
  assert.equal(status, 200);
  assert.equal(body.ephemeralToken, "ek_stub_token");
  assert.equal(body.engine, "gpt-realtime");
  assert.equal(JSON.stringify(body).includes("OPENAI_API_KEY"), false);
});

test("POST /v1/session-token rejects an invalid mode with 400", async () => {
  const app = buildTestApp(stubGateway);
  const { status, body } = await postJson(app, "/v1/session-token", {
    mode: "NOT_A_REAL_MODE",
    engine: "gpt-realtime",
    targetLanguage: "en",
  });
  assert.equal(status, 400);
  assert.equal(body.error, "invalid_mode");
});

test("POST /v1/session-token rejects an unreviewed target language with 400", async () => {
  const app = buildTestApp(stubGateway);
  const { status, body } = await postJson(app, "/v1/session-token", {
    mode: "CONVERSATION",
    engine: "gpt-realtime-translate",
    targetLanguage: "xx",
  });
  assert.equal(status, 400);
  assert.equal(body.error, "invalid_target_language");
});

test("POST /v1/session-token maps an upstream gateway failure to a sanitized 502", async () => {
  const failingGateway: OpenAiRealtimeGateway = {
    async mintSessionToken() {
      const { OpenAiGatewayError } = await import("../src/lib/openaiClient.js");
      throw new OpenAiGatewayError(500, "OpenAI request failed");
    },
  };
  const app = buildTestApp(failingGateway);
  const { status, body } = await postJson(app, "/v1/session-token", {
    mode: "PROFESSIONAL",
    engine: "gpt-realtime",
    targetLanguage: "en",
  });
  assert.equal(status, 502);
  assert.equal(body.error, "upstream_error");
});
