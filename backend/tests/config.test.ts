import { test } from "node:test";
import assert from "node:assert/strict";
import { loadConfig, assertRuntimeConfigForServing, MissingApiKeyError } from "../src/lib/config.js";

test("loadConfig applies documented defaults when env vars are absent", () => {
  const config = loadConfig({});
  assert.equal(config.port, 8787);
  assert.equal(config.openAiApiBaseUrl, "https://api.openai.com");
  assert.deepEqual(config.allowedOrigins, ["http://localhost:8787"]);
  assert.equal(config.sessionTokenRateLimitPerMinute, 30);
  assert.equal(config.openAiApiKey, "");
});

test("loadConfig never fabricates an API key from other env vars", () => {
  const config = loadConfig({ PORT: "9000", OPENAI_API_BASE_URL: "https://example-azure.openai.azure.com" });
  assert.equal(config.openAiApiKey, "");
  assert.equal(config.port, 9000);
  assert.equal(config.openAiApiBaseUrl, "https://example-azure.openai.azure.com");
});

test("assertRuntimeConfigForServing throws MissingApiKeyError when no key is configured", () => {
  const config = loadConfig({});
  assert.throws(() => assertRuntimeConfigForServing(config), MissingApiKeyError);
});

test("assertRuntimeConfigForServing passes silently once a key is present", () => {
  const config = loadConfig({ OPENAI_API_KEY: "sk-test-not-a-real-key" });
  assert.doesNotThrow(() => assertRuntimeConfigForServing(config));
});
